import { PlaylistData, PlaylistStats, LatestSongResponse, PlaylistDataWithTrackPlays, PlaylistDataWithView } from '../types';

export class PlaylistService {
  static async getSongs(
    page: number = 0, 
    size: number = 50, 
    sortBy: string = 'title', 
    sortDirection: 'asc' | 'desc' = 'asc', 
    search: string = ''
  ): Promise<PlaylistData> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy,
      sortDirection
    });
    
    if (search.trim()) {
      params.append('search', search.trim());
    }

    const response = await fetch(`/api/songs?${params}`);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  static async getStats(): Promise<PlaylistStats> {
    const response = await fetch('/api/stats');
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  static async getPlaylistStatus(): Promise<any> {
    const response = await fetch('/api/playlist/status');
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  static async getLatestSongTime(): Promise<LatestSongResponse> {
    const response = await fetch('/api/latest-song-time');
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  static async getSongsWithTrackPlays(
    page: number = 0, 
    size: number = 50, 
    sortBy: string = 'title', 
    sortDirection: 'asc' | 'desc' = 'asc', 
    search: string = ''
  ): Promise<PlaylistDataWithView> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy,
      sortDirection
    });
    
    if (search.trim()) {
      params.append('search', search.trim());
    }

    const response = await fetch(`/api/songs-with-track-plays?${params}`);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }
}