import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  ELO_KINDS,
  ELO_SERVERS,
  ELO_TIME_CONTROLS,
  eloTimeControls,
  eloTypeLabel,
  FIDE,
  internationalEloType,
} from '../utils/eloType';
import type { EloKind, EloTimeControl, EloTypeInfo } from '../utils/eloType';
import { NATIONS, nationInfo } from '../utils/nations';
import type { NationInfo } from '../utils/nations';
import { EloTypeIcons, NationFlag, ServerLogo, TimeControlIcon } from './EloTypeIcons';
import { IconSelect } from './IconSelect';

/** Whether a nation matches what's typed in the search: its name, or the start of its code. */
function nationMatches(nation: NationInfo, query: string): boolean {
  const q = query.toLowerCase();
  return nation.name.toLowerCase().includes(q) || nation.ioc.toLowerCase().startsWith(q);
}

interface RatingFieldProps {
  elo: string;
  onEloChange: (elo: string) => void;
  type: EloTypeInfo | null;
  onTypeChange: (type: EloTypeInfo | null) => void;
  /** Whose rating it is: "White". */
  player: string;
  error?: string;
  /**
   * Whether the players' ratings can be of different types; when not, changing one changes both.
   * Only a choice in the dialog, not saved with the game.
   */
  differentTypes: boolean;
  onDifferentTypesChange: (different: boolean) => void;
}

/**
 * A player's rating, with its type in front of it: FIDE, a national rating, a server's. Clicking
 * the type opens a small popover to change it; the changes apply as they're made.
 */
