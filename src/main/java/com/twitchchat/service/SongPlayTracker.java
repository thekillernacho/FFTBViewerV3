package com.twitchchat.service;

import com.twitchchat.config.TrackPlayProperties;
import com.twitchchat.event.TrackPlayEvent;
import com.twitchchat.model.Song;
import com.twitchchat.model.TrackPlay;
import com.twitchchat.repository.SongRepository;
import com.twitchchat.repository.TrackPlayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track song plays from TrackPlayEvent and update occurrence counts
 */
@Service
public class SongPlayTracker {
    private static final Logger logger = LoggerFactory.getLogger(SongPlayTracker.class);
    
    @Autowired
    private SongRepository songRepository;
    
    @Autowired
    private TrackPlayRepository trackPlayRepository;
    
    @Autowired
    private TrackPlayProperties trackPlayProperties;
    
    @Value("${track.play.deduplication.seconds:10}")
    private int deduplicationSeconds;
    
    // Thread-safe map to prevent concurrent processing of the same song
    private final ConcurrentHashMap<String, Object> songLocks = new ConcurrentHashMap<>();
    
    /**
     * Asynchronously track a song play and update its occurrence count
     * @param event The TrackPlayEvent containing song information
     * @return CompletableFuture<Boolean> indicating if the song was found and updated
     */
    @Async
    public CompletableFuture<Boolean> trackSongPlayAsync(TrackPlayEvent event) {
        try {
            // Check if track play updates are enabled
            if (!trackPlayProperties.isEnabled()) {
                logger.info("Track play updates are disabled, skipping database update for: {}", event.getSongTitle());
                return CompletableFuture.completedFuture(false);
            }
            
            // Check if we're in log-only mode
            if (trackPlayProperties.isLogOnly()) {
                logger.info("LOG-ONLY MODE: Would track play for '{}' ({}s)", 
                           event.getSongTitle(), event.getDurationSeconds());
                return CompletableFuture.completedFuture(true);
            }
            
            // Full database update mode with synchronization to prevent race conditions
            boolean result = trackSongPlaySynchronized(event.getSongTitle());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            logger.error("Error tracking song play asynchronously: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * Track a song play with synchronization to prevent race conditions
     * @param songTitle The title of the song that was played
     * @return true if the song was found and updated, false otherwise
     */
    private boolean trackSongPlaySynchronized(String songTitle) {
        // Use song title as lock key to prevent concurrent processing of same song
        Object lock = songLocks.computeIfAbsent(songTitle, k -> new Object());
        
        synchronized (lock) {
            try {
                return trackSongPlay(songTitle);
            } finally {
                // Clean up lock if no other threads are waiting
                // Only remove if the same object is still in the map
                songLocks.remove(songTitle, lock);
            }
        }
    }
    
    /**
     * Track a song play and update its occurrence count with deduplication
     * @param songTitle The title of the song that was played
     * @return true if the song was found and updated, false otherwise
     */
    private boolean trackSongPlay(String songTitle) {
        Optional<Song> songOpt = songRepository.findByTitle(songTitle);
        
        if (songOpt.isPresent()) {
            Song song = songOpt.get();
            
            // Check for duplicate plays within the deduplication window (song duration or 10 seconds minimum)
            int deduplicationWindow = calculateDeduplicationWindow(song);
            LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(deduplicationWindow);
            if (trackPlayRepository.existsBySongAndPlayedAtAfter(song, cutoffTime)) {
                logger.debug("Duplicate track play detected for '{}' within {} seconds (song duration: {}), skipping", 
                           songTitle, deduplicationWindow, song.getDuration());
                return false;
            }
            
            // Update song occurrence count and timestamp only if enabled
            if (trackPlayProperties.isUpdateOccurrences()) {
                song.setOccurrence(song.getOccurrence() + 1);
                song.setUpdatedAt(LocalDateTime.now());
                songRepository.save(song);
                logger.debug("Updated occurrence count for '{}' - occurrence now: {}", songTitle, song.getOccurrence());
            }
            
            // Create and save track play record if enabled
            TrackPlay trackPlay = null;
            if (trackPlayProperties.isRecordTrackPlays()) {
                trackPlay = new TrackPlay(song);
                trackPlayRepository.save(trackPlay);
                logger.debug("Created TrackPlay record for '{}' - TrackPlay ID: {}", songTitle, trackPlay.getId());
            }
            
            logger.info("Tracked play for '{}' - occurrence updates: {}, TrackPlay recording: {}", 
                       songTitle, trackPlayProperties.isUpdateOccurrences(), trackPlayProperties.isRecordTrackPlays());
            return true;
        } else {
            logger.warn("Song '{}' not found in database, cannot track play", songTitle);
            return false;
        }
    }
    
    /**
     * Check if a song can be tracked (not a duplicate within the deduplication window)
     * Uses the song's duration as the deduplication window, with a 10-second minimum
     * @param songTitle The title of the song to check
     * @return true if the song can be tracked, false if it's a duplicate
     */
    public boolean canTrackSong(String songTitle) {
        Optional<Song> songOpt = songRepository.findByTitle(songTitle);
        
        if (songOpt.isPresent()) {
            Song song = songOpt.get();
            int deduplicationWindow = calculateDeduplicationWindow(song);
            LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(deduplicationWindow);
            return !trackPlayRepository.existsBySongAndPlayedAtAfter(song, cutoffTime);
        }
        
        return false; // Song not found
    }
    
    /**
     * Get the deduplication window in seconds
     * @return The deduplication window duration
     */
    public int getDeduplicationSeconds() {
        return deduplicationSeconds;
    }
    
    /**
     * Calculate the deduplication window for a song based on its duration
     * @param song The song to calculate the window for
     * @return The deduplication window in seconds (minimum 10 seconds)
     */
    private int calculateDeduplicationWindow(Song song) {
        try {
            String duration = song.getDuration();
            if (duration == null || duration.trim().isEmpty()) {
                return Math.max(deduplicationSeconds, 10); // Default to config or 10 seconds minimum
            }
            
            // Parse duration format "M:SS" or "MM:SS"
            String[] parts = duration.split(":");
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                int totalSeconds = minutes * 60 + seconds;
                
                // Use song duration but enforce 10-second minimum
                return Math.max(totalSeconds, 10);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse song duration '{}' for song '{}', using default deduplication window", 
                       song.getDuration(), song.getTitle());
        }
        
        // Fallback to configured value or 10 seconds minimum
        return Math.max(deduplicationSeconds, 10);
    }
    
    /**
     * Get the total number of tracked plays across all songs
     * @return The total play count
     */
    public long getTotalPlays() {
        return songRepository.findAll().stream()
            .mapToLong(song -> song.getOccurrence())
            .sum();
    }
    
    /**
     * Get the number of unique songs that have been played at least once
     * @return The count of played songs
     */
    public long getPlayedSongsCount() {
        return trackPlayRepository.countDistinctSongs();
    }
    
    /**
     * Get the date when tracking started (earliest track play)
     * @return The tracking start date as a formatted string, or null if no plays recorded
     */
    public String getTrackingStartDate() {
        try {
            LocalDateTime earliestDate = trackPlayRepository.findEarliestTrackPlayDate();
            return earliestDate != null ? earliestDate.toString() : null;
        } catch (Exception e) {
            logger.error("Error retrieving tracking start date: {}", e.getMessage());
            return null;
        }
    }
}