import React, { useState } from 'react';

interface ButtonSearchBarProps {
  onSearch: (term: string) => void;
  searchTerm: string;
}

// COMPLETELY NEW SEARCH COMPONENT - BUTTON/ENTER ONLY
const ButtonSearchBar: React.FC<ButtonSearchBarProps> = ({ onSearch, searchTerm }) => {
  const [inputValue, setInputValue] = useState<string>(searchTerm);

  // SEARCH ONLY ON FORM SUBMIT - NEVER ON TEXT CHANGE
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSearch(inputValue);
  };

  const handleClear = () => {
    setInputValue('');
    onSearch('');
  };

  // TEXT CHANGE ONLY UPDATES STATE - NO SEARCH TRIGGERED
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInputValue(e.target.value);
    // CRITICAL: NO onSearch() CALL HERE
  };

  // ENTER KEY TRIGGERS SEARCH
  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      onSearch(inputValue);
    }
  };

  return (
    <div className="search-container">
      <form onSubmit={handleSubmit} className="search-form">
        <div className="search-input-group">
          <input
            type="text"
            value={inputValue}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            placeholder="🔍 BUTTON/ENTER SEARCH ONLY - Type and press Enter or click Search"
            className="search-input"
            style={{ border: '2px solid #4CAF50', padding: '8px' }}
          />
          <button 
            type="submit" 
            className="search-button"
            style={{ backgroundColor: '#4CAF50', color: 'white', padding: '8px 12px', border: 'none' }}
          >
            🔍 SEARCH
          </button>
          {inputValue && (
            <button 
              type="button" 
              onClick={handleClear} 
              className="clear-button"
              style={{ backgroundColor: '#f44336', color: 'white', padding: '8px 12px', border: 'none' }}
            >
              ✕ CLEAR
            </button>
          )}
        </div>
      </form>
    </div>
  );
};

export default ButtonSearchBar;