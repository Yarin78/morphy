import { useEffect, useRef, useState } from 'react';
import {
  LINE_EVALUATIONS,
  LINE_RESULT,
  RESULTS,
  lineEvaluationSymbol,
  validateGameInfo,
} from '../utils/gameInfo';
import type { GameInfo, GameInfoErrors } from '../utils/gameInfo';
import './GameInfoDialog.css';

interface GameInfoDialogProps {
  initial: GameInfo;
  onSave: (info: GameInfo) => void;
  onCancel: () => void;
}

type InputProps = React.InputHTMLAttributes<HTMLInputElement> & { ref?: React.Ref<HTMLInputElement> };

export const GameInfoDialog: React.FC<GameInfoDialogProps> = ({ initial, onSave, onCancel }) => {
  const [info, setInfo] = useState<GameInfo>(initial);
  const [errors, setErrors] = useState<GameInfoErrors>({});
  const firstFieldRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    firstFieldRef.current?.focus();
  }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onCancel]);

  const set = (field: keyof GameInfo) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setInfo((current) => ({ ...current, [field]: e.target.value }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const found = validateGameInfo(info);
    setErrors(found);
    if (Object.keys(found).length === 0) onSave(info);
  };

  const input = (name: keyof GameInfo, props: InputProps = {}) => (
    <>
      <input
        type="text"
        value={info[name]}
        onChange={set(name)}
        aria-invalid={errors[name] ? true : undefined}
        // Not a login form: keep browsers and password managers from offering to fill it in
        autoComplete="off"
        data-1p-ignore
        data-lpignore="true"
        {...props}
      />
      {errors[name] && <span className="game-info-error">{errors[name]}</span>}
    </>
  );

  /** An input with its label above it. */
  const field = (name: keyof GameInfo, label: string, props: InputProps = {}) => (
    <label className={`game-info-field game-info-field-${name}`}>
      <span className="game-info-label">{label}</span>
      {input(name, props)}
    </label>
  );

  /** An input in the Players grid, labelled by its row and column headings. */
  const playerField = (name: keyof GameInfo, label: string, props: InputProps = {}) => (
    <div className={`game-info-field game-info-field-${name}`}>{input(name, { 'aria-label': label, ...props })}</div>
  );

  const numberProps = { inputMode: 'numeric' as const };

  // Keep an evaluation the list doesn't offer selectable, rather than silently dropping it.
  const evaluations =
    !initial.lineEvaluation || LINE_EVALUATIONS.some((e) => e.value === initial.lineEvaluation)
      ? LINE_EVALUATIONS
      : [...LINE_EVALUATIONS, { value: initial.lineEvaluation, symbol: lineEvaluationSymbol(initial.lineEvaluation) }];

  return (
    <div className="game-info-overlay" onMouseDown={onCancel}>
      <form
        className="game-info-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="game-info-title"
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
        noValidate
      >
        <h3 id="game-info-title">Edit Game Info</h3>

        <fieldset className="game-info-players">
          <legend>Players</legend>
          <span />
          <span className="game-info-label">Last name</span>
          <span className="game-info-label">First name</span>
          <span className="game-info-label">Rating</span>

          <span className="game-info-row-label">White</span>
          {playerField('whiteLastName', 'White last name', { ref: firstFieldRef })}
          {playerField('whiteFirstName', 'White first name')}
          {playerField('whiteElo', 'White rating', numberProps)}

          <span className="game-info-row-label">Black</span>
          {playerField('blackLastName', 'Black last name')}
          {playerField('blackFirstName', 'Black first name')}
          {playerField('blackElo', 'Black rating', numberProps)}
        </fieldset>

        <fieldset className="game-info-row">
          <legend>Tournament</legend>
          {field('tournament', 'Name')}
          {field('round', 'Round', numberProps)}
          {field('subRound', 'Sub-round', numberProps)}
          {field('board', 'Board', numberProps)}
        </fieldset>

        <div className="game-info-pair">
          <fieldset className="game-info-row">
            <legend>Result</legend>
            <label className="game-info-field">
              <span className="game-info-label">Result</span>
              <select value={info.result} onChange={set('result')}>
                {RESULTS.map((r) => (
                  <option key={r.value} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </select>
            </label>
            {info.result === LINE_RESULT && (
              <label className="game-info-field">
                <span className="game-info-label">Evaluation</span>
                <select value={info.lineEvaluation} onChange={set('lineEvaluation')}>
                  <option value=""></option>
                  {evaluations.map((e) => (
                    <option key={e.value} value={e.value}>
                      {e.symbol}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </fieldset>

          <fieldset className="game-info-row">
            <legend>Date</legend>
            {field('year', 'Year', { ...numberProps, placeholder: 'yyyy' })}
            {field('month', 'Month', { ...numberProps, placeholder: 'mm' })}
            {field('day', 'Day', { ...numberProps, placeholder: 'dd' })}
          </fieldset>
        </div>

        <div className="game-info-buttons">
          <button type="button" className="game-info-cancel" onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" className="game-info-ok">
            OK
          </button>
        </div>
      </form>
    </div>
  );
};
