import React, { useEffect, useState } from 'react';

interface SimpleSearchWithButtonProps {
  searchTerm: string;
  onSearch: (term: string) => void;
  onClear: () => void;
  disabled?: boolean;
}

export const SimpleSearchWithButton: React.FC<SimpleSearchWithButtonProps> = ({ 
  searchTerm, 
  onSearch, 
  onClear,
  disabled = false
}) => {
  const [inputValue, setInputValue] = useState(searchTerm);

  // Keep the input in sync when the parent updates searchTerm (e.g. Clear).
  useEffect(() => {
    setInputValue(searchTerm);
  }, [searchTerm]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInputValue(e.target.value);
  };

  const handleSearch = () => {
    onSearch(inputValue);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (!disabled) onSearch(inputValue);
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
        onKeyDown={handleKeyDown}
        disabled={disabled}
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
        disabled={disabled}
        style={{
          padding: '12px 20px',
          fontSize: '16px',
          background: '#007bff',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: disabled ? 'not-allowed' : 'pointer',
          opacity: disabled ? 0.7 : 1
        }}
      >
        Search
      </button>

      {inputValue && (
        <button 
          onClick={handleClear}
          disabled={disabled}
          style={{
            padding: '12px',
            fontSize: '16px',
            background: '#dc3545',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: disabled ? 'not-allowed' : 'pointer',
            opacity: disabled ? 0.7 : 1
          }}
        >
          ✕
        </button>
      )}
    </div>
  );
};