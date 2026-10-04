import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { TbLock, TbLockOpen, TbMinus, TbPlus, TbSettings } from 'react-icons/tb';
import {
  CORES,
  getAnalysis,
  HASH_SIZES_MB,
  MAX_LINES,
  MIN_LINES,
  resumeAnalysis,
  setAnalysisOn,
  setEngine,
  setHashMb,
  setLocked,
  setMultiPv,
  setThreads,
  subscribeAnalysis,
} from '../../engine/analysis';
import { ENGINES, type EngineId } from '../../engine/engines';
import { formatScore, lineToSan, scoreForWhite } from '../../engine/lines';
import { useSettings } from '../settings';

// The moves shown of each line
const LINE_MOVES = 16;

function formatNps(nps: number): string {
  if (nps >= 1e6) return `${(nps / 1e6).toFixed(1)} Mn/s`;
  if (nps >= 1e3) return `${Math.round(nps / 1e3)} kn/s`;
  return `${nps} n/s`;
}

function formatMb(mb: number): string {
  return mb >= 1024 ? `${mb / 1024} GB` : `${mb} MB`;
}

/** The engine's settings, in a popup under the settings button: which build, and its memory. */
function EngineSettings({ onClose }: { onClose: () => void }) {
  const analysis = useSyncExternalStore(subscribeAnalysis, getAnalysis);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onDown = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) onClose();
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  const sizes = HASH_SIZES_MB.filter((mb) => analysis.maxHashMb === null || mb <= analysis.maxHashMb);
  return (
    <div className="engine-settings" ref={ref} role="dialog" aria-label="Engine settings">
      <div className="engine-settings-title">Engine</div>
      {(Object.keys(ENGINES) as EngineId[]).map((id) => (
        <label key={id} className="engine-settings-choice">
          <input type="radio" name="engine" checked={analysis.engineId === id} onChange={() => setEngine(id)} />
          {ENGINES[id].variant}
        </label>
      ))}
      <p className="engine-settings-note">
        The full engine is strongest, but about 100 MB to load the first time; the lite one loads at once.
      </p>
      <label className="engine-settings-row">
        Memory
        <select value={analysis.hashMb} onChange={(e) => setHashMb(Number(e.target.value))}>
          {sizes.map((mb) => (
            <option key={mb} value={mb}>
              {formatMb(mb)}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}

/** The engine's analysis of the position on the board: its best lines, as they deepen. */
export function EnginePane() {
  const analysis = useSyncExternalStore(subscribeAnalysis, getAnalysis);
  const { status, fen, lines, on, locked } = analysis;
  const profile = ENGINES[analysis.engineId];
  const [settingsOpen, setSettingsOpen] = useState(false);
  const { moveNotation } = useSettings().notation;

  // Analysis that was on when the app was left starts again
  useEffect(resumeAnalysis, []);

  const whiteToMove = fen?.split(' ')[1] !== 'b';
  const elsewhere = !!fen && analysis.boardFen !== fen;
  // Off, the last lines found stay shown
  const showLines = (status === 'running' || status === 'off') && !!fen && lines.length > 0;

  return (
    <div className="engine-pane">
      <div className="engine-head">
        <div className="engine-head-left">
          <button
            role="switch"
            aria-checked={on}
            className={`engine-switch${on ? ' on' : ''}`}
            onClick={() => setAnalysisOn(!on)}
            title={on ? 'Turn the engine off' : 'Turn the engine on'}
          >
            <span className="engine-switch-knob" />
          </button>
          <div className="engine-lines-stepper" title={`Lines searched: ${analysis.multiPv}`}>
            <button
              className="engine-icon-button"
              onClick={() => setMultiPv(analysis.multiPv - 1)}
              disabled={analysis.multiPv <= MIN_LINES}
              aria-label="Fewer lines"
            >
              <TbMinus />
            </button>
            <button
              className="engine-icon-button"
              onClick={() => setMultiPv(analysis.multiPv + 1)}
              disabled={analysis.multiPv >= MAX_LINES}
              aria-label="More lines"
            >
              <TbPlus />
            </button>
          </div>
          <select
            className="engine-cpus"
            value={profile.multiThreaded ? analysis.threads : 1}
            onChange={(e) => setThreads(Number(e.target.value))}
            disabled={!profile.multiThreaded}
            title={profile.multiThreaded ? 'Cores searched on' : 'This engine searches on one core'}
          >
            {Array.from({ length: CORES }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>
                {n} {n === 1 ? 'CPU' : 'CPUs'}
              </option>
            ))}
          </select>
        </div>
        <span className="engine-name" title={profile.variant}>
          {profile.name}
        </span>
        <div className="engine-head-right">
          {showLines && analysis.depth > 0 && (
            <span className="engine-stats">
              depth {analysis.depth} · {formatNps(analysis.nps)}
            </span>
          )}
          <button
            className={`engine-icon-button${locked ? ' active' : ''}`}
            onClick={() => setLocked(!locked)}
            aria-pressed={locked}
            title={
              locked
                ? 'Locked: analysing this position whatever the board shows. Unlock to follow the board.'
                : 'Lock the analysis on this position'
            }
          >
            {locked ? <TbLock /> : <TbLockOpen />}
          </button>
          <div className="engine-settings-anchor">
            <button
              className={`engine-icon-button${settingsOpen ? ' active' : ''}`}
              onClick={() => setSettingsOpen(!settingsOpen)}
              onPointerDown={(e) => e.stopPropagation()}
              aria-label="Engine settings"
              title="Engine settings"
            >
              <TbSettings />
            </button>
            {settingsOpen && <EngineSettings onClose={() => setSettingsOpen(false)} />}
          </div>
        </div>
      </div>
      <div className="engine-body">
        {elsewhere && locked && <p className="engine-locked-note">Locked on an earlier position</p>}
        {elsewhere && !locked && showLines && status === 'off' && (
          <p className="engine-locked-note">Stopped on an earlier position</p>
        )}
        {status === 'off' && !showLines && (
          <p className="engine-note">Turn the engine on to analyse the position on the board.</p>
        )}
        {status === 'starting' && (
          <p className="engine-note">
            Loading {profile.name}…{' '}
            {profile.variant.startsWith('Full') && 'The first time takes a moment: it’s about 100 MB.'}
          </p>
        )}
        {status === 'error' && <p className="engine-error">The engine couldn’t run: {analysis.error}</p>}
        {status === 'running' && lines.length === 0 && <p className="engine-note">Thinking…</p>}
        {showLines &&
          lines.map((line) => {
            const score = scoreForWhite(line.score, whiteToMove);
            return (
              <div key={line.multipv} className="engine-line">
                <span
                  className={`engine-score${score.value > 0 ? ' white' : score.value < 0 ? ' black' : ''}`}
                  title={`Depth ${line.depth}${line.bound ? ` (${line.bound} bound)` : ''}`}
                >
                  {formatScore(score)}
                </span>
                <span className="engine-pv">{lineToSan(fen!, line.pv, LINE_MOVES, moveNotation)}</span>
              </div>
            );
          })}
      </div>
    </div>
  );
}
