import { LINE_EVALUATIONS, LINE_RESULT, lineEvaluationSymbol, RESULTS } from '../utils/gameInfo';
import type { ResultGroup } from '../utils/gameInfo';

interface ResultFieldProps {
  /** A PGN Result tag value: one of RESULTS. */
  result: string;
  /** A line's evaluation, a NAG name; '' for none. */
  lineEvaluation: string;
  onChange: (result: string, lineEvaluation: string) => void;
  /** The evaluation the game had, kept selectable even if it isn't one of LINE_EVALUATIONS. */
  initialLineEvaluation: string;
}

/** Between a line's result and its evaluation in the dropdown's values; in neither of them. */
const SEPARATOR = '|';

/** A line's option is its result followed by the evaluation, if any. */
const choice = (result: string, lineEvaluation: string) =>
  result === LINE_RESULT && lineEvaluation ? `${LINE_RESULT}${SEPARATOR}${lineEvaluation}` : result;

/**
 * The game's result, or for a line, its evaluation: picked together, as one choice, from a single
 * dropdown, since a game has one or the other.
 */
export const ResultField: React.FC<ResultFieldProps> = ({ result, lineEvaluation, onChange, initialLineEvaluation }) => {
  // Keep an evaluation the list doesn't offer selectable, rather than silently dropping it
  const evaluations =
    !initialLineEvaluation || LINE_EVALUATIONS.some((e) => e.value === initialLineEvaluation)
      ? LINE_EVALUATIONS
      : [...LINE_EVALUATIONS, { value: initialLineEvaluation, symbol: lineEvaluationSymbol(initialLineEvaluation) }];
  const options = (group: ResultGroup) =>
    RESULTS.filter((r) => r.group === group).map((r) => (
      <option key={r.value} value={r.value}>
        {r.label}
      </option>
    ));
  return (
    <label className="game-info-field game-info-field-result">
      <span className="game-info-label">Result</span>
      <select
        value={choice(result, lineEvaluation)}
        onChange={(e) => {
          const [chosen, evaluation = ''] = e.target.value.split(SEPARATOR);
          onChange(chosen, evaluation);
        }}
      >
        {options('game')}
        <optgroup label="Line, with its evaluation">
          {options('line')}
          {evaluations.map((e) => (
            <option key={e.value} value={choice(LINE_RESULT, e.value)}>
              Line {e.symbol}
            </option>
          ))}
        </optgroup>
        <optgroup label="Forfeits, both lost">{options('other')}</optgroup>
      </select>
    </label>
  );
};
