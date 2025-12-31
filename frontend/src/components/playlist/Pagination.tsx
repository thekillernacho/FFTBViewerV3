import React from 'react';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  hasNext: boolean;
  hasPrevious: boolean;
}

const Pagination: React.FC<PaginationProps> = ({ 
  currentPage, 
  totalPages, 
  pageSize, 
  onPageChange, 
  onPageSizeChange, 
  hasNext, 
  hasPrevious 
}) => {
  const pageSizeOptions = [10, 25, 50, 100, 250, 500];

  return (
    <div className="pagination-container">
      <div className="pagination-info">
        <span>Page <strong>{currentPage + 1}</strong> of <strong>{totalPages}</strong></span>
      </div>
      
      <div className="pagination-controls">
        <button 
          onClick={() => onPageChange(0)}
          disabled={!hasPrevious}
          className="pagination-button"
          title="Go to first page"
        >
          First
        </button>
        
        <button 
          onClick={() => onPageChange(currentPage - 1)}
          disabled={!hasPrevious}
          className="pagination-button"
        >
          Previous
        </button>
        
        <button 
          onClick={() => onPageChange(currentPage + 1)}
          disabled={!hasNext}
          className="pagination-button"
        >
          Next
        </button>
        
        <button 
          onClick={() => onPageChange(totalPages - 1)}
          disabled={!hasNext}
          className="pagination-button"
          title="Go to last page"
        >
          Last
        </button>
      </div>

      <div className="page-size-container">
        <span className="page-size-label">Page Size:</span>
        <select 
          value={pageSize} 
          onChange={(e) => onPageSizeChange(parseInt(e.target.value))}
          className="page-size-select"
        >
          {pageSizeOptions.map(size => (
            <option key={size} value={size}>{size}</option>
          ))}
        </select>
      </div>
    </div>
  );
};

export default Pagination;