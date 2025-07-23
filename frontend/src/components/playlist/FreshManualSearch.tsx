import React, { useState } from 'react';

interface FreshManualSearchProps {
  searchTerm: string;
  onSearch: (term: string) => void;
  onClear: () => void;
}

// BRAND NEW COMPONENT - ZERO AUTOMATIC SEARCH TRIGGERS
export const FreshManualSearch: React.FC<FreshManualSearchProps> = ({ 
  searchTerm, 
  onSearch, 
  onClear 
}) => {
  const [localValue, setLocalValue] = useState(searchTerm);

  // MANUAL SEARCH - BUTTON CLICK ONLY
  const triggerManualSearch = () => {
    console.log('🔍 MANUAL SEARCH ACTIVATED:', localValue);
    onSearch(localValue);
  };

  // MANUAL SEARCH - ENTER KEY ONLY
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      console.log('⌨️ ENTER KEY SEARCH ACTIVATED:', localValue);
      onSearch(localValue);
    }
  };

  // INPUT CHANGE - NO SEARCH TRIGGER
  const updateInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setLocalValue(value);
    console.log('📝 INPUT UPDATED (NO SEARCH):', value);
    // CRITICAL: NO onSearch() call here
  };

  const clearSearch = () => {
    setLocalValue('');
    onClear();
  };

  return (
    <div style={{
      background: 'linear-gradient(45deg, #2a2a2a, #3a3a3a)',
      padding: '25px',
      margin: '15px 0',
      borderRadius: '12px',
      border: '3px solid #00ff00',
      boxShadow: '0 0 20px rgba(0,255,0,0.3)'
    }}>
      <div style={{
        color: '#00ff00',
        fontSize: '20px',
        fontWeight: 'bold',
        textAlign: 'center',
        marginBottom: '15px',
        textShadow: '0 0 10px rgba(0,255,0,0.5)'
      }}>
        🚀 FRESH MANUAL SEARCH - NO AUTO-SEARCH 🚀
      </div>
      
      <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <input
          type="text"
          value={localValue}
          onChange={updateInput}
          onKeyPress={handleKeyPress}
          placeholder="Type here... Search ONLY with button or Enter"
          style={{
            flex: 1,
            padding: '16px',
            fontSize: '18px',
            border: '2px solid #00ff00',
            borderRadius: '8px',
            backgroundColor: '#1a1a1a',
            color: '#ffffff',
            outline: 'none'
          }}
        />
        
        <button
          type="button"
          onClick={triggerManualSearch}
          style={{
            padding: '16px 30px',
            fontSize: '18px',
            fontWeight: 'bold',
            backgroundColor: '#00ff00',
            color: '#000000',
            border: 'none',
            borderRadius: '8px',
            cursor: 'pointer',
            boxShadow: '0 4px 8px rgba(0,0,0,0.3)'
          }}
        >
          🔍 SEARCH
        </button>
        
        {localValue && (
          <button
            type="button"
            onClick={clearSearch}
            style={{
              padding: '16px',
              fontSize: '18px',
              backgroundColor: '#ff4444',
              color: '#ffffff',
              border: 'none',
              borderRadius: '8px',
              cursor: 'pointer'
            }}
          >
            ✕
          </button>
        )}
      </div>
      
      <div style={{
        color: '#ffff00',
        fontSize: '16px',
        textAlign: 'center',
        marginTop: '12px',
        fontWeight: 'bold'
      }}>
        ⚡ MANUAL TRIGGERS ONLY - NO AUTOMATIC SEARCH ⚡
      </div>
    </div>
  );
};