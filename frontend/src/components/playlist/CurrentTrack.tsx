import React, { useState, useEffect } from 'react';
import { webSocketService, TrackEvent } from '../../services/WebSocketService';
import { CurrentTrackService, CurrentTrackResponse } from '../../services/CurrentTrackService';

const styles = require('../../styles/PlaylistView.module.css');

interface CurrentTrackProps {
  className?: string;
}

const CurrentTrack: React.FC<CurrentTrackProps> = ({ className }) => {
  const [currentTrack, setCurrentTrack] = useState<TrackEvent | null>(null);
  const [timeRemaining, setTimeRemaining] = useState<number | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected'>('connecting');
  const [loading, setLoading] = useState<boolean>(true);

  // Load current track from API on component mount
  useEffect(() => {
    const loadCurrentTrack = async () => {
      setLoading(true);
      try {
        const trackData = await CurrentTrackService.getCurrentTrack();
        
        if (trackData.songTitle) {
          // Show track information whether it's currently playing or finished
          const trackEvent: TrackEvent = {
            songTitle: trackData.songTitle,
            duration: trackData.duration || 0,
            username: trackData.username || '',
            time: trackData.startTime || new Date().toISOString()
          };
          
          setCurrentTrack(trackEvent);
          // Always set time remaining - could be positive (active), zero (just finished), or negative (overtime)
          setTimeRemaining(trackData.remainingSeconds !== undefined ? trackData.remainingSeconds : 0);
        } else {
          // No track data available
          setCurrentTrack(null);
          setTimeRemaining(null);
        }
      } catch (error) {
        console.error('Failed to load current track:', error);
      } finally {
        setLoading(false);
      }
    };
    
    loadCurrentTrack();
  }, []);

  useEffect(() => {
    // Subscribe to real-time track events via WebSocket
    const unsubscribe = webSocketService.subscribeToTracks((trackEvent: any) => {
      console.log('🎵 Raw WebSocket message received:', trackEvent);
      console.log('🎵 WebSocket message type:', typeof trackEvent);
      console.log('🎵 WebSocket message keys:', Object.keys(trackEvent || {}));
      
      try {
        // Convert backend format to frontend format
        const normalizedEvent: TrackEvent = {
          songTitle: trackEvent.songTitle || '',
          duration: trackEvent.durationSeconds || trackEvent.duration || 0,
          username: trackEvent.username || '',
          time: trackEvent.eventTime || trackEvent.time || new Date().toISOString()
        };
        
        console.log('🎵 Normalized track event:', normalizedEvent);
        
        setCurrentTrack(normalizedEvent);
        setTimeRemaining(normalizedEvent.duration);
        setLoading(false);
        setConnectionStatus('connected');
      } catch (error) {
        console.error('🎵 Error processing track event:', error);
      }
    });

    // Monitor WebSocket connection status
    const checkConnection = () => {
      setConnectionStatus(webSocketService.isConnected() ? 'connected' : 'disconnected');
    };
    
    checkConnection();
    const connectionInterval = setInterval(checkConnection, 5000);

    return () => {
      unsubscribe();
      clearInterval(connectionInterval);
    };
  }, []);

  // Countdown timer for track duration
  useEffect(() => {
    if (timeRemaining !== null && timeRemaining > 0) {
      const timer = setTimeout(() => {
        setTimeRemaining(prev => prev !== null ? prev - 1 : null);
      }, 1000);

      return () => clearTimeout(timer);
    } else if (timeRemaining === 0) {
      // Track finished playing
      setTimeRemaining(null);
    }
  }, [timeRemaining]);

  const formatTime = (seconds: number): string => {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
  };

  const formatProgress = (current: number, total: number): number => {
    return ((total - current) / total) * 100;
  };

  if (loading) {
    return (
      <div className={`current-track-container ${className || ''}`}>
        <div className="current-track-waiting">
          <span className="status-indicator connecting">●</span>
          <span>Loading current track...</span>
        </div>
      </div>
    );
  }

  if (connectionStatus === 'disconnected' && !currentTrack) {
    return (
      <div className={`current-track-container ${className || ''}`}>
        <div className="current-track-error">
          <span className="status-indicator disconnected">●</span>
          <span>Unable to connect to live track updates</span>
        </div>
      </div>
    );
  }

  if (!currentTrack) {
    return (
      <div className={`current-track-container ${className || ''}`}>
        <div className="current-track-waiting">
          <span className="status-indicator waiting">●</span>
          <span>No track currently playing</span>
        </div>
      </div>
    );
  }

  const elapsed = currentTrack.duration - (timeRemaining || 0);
  // If time remaining is less than 1 second, show progress bar as 100% full
  const progressPercent = timeRemaining !== null && timeRemaining >= 1 
    ? formatProgress(timeRemaining, currentTrack.duration) 
    : 100;

  return (
    <div className={`current-track-container ${className || ''}`}>
      <div className="current-track-header">
        <span className="status-indicator connected">●</span>
        <span className="current-track-label">Now Playing</span>
      </div>
      
      <div className="current-track-info">
        <div className="current-track-title">{currentTrack.songTitle}</div>
        
        <div className="current-track-details">
          <div className="track-timing">
            <span className="time-elapsed">{formatTime(elapsed)}</span>
            <span className="time-separator"> / </span>
            <span className="time-total">{formatTime(currentTrack.duration)}</span>
            {timeRemaining !== null && timeRemaining > 0 && (
              <span className="time-remaining"> ({formatTime(timeRemaining)} remaining)</span>
            )}
            {timeRemaining !== null && timeRemaining <= 0 && (
              <span className="time-overtime"> ({formatTime(Math.abs(timeRemaining))} overtime)</span>
            )}
          </div>
          
          <div className="progress-bar">
            <div 
              className="progress-fill" 
              style={{ width: `${progressPercent}%` }}
            ></div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CurrentTrack;