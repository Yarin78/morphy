import { useState } from 'react';
import type { Annotation } from '../model/annotations';
import { findAnnotation } from '../model/annotations';
import { parseTime, withClocks, withTimeSpent } from '../model/moveTime';
import { formatClock, formatTimeSpent } from '../utils/moveInfo';
import { AnnotationDialog } from './AnnotationDialog';
import './MoveTimeDialog.css';

interface MoveTimeDialogProps {
  /** The move, as its name, like '12...Nf6'. */
  moveName: string;
  /** The annotations of the move. */
  annotations: readonly Annotation[];
  /** Saves the annotations of the move with the time chosen. */
  onSave: (annotations: Annotation[]) => void;
  onCancel: () => void;
}

const TIME_HINT = 'Like 1:25:00, 4:30 or 45';

/**
 * A dialog to give a move its time: the time left on both clocks after it, or the time spent on
 * it, or to take its time away. A field left empty has no time.
 */
export const MoveTimeDialog: React.FC<MoveTimeDialogProps> = ({ moveName, annotations, onSave, onCancel }) => {
  const white = findAnnotation(annotations, 'whiteClock');
  const black = findAnnotation(annotations, 'blackClock');
  const spent = findAnnotation(annotations, 'timeSpent');
  const initial = {
    white: white ? formatClock(white.centiseconds) : '',
    black: black ? formatClock(black.centiseconds) : '',
    spent: spent ? formatTimeSpent(spent) : '',
  };
  const [mode, setMode] = useState<'clocks' | 'spent' | 'none'>(spent && !white && !black ? 'spent' : 'clocks');
  const [fields, setFields] = useState(initial);

  const error = (name: keyof typeof fields) => (parseTime(fields[name]) === null ? TIME_HINT : undefined);
  const invalid =
    mode === 'clocks' ? !!(error('white') || error('black')) : mode === 'spent' ? !!error('spent') : false;

  // A clock left as it was keeps its hundredths of a second
  const clock = (name: 'white' | 'black', original: number | undefined) => {
    if (fields[name] === initial[name]) return original;
    const seconds = parseTime(fields[name]);
    return seconds === undefined || seconds === null ? undefined : seconds * 100;
  };

  const handleSubmit = () => {
    onSave(
      mode === 'clocks'
        ? withClocks(annotations, clock('white', white?.centiseconds), clock('black', black?.centiseconds))
        : // No time spent, and so no clocks either, for no time
          withTimeSpent(annotations, mode === 'spent' ? (parseTime(fields.spent) ?? undefined) : undefined)
    );
  };

  const field = (name: keyof typeof fields, label: string, disabled: boolean) => (
    <label className="game-info-field">
      <span className="game-info-label">{label}</span>
      <input
        type="text"
        value={fields[name]}
        onChange={(e) => setFields({ ...fields, [name]: e.target.value })}
        placeholder="h:mm:ss"
        disabled={disabled}
        aria-invalid={error(name) ? true : undefined}
        autoComplete="off"
        data-1p-ignore
      />
      {!disabled && error(name) && <span className="game-info-error">{error(name)}</span>}
    </label>
  );

  return (
    <AnnotationDialog title={`Move Time: ${moveName}`} onSubmit={handleSubmit} onCancel={onCancel} invalid={invalid}>
      <label className="move-time-choice">
        <input type="radio" checked={mode === 'clocks'} onChange={() => setMode('clocks')} />
        Time left on the clocks after the move
      </label>
      <div className="move-time-fields">
        {field('white', 'White', mode !== 'clocks')}
        {field('black', 'Black', mode !== 'clocks')}
      </div>

      <label className="move-time-choice">
        <input type="radio" checked={mode === 'spent'} onChange={() => setMode('spent')} />
        Time spent on the move
      </label>
      <div className="move-time-fields">{field('spent', 'Time spent', mode !== 'spent')}</div>

      <label className="move-time-choice move-time-none">
        <input type="radio" checked={mode === 'none'} onChange={() => setMode('none')} />
        No time
      </label>
    </AnnotationDialog>
  );
};