export const RatingField: React.FC<RatingFieldProps> = ({
  elo,
  onEloChange,
  type,
  onTypeChange,
  player,
  error,
  differentTypes,
  onDifferentTypesChange,
}) => {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<React.CSSProperties | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);

  // The popover is put on the page, not in the dialog, which would cut it off
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const rect = buttonRef.current?.getBoundingClientRect();
      if (!rect) return;
      const width = 240;
      setPosition({ top: rect.bottom + 4, left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)), width });
    };
    place();
    window.addEventListener('resize', place);
    window.addEventListener('scroll', place, true);
    return () => {
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', place, true);
    };
  }, [open]);

  // Closes on a click anywhere else
  useEffect(() => {
    if (!open) return;
    const handleMouseDown = (e: MouseEvent) => {
      const target = e.target as Node;
      if (!popoverRef.current?.contains(target) && !buttonRef.current?.contains(target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleMouseDown);
    return () => document.removeEventListener('mousedown', handleMouseDown);
  }, [open]);

  useEffect(() => {
    if (open) popoverRef.current?.querySelector<HTMLInputElement>('input:checked')?.focus();
  }, [open]);

  const current = type ?? FIDE;

  // The time control stays as it is when the kind changes, if the new kind has it
  const setKind = (kind: EloKind) => {
    if (kind === current.kind) return;
    const timeControl = eloTimeControls(kind).includes(current.timeControl) ? current.timeControl : 'NORMAL';
    if (kind === 'INTERNATIONAL') {
      onTypeChange(internationalEloType(timeControl));
    } else if (kind === 'NATIONAL') {
      onTypeChange({ kind, timeControl, nation: '' });
    } else {
      onTypeChange({ kind, timeControl, name: ELO_SERVERS[0] });
    }
  };

  const setTimeControl = (timeControl: EloTimeControl) =>
    onTypeChange(current.kind === 'INTERNATIONAL' ? internationalEloType(timeControl) : { ...current, timeControl });

  const setServer = (name: string) => onTypeChange({ ...current, name });

  // A stored type ChessBase wouldn't offer stays selectable, rather than being changed by just
  // opening the popover
  const timeControls = ELO_TIME_CONTROLS.filter(
    (t) => eloTimeControls(current.kind).includes(t.value) || t.value === current.timeControl
  );
  const servers = [...ELO_SERVERS];
  if (current.kind === 'SERVER' && current.name && !servers.includes(current.name)) servers.push(current.name);

  return (
    <div className="game-info-field game-info-field-rating">
      <div className={`rating-input${error ? ' invalid' : ''}`}>
        <button
          type="button"
          ref={buttonRef}
          className="rating-type-button"
          onClick={() => setOpen((o) => !o)}
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-label={`${player} rating type: ${eloTypeLabel(type)}`}
        >
          <EloTypeIcons type={type} />
        </button>
        <input
          type="text"
          value={elo}
          onChange={(e) => onEloChange(e.target.value)}
          inputMode="numeric"
          aria-label={`${player} rating`}
          aria-invalid={error ? true : undefined}
          autoComplete="off"
          data-1p-ignore
          data-lpignore="true"
        />
      </div>
      {error && <span className="game-info-error">{error}</span>}
      {open &&
        position &&
        createPortal(
          <div
            ref={popoverRef}
            className="rating-type-popover"
            role="dialog"
            aria-label={`${player} rating type`}
            style={position}
            onKeyDown={(e) => {
              if (e.key === 'Escape' || e.key === 'Enter') {
                // Close the popover, not the dialog, nor submit it
                e.preventDefault();
                e.stopPropagation();
                setOpen(false);
                buttonRef.current?.focus();
              }
            }}
          >
            <div className="rating-type-kinds" role="radiogroup" aria-label="Kind">
              {ELO_KINDS.map((k) => (
                <label key={k.value} className="rating-type-kind">
                  <input
                    type="radio"
                    name={`${player}-rating-kind`}
                    checked={current.kind === k.value}
                    onChange={() => setKind(k.value)}
                  />
                  {k.label}
                </label>
              ))}
            </div>
            {current.kind === 'NATIONAL' && (
              <div className="game-info-field">
                <span className="game-info-label">Nation</span>
                <IconSelect<NationInfo>
                  options={NATIONS}
                  value={nationInfo(current.nation)}
                  onChange={(nation) => onTypeChange({ ...current, nation: nation.ioc })}
                  optionKey={(nation) => nation.ioc}
                  renderOption={(nation) => (
                    <>
                      <NationFlag nation={nation.ioc} />
                      <span>{nation.name}</span>
                    </>
                  )}
                  placeholder={
                    <>
                      <NationFlag nation={undefined} />
                      <span className="icon-select-placeholder">Choose a nation</span>
                    </>
                  }
                  label="Nation"
                  matches={nationMatches}
                />
              </div>
            )}
            {current.kind === 'SERVER' && (
              <div className="game-info-field">
                <span className="game-info-label">Server</span>
                <IconSelect<string>
                  options={servers}
                  value={current.name}
                  onChange={setServer}
                  optionKey={(name) => name}
                  renderOption={(name) => (
                    <>
                      <ServerLogo name={name} />
                      <span>{name}</span>
                    </>
                  )}
                  placeholder={<span className="icon-select-placeholder">Choose a server</span>}
                  label="Server"
                />
              </div>
            )}
            <div className="game-info-field">
              <span className="game-info-label">Time control</span>
              <div className="rating-type-choices" role="radiogroup" aria-label="Time control">
                {timeControls.map((t) => (
                  <button
                    key={t.value}
                    type="button"
                    role="radio"
                    aria-checked={current.timeControl === t.value}
                    aria-label={t.label}
                    title={t.label}
                    className={`rating-type-choice${current.timeControl === t.value ? ' selected' : ''}`}
                    onClick={() => setTimeControl(t.value)}
                  >
                    {/* Normal has no icon, so it's spelled out */}
                    {t.value === 'NORMAL' ? <span className="elo-type-text">Normal</span> : <TimeControlIcon timeControl={t.value} />}
                  </button>
                ))}
              </div>
            </div>
            <label className="rating-type-different">
              <input type="checkbox" checked={differentTypes} onChange={(e) => onDifferentTypesChange(e.target.checked)} />
              Different rating types
            </label>
          </div>,
          document.body
        )}
    </div>
  );
};
