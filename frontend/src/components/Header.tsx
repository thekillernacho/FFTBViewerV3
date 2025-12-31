import React from 'react';
import { ViewType } from '../types';

interface HeaderProps {
  currentView: ViewType;
  onViewChange: (view: ViewType) => void;
}

function Header({ currentView, onViewChange }: HeaderProps) {
  return (
    <div className="header">
      <h1>FFTB View</h1>
      <div className="nav-buttons">
        <button 
          className={`nav-button ${currentView === 'chat' ? 'active' : ''}`}
          onClick={() => window.location.href = '/'}
        >
          Live Chat
        </button>
        <button 
          className={`nav-button ${currentView === 'playlist' ? 'active' : ''}`}
          onClick={() => window.location.href = '/music'}
        >
          Music Playlist
        </button>
      </div>
    </div>
  );
}

export default Header;