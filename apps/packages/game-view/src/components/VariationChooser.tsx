import { useEffect, useRef, useState } from 'react';
import type { MoveNode } from '../model/GameTree';
import { lineStart } from '../utils/variationChoice';
import './VariationChooser.css';

interface VariationChooserProps {
  /** The moves to choose between, the main move last. */
  moves: readonly MoveNode[];
  /** Plays the move chosen. */
  onChoose: (move: MoveNode) => void;
  /** Goes back without playing a move. */
  onCancel: () => void;
}

// The moves shown of a variation; of the main line, just the move
const VARIATION_PLIES = 6;

/**
 * A popup to choose the next move by when there are variations: each with the start of its line,
 * and the main move on its own.
 * The arrow keys up and down pick one, the main move to begin with; Enter or the right arrow plays
 * it, and the left arrow, Escape or a click outside goes back.
 */
export const VariationChooser: React.FC<VariationChooserProps> = ({ moves, onChoose, onCancel }) => {
  const [selected, setSelected] = useState(moves.length - 1);
  const popupRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'ArrowUp':
          setSelected((i) => Math.max(0, i - 1));
          break;
        case 'ArrowDown':
          setSelected((i) => Math.min(moves.length - 1, i + 1));
          break;
        case 'Enter':
        case 'ArrowRight':
          onChoose(moves[selected]);
          break;
        case 'ArrowLeft':
        case 'Escape':
          onCancel();
          break;
        default:
          return;
      }
      e.preventDefault();
      // Before the keys go through the moves of the notation
      e.stopImmediatePropagation();
    };
    const handleMouseDown = (e: MouseEvent) => {
      if (!popupRef.current?.contains(e.target as Node)) onCancel();
    };
    // Capturing, to come before the other handlers of the keys
    window.addEventListener('keydown', handleKeyDown, true);
    window.addEventListener('mousedown', handleMouseDown, true);
    return () => {
      window.removeEventListener('keydown', handleKeyDown, true);
      window.removeEventListener('mousedown', handleMouseDown, true);
    };
  }, [moves, selected, onChoose, onCancel]);

  return (
    <div className="variation-chooser" ref={popupRef} role="listbox" aria-labelledby="variation-chooser-title">
      <div className="variation-chooser-title" id="variation-chooser-title">
        Choose variation
      </div>
      {moves.map((move, i) => (
        <div
          key={i}
          className={`variation-chooser-line${i === selected ? ' selected' : ''}${i === moves.length - 1 ? ' main' : ''}`}
          role="option"
          aria-selected={i === selected}
          onMouseEnter={() => setSelected(i)}
          onClick={() => onChoose(move)}
        >
          {lineStart(move, i === moves.length - 1 ? 1 : VARIATION_PLIES)}
        </div>
      ))}
    </div>
  );
};
