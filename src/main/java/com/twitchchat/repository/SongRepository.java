package com.twitchchat.repository;

import com.twitchchat.model.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Song entities with custom queries for playlist management
 */
@Repository
public interface SongRepository extends JpaRepository<Song, Long> {
    
    /**
     * Find song by exact title match
     */
    Optional<Song> findByTitle(String title);
    
    /**
     * Check if a song exists by title
     */
    boolean existsByTitle(String title);
    
    /**
     * Search songs by title containing search term (case insensitive)
     */
    @Query("SELECT s FROM Song s WHERE LOWER(s.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY s.title")
    List<Song> findByTitleContainingIgnoreCase(String searchTerm);
    
    /**
     * Search songs by title containing search term (case insensitive) with pagination
     */
    @Query("SELECT s FROM Song s WHERE LOWER(s.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Song> findByTitleContainingIgnoreCase(String searchTerm, Pageable pageable);
    
    /**
     * Get all songs ordered by title
     */
    List<Song> findAllByOrderByTitleAsc();
    
    /**
     * Get all songs ordered by duration
     */
    List<Song> findAllByOrderByDurationAsc();
    
    /**
     * Count total songs in database
     */
    @Query("SELECT COUNT(s) FROM Song s")
    long countAllSongs();
    
    /**
     * Get all song titles for duplicate checking (more efficient than loading full objects)
     */
    @Query("SELECT s.title FROM Song s WHERE s.title IS NOT NULL")
    List<String> findAllTitles();
    
    /**
     * Get recently added songs (for sync verification)
     */
    List<Song> findTop10ByOrderByCreatedAtDesc();
    
    /**
     * Get most played songs by occurrence count
     */
    List<Song> findTop20ByOrderByOccurrenceDesc();
    
    /**
     * Get songs that have been played at least once
     */
    List<Song> findByOccurrenceGreaterThanOrderByOccurrenceDesc(int occurrence);
    
    /**
     * Update duration for a song by title if it currently has the old duration
     */
    @Modifying
    @Transactional
    @Query("UPDATE Song s SET s.duration = :newDuration WHERE s.title = :title AND s.duration = :oldDuration")
    int updateDurationByTitle(String title, String newDuration, String oldDuration);
    
    /**
     * Delete songs by title list (batch deletion for removed tracks)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Song s WHERE s.title IN :titles")
    int deleteByTitleIn(@Param("titles") List<String> titles);
    
    /**
     * Delete a single song by title (individual transactional delete)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM songs WHERE title = :title", nativeQuery = true)
    int deleteByTitle(@Param("title") String title);
    
    /**
     * Get the timestamp of the most recently added song
     */
    @Query("SELECT MAX(s.createdAt) FROM Song s")
    java.time.LocalDateTime findLatestSongAddedTime();
    
    /**
     * Find all songs ordered by updatedAt DESC with NULL values last
     */
    @Query("SELECT s FROM Song s ORDER BY s.updatedAt DESC NULLS LAST")
    Page<Song> findAllOrderByUpdatedAtDescNullsLast(Pageable pageable);
    
    /**
     * Find all songs ordered by updatedAt ASC with NULL values last
     */
    @Query("SELECT s FROM Song s ORDER BY s.updatedAt ASC NULLS LAST")
    Page<Song> findAllOrderByUpdatedAtAscNullsLast(Pageable pageable);
    
    /**
     * Search songs by title containing search term, ordered by updatedAt DESC with NULL values last
     */
    @Query("SELECT s FROM Song s WHERE LOWER(s.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY s.updatedAt DESC NULLS LAST")
    Page<Song> findByTitleContainingIgnoreCaseOrderByUpdatedAtDescNullsLast(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    /**
     * Search songs by title containing search term, ordered by updatedAt ASC with NULL values last
     */
    @Query("SELECT s FROM Song s WHERE LOWER(s.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY s.updatedAt ASC NULLS LAST")
    Page<Song> findByTitleContainingIgnoreCaseOrderByUpdatedAtAscNullsLast(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    /**
     * Find all songs with problematic durations (0:00 or containing -1)
     * This is used for batch duration discrepancy checking
     */
    @Query("SELECT s FROM Song s WHERE s.duration = '0:00' OR s.duration LIKE '%-1%'")
    List<Song> findSongsWithProblematicDurations();
    
    /**
     * Batch update durations for multiple songs
     */
    @Modifying
    @Transactional
    @Query("UPDATE Song s SET s.duration = :newDuration WHERE s.title = :title")
    int updateDurationForTitle(@Param("title") String title, @Param("newDuration") String newDuration);
}