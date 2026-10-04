import { NAG_PALETTE, nagInfo, nagsOf, type NagType } from '../model/nags';
import type { Annotation } from '../model/annotations';
import type { MoveAction } from './moveActions';

/** The groups of buttons of the bar: what can be done to the moves and their comments, and each type of NAG. */
export type NotationBarGroup = 'moves' | 'comments' | NagType;

/** The groups of the bar in order, with what they're called where they're picked. */
export const NOTATION_BAR_GROUPS: readonly { id: NotationBarGroup; label: string }[] = [
  { id: 'moves', label: 'Moves: promote and delete variations' },
  { id: 'comments', label: 'Comments, null move and clearing' },
  { id: 'moveComment', label: 'Move symbols: ! ? !! ??' },
  { id: 'lineEvaluation', label: 'Evaluations: ± = ∓' },
  { id: 'movePrefix', label: 'Prefixes: □ ∆ ⌓' },
];

interface NagBarProps {
  /** The annotations of the current move, or null at the start position, which has no move. */
  annotations: readonly Annotation[] | null;
  /** Adds the NAG to the current move, or removes it if the move has it. */
  onToggle: (nag: number) => void;
  /** The things that can be done from the current move, in groups, shown first. */
  actionGroups?: readonly { id: NotationBarGroup; actions: readonly MoveAction[] }[];
  /** The groups shown; all by default */
  shownGroups?: ReadonlySet<NotationBarGroup>;
}

/**
 * The NAGs that can be given a move, for the bar below the notation, a group for each type:
 * comments on the move, evaluations and prefixes; after the things that can be done from it.
 * Clicking one the current move has takes it away again.
 */
export const NagBar: React.FC<NagBarProps> = ({ annotations, onToggle, actionGroups = [], shownGroups }) => {
  const current = annotations ? nagsOf(annotations) : [];
  const shown = (group: NotationBarGroup) => !shownGroups || shownGroups.has(group);
  return (
    <>
      {actionGroups.filter(({ id }) => shown(id)).map(({ id, actions }) => (
        <div key={id} className="notation-bar-group" role="group" aria-label="Moves">
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
      {NAG_PALETTE.filter((group) => shown(group.type)).map((group) => (
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
