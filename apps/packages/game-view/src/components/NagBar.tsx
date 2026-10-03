import { NAG_PALETTE, nagInfo, nagsOf } from '../model/nags';
import type { Annotation } from '../model/annotations';
import type { MoveAction } from './moveActions';

interface NagBarProps {
  /** The annotations of the current move, or null at the start position, which has no move. */
  annotations: readonly Annotation[] | null;
  /** Adds the NAG to the current move, or removes it if the move has it. */
  onToggle: (nag: number) => void;
  /** The things that can be done from the current move, in groups, shown first. */
  actionGroups?: readonly (readonly MoveAction[])[];
}

/**
 * The NAGs that can be given a move, for the bar below the notation, a group for each type:
 * comments on the move, evaluations and prefixes; after the things that can be done from it.
 * Clicking one the current move has takes it away again.
 */
export const NagBar: React.FC<NagBarProps> = ({ annotations, onToggle, actionGroups = [] }) => {
  const current = annotations ? nagsOf(annotations) : [];
  return (
    <>
      {actionGroups.map((actions, i) => (
        <div key={i} className="notation-bar-group" role="group" aria-label="Moves">
          {actions.map((action) => (
            <button
              key={action.label}
              type="button"
              className="nag-bar-button"
              title={action.shortcut ? `${action.label} (${action.shortcut})` : action.label}
              disabled={action.disabled}
              // Keep the focus where it is, so the arrow keys still go through the moves
              onMouseDown={(e) => e.preventDefault()}
              onClick={action.run}
            >
              {action.icon}
            </button>
          ))}
        </div>
      ))}
      {NAG_PALETTE.map((group) => (
        <div key={group.type} className="notation-bar-group" role="group" aria-label="Symbols">
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
    </>
  );
};
