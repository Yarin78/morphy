import type { GameNode } from '../model/GameTree';
import type { EvalBar } from '../utils/evalGraph';
import './EvalGraph.css';

interface EvalGraphProps {
  /** The bars, one for each position of the main line with an evaluation. */
  bars: readonly EvalBar[];
  /** The current position, whose bar is highlighted. */
  current: GameNode;
  /** Goes to the position of a bar. */
  onSelect: (node: GameNode) => void;
}

/**
 * The evaluations of the main line as a graph: a bar for each position, up from the middle line
 * when White is better and down when Black is. Clicking a bar goes to its position.
 */
export const EvalGraph: React.FC<EvalGraphProps> = ({ bars, current, onSelect }) => (
  <div className="eval-graph" role="group" aria-label="Evaluations">
    {bars.map((bar, i) => {
      const value = bar.value ?? 0;
      const side = value >= 0 ? 'white' : 'black';
      return (
        <div
          key={i}
          className={`eval-graph-column${bar.node === current ? ' current' : ''}`}
          title={bar.label}
          // Keep the focus where it is, so the arrow keys still go through the moves
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => onSelect(bar.node)}
        >
          {bar.value !== null && (
            <div className={`eval-graph-bar ${side}`} style={{ height: `${Math.abs(value) * 50}%` }} />
          )}
        </div>
      );
    })}
  </div>
);
