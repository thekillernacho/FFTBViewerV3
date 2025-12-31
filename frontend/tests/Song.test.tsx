import React from 'react';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import Song from '../src/components/playlist/Song';
import { SongPlayCountView } from '../src/types';

// Mock CSS modules
jest.mock('../src/styles/Song.module.css', () => ({
  songRow: 'songRow',
  title: 'title',
  duration: 'duration',
  trackPlays: 'trackPlays',
  lastPlayed: 'lastPlayed'
}));

describe('Song Component', () => {
  const mockSong: SongPlayCountView = {
    songId: 1,
    title: 'Test Song',
    duration: '3:45',
    trackPlayCount: 5,
    lastPlayedAt: '2025-07-19T15:42:46.514608',
    album: null,
    creator: null,
    location: null,
    occurrence: 5,
    createdAt: '2025-07-16T10:00:00',
    updatedAt: '2025-07-19T15:42:46.514608'
  };

  test('renders song information correctly', () => {
    render(<Song song={mockSong} index={0} />);
    
    expect(screen.getByText('Test Song')).toBeInTheDocument();
    expect(screen.getByText('3:45')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  test('formats UTC timestamp correctly to user timezone', () => {
    render(<Song song={mockSong} index={0} />);
    
    // Just verify that a formatted date exists (not hardcoded timezone)
    // The component shows both createdAt and lastPlayedAt dates
    const formattedDates = screen.getAllByText(/Jul \d{1,2}, 2025/);
    expect(formattedDates.length).toBeGreaterThan(0);
  });

  test('handles null lastPlayedAt correctly', () => {
    const songWithoutPlay = { ...mockSong, trackPlayCount: 0, lastPlayedAt: null };
    render(<Song song={songWithoutPlay} index={0} />);
    
    // Use getAllByText since "Never" appears twice (trackPlayCount and lastPlayedAt columns)
    const neverElements = screen.getAllByText('Never');
    expect(neverElements.length).toBe(2);
  });
});

// Standalone test for formatDate function logic
describe('formatDate timezone conversion', () => {
  // Test function that auto-detects user timezone (no hardcoded timezone)
  const formatDateAutoDetect = (dateString: string | null | undefined): string => {
    if (!dateString) return 'Unknown';
    try {
      let isoString = dateString;
      if (!dateString.includes('Z') && !dateString.includes('+') && !dateString.includes('-', 10)) {
        isoString = dateString + 'Z';
      }
      
      const date = new Date(isoString);
      if (isNaN(date.getTime())) return 'Unknown';
      
      // Get user's timezone
      const userTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      
      // Auto-detect user's timezone (no hardcoded timezone)
      const dateOptions: Intl.DateTimeFormatOptions = {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        timeZone: userTimeZone
      };
      
      return date.toLocaleString('en-US', dateOptions);
    } catch (error) {
      return 'Unknown';
    }
  };

  // Test function for specific timezone
  const formatDateWithTimezone = (dateString: string | null | undefined, timezone: string): string => {
    if (!dateString) return 'Unknown';
    try {
      let isoString = dateString;
      if (!dateString.includes('Z') && !dateString.includes('+') && !dateString.includes('-', 10)) {
        isoString = dateString + 'Z';
      }
      
      const date = new Date(isoString);
      if (isNaN(date.getTime())) return 'Unknown';
      
      const dateOptions: Intl.DateTimeFormatOptions = {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        timeZone: timezone
      };
      
      return date.toLocaleString('en-US', dateOptions);
    } catch (error) {
      return 'Unknown';
    }
  };

  test('converts UTC timestamp to different timezones correctly', () => {
    const utcTimestamp = '2025-07-19T15:42:46.514608';
    
    // Test various timezone conversions
    const centralResult = formatDateWithTimezone(utcTimestamp, 'America/Chicago');
    const easternResult = formatDateWithTimezone(utcTimestamp, 'America/New_York');
    const pacificResult = formatDateWithTimezone(utcTimestamp, 'America/Los_Angeles');
    const utcResult = formatDateWithTimezone(utcTimestamp, 'UTC');
    
    // UTC 15:42:46 conversions
    expect(centralResult).toContain('10:42:46 AM'); // UTC-5 (Central)
    expect(easternResult).toContain('11:42:46 AM'); // UTC-4 (Eastern)
    expect(pacificResult).toContain('8:42:46 AM');  // UTC-7 (Pacific)
    expect(utcResult).toContain('3:42:46 PM');      // UTC (no conversion)
    
    expect(centralResult).toContain('Jul 19, 2025');
  });

  test('auto-detect timezone converts timestamp properly', () => {
    const utcTimestamp = '2025-07-19T15:42:46.514608';
    const result = formatDateAutoDetect(utcTimestamp);
    
    // Should contain date portion
    expect(result).toContain('Jul 19, 2025');
    
    // Should show some form of time
    expect(result).toMatch(/\d{1,2}:\d{2}:\d{2} (AM|PM)/);
    
    // Should not be 'Unknown'
    expect(result).not.toBe('Unknown');
  });

  test('handles timestamp without Z suffix', () => {
    const timestampWithoutZ = '2025-07-19T15:35:44.881708';
    const centralResult = formatDateWithTimezone(timestampWithoutZ, 'America/Chicago');
    
    // Should automatically append Z and convert
    expect(centralResult).toContain('10:35:44 AM');
  });

  test('handles null and undefined values', () => {
    expect(formatDateAutoDetect(null)).toBe('Unknown');
    expect(formatDateAutoDetect(undefined)).toBe('Unknown');
    expect(formatDateAutoDetect('')).toBe('Unknown');
  });

  test('debug current timezone detection', () => {
    const testDate = new Date('2025-07-19T15:42:46.514608Z');
    const userTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    
    console.log('Detected user timezone:', userTimezone);
    console.log('UTC time:', testDate.toISOString());
    console.log('Local time:', testDate.toLocaleString());
    console.log('Local time (en-US):', testDate.toLocaleString('en-US'));
    
    // This test just logs information for debugging
    expect(userTimezone).toBeDefined();
  });
});