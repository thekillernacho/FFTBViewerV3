package com.twitchchat.service;

import com.twitchchat.model.Song;
import com.twitchchat.model.TrackPlay;
import com.twitchchat.repository.SongRepository;
import com.twitchchat.repository.TrackPlayRepository;
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

    /**
     * Initial sync when application starts (async to avoid blocking startup)
     * DISABLED: Use manual sync or scheduled sync instead of startup sync
     */
    // @PostConstruct
    public void initialSync() {
        logger.info("Startup sync disabled - use scheduled sync or manual sync endpoint");
        // new Thread(() -> {
        //     try {
        //         Thread.sleep(5000); // Wait 5 seconds for app to fully start
        //         syncPlaylist();
        //     } catch (Exception e) {
        //         logger.error("Initial sync failed", e);
        //     }
        // }).start();
    }

    /**
     * Scheduled sync moved to ProductionSchedulingConfig
     * This method is no longer used
     */
    // Moved to ProductionSchedulingConfig for proper profile control

    /**
     * Synchronize playlist data from XML feed (synchronized to prevent race conditions)
     */
    @Transactional
    public void syncPlaylist() {
        synchronized (syncLock) {
            doSyncPlaylist();
        }
    }
    
    /**
     * Internal sync method (called within synchronized block)
     */
    private void doSyncPlaylist() {
        try {
            List<Song> xmlSongs = fetchSongsFromXml();
            
            if (xmlSongs.isEmpty()) {
                logger.warn("No songs found in XML feed, skipping sync");
                return;
            }
            
            logger.info("Fetched {} songs from XML feed", xmlSongs.size());

            // Get existing song titles from database (more efficient than loading all songs)
            List<String> existingTitlesList = songRepository.findAllTitles();
            Set<String> existingTitles = new HashSet<>(existingTitlesList);
            logger.info("Found {} existing songs in database", existingTitles.size());

            // Build set of current XML song titles for comparison
            Set<String> xmlTitles = new HashSet<>();
            for (Song xmlSong : xmlSongs) {
                String title = xmlSong.getTitle();
                if (title != null && !title.trim().isEmpty()) {
                    xmlTitles.add(title);
                }
            }
            
            // Find songs that exist in database but not in XML (removed/renamed tracks)
            Set<String> removedTitles = new HashSet<>(existingTitles);
            removedTitles.removeAll(xmlTitles);
            
            // Remove songs that are no longer in the XML feed
            if (!removedTitles.isEmpty()) {
                logger.info("Found {} songs in database that are missing from XML feed", removedTitles.size());
                
                // Process removals in batches to avoid memory issues
                List<String> removedTitlesList = new ArrayList<>(removedTitles);
                int batchSize = 100;
                int totalBatches = (int) Math.ceil((double) removedTitlesList.size() / batchSize);
                
                for (int i = 0; i < removedTitlesList.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, removedTitlesList.size());
                    List<String> batch = removedTitlesList.subList(i, endIndex);
                    int currentBatch = (i / batchSize) + 1;
                    
                    try {
                        int deletedCount = songRepository.deleteByTitleIn(batch);
                        logger.info("Removal batch {}/{} completed: Deleted {} songs", 
                                  currentBatch, totalBatches, deletedCount);
                    } catch (Exception e) {
                        logger.error("Error removing songs in batch {}/{}", currentBatch, totalBatches, e);
                    }
                }
                
                logger.info("Successfully removed {} songs that were missing from XML feed", removedTitles.size());
            }

            // Check for duration discrepancies between XML and database
            checkDurationDiscrepancies(xmlSongs, existingTitles);

            // Add new songs (keeping unique titles only, prevent duplicates)
            List<Song> newSongs = new ArrayList<>();
            Set<String> processedTitles = new HashSet<>();
            
            for (Song xmlSong : xmlSongs) {
                String title = xmlSong.getTitle();
                if (title != null && !title.trim().isEmpty() && !processedTitles.contains(title)) {
                    processedTitles.add(title);
                    
                    // Only add if it doesn't exist in database
                    if (!existingTitles.contains(title)) {
                        // Add new song with occurrence = 0 (will be tracked by SongPlayTracker)
                        xmlSong.setOccurrence(0);
                        newSongs.add(xmlSong);
                    }
                }
            }

            if (!newSongs.isEmpty()) {
                logger.info("Processing {} new songs for database insertion", newSongs.size());
                
                // Process in smaller batches for better performance with large datasets
                int batchSize = 500;
                int totalBatches = (int) Math.ceil((double) newSongs.size() / batchSize);
                logger.info("Processing {} songs in {} batches", newSongs.size(), totalBatches);
                
                for (int i = 0; i < newSongs.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, newSongs.size());
                    List<Song> batch = newSongs.subList(i, endIndex);
                    int currentBatch = (i / batchSize) + 1;
                    
                    try {
                        // Use saveAll with exception handling for potential duplicate key violations
                        songRepository.saveAll(batch);
                        logger.info("Batch {}/{} completed: Saved {} songs (Total: {}/{})", 
                                  currentBatch, totalBatches, batch.size(), endIndex, newSongs.size());
                    } catch (Exception e) {
                        // Handle potential duplicate key violations gracefully
                        if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                            logger.warn("Duplicate key violation in batch {}/{} - some songs may already exist, continuing", 
                                      currentBatch, totalBatches);
                            // Try saving songs individually to identify which ones are duplicates
                            for (Song song : batch) {
                                try {
                                    songRepository.save(song);
                                } catch (Exception singleSaveError) {
                                    logger.debug("Skipped duplicate song: {}", song.getTitle());
                                }
                            }
                        } else {
                            logger.error("Error saving batch {}/{}", currentBatch, totalBatches, e);
                            throw e; // Re-throw for other types of errors
                        }
                    }
                }
                
                logger.info("Successfully added {} new songs to database", newSongs.size());
            } else {
                logger.info("No new songs to add, database is up to date");
            }

            // Clean up duplicate track plays
            cleanupDuplicateTrackPlays();

            long totalSongs = songRepository.countAllSongs();
            logger.info("Playlist sync completed. Total songs in database: {}", totalSongs);

        } catch (Exception e) {
            logger.error("Error during playlist synchronization", e);
        }
    }

    /**
     * Fetch XML document from playlist endpoint with retry logic
     */
    @Retryable(
        value = {Exception.class}, 
        maxAttempts = 3, 
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    private Document fetchXmlDocument() throws Exception {
        logger.info("Fetching playlist from: {}", PLAYLIST_URL);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new URL(PLAYLIST_URL).openStream());
    }

    /**
     * Recovery method when all retry attempts fail
     */
    @Recover
    private Document recoverFromXmlFetchFailure(Exception ex) {
        logger.error("Failed to fetch XML after all retry attempts. Using empty playlist.", ex);
        // Return a minimal empty XML document
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.newDocument();
        } catch (Exception e) {
            logger.error("Failed to create empty document", e);
            throw new RuntimeException("Complete XML fetch failure", ex);
        }
    }

    /**
     * Fetch songs from XML feed and parse them
     */
    private List<Song> fetchSongsFromXml() {
        List<Song> songs = new ArrayList<>();

        try {
            Document document = fetchXmlDocument();

            NodeList leafNodes = document.getElementsByTagName("leaf");
            logger.info("Found {} songs in XML feed", leafNodes.getLength());

            for (int i = 0; i < leafNodes.getLength(); i++) {
                Element leafElement = (Element) leafNodes.item(i);
                String uriStr = leafElement.getAttribute("uri");
                String durationStr = leafElement.getAttribute("duration");

                if (uriStr != null && !uriStr.trim().isEmpty()) {
                    Song song = new Song();
                    
                    // Extract and clean song title from URI
                    String cleanTitle = extractTitleFromUri(uriStr);
                    if (cleanTitle != null && !cleanTitle.trim().isEmpty()) {
                        song.setTitle(cleanTitle);
                    
                    // Parse and format duration from seconds to MM:SS format
                    if (durationStr != null && !durationStr.trim().isEmpty()) {
                        try {
                            int totalSeconds = Integer.parseInt(durationStr.trim());
                            
                            // Validate duration is positive - this prevents -1 duration issues
                            if (totalSeconds < 0) {
                                logger.warn("Invalid negative duration '{}' for song '{}' - setting to 0", durationStr, cleanTitle);
                                totalSeconds = 0;
                            }
                            
                            String formattedDuration = formatDuration(totalSeconds);
                            song.setDuration(formattedDuration);
                            logger.debug("Parsed duration for '{}': {} seconds -> {}", cleanTitle, totalSeconds, formattedDuration);
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse duration '{}' for song '{}' - setting default duration", durationStr, cleanTitle);
                            song.setDuration("0:00"); // Set default duration instead of leaving null
                        }
                    } else {
                        logger.warn("Empty or null duration for song '{}' - setting default duration", cleanTitle);
                        song.setDuration("0:00"); // Set default duration instead of leaving null
                    }

                    
                    // Set creation timestamp
                    song.setCreatedAt(LocalDateTime.now());
                    
                        songs.add(song);
                    } else {
                        logger.debug("Could not extract valid title from URI: {}", uriStr);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error fetching songs from XML feed", e);
        }

        return songs;
    }

    /**
     * Check for duration discrepancies between XML source and database
     */
    private void checkDurationDiscrepancies(List<Song> xmlSongs, Set<String> existingTitles) {
        logger.info("Checking for duration discrepancies...");
        
        int discrepancyCount = 0;
        int fixedCount = 0;
        
        for (Song xmlSong : xmlSongs) {
            String title = xmlSong.getTitle();
            if (title != null && !title.trim().isEmpty() && existingTitles.contains(title)) {
                try {
                    // Find the existing song in database
                    Optional<Song> existingSong = songRepository.findByTitle(title);
                    if (existingSong.isPresent()) {
                        String dbDuration = existingSong.get().getDuration();
                        String xmlDuration = xmlSong.getDuration();
                        
                        // Check for problematic durations
                        if (dbDuration != null && (dbDuration.equals("0:00") || dbDuration.contains("-1"))) {
                            discrepancyCount++;
                            logger.warn("Duration discrepancy found for '{}': DB='{}', XML='{}'", 
                                      title, dbDuration, xmlDuration);
                            
                            // Fix the duration if XML has valid duration
                            if (xmlDuration != null && !xmlDuration.equals("0:00") && !xmlDuration.contains("-1")) {
                                int updated = songRepository.updateDurationByTitle(title, xmlDuration, dbDuration);
                                if (updated > 0) {
                                    fixedCount++;
                                    logger.info("Fixed duration for '{}': '{}' -> '{}'", title, dbDuration, xmlDuration);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error checking duration for song: {}", title, e);
                }
            }
        }
        
        if (discrepancyCount > 0) {
            logger.info("Duration discrepancy check completed: {} discrepancies found, {} fixed", 
                      discrepancyCount, fixedCount);
        } else {
            logger.info("No duration discrepancies found");
        }
    }

    /**
     * Extract song title from URI field using proper URL decoding
     * Example: file:///C:/sharec/FFTBattleground-battle/4%20Elements%20II%20-%20World%20of%20Magic.mp3
     * Returns: 4 Elements II - World of Magic
     */
    private String extractTitleFromUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Expected pattern: file:///C:/sharec/FFTBattleground-battle/FILENAME.mp3
            String prefix = "file:///C:/sharec/FFTBattleground-battle/";
            String suffix = ".mp3";
            
            if (uri.startsWith(prefix) && uri.endsWith(suffix)) {
                // Extract the filename part between prefix and suffix
                String encodedFilename = uri.substring(prefix.length(), uri.length() - suffix.length());
                
                // Add debug logging to trace the extraction process
                logger.debug("URI parsing - Original: '{}', Encoded filename: '{}'", uri, encodedFilename);
                
                // URL decode the filename
                String decodedFilename = URLDecoder.decode(encodedFilename, StandardCharsets.UTF_8);
                logger.debug("URI parsing - Decoded filename: '{}'", decodedFilename);
                
                // Clean up the title
                String cleanTitle = cleanSongTitle(decodedFilename);
                
                logger.debug("URI parsing - Final clean title: '{}'", cleanTitle);
                return cleanTitle;
            } else {
                logger.warn("URI does not match expected pattern: {}", uri);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error extracting title from URI: {}", uri, e);
            return null;
        }
    }

    /**
     * Clean up song title by removing unwanted characters and formatting
     */
    private String cleanSongTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            return rawTitle;
        }
        
        String cleanTitle = rawTitle.trim();
        
        // Replace underscores with spaces
        cleanTitle = cleanTitle.replace("_", " ");
        
        // Remove multiple spaces and trim
        cleanTitle = cleanTitle.replaceAll("\\s+", " ").trim();
        
        return cleanTitle;
    }

    /**
     * Format duration from seconds to MM:SS or H:MM:SS format
     * Handles negative durations by converting them to 0:00
     */
    private String formatDuration(int totalSeconds) {
        // Critical fix: Handle negative durations to prevent "0:-1" format
        if (totalSeconds < 0) {
            logger.warn("Negative duration detected: {} seconds - converting to 0:00", totalSeconds);
            totalSeconds = 0;
        }
        
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    /**
     * Get total song count from database
     */
    public long getTotalSongCount() {
        return songRepository.countAllSongs();
    }

    /**
     * Force manual sync (for admin/testing purposes)
     */
    public void forceSyncPlaylist() {
        logger.info("Manual playlist sync triggered");
        syncPlaylist();
    }
    
    /**
     * Clean up duplicate track plays within each song's duration window
     * Keeps only the oldest play within each duplicate group
     */
    @Transactional
    public void cleanupDuplicateTrackPlays() {
        try {
            logger.info("Starting duplicate track play cleanup...");
            
            // Find all track plays for songs with multiple plays
            List<TrackPlay> potentialDuplicates = trackPlayRepository.findAllPotentialDuplicateTrackPlays();
            
            if (potentialDuplicates.isEmpty()) {
                logger.info("No songs with multiple track plays found");
                return;
            }
            
            logger.info("Analyzing {} track plays for duplicates within duration windows", potentialDuplicates.size());
            
            // Group track plays by song
            Map<Long, List<TrackPlay>> trackPlaysBySong = new HashMap<>();
            for (TrackPlay trackPlay : potentialDuplicates) {
                Long songId = trackPlay.getSong().getId();
                trackPlaysBySong.computeIfAbsent(songId, k -> new ArrayList<>()).add(trackPlay);
            }
            
            List<Long> duplicateIds = new ArrayList<>();
            Map<String, Integer> songDuplicateCounts = new HashMap<>();
            
            // Process each song's track plays
            for (Map.Entry<Long, List<TrackPlay>> entry : trackPlaysBySong.entrySet()) {
                List<TrackPlay> songTrackPlays = entry.getValue();
                if (songTrackPlays.size() < 2) continue;
                
                // Sort by played time (oldest first)
                songTrackPlays.sort((tp1, tp2) -> tp1.getPlayedAt().compareTo(tp2.getPlayedAt()));
                
                Song song = songTrackPlays.get(0).getSong();
                long durationSeconds = parseDurationToSeconds(song.getDuration());
                
                // Find duplicates within duration window
                List<TrackPlay> duplicatesForSong = findDuplicatesWithinWindow(songTrackPlays, durationSeconds);
                
                if (!duplicatesForSong.isEmpty()) {
                    for (TrackPlay duplicate : duplicatesForSong) {
                        duplicateIds.add(duplicate.getId());
                    }
                    songDuplicateCounts.put(song.getTitle(), duplicatesForSong.size());
                    logger.info("Song '{}' ({} seconds) has {} duplicate track plays to remove", 
                              song.getTitle(), durationSeconds, duplicatesForSong.size());
                }
            }
            
            if (duplicateIds.isEmpty()) {
                logger.info("No duplicate track plays found within duration windows");
                return;
            }
            
            logger.info("Found {} duplicate track plays to remove across {} songs", 
                      duplicateIds.size(), songDuplicateCounts.size());
            
            // Process deletions in batches
            int batchSize = 100;
            int totalDeleted = 0;
            
            for (int i = 0; i < duplicateIds.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, duplicateIds.size());
                List<Long> batch = duplicateIds.subList(i, endIndex);
                
                try {
                    int deletedCount = trackPlayRepository.deleteByIdIn(batch);
                    totalDeleted += deletedCount;
                    logger.info("Deleted batch of {} duplicate track plays (Total: {}/{})", 
                              deletedCount, totalDeleted, duplicateIds.size());
                } catch (Exception e) {
                    logger.error("Error deleting batch of duplicate track plays", e);
                }
            }
            
            logger.info("Duplicate track play cleanup completed. Removed {} duplicate plays across {} songs", 
                      totalDeleted, songDuplicateCounts.size());
                      
        } catch (Exception e) {
            logger.error("Error during duplicate track play cleanup", e);
        }
    }
    
    /**
     * Find duplicates within a list of track plays for a single song
     * Keeps only the oldest play within each duration window
     */
    private List<TrackPlay> findDuplicatesWithinWindow(List<TrackPlay> trackPlays, long durationSeconds) {
        List<TrackPlay> duplicates = new ArrayList<>();
        
        for (int i = 0; i < trackPlays.size(); i++) {
            TrackPlay current = trackPlays.get(i);
            boolean isDuplicate = false;
            
            // Check if this play is within the duration window of any earlier play
            for (int j = 0; j < i; j++) {
                TrackPlay earlier = trackPlays.get(j);
                long timeDifference = Math.abs(java.time.Duration.between(earlier.getPlayedAt(), current.getPlayedAt()).getSeconds());
                
                if (timeDifference <= durationSeconds) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (isDuplicate) {
                duplicates.add(current);
            }
        }
        
        return duplicates;
    }
    
    /**
     * Parse duration string (MM:SS) to seconds for deduplication logic
     */
    private long parseDurationToSeconds(String duration) {
        if (duration == null || duration.trim().isEmpty() || duration.equals("0:00")) {
            return 10; // Default 10-second window for songs without duration
        }
        
        try {
            String[] parts = duration.split(":");
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                long totalSeconds = (minutes * 60L) + seconds;
                return Math.max(10, totalSeconds); // Minimum 10-second window
            }
        } catch (Exception e) {
            logger.warn("Could not parse duration '{}', using 10-second default", duration);
        }
        
        return 10; // Default fallback
    }
}