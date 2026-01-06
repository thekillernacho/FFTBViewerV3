package com.twitchchat.service;

import com.twitchchat.model.Song;
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
     * Note: @Transactional removed to allow individual operations to commit immediately
     * rather than rolling back all changes if any single operation fails
     */
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

            // DEBUG: Log songs with underscores from XML to verify parsing
            int underscoreCount = 0;
            for (Song xmlSong : xmlSongs) {
                String title = xmlSong.getTitle();
                if (title != null && title.contains("_")) {
                    underscoreCount++;
                    if (underscoreCount <= 5) {
                        logger.info("UNDERSCORE_DEBUG: Found song with underscore in XML: '{}'", title);
                    }
                }
            }
            logger.info("UNDERSCORE_DEBUG: Total songs with underscores in XML: {}", underscoreCount);

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
                logger.info("SYNC_DEBUG: Starting removal process...");

                // Process removals in batches to avoid memory issues
                List<String> removedTitlesList = new ArrayList<>(removedTitles);
                int batchSize = 100;
                int totalBatches = (int) Math.ceil((double) removedTitlesList.size() / batchSize);
                logger.info("SYNC_DEBUG: Will process {} removal batches", totalBatches);

                for (int i = 0; i < removedTitlesList.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, removedTitlesList.size());
                    List<String> batch = removedTitlesList.subList(i, endIndex);
                    int currentBatch = (i / batchSize) + 1;

                    try {
                        logger.info("SYNC_DEBUG: About to delete batch {} with {} songs: {}", currentBatch, batch.size(), batch);
                        
                        // First, delete associated track plays to avoid foreign key constraint violations
                        try {
                            int deletedTrackPlays = trackPlayRepository.deleteBySongTitleIn(batch);
                            if (deletedTrackPlays > 0) {
                                logger.info("SYNC_DEBUG: Deleted {} track plays for batch {}", deletedTrackPlays, currentBatch);
                            }
                        } catch (Exception trackPlayError) {
                            logger.warn("SYNC_DEBUG: Error deleting track plays for batch {}: {}", currentBatch, trackPlayError.getMessage());
                        }
                        
                        logger.info("SYNC_DEBUG: Entering deletion loop for batch {}", currentBatch);
                        // Delete songs one by one to avoid transaction blocking
                        int deletedCount = 0;
                        int processedCount = 0;
                        for (String title : batch) {
                            processedCount++;
                            try {
                                int deleted = songRepository.deleteByTitle(title);
                                if (deleted > 0) {
                                    deletedCount++;
                                    logger.debug("SYNC_DEBUG: Deleted song: '{}' (rows: {})", title, deleted);
                                }
                            } catch (Exception deleteError) {
                                logger.warn("SYNC_DEBUG: Failed to delete song '{}': {}", title, deleteError.getMessage());
                            }
                        }
                        logger.info("SYNC_DEBUG: Loop completed - processed {} songs, deleted {}", processedCount, deletedCount);
                        logger.info("Removal batch {}/{} completed: Deleted {} songs", 
                                  currentBatch, totalBatches, deletedCount);
                    } catch (Exception e) {
                        logger.error("SYNC_DEBUG: Error in removal batch {}/{}: {}", currentBatch, totalBatches, e.getMessage(), e);
                    }
                }

                logger.info("Successfully removed {} songs that were missing from XML feed", removedTitles.size());
            } else {
                logger.info("SYNC_DEBUG: No songs to remove from database");
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

            // DEBUG: Log underscore songs that should be added
            int underscoreNewCount = 0;
            for (Song newSong : newSongs) {
                if (newSong.getTitle().contains("_")) {
                    underscoreNewCount++;
                    if (underscoreNewCount <= 5) {
                        logger.info("UNDERSCORE_DEBUG: Adding new song with underscore: '{}'", newSong.getTitle());
                    }
                }
            }
            logger.info("UNDERSCORE_DEBUG: Total new songs with underscores to add: {}", underscoreNewCount);

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
     * Updates ALL songs where duration differs from XML, not just problematic ones
     */
    private void checkDurationDiscrepancies(List<Song> xmlSongs, Set<String> existingTitles) {
        logger.info("Checking for duration discrepancies...");

        try {
            // Step 1: Build a map of XML songs by title for quick lookup
            Map<String, String> xmlDurationsByTitle = new HashMap<>();
            for (Song xmlSong : xmlSongs) {
                String title = xmlSong.getTitle();
                if (title != null && !title.trim().isEmpty() && xmlSong.getDuration() != null) {
                    xmlDurationsByTitle.put(title, xmlSong.getDuration());
                }
            }
            
            // Step 2: Fetch ALL existing songs to compare durations
            List<Song> allDbSongs = songRepository.findAll();
            logger.info("Comparing durations for {} songs in database", allDbSongs.size());
            
            int fixedCount = 0;
            int discrepancyCount = 0;
            
            // Step 3: Update durations for songs where XML duration differs from database
            for (Song dbSong : allDbSongs) {
                String title = dbSong.getTitle();
                String xmlDuration = xmlDurationsByTitle.get(title);
                String dbDuration = dbSong.getDuration();
                
                // Check if XML has a different duration than database
                if (xmlDuration != null && !xmlDuration.equals(dbDuration)) {
                    discrepancyCount++;
                    try {
                        int updated = songRepository.updateDurationForTitle(title, xmlDuration);
                        if (updated > 0) {
                            fixedCount++;
                            if (fixedCount <= 10) {
                                logger.info("Updated duration for '{}': '{}' -> '{}'", title, dbDuration, xmlDuration);
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Could not update duration for '{}': {}", title, e.getMessage());
                    }
                }
            }

            if (discrepancyCount > 0) {
                logger.info("Duration check completed: {} discrepancies found, {} updated", 
                          discrepancyCount, fixedCount);
            } else {
                logger.info("No duration discrepancies found");
            }
        } catch (Exception e) {
            logger.error("Error during duration discrepancy check: {}", e.getMessage());
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

        // IMPORTANT: Do NOT strip or replace underscores.
        // Some tracks intentionally include underscores, and removing them breaks
        // searching/matching (and is not reversible).

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
}