import React from 'react';
import './PromotionDialog.css';

type PieceType = 'q' | 'r' | 'b' | 'n';
type Color = 'white' | 'black';

interface PromotionDialogProps {
  color: Color;
  onSelect: (piece: PieceType) => void;
  onCancel: () => void;
}

export const PromotionDialog: React.FC<PromotionDialogProps> = ({ color, onSelect, onCancel }) => {
  const pieces: { type: PieceType; name: string }[] = [
    { type: 'q', name: 'Queen' },
    { type: 'r', name: 'Rook' },
    { type: 'b', name: 'Bishop' },
    { type: 'n', name: 'Knight' },
  ];

  return (
    <>
      <div className="promotion-dialog-overlay" onClick={onCancel} />
      <div className="promotion-dialog-positioner">
        <div className="promotion-dialog cg-wrap" onClick={(e) => e.stopPropagation()}>
          <h3>Choose promotion piece</h3>
          <div className="promotion-pieces">
            {pieces.map(({ type, name }) => (
              <button
                key={type}
                className="promotion-piece-button"
                onClick={() => onSelect(type)}
                title={name}
              >
                {React.createElement('piece', { className: `${color} ${name.toLowerCase()}` })}
              </button>
            ))}
          </div>
          <button className="promotion-cancel" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </div>
    </>
  );
};
