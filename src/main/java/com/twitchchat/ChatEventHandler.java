package com.twitchchat;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.twitchchat.event.TrackPlayEvent;
import com.twitchchat.event.detector.TrackPlayDetector;
import com.twitchchat.model.ChatMessage;

import com.twitchchat.service.SongPlayTracker;
import com.twitchchat.service.CurrentTrackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Spring component that handles various Twitch chat events and formats them for console display
 */
@Component
public class ChatEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChatEventHandler.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private SongPlayTracker songPlayTracker;
    
    @Autowired
    private TrackPlayDetector trackPlayDetector;
    
    @Autowired
    private CurrentTrackService currentTrackService;

    /**
     * Handle incoming chat messages
     */
    public void onChannelMessage(ChannelMessageEvent event) {
        try {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String username = event.getUser().getName();
            String message = event.getMessage();
            String channel = event.getChannel().getName();

            // Create chat message object
            ChatMessage chatMessage = new ChatMessage(username, message, channel);
            
            // Detect track play events
            TrackPlayEvent trackPlayEvent = trackPlayDetector.detect(chatMessage);
            if (trackPlayEvent != null) {
                logger.info("Track play event detected: {}", trackPlayEvent);
                
                // Update current track cache
                currentTrackService.updateCurrentTrack(trackPlayEvent);
                
                // Broadcast track event via WebSocket to all subscribers
                logger.info("Broadcasting track event to /topic/tracks: {}", trackPlayEvent);
                messagingTemplate.convertAndSend("/topic/tracks", trackPlayEvent);
                
                // Asynchronously update the database
                songPlayTracker.trackSongPlayAsync(trackPlayEvent)
                    .thenAccept(success -> {
                        if (success) {
                            logger.info("Song play tracked from message: {}", message);
                        } else {
                            logger.warn("Failed to track song play for: {}", trackPlayEvent.getSongTitle());
                        }
                    });
            }
            
            // Broadcast message via WebSocket
            messagingTemplate.convertAndSend("/topic/messages", chatMessage);

            // Format and display the message
            String formattedMessage = String.format("[%s] %s: %s", 
                timestamp, username, message);
            
            System.out.println(formattedMessage);
            
            // Log the message for debugging
            logger.debug("Message in {}: {} - {}", channel, username, message);
            
        } catch (Exception e) {
            logger.error("Error processing channel message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle user join events (simplified version without specific join events)
     */
    public void onUserJoin(Object event) {
        try {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            
            // Format and display the join message
            String joinMessage = String.format("[%s] --> User joined the chat", timestamp);
            
            System.out.println(joinMessage);
            
            logger.debug("User joined event received");
            
        } catch (Exception e) {
            logger.error("Error processing user join event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle user leave events (simplified version without specific leave events)
     */
    public void onUserLeave(Object event) {
        try {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);

            // Format and display the leave message
            String leaveMessage = String.format("[%s] <-- User left the chat", timestamp);
            
            System.out.println(leaveMessage);
            
            logger.debug("User left event received");
            
        } catch (Exception e) {
            logger.error("Error processing user leave event: {}", e.getMessage(), e);
        }
    }
}
