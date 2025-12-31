import React, { useState } from 'react';

interface FinalSearchBarProps {
  searchTerm: string;
  onSearch: (term: string) => void;
  onClear: () => void;
}

export const FinalSearchBar: React.FC<FinalSearchBarProps> = ({ 
  searchTerm, 
  onSearch, 
  onClear 
}) => {
  const [localSearchTerm, setLocalSearchTerm] = useState(searchTerm);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSearch(localSearchTerm);
  };

  const handleSearchClick = () => {
    onSearch(localSearchTerm);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onSearch(localSearchTerm);
    }
  };

  const handleClear = () => {
    setLocalSearchTerm('');
    onClear();
  };

  return (
    <div style={{
      display: 'flex',
      gap: '10px',
      alignItems: 'center',
      backgroundColor: '#2d3748',
      padding: '15px',
      borderRadius: '8px',
      border: '2px solid #4a5568'
    }}>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px', alignItems: 'center', flex: 1 }}>
        <input
          type="text"
          value={localSearchTerm}
          onChange={(e) => setLocalSearchTerm(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder="Search songs... (Press Enter or click Search)"
          style={{
            flex: 1,
            padding: '10px',
            borderRadius: '4px',
            border: '2px solid #22c55e',
            backgroundColor: '#1a202c',
            color: '#ffffff',
            fontSize: '16px'
          }}
        />
        <button
          type="button"
          onClick={handleSearchClick}
          style={{
            padding: '10px 20px',
            backgroundColor: '#22c55e',
            color: '#ffffff',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontSize: '16px',
            fontWeight: 'bold'
          }}
        >
          🔍 SEARCH
        </button>
        {localSearchTerm && (
          <button
            type="button"
            onClick={handleClear}
            style={{
              padding: '10px',
              backgroundColor: '#ef4444',
              color: '#ffffff',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            ✕
          </button>
        )}
      </form>
      <div style={{ 
        color: '#a0aec0', 
        fontSize: '12px', 
        fontStyle: 'italic',
        minWidth: '200px'
      }}>
        MANUAL SEARCH ONLY - No automatic searching
      </div>
    </div>
  );
};