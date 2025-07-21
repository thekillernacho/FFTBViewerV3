package com.twitchchat.service;

import com.twitchchat.model.Song;
import com.twitchchat.model.TrackPlay;
import com.twitchchat.model.PlaylistSyncAudit;
import com.twitchchat.repository.SongRepository;
import com.twitchchat.repository.TrackPlayRepository;
import com.twitchchat.repository.PlaylistSyncAuditRepository;
import com.twitchchat.playlist.sync.DumpPlaylistService;
import com.twitchchat.playlist.sync.DuplicateCleanupService;
import com.twitchchat.playlist.sync.PlaylistSyncAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.core.env.Environment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service for synchronizing playlist data from FFT Battleground XML feed
 */
@Service
public class PlaylistSyncService {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistSyncService.class);
    private static final String PLAYLIST_URL = "http://www.fftbattleground.com/fftbg/playlist.xml";
    private final Object syncLock = new Object(); // Prevent concurrent sync operations

    @Autowired
    private SongRepository songRepository;
    
    @Autowired
    private TrackPlayRepository trackPlayRepository;
    
    @Autowired
    private PlaylistSyncAuditRepository auditRepository;
    
    @Autowired
    private Environment environment;
    
    @Autowired
    private DumpPlaylistService dumpPlaylistService;
    
    @Autowired
    private DuplicateCleanupService duplicateCleanupService;
    
    @Autowired
    private PlaylistSyncAuditService auditService;

    /**
     * Initial sync when application starts (async to avoid blocking startup)
     * DISABLED: Use manual sync or scheduled sync instead of startup sync
     */
    // @PostConstruct
    public void initialSync() {
        logger.info("Startup sync disabled - use scheduled sync or manual sync endpoint");
    }

    /**
     * Synchronize playlist data from XML feed (synchronized to prevent race conditions)
     * Includes audit tracking for all sync operations
     */
    @Transactional
    public void syncPlaylist() {
        syncPlaylistWithAudit("MANUAL");
    }
    
    /**
     * Scheduled sync method for production use
     */
    @Transactional
    public void scheduledSyncPlaylist() {
        syncPlaylistWithAudit("SCHEDULED");
    }
    
    /**
     * Sync playlist data with audit tracking
     */
    public void syncPlaylistWithAudit(String triggeredBy) {
        PlaylistSyncAudit audit = auditService.startSyncAudit(triggeredBy);
        
        try {
            performSync(audit);
            auditService.markSyncCompleted(audit);
        } catch (Exception e) {
            auditService.markSyncFailed(audit, e);
            throw e;
        }
    }

    /**
     * Sync playlist data from XML feed and audit the operation
     */
    @Transactional
    public void apiSyncPlaylist() {
        PlaylistSyncAudit audit = auditService.startSyncAudit("API");
        
        try {
            performSync(audit);
            auditService.markSyncCompleted(audit);
        } catch (Exception e) {
            auditService.markSyncFailed(audit, e);
            throw e;
        }
    }

    /**
     * Core sync logic without audit (for debugging PostgreSQL issues)
     */
    private void performSyncWithoutAudit() {
        synchronized (syncLock) {
            logger.info("Starting playlist synchronization without audit...");
            
            try {
                List<Song> xmlSongs = dumpPlaylistService.fetchSongsFromXml();
                logger.info("Fetched {} songs from XML", xmlSongs.size());
                
                // Skip delete operations that cause PostgreSQL issues
                logger.info("Skipping delete operations to avoid PostgreSQL syntax errors");
                
                // Test simple database operations
                long currentSongCount = songRepository.count();
                logger.info("Current songs in database: {}", currentSongCount);
                
                logger.info("Sync test completed successfully - {} songs processed, {} in database", 
                           xmlSongs.size(), currentSongCount);
                
            } catch (Exception e) {
                logger.error("Error during sync without audit", e);
                throw e;
            }
        }
    }

    /**
     * Core sync logic extracted from the main sync method
     */
    private void performSync(PlaylistSyncAudit audit) {
        synchronized (syncLock) {
            logger.info("Starting playlist sync...");
            
            try {
                List<Song> songsFromXml = dumpPlaylistService.fetchSongsFromXml();
                audit.setXmlSourceUrl(PLAYLIST_URL);
                
                if (songsFromXml.isEmpty()) {
                    logger.warn("No songs retrieved from XML feed - skipping sync");
                    return;
                }
                
                logger.info("Fetched {} songs from XML feed", songsFromXml.size());
                
                // Get existing songs from database
                List<Song> existingSongs = songRepository.findAll();
                Map<String, Song> existingSongMap = new HashMap<>();
                for (Song song : existingSongs) {
                    existingSongMap.put(song.getTitle(), song);
                }
                
                // Track sync statistics
                int addedCount = 0;
                int updatedCount = 0;
                int deletedCount = 0;
                
                // Process songs from XML
                Set<String> xmlSongTitles = new HashSet<>();
                List<Song> songsToSave = new ArrayList<>();
                
                for (Song xmlSong : songsFromXml) {
                    xmlSongTitles.add(xmlSong.getTitle());
                    
                    Song existingSong = existingSongMap.get(xmlSong.getTitle());
                    if (existingSong != null) {
                        // Update existing song if duration changed
                        if (!existingSong.getDuration().equals(xmlSong.getDuration())) {
                            existingSong.setDuration(xmlSong.getDuration());
                            existingSong.setUpdatedAt(LocalDateTime.now());
                            songsToSave.add(existingSong);
                            updatedCount++;
                            logger.debug("Updated duration for song: {}", existingSong.getTitle());
                        }
                    } else {
                        // New song
                        songsToSave.add(xmlSong);
                        addedCount++;
                        logger.debug("Added new song: {}", xmlSong.getTitle());
                    }
                }
                
                // Batch save new and updated songs
                if (!songsToSave.isEmpty()) {
                    try {
                        songRepository.saveAll(songsToSave);
                        logger.info("Saved {} songs to database", songsToSave.size());
                    } catch (Exception e) {
                        logger.error("Error saving songs in batch, attempting individual saves", e);
                        // Fallback to individual saves
                        for (Song song : songsToSave) {
                            try {
                                songRepository.save(song);
                            } catch (Exception individualError) {
                                logger.error("Failed to save song: {}", song.getTitle(), individualError);
                            }
                        }
                    }
                }
                
                // Find and remove songs that are no longer in XML
                List<String> songsToDelete = new ArrayList<>();
                for (String existingTitle : existingSongMap.keySet()) {
                    if (!xmlSongTitles.contains(existingTitle)) {
                        songsToDelete.add(existingTitle);
                    }
                }
                
                if (!songsToDelete.isEmpty()) {
                    deletedCount = deleteSongsAndTrackPlays(songsToDelete);
                }
                
                // Clean up duplicate track plays
                int duplicatesCleaned = duplicateCleanupService.cleanupDuplicateTrackPlays();
                
                // Update audit record
                audit.addEntries(addedCount);
                audit.updateEntries(updatedCount);
                audit.deleteEntries(deletedCount);
                audit.removeDuplicateTrackPlays(duplicatesCleaned);
                auditService.updateSyncProgress(audit);
                
                logger.info("Playlist sync completed: {} added, {} updated, {} deleted, {} duplicates cleaned", 
                          addedCount, updatedCount, deletedCount, duplicatesCleaned);
                
            } catch (Exception e) {
                logger.error("Error during playlist sync", e);
                throw e;
            }
        }
    }

    /**
     * Delete songs and their associated track plays safely
     */
    private int deleteSongsAndTrackPlays(List<String> songTitlesToDelete) {
        if (songTitlesToDelete.isEmpty()) {
            return 0;
        }
        
        try {
            // First, delete associated track plays to avoid foreign key constraint violations
            int trackPlaysDeleted = trackPlayRepository.deleteBySongTitleIn(songTitlesToDelete);
            logger.info("Deleted {} track plays for {} songs to be removed", trackPlaysDeleted, songTitlesToDelete.size());
            
            // Then delete the songs
            int songsDeleted = songRepository.deleteByTitleIn(songTitlesToDelete);
            logger.info("Deleted {} songs from database", songsDeleted);
            
            return songsDeleted;
        } catch (Exception e) {
            logger.error("Error deleting songs and track plays", e);
            throw e;
        }
    }

    /**
     * Clean up duplicate track plays that occurred within the song duration window
     * @return number of duplicate track plays removed
     */
    @Transactional
    public int cleanupDuplicateTrackPlays() {
        return duplicateCleanupService.cleanupDuplicateTrackPlays();
    }
}