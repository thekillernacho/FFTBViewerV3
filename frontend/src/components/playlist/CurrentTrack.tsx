import React, { useState, useEffect } from 'react';
import { webSocketService, TrackEvent } from '../../services/WebSocketService';

const styles = require('../../styles/PlaylistView.module.css');

interface CurrentTrackProps {
  className?: string;
}

const CurrentTrack: React.FC<CurrentTrackProps> = ({ className }) => {
  const [currentTrack, setCurrentTrack] = useState<TrackEvent | null>(null);
  const [timeRemaining, setTimeRemaining] = useState<number | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected'>('connecting');

  useEffect(() => {
    // Subscribe to track events
    const unsubscribe = webSocketService.subscribeToTracks((trackEvent: TrackEvent) => {
      console.log('Received track event:', trackEvent);
      setCurrentTrack(trackEvent);
      setTimeRemaining(trackEvent.duration);
    });

    // Monitor connection status
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

  if (connectionStatus === 'disconnected') {
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
          <span className="status-indicator connecting">●</span>
          <span>Waiting for track information...</span>
        </div>
      </div>
    );
  }

  const elapsed = currentTrack.duration - (timeRemaining || 0);
  const progressPercent = timeRemaining !== null ? formatProgress(timeRemaining, currentTrack.duration) : 100;

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
            {timeRemaining !== null ? (
              <>
                <span className="time-elapsed">{formatTime(elapsed)}</span>
                <span className="time-separator"> / </span>
                <span className="time-total">{formatTime(currentTrack.duration)}</span>
                <span className="time-remaining"> ({formatTime(timeRemaining)} remaining)</span>
              </>
            ) : (
              <span className="time-total">{formatTime(currentTrack.duration)}</span>
            )}
          </div>
          
          {timeRemaining !== null && (
            <div className="progress-bar">
              <div 
                className="progress-fill" 
                style={{ width: `${progressPercent}%` }}
              ></div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default CurrentTrack;