import React, { useState } from 'react';

interface ManualSearchBarProps {
  onSearch: (term: string) => void;
  searchTerm: string;
}

const ManualSearchBar: React.FC<ManualSearchBarProps> = ({ onSearch, searchTerm }) => {
  const [inputValue, setInputValue] = useState<string>(searchTerm);

  // MANUAL SEARCH ONLY - NO AUTOMATIC SEARCHING ON TEXT CHANGE
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSearch(inputValue);
  };

  const handleClear = () => {
    setInputValue('');
    onSearch('');
  };

  // ONLY UPDATE INPUT VALUE - NO SEARCHING
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInputValue(e.target.value);
    // NO onSearch CALL HERE - MANUAL ONLY!
  };

  // MANUAL SEARCH ON ENTER KEY
  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
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
            onKeyPress={handleKeyPress}
            placeholder="MANUAL SEARCH: Press Enter or click Search button"
            className="search-input"
          />
          <button 
            type="submit" 
            className="search-button"
            aria-label="Manual Search"
          >
            🔍 SEARCH
          </button>
          {inputValue && (
            <button 
              type="button" 
              onClick={handleClear} 
              className="clear-button"
              aria-label="Clear search"
            >
              ✕ CLEAR
            </button>
          )}
        </div>
      </form>
    </div>
  );
};

export default ManualSearchBar;