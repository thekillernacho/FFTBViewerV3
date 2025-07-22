package com.twitchchat.controller;

import com.twitchchat.model.Song;
import com.twitchchat.model.SongPlayCountView;
import com.twitchchat.repository.SongRepository;
import com.twitchchat.service.PlaylistSyncService;
import com.twitchchat.service.PlaylistService;
import com.twitchchat.service.SongPlayTracker;
import com.twitchchat.service.SongPlayCountViewService;
import com.twitchchat.dto.SongWithTrackPlayCount;
import com.twitchchat.playlist.sync.PlaylistSyncAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for playlist and sync management
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private PlaylistSyncService playlistSyncService;
    
    @Autowired
    private SongPlayTracker songPlayTracker;
    
    @Autowired
    private SongRepository songRepository;
    
    @Autowired
    private SongPlayCountViewService songPlayCountViewService;
    
    @Autowired
    private PlaylistSyncAuditService playlistSyncAuditService;

    /**
     * Get playlist status and statistics
     */
    @GetMapping("/playlist/status")
    public ResponseEntity<Map<String, Object>> getPlaylistStatus() {
        Map<String, Object> status = new HashMap<>();
        
        long totalSongs = playlistService.getTotalSongCount();
        boolean isAvailable = playlistService.isPlaylistAvailable();
        long totalPlays = songPlayTracker.getTotalPlays();
        long playedSongs = songPlayTracker.getPlayedSongsCount();
        
        // Get the last sync completion time
        String lastSyncTime = playlistSyncAuditService.getLastSyncCompletionTime();
        
        status.put("totalSongs", totalSongs);
        status.put("isAvailable", isAvailable);
        status.put("status", isAvailable ? "ready" : "syncing");
        status.put("totalPlays", totalPlays);
        status.put("playedSongs", playedSongs);
        status.put("lastSyncTime", lastSyncTime);
        
        return ResponseEntity.ok(status);
    }

    /**
     * Get songs with pagination, search, and sorting
     */
    @GetMapping("/songs")
    public ResponseEntity<Map<String, Object>> getSongs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) String search) {

        Map<String, Object> response = new HashMap<>();

        try {
            org.springframework.data.domain.Page<Song> songsPage;
            
            if (search != null && !search.trim().isEmpty()) {
                songsPage = playlistService.searchSongsPaginated(search, page, size, sortBy, sortDirection);
            } else {
                songsPage = playlistService.fetchSongsPaginated(page, size, sortBy, sortDirection);
            }

            response.put("songs", songsPage.getContent());
            response.put("totalSongs", songsPage.getTotalElements());
            response.put("currentPage", page);
            response.put("totalPages", songsPage.getTotalPages());
            response.put("hasNext", songsPage.hasNext());
            response.put("hasPrevious", songsPage.hasPrevious());
            response.put("showingSongs", songsPage.getContent().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", "Database access failed");
            response.put("message", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get songs with track play counts from database view (EFFICIENT)
     */
    @GetMapping("/songs-with-track-plays")
    public ResponseEntity<Map<String, Object>> getSongsWithTrackPlays(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) String search) {

        Map<String, Object> response = new HashMap<>();

        try {
            org.springframework.data.domain.Page<SongPlayCountView> songsPage;
            
            if (search != null && !search.trim().isEmpty()) {
                songsPage = songPlayCountViewService.searchSongs(search, page, size, sortBy, sortDirection);
            } else {
                songsPage = songPlayCountViewService.fetchSongs(page, size, sortBy, sortDirection);
            }

            response.put("songs", songsPage.getContent());
            response.put("totalSongs", songsPage.getTotalElements());
            response.put("currentPage", page);
            response.put("totalPages", songsPage.getTotalPages());
            response.put("hasNext", songsPage.hasNext());
            response.put("hasPrevious", songsPage.hasPrevious());
            response.put("showingSongs", songsPage.getContent().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", "Database view access failed");
            response.put("message", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get playlist statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            long totalSongs = playlistService.getTotalSongCount();
            java.time.LocalDateTime latestTime = playlistService.getLatestSongAddedTime();
            
            response.put("totalSongs", totalSongs);
            response.put("latestSongTime", latestTime != null ? latestTime.toString() : null);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Stats retrieval failed");
            response.put("message", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get latest song timestamp
     */
    @GetMapping("/latest-song-time")
    public ResponseEntity<Map<String, Object>> getLatestSongTime() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            java.time.LocalDateTime latestTime = playlistService.getLatestSongAddedTime();
            response.put("timestamp", latestTime != null ? latestTime.toString() : null);
            response.put("message", "Latest song time retrieved successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to retrieve latest song time");
            response.put("message", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Force manual sync of playlist data
     */
    @PostMapping("/playlist/sync")
    public ResponseEntity<Map<String, String>> forceSync() {
        try {
            playlistSyncService.apiSyncPlaylist();
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Playlist sync initiated");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Sync failed: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get song play statistics
     */
    @GetMapping("/songs/stats")
    public ResponseEntity<Map<String, Object>> getSongStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalPlays", songPlayTracker.getTotalPlays());
        stats.put("playedSongs", songPlayTracker.getPlayedSongsCount());
        stats.put("totalSongs", playlistService.getTotalSongCount());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get most played songs
     */
    @GetMapping("/songs/most-played")
    public ResponseEntity<List<Song>> getMostPlayedSongs() {
        List<Song> mostPlayed = songRepository.findTop20ByOrderByOccurrenceDesc();
        return ResponseEntity.ok(mostPlayed);
    }
}