import React, { useEffect, useState } from 'react';

interface PlaylistStatsProps {
  totalSongs: number;
  showingSongs: number;
  latestSongTime?: string | null;
}

const PlaylistStats: React.FC<PlaylistStatsProps> = ({ totalSongs, showingSongs, latestSongTime }) => {
  const [formattedTime, setFormattedTime] = useState<string>('Loading...');

  useEffect(() => {
    if (latestSongTime) {
      try {
        // Handle database timestamps - they come as "2025-07-19T15:35:44.881708" (UTC without Z)
        // Convert to proper ISO string with Z suffix for UTC
        let isoString = latestSongTime;
        if (!latestSongTime.includes('Z') && !latestSongTime.includes('+') && !latestSongTime.includes('-', 10)) {
          isoString = latestSongTime + 'Z';
        }
        
        const date = new Date(isoString);
        // Get user's actual timezone instead of relying on auto-detect
        const userTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
        
        const fullOptions: Intl.DateTimeFormatOptions = {
          year: 'numeric',
          month: 'short',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          timeZone: userTimeZone // Use detected timezone explicitly
        };
        setFormattedTime(date.toLocaleString('en-US', fullOptions));
      } catch (error) {
        console.error('Error formatting timestamp:', error);
        setFormattedTime('Unknown');
      }
    } else {
      setFormattedTime('Never');
    }
  }, [latestSongTime]);

  return (
    <div className="playlist-stats">
      <div className="stat-item">
        <div className="stat-label">Total Tracks</div>
        <div className="stat-value">{(totalSongs ?? 0).toLocaleString()}</div>
      </div>
      <div className="stat-item">
        <div className="stat-label">Showing</div>
        <div className="stat-value">{showingSongs}</div>
      </div>
      {latestSongTime && (
        <div className="stat-item">
          <div className="stat-label">Last Sync</div>
          <div className="stat-value">{formattedTime}</div>
        </div>
      )}
    </div>
  );
};

export default PlaylistStats;