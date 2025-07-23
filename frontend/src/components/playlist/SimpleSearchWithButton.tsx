import React, { useState } from 'react';

interface SimpleSearchWithButtonProps {
  searchTerm: string;
  onSearch: (term: string) => void;
  onClear: () => void;
}

export const SimpleSearchWithButton: React.FC<SimpleSearchWithButtonProps> = ({ 
  searchTerm, 
  onSearch, 
  onClear 
}) => {
  const [inputValue, setInputValue] = useState(searchTerm);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInputValue(e.target.value);
  };

  const handleSearch = () => {
    onSearch(inputValue);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      onSearch(inputValue);
    }
  };

  const handleClear = () => {
    setInputValue('');
    onClear();
  };

  return (
    <div style={{ 
      display: 'flex', 
      gap: '8px', 
      alignItems: 'center',
      marginBottom: '20px' 
    }}>
      <input
        type="text"
        value={inputValue}
        onChange={handleInputChange}
        onKeyPress={handleKeyPress}
        placeholder="Search songs... (Press Enter or click Search)"
        style={{
          flex: 1,
          padding: '12px',
          fontSize: '16px',
          border: '1px solid #ccc',
          borderRadius: '4px',
          outline: 'none'
        }}
      />
      
      <button 
        onClick={handleSearch}
        style={{
          padding: '12px 20px',
          fontSize: '16px',
          background: '#007bff',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer'
        }}
      >
        Search
      </button>
      
      {inputValue && (
        <button 
          onClick={handleClear}
          style={{
            padding: '12px',
            fontSize: '16px',
            background: '#dc3545',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}
        >
          ✕
        </button>
      )}
    </div>
  );
};