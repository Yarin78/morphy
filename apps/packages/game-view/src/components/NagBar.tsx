import { NAG_PALETTE, nagInfo, nagsOf } from '../model/nags';
import type { Annotation } from '../model/annotations';
import './NagBar.css';

interface NagBarProps {
  /** The annotations of the current move, or null at the start position, which has no move. */
  annotations: readonly Annotation[] | null;
  /** Adds the NAG to the current move, or removes it if the move has it. */
  onToggle: (nag: number) => void;
}

/**
 * A bar of the NAGs that can be given a move: comments on the move, evaluations and prefixes.
 * Clicking one the current move has takes it away again.
 */
export const NagBar: React.FC<NagBarProps> = ({ annotations, onToggle }) => {
  const current = annotations ? nagsOf(annotations) : [];
  return (
    <div className="nag-bar" role="toolbar" aria-label="Symbols">
      {NAG_PALETTE.map((group) => (
        <div key={group.type} className="nag-bar-group">
          {group.nags.map((nag) => {
            const info = nagInfo(nag)!;
            const active = current.includes(nag);
            return (
              <button
                key={nag}
                type="button"
                className="nag-bar-button"
                title={info.name}
                aria-pressed={active}
                disabled={!annotations}
                // Keep the focus where it is, so the arrow keys still go through the moves
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => onToggle(nag)}
              >
                {info.symbol}
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
};
