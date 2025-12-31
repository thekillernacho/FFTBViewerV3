import React, { useState } from 'react';

interface UltimateManualSearchBarProps {
  searchTerm: string;
  onSearch: (term: string) => void;
  onClear: () => void;
}

// COMPLETELY NEW SEARCH COMPONENT - 100% MANUAL TRIGGERS ONLY
export const UltimateManualSearchBar: React.FC<UltimateManualSearchBarProps> = ({ 
  searchTerm, 
  onSearch, 
  onClear 
}) => {
  const [inputValue, setInputValue] = useState(searchTerm);

  // FORM SUBMISSION - MANUAL TRIGGER
  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    console.log('MANUAL SEARCH TRIGGERED BY FORM SUBMIT:', inputValue);
    onSearch(inputValue);
  };

  // BUTTON CLICK - MANUAL TRIGGER  
  const handleButtonClick = () => {
    console.log('MANUAL SEARCH TRIGGERED BY BUTTON CLICK:', inputValue);
    onSearch(inputValue);
  };

  // ENTER KEY - MANUAL TRIGGER
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      console.log('MANUAL SEARCH TRIGGERED BY ENTER KEY:', inputValue);
      onSearch(inputValue);
    }
  };

  // INPUT CHANGE - ONLY UPDATES STATE, NO SEARCH
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setInputValue(newValue);
    console.log('INPUT CHANGED (NO SEARCH TRIGGERED):', newValue);
    // CRITICAL: NO onSearch() CALL HERE - ONLY STATE UPDATE
  };

  const handleClearClick = () => {
    setInputValue('');
    onClear();
  };

  return (
    <div style={{
      backgroundColor: '#ff0000', // RED BACKGROUND TO SHOW NEW COMPONENT
      padding: '20px',
      margin: '20px 0',
      border: '5px solid #00ff00', // GREEN BORDER
      borderRadius: '10px'
    }}>
      <div style={{ 
        color: '#ffffff', 
        fontSize: '18px', 
        fontWeight: 'bold',
        marginBottom: '10px',
        textAlign: 'center'
      }}>
        🚨 ULTIMATE MANUAL SEARCH BAR - VERSION 22 🚨
      </div>
      
      <form onSubmit={handleFormSubmit} style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
        <input
          type="text"
          value={inputValue}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          placeholder="Type here... SEARCH ONLY on button click or Enter key"
          style={{
            flex: 1,
            padding: '15px',
            fontSize: '16px',
            border: '3px solid #ffff00', // YELLOW BORDER
            backgroundColor: '#000000',
            color: '#ffffff',
            borderRadius: '5px'
          }}
        />
        
        <button
          type="button"
          onClick={handleButtonClick}
          style={{
            padding: '15px 25px',
            fontSize: '16px',
            fontWeight: 'bold',
            backgroundColor: '#00ff00',
            color: '#000000',
            border: 'none',
            borderRadius: '5px',
            cursor: 'pointer'
          }}
        >
          🔍 MANUAL SEARCH
        </button>
        
        {inputValue && (
          <button
            type="button"
            onClick={handleClearClick}
            style={{
              padding: '15px',
              fontSize: '16px',
              backgroundColor: '#ff4444',
              color: '#ffffff',
              border: 'none',
              borderRadius: '5px',
              cursor: 'pointer'
            }}
          >
            ✕ CLEAR
          </button>
        )}
      </form>
      
      <div style={{ 
        color: '#ffff00', 
        fontSize: '14px',
        marginTop: '10px',
        textAlign: 'center'
      }}>
        TEXT CHANGES DO NOT TRIGGER SEARCH - ONLY BUTTON CLICK OR ENTER KEY
      </div>
    </div>
  );
};