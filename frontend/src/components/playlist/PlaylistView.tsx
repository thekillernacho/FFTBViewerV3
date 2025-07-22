import React, { useState, useEffect } from 'react';
import { PlaylistService } from '../../services/PlaylistService';
import { PlaylistData, PlaylistDataWithView } from '../../types';
import SearchBar from './SearchBar';
import SongTable from './SongTable';
import Pagination from './Pagination';
import PlaylistStats from './PlaylistStats';
const styles = require('../../styles/PlaylistView.module.css');

const PlaylistView: React.FC = () => {
  const [playlistData, setPlaylistData] = useState<PlaylistDataWithView | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [pageSize, setPageSize] = useState<number>(50);
  const [sortBy, setSortBy] = useState<string>('updatedAt');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');
  const [latestSongTime, setLatestSongTime] = useState<string | null>(null);
  const [totalPlays, setTotalPlays] = useState<number>(0);
  const [trackingStartDate, setTrackingStartDate] = useState<string | null>(null);
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState<string>('');

  // Debounce search term
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearchTerm(searchTerm);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  // Reset page when search term changes
  useEffect(() => {
    setCurrentPage(0);
  }, [debouncedSearchTerm]);

  // Load playlist data
  useEffect(() => {
    const loadData = async () => {
      try {
        setLoading(true);
        setError(null);
        
        const [data, statusResponse] = await Promise.all([
          PlaylistService.getSongsWithTrackPlays(currentPage, pageSize, sortBy, sortDirection, debouncedSearchTerm),
          PlaylistService.getPlaylistStatus()
        ]);
        
        setPlaylistData(data);
        setLatestSongTime(statusResponse.lastSyncTime);
        setTotalPlays(statusResponse.totalPlays || 0);
        setTrackingStartDate(statusResponse.trackingStartDate);
      } catch (err) {
        console.error('Error loading playlist data:', err);
        setError('Failed to load playlist data. Please try again.');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [currentPage, pageSize, sortBy, sortDirection, debouncedSearchTerm]);

  const handleSort = (field: string) => {
    if (sortBy === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(field);
      // Default to descending for fields where users expect newest/highest first
      const defaultDescFields = ['updatedAt', 'createdAt', 'occurrence', 'trackPlayCount'];
      setSortDirection(defaultDescFields.includes(field) ? 'desc' : 'asc');
    }
    setCurrentPage(0);
  };

  const handleSearch = (term: string) => {
    setSearchTerm(term);
  };

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    setCurrentPage(0);
  };

  if (loading) {
    return (
      <div className={styles.playlistContainer}>
        <div className={styles.loading}>Loading playlist...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.playlistContainer}>
        <div className={styles.error}>{error}</div>
      </div>
    );
  }

  if (!playlistData) {
    return (
      <div className={styles.playlistContainer}>
        <div className={styles.error}>No playlist data available</div>
      </div>
    );
  }

  return (
    <div className={styles.playlistContainer}>
      <SearchBar onSearch={handleSearch} searchTerm={searchTerm} />
      
      <SongTable 
        songs={playlistData?.songs || []}
        sortBy={sortBy}
        sortDirection={sortDirection}
        onSort={handleSort}
      />
      
      <div className={styles.playlistFooter}>
        {playlistData && (
          <PlaylistStats 
            totalSongs={playlistData.totalSongs || 0}
            showingSongs={playlistData.songs?.length || 0}
            latestSongTime={latestSongTime}
            totalPlays={totalPlays}
            trackingStartDate={trackingStartDate}
          />
        )}
        
        <Pagination
          currentPage={currentPage}
          totalPages={playlistData?.totalPages || 0}
          pageSize={pageSize}
          onPageChange={handlePageChange}
          onPageSizeChange={handlePageSizeChange}
          hasNext={playlistData?.hasNext ?? false}
          hasPrevious={playlistData?.hasPrevious ?? false}
        />
      </div>
    </div>
  );
};

export default PlaylistView;