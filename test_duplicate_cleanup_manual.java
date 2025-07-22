// Manual test to isolate the exact issue in duplicate cleanup
// Let's create a simple diagnostic controller to test step by step

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class DuplicateCleanupDiagnosticController {
    
    @Autowired
    private TrackPlayRepository trackPlayRepository;
    
    @GetMapping("/api/diagnostic/potential-duplicates")
    public String testPotentialDuplicates() {
        try {
            List<TrackPlay> potentialDuplicates = trackPlayRepository.findAllPotentialDuplicateTrackPlays();
            
            StringBuilder result = new StringBuilder();
            result.append("Found " + potentialDuplicates.size() + " potential duplicates:\n");
            
            for (int i = 0; i < Math.min(5, potentialDuplicates.size()); i++) {
                TrackPlay tp = potentialDuplicates.get(i);
                result.append("ID: " + tp.getId());
                
                if (tp.getSong() == null) {
                    result.append(" - Song: NULL (THIS IS THE PROBLEM!)\n");
                } else {
                    result.append(" - Song: " + tp.getSong().getTitle() + "\n");
                }
            }
            
            return result.toString();
            
        } catch (Exception e) {
            return "Error: " + e.getMessage() + "\nCause: " + e.getCause();
        }
    }
}