import { type ReactNode, useEffect, useSyncExternalStore } from 'react';
import { MOVE_NOTATIONS, NOTATION_BAR_GROUPS, type LastMoveStyle, type MoveNotation } from 'game-view';
import {
  CORES,
  getAnalysis,
  HASH_SIZES_MB,
  MAX_LINES,
  MIN_LINES,
  setEngine,
  setHashMb,
  setMultiPv,
  setThreads,
  subscribeAnalysis,
} from '../engine/analysis';
import { ENGINES, type EngineId } from '../engine/engines';
import {
  BOARD_THEMES,
  type BoardTheme,
  NOTATION_FONT_SIZES,
  PIECE_SETS,
  type PieceSet,
  resetSettings,
  setBoardSettings,
  setNotationSettings,
  useSettings,
} from './settings';
import { closeSettings, openSettings, type SettingsTab, useSettingsTab } from './settingsDialogStore';

const TABS: { id: SettingsTab; label: string }[] = [
  { id: 'general', label: 'General' },
  { id: 'board', label: 'Board' },
  { id: 'notation', label: 'Notation' },
  { id: 'engine', label: 'Engine' },
];

const LAST_MOVE_STYLES: { id: LastMoveStyle; label: string }[] = [
  { id: 'arrow', label: 'Arrow' },
  { id: 'squares', label: 'Highlighted squares' },
  { id: 'none', label: 'Not shown' },
];

function formatMb(mb: number): string {
  return mb >= 1024 ? `${mb / 1024} GB` : `${mb} MB`;
}

/** A setting: what it is on the left, how it's set on the right, and a note below if needed. */
function Row({ label, note, children }: { label: string; note?: string; children: ReactNode }) {
  return (
    <div className="settings-row">
      <div className="settings-label">{label}</div>
      <div className="settings-control">
        {children}
        {note && <div className="settings-note">{note}</div>}
      </div>
    </div>
  );
}

function Check({ checked, onChange, label }: { checked: boolean; onChange: (on: boolean) => void; label: string }) {
  return (
    <label className="settings-check">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      {label}
    </label>
  );
}

