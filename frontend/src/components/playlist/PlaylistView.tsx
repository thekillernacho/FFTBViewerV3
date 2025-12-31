import React, { useEffect, useState, useCallback } from 'react';
import { PlaylistService } from '../../services/PlaylistService';
import { SongPlayCountView } from '../../types';
import PlaylistStats from './PlaylistStats';
import SongTable from './SongTable';
import Pagination from './Pagination';
import CurrentTrack from './CurrentTrack';
import { SimpleSearchWithButton } from './SimpleSearchWithButton';

interface PlaylistState {
  songs: SongPlayCountView[];
  totalSongs: number;
  totalPages: number;
  currentPage: number;
  hasNext: boolean;
  hasPrevious: boolean;
  loading: boolean;
  error: string | null;
}

interface PlaylistStatusData {
  totalSongs: number;
  totalPlays: number;
  trackingStartDate: string | null;
  lastSyncTime: string | null;
}

const PlaylistView: React.FC = () => {
  const [state, setState] = useState<PlaylistState>({
    songs: [],
    totalSongs: 0,
    totalPages: 0,
    currentPage: 0,
    hasNext: false,
    hasPrevious: false,
    loading: true,
    error: null
  });

  const [statusData, setStatusData] = useState<PlaylistStatusData | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState('title');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [pageSize, setPageSize] = useState(50);

  const fetchSongs = useCallback(async (page: number, search: string, sort: string, direction: 'asc' | 'desc', size: number) => {
    setState(prev => ({ ...prev, loading: true, error: null }));
    try {
      const data = await PlaylistService.getSongsWithTrackPlays(page, size, sort, direction, search);
      setState({
        songs: data.songs || data.content || [],
        totalSongs: data.totalSongs ?? data.totalElements ?? 0,
        totalPages: data.totalPages ?? 0,
        currentPage: data.currentPage ?? data.number ?? page,
        hasNext: data.hasNext ?? !(data.last ?? true),
        hasPrevious: data.hasPrevious ?? !(data.first ?? true),
        loading: false,
        error: null
      });
    } catch (err) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: err instanceof Error ? err.message : 'Failed to fetch songs'
      }));
    }
  }, []);

  const fetchStatus = useCallback(async () => {
    try {
      const status = await PlaylistService.getPlaylistStatus();
      setStatusData({
        totalSongs: status.totalSongs ?? 0,
        totalPlays: status.totalPlays ?? 0,
        trackingStartDate: status.trackingStartDate ?? null,
        lastSyncTime: status.lastSyncTime ?? null
      });
    } catch (err) {
      console.error('Failed to fetch playlist status:', err);
    }
  }, []);

  useEffect(() => {
    fetchSongs(0, searchTerm, sortBy, sortDirection, pageSize);
    fetchStatus();
  }, [fetchSongs, fetchStatus]);

  const handleSearch = (term: string) => {
    setSearchTerm(term);
    fetchSongs(0, term, sortBy, sortDirection, pageSize);
  };

  const handleClearSearch = () => {
    setSearchTerm('');
    fetchSongs(0, '', sortBy, sortDirection, pageSize);
  };

  const handleSort = (column: string) => {
    const newDirection = sortBy === column && sortDirection === 'asc' ? 'desc' : 'asc';
    setSortBy(column);
    setSortDirection(newDirection);
    fetchSongs(state.currentPage, searchTerm, column, newDirection, pageSize);
  };

  const handlePageChange = (page: number) => {
    fetchSongs(page, searchTerm, sortBy, sortDirection, pageSize);
  };

  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    fetchSongs(0, searchTerm, sortBy, sortDirection, size);
  };

  const displayTotalSongs = statusData?.totalSongs ?? state.totalSongs;

  return (
    <div className="playlist-view">
      <CurrentTrack />
      
      <div className="playlist-header">
        <h2>Music Playlist</h2>
      </div>

      <SimpleSearchWithButton
        searchTerm={searchTerm}
        onSearch={handleSearch}
        onClear={handleClearSearch}
        disabled={state.loading}
      />

      {state.error && (
        <div className="error-message">
          Error: {state.error}
        </div>
      )}

      {state.loading ? (
        <div className="loading">Loading songs...</div>
      ) : (
        <>
          <SongTable
            songs={state.songs}
            sortBy={sortBy}
            sortDirection={sortDirection}
            onSort={handleSort}
          />

          <Pagination
            currentPage={state.currentPage}
            totalPages={state.totalPages}
            pageSize={pageSize}
            onPageChange={handlePageChange}
            onPageSizeChange={handlePageSizeChange}
            hasNext={state.hasNext}
            hasPrevious={state.hasPrevious}
          />

          <PlaylistStats
            totalSongs={displayTotalSongs}
            showingSongs={state.songs.length}
            latestSongTime={statusData?.lastSyncTime}
            totalPlays={statusData?.totalPlays}
            trackingStartDate={statusData?.trackingStartDate}
          />
        </>
      )}
    </div>
  );
};

export default PlaylistView;
