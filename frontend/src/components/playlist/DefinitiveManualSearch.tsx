import React, { useState } from 'react';

interface DefinitiveManualSearchProps {
  searchTerm: string;
  onSearch: (term: string) => void;
  onClear: () => void;
}

// DEFINITIVE MANUAL SEARCH - 46TH IMPLEMENTATION 
// ABSOLUTE FINAL VERSION - MANUAL TRIGGERS ONLY
export const DefinitiveManualSearch: React.FC<DefinitiveManualSearchProps> = ({ 
  searchTerm, 
  onSearch, 
  onClear 
}) => {
  const [inputValue, setInputValue] = useState(searchTerm);

  // CRITICAL: Only update local state, NEVER trigger search
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setInputValue(value);
    console.log('DEFINITIVE: Input changed, NO search triggered:', value);
    // ABSOLUTELY NO onSearch() call here - this is input tracking only
  };

  // MANUAL SEARCH - Button click only
  const handleButtonSearch = () => {
    console.log('DEFINITIVE: Manual button search triggered:', inputValue);
    onSearch(inputValue);
  };

  // MANUAL SEARCH - Enter key only
  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    console.log('DEFINITIVE: Manual Enter key search triggered:', inputValue);
    onSearch(inputValue);
  };

  // Clear search
  const handleClear = () => {
    setInputValue('');
    onClear();
    console.log('DEFINITIVE: Search cleared');
  };

  return (
    <div style={{
      background: 'linear-gradient(90deg, #ff6b35, #f7931e)',
      padding: '25px',
      margin: '20px 0',
      borderRadius: '15px',
      border: '4px solid #fff',
      boxShadow: '0 8px 32px rgba(255, 107, 53, 0.4)'
    }}>
      <div style={{
        color: '#fff',
        fontSize: '22px',
        fontWeight: 'bold',
        textAlign: 'center',
        marginBottom: '20px',
        textShadow: '2px 2px 4px rgba(0,0,0,0.5)'
      }}>
        DEFINITIVE MANUAL SEARCH - IMPLEMENTATION #46
      </div>
      
      <form onSubmit={handleFormSubmit} style={{ 
        display: 'flex', 
        gap: '15px', 
        alignItems: 'center' 
      }}>
        <input
          type="text"
          value={inputValue}
          onChange={handleInputChange}
          placeholder="Type here... Search ONLY with button or Enter"
          style={{
            flex: 1,
            padding: '18px',
            fontSize: '18px',
            border: '3px solid #fff',
            borderRadius: '12px',
            background: '#fff',
            color: '#333',
            outline: 'none',
            fontWeight: '500'
          }}
        />
        
        <button 
          type="button"
          onClick={handleButtonSearch}
          style={{
            padding: '18px 35px',
            fontSize: '18px',
            fontWeight: 'bold',
            background: '#fff',
            color: '#ff6b35',
            border: 'none',
            borderRadius: '12px',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
            transition: 'all 0.2s'
          }}
        >
          🔍 MANUAL SEARCH
        </button>
        
        <button 
          type="button"
          onClick={handleClear}
          style={{
            padding: '18px',
            fontSize: '18px',
            background: '#dc3545',
            color: '#fff',
            border: 'none',
            borderRadius: '12px',
            cursor: 'pointer',
            fontWeight: 'bold'
          }}
        >
          ✕
        </button>
      </form>
      
      <div style={{
        textAlign: 'center',
        color: '#fff',
        fontSize: '16px',
        marginTop: '15px',
        fontWeight: 'bold',
        textShadow: '1px 1px 2px rgba(0,0,0,0.5)'
      }}>
        MANUAL TRIGGERS ONLY - NO AUTOMATIC SEARCH
      </div>
    </div>
  );
};