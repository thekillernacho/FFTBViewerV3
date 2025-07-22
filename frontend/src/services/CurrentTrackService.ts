export interface CurrentTrackResponse {
  hasTrack: boolean;
  songTitle?: string;
  duration?: number;
  username?: string;
  startTime?: string;
  elapsedSeconds?: number;
  remainingSeconds?: number;
  message?: string;
}

export class CurrentTrackService {
  private static readonly API_BASE = '/api';

  static async getCurrentTrack(): Promise<CurrentTrackResponse> {
    try {
      const response = await fetch(`${this.API_BASE}/current-track`);
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      return await response.json();
    } catch (error) {
      console.error('Failed to fetch current track:', error);
      return {
        hasTrack: false,
        message: 'Failed to load current track information'
      };
    }
  }
}