// Simple SQL-based duplicate cleanup that doesn't rely on JPA entities
// This will work even if there are entity loading issues

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SqlDuplicateCleanupController {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @PostMapping("/api/sql-cleanup/duplicates")
    public String cleanupDuplicatesDirectly() {
        try {
            // SQL to find and delete duplicate track plays
            String findDuplicatesQuery = """
                WITH duplicate_pairs AS (
                  SELECT 
                    tp1.id as keep_id,
                    tp2.id as delete_id,
                    s.title,
                    s.duration,
                    EXTRACT(EPOCH FROM (tp2.played_at - tp1.played_at)) as seconds_between,
                    CASE 
                      WHEN s.duration ~ '^[0-9]+:[0-9]+$' THEN 
                        (SPLIT_PART(s.duration, ':', 1)::integer * 60) + SPLIT_PART(s.duration, ':', 2)::integer
                      ELSE 10
                    END as duration_seconds
                  FROM track_plays tp1
                  JOIN track_plays tp2 ON tp1.song_id = tp2.song_id AND tp1.id < tp2.id
                  JOIN songs s ON tp1.song_id = s.id
                  WHERE EXTRACT(EPOCH FROM (tp2.played_at - tp1.played_at)) < GREATEST(
                    CASE 
                      WHEN s.duration ~ '^[0-9]+:[0-9]+$' THEN 
                        (SPLIT_PART(s.duration, ':', 1)::integer * 60) + SPLIT_PART(s.duration, ':', 2)::integer
                      ELSE 10
                    END, 10)
                )
                DELETE FROM track_plays 
                WHERE id IN (SELECT delete_id FROM duplicate_pairs)
                """;
            
            int deletedCount = jdbcTemplate.update(findDuplicatesQuery);
            
            return "SQL-based cleanup completed successfully. Deleted " + deletedCount + " duplicate track plays.";
            
        } catch (Exception e) {
            return "Error during SQL cleanup: " + e.getMessage() + "\\nCause: " + (e.getCause() != null ? e.getCause().getMessage() : "none");
        }
    }
    
    @GetMapping("/api/sql-cleanup/count")
    public String countDuplicates() {
        try {
            String countQuery = """
                SELECT COUNT(*) as total_duplicate_pairs
                FROM (
                  SELECT 
                    tp1.id as first_id,
                    tp2.id as second_id,
                    s.title,
                    s.duration,
                    EXTRACT(EPOCH FROM (tp2.played_at - tp1.played_at)) as seconds_between,
                    CASE 
                      WHEN s.duration ~ '^[0-9]+:[0-9]+$' THEN 
                        (SPLIT_PART(s.duration, ':', 1)::integer * 60) + SPLIT_PART(s.duration, ':', 2)::integer
                      ELSE 10
                    END as duration_seconds
                  FROM track_plays tp1
                  JOIN track_plays tp2 ON tp1.song_id = tp2.song_id AND tp1.id < tp2.id
                  JOIN songs s ON tp1.song_id = s.id
                  WHERE EXTRACT(EPOCH FROM (tp2.played_at - tp1.played_at)) < GREATEST(
                    CASE 
                      WHEN s.duration ~ '^[0-9]+:[0-9]+$' THEN 
                        (SPLIT_PART(s.duration, ':', 1)::integer * 60) + SPLIT_PART(s.duration, ':', 2)::integer
                      ELSE 10
                    END, 10)
                ) duplicates
                """;
            
            Integer count = jdbcTemplate.queryForObject(countQuery, Integer.class);
            
            return "Found " + count + " duplicate track play pairs that can be cleaned up.";
            
        } catch (Exception e) {
            return "Error counting duplicates: " + e.getMessage() + "\\nCause: " + (e.getCause() != null ? e.getCause().getMessage() : "none");
        }
    }
}