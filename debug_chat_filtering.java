// Quick test to demonstrate the chat filtering logic
import com.twitchchat.event.detector.TrackPlayDetector;
import com.twitchchat.model.ChatMessage;
import com.twitchchat.event.TrackPlayEvent;

public class DebugChatFiltering {
    public static void main(String[] args) {
        TrackPlayDetector detector = new TrackPlayDetector();
        
        // Test 1: New track announcement (should be detected)
        ChatMessage newTrack = new ChatMessage("fftbattleground", 
            "The track is now: Red Ball 4 - Game Loop 2. It will play for 85 seconds.", 
            "fftbattleground");
        TrackPlayEvent result1 = detector.detect(newTrack);
        System.out.println("New track announcement: " + (result1 != null ? "DETECTED ✓" : "IGNORED ✗"));
        
        // Test 2: User status response (should be ignored)  
        ChatMessage userStatus = new ChatMessage("fftbattleground",
            "OtherBrand, the current track is: Red Ball 4 - Game Loop 2. It will play for another 45 seconds.",
            "fftbattleground");
        TrackPlayEvent result2 = detector.detect(userStatus);
        System.out.println("User status response: " + (result2 == null ? "IGNORED ✓" : "DETECTED ✗"));
    }
}