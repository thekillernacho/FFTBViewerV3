import React, { useState, useEffect } from 'react';
import { PlaylistService } from '../../services/PlaylistService';
import { PlaylistData, PlaylistDataWithView } from '../../types';
import { FinalSearchBar } from './FinalSearchBar';
import SongTable from './SongTable';
import Pagination from './Pagination';
import PlaylistStats from './PlaylistStats';
import CurrentTrack from './CurrentTrack';
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
  const [totalSongs, setTotalSongs] = useState<number>(0);

  // Reset page when search term changes
  useEffect(() => {
    setCurrentPage(0);
  }, [searchTerm]);

  // Load playlist data
  useEffect(() => {
    const loadData = async () => {
      try {
        setLoading(true);
        setError(null);
        
        const [data, statusResponse] = await Promise.all([
          PlaylistService.getSongsWithTrackPlays(currentPage, pageSize, sortBy, sortDirection, searchTerm),
          PlaylistService.getPlaylistStatus()
        ]);
        
        setPlaylistData(data);
        setLatestSongTime(statusResponse.lastSyncTime);
        setTotalPlays(statusResponse.totalPlays || 0);
        setTrackingStartDate(statusResponse.trackingStartDate);
        // Only update totalSongs if we have valid data to prevent it from being reset to 0
        if (data && data.totalElements !== undefined && data.totalElements > 0) {
          setTotalSongs(data.totalElements);
        }
      } catch (err) {
        console.error('Error loading playlist data:', err);
        setError('Failed to load playlist data. Please try again.');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [currentPage, pageSize, sortBy, sortDirection, searchTerm]);

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

  const renderPlaylistContent = () => {
    if (loading) {
      return <div className={styles.loading}>Loading playlist...</div>;
    }

    if (error) {
      return <div className={styles.error}>{error}</div>;
    }

    if (!playlistData) {
      return <div className={styles.error}>No playlist data available</div>;
    }

    return (
      <>
        <FinalSearchBar 
          searchTerm={searchTerm} 
          onSearch={handleSearch} 
          onClear={() => handleSearch('')} 
        />
        
        <SongTable 
          songs={playlistData?.songs || []}
          sortBy={sortBy}
          sortDirection={sortDirection}
          onSort={handleSort}
        />

        <Pagination 
          currentPage={currentPage}
          totalPages={playlistData?.totalPages || 0}
          onPageChange={handlePageChange}
          pageSize={pageSize}
          onPageSizeChange={handlePageSizeChange}
          hasNext={playlistData?.hasNext ?? false}
          hasPrevious={playlistData?.hasPrevious ?? false}
        />

        <PlaylistStats 
          totalSongs={totalSongs}
          showingSongs={playlistData?.songs?.length || 0}
          latestSongTime={latestSongTime}
          totalPlays={totalPlays}
          trackingStartDate={trackingStartDate}
        />
      </>
    );
  };

  return (
    <div className={styles.playlistContainer}>
      <CurrentTrack className={styles.currentTrack} />
      {renderPlaylistContent()}
    </div>
  );
};

export default PlaylistView;