function Select<T extends string | number>({
  value,
  options,
  onChange,
  disabled,
}: {
  value: T;
  options: readonly { id: T; label: string }[];
  onChange: (value: T) => void;
  disabled?: boolean;
}) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(options.find((o) => String(o.id) === e.target.value)!.id)}
    >
      {options.map((o) => (
        <option key={o.id} value={o.id}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

// Not there yet: they're shown so it's clear where they'll be
function GeneralTab() {
  return (
    <>
      <Row label="Color theme" note="Only the light theme for now.">
        <Select value="light" options={[{ id: 'light', label: 'Light' }]} onChange={() => {}} disabled />
      </Row>
      <Row label="Font" note="Only the default font for now.">
        <Select value="default" options={[{ id: 'default', label: 'Default' }]} onChange={() => {}} disabled />
      </Row>
      <Row label="Font size" note="The notation's text size is under Notation.">
        <Select value="normal" options={[{ id: 'normal', label: 'Normal' }]} onChange={() => {}} disabled />
      </Row>
      <Row label="Language" note="Only English for now.">
        <Select value="en" options={[{ id: 'en', label: 'English' }]} onChange={() => {}} disabled />
      </Row>
    </>
  );
}

function BoardTab() {
  const { board } = useSettings();
  return (
    <>
      <Row label="Pieces">
        <Select<PieceSet> value={board.pieceSet} options={PIECE_SETS} onChange={(pieceSet) => setBoardSettings({ pieceSet })} />
      </Row>
      <Row label="Board">
        <div className="settings-swatches" role="radiogroup" aria-label="Board">
          {BOARD_THEMES.map((t) => (
            <button
              key={t.id}
              role="radio"
              aria-checked={board.boardTheme === t.id}
              className={`settings-swatch${board.boardTheme === t.id ? ' selected' : ''}`}
              title={t.label}
              onClick={() => setBoardSettings({ boardTheme: t.id as BoardTheme })}
            >
              <span style={{ background: t.light }} />
              <span style={{ background: t.dark }} />
              <span style={{ background: t.dark }} />
              <span style={{ background: t.light }} />
            </button>
          ))}
        </div>
      </Row>
      <Row label="Last move">
        <Select<LastMoveStyle>
          value={board.lastMove}
          options={LAST_MOVE_STYLES}
          onChange={(lastMove) => setBoardSettings({ lastMove })}
        />
      </Row>
      <Row
        label="Moving"
        note="Press the square a piece is to go to: the piece the engine finds best is circled, and moves when you let go. To move another, point at it before letting go; let go off the board to make no move."
      >
        <Check
          checked={board.destinationMoves}
          onChange={(destinationMoves) => setBoardSettings({ destinationMoves })}
          label="Move by pressing the square to go to"
        />
      </Row>
      <Row label="Show">
        <Check
          checked={board.coordinates}
          onChange={(coordinates) => setBoardSettings({ coordinates })}
          label="Coordinates"
        />
        <Check
          checked={board.navigation}
          onChange={(navigation) => setBoardSettings({ navigation })}
          label="Buttons to move through the game, below the board"
        />
        <Check checked={board.animation} onChange={(animation) => setBoardSettings({ animation })} label="Animate moves" />
      </Row>
    </>
  );
}

function NotationTab() {
  const { notation } = useSettings();
  const toggleGroup = (id: (typeof notation.barGroups)[number], on: boolean) =>
    setNotationSettings({
      // In the bar's order
      barGroups: NOTATION_BAR_GROUPS.map((g) => g.id).filter((g) => (g === id ? on : notation.barGroups.includes(g))),
    });
  return (
    <>
      <Row label="Pieces in moves" note="Also in the engine's lines and wherever else moves are shown.">
        <Select<MoveNotation>
          value={notation.moveNotation}
          options={MOVE_NOTATIONS}
          onChange={(moveNotation) => setNotationSettings({ moveNotation })}
        />
      </Row>
      <Row label="Text size">
        <Select<number>
          value={notation.fontSize}
          options={NOTATION_FONT_SIZES.map((px) => ({ id: px, label: `${px} px` }))}
          onChange={(fontSize) => setNotationSettings({ fontSize })}
        />
      </Row>
      <Row label="Annotation bar" note="The buttons below the notation, when editing.">
        {NOTATION_BAR_GROUPS.map((g) => (
          <Check
            key={g.id}
            checked={notation.barGroups.includes(g.id)}
            onChange={(on) => toggleGroup(g.id, on)}
            label={g.label}
          />
        ))}
      </Row>
      <Row label="Keys">
        <Check
          checked={notation.annotationKeys}
          onChange={(annotationKeys) => setNotationSettings({ annotationKeys })}
          label="Annotate with the keys: ! ? = + − for symbols, A and B for comments, D for a diagram"
        />
      </Row>
    </>
  );
}

function EngineTab() {
  const analysis = useSyncExternalStore(subscribeAnalysis, getAnalysis);
  const profile = ENGINES[analysis.engineId];
  const sizes = HASH_SIZES_MB.filter((mb) => analysis.maxHashMb === null || mb <= analysis.maxHashMb);
  return (
    <>
      <Row label="Engine" note="The full engine is strongest, but about 100 MB to load the first time.">
        <Select<EngineId>
          value={analysis.engineId}
          options={(Object.keys(ENGINES) as EngineId[]).map((id) => ({ id, label: `${ENGINES[id].name}, ${ENGINES[id].variant}` }))}
          onChange={setEngine}
        />
      </Row>
      <Row label="CPUs" note={profile.multiThreaded ? undefined : 'This engine searches on one core.'}>
        <Select<number>
          value={profile.multiThreaded ? analysis.threads : 1}
          options={Array.from({ length: CORES }, (_, i) => ({ id: i + 1, label: `${i + 1} of ${CORES}` }))}
          onChange={setThreads}
          disabled={!profile.multiThreaded}
        />
      </Row>
      <Row label="Memory">
        <Select<number>
          value={analysis.hashMb}
          options={sizes.map((mb) => ({ id: mb, label: formatMb(mb) }))}
          onChange={setHashMb}
        />
      </Row>
      <Row label="Lines">
        <Select<number>
          value={analysis.multiPv}
          options={Array.from({ length: MAX_LINES - MIN_LINES + 1 }, (_, i) => ({
            id: MIN_LINES + i,
            label: String(MIN_LINES + i),
          }))}
          onChange={setMultiPv}
        />
      </Row>
      <p className="settings-footnote">The Engine pane changes these too.</p>
    </>
  );
}

const RESETTABLE: Partial<Record<SettingsTab, () => void>> = {
  board: () => resetSettings('board'),
  notation: () => resetSettings('notation'),
};

/** The settings of the app, by tab; they apply at once, everywhere. */
export function SettingsDialog() {
  const tab = useSettingsTab();

  useEffect(() => {
    if (!tab) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && closeSettings();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [tab]);

  if (!tab) return null;
  const reset = RESETTABLE[tab];
  return (
    <div className="dialog-backdrop" onClick={closeSettings}>
      <div className="dialog settings-dialog" role="dialog" aria-label="Settings" onClick={(e) => e.stopPropagation()}>
        <nav className="settings-tabs" role="tablist" aria-orientation="vertical">
          <h2>Settings</h2>
          {TABS.map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              className={`settings-tab${tab === t.id ? ' selected' : ''}`}
              onClick={() => openSettings(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
        <div className="settings-page" role="tabpanel">
          <div className="settings-rows">
            {tab === 'general' && <GeneralTab />}
            {tab === 'board' && <BoardTab />}
            {tab === 'notation' && <NotationTab />}
            {tab === 'engine' && <EngineTab />}
          </div>
          <div className="dialog-buttons">
            {reset && (
              <button className="dialog-secondary dialog-left" onClick={reset}>
                Restore Defaults
              </button>
            )}
            <button onClick={closeSettings}>Close</button>
          </div>
        </div>
      </div>
    </div>
  );
}
