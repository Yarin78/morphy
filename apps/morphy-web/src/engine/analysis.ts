import { ENGINES, type EngineId } from './engines';
import { UciEngine, type EngineInfo, type UciScore } from './uci';

// The analysis of the position on the board shown: one engine for the whole app, following the
// active board's position, so boards not shown don't take the computer's time. Locked, it keeps
// analysing its position whatever the board shows. Its settings, and whether it's on, are
// remembered.

export interface AnalysisLine {
  /** 1 for the best line */
  multipv: number;
  depth: number;
  score: UciScore;
  bound?: 'lower' | 'upper';
  /** UCI moves */
  pv: string[];
}

export interface AnalysisSettings {
  on: boolean;
  engineId: EngineId;
  /** How many lines are searched */
  multiPv: number;
  /** The cores searched on, for an engine that can use several */
  threads: number;
  /** The engine's hash table, in MB */
  hashMb: number;
  /** Whether the analysis keeps its position whatever the board shows */
  locked: boolean;
}

export interface AnalysisState extends AnalysisSettings {
  /** off: not analysing; starting: the engine is loading; running: analysing; error: it failed */
  status: 'off' | 'starting' | 'running' | 'error';
  error: string | null;
  /** The position analysed, and the lines found for it, best first */
  fen: string | null;
  /** The position on the board, which isn't the one analysed while locked on another */
  boardFen: string | null;
  lines: AnalysisLine[];
  depth: number;
  nodes: number;
  nps: number;
  /** The most hash the engine takes, once it's started */
  maxHashMb: number | null;
}

export const MIN_LINES = 1;
export const MAX_LINES = 8;
export const CORES = Math.max(1, navigator.hardwareConcurrency || 1);
export const HASH_SIZES_MB = [16, 32, 64, 128, 256, 512, 1024];

const SETTINGS_KEY = 'morphy-analysis';
// The engine says far more than is worth drawing; what it said is handed on this often
const PUBLISH_MS = 100;

// The lite engine loads at once; the full one is a setting away. Half the cores leave the
// computer room for the rest.
const DEFAULT_SETTINGS: AnalysisSettings = {
  on: false,
  engineId: 'stockfishLite',
  multiPv: 3,
  threads: Math.max(1, Math.floor(CORES / 2)),
  hashMb: 128,
  locked: false,
};

function loadSettings(): AnalysisSettings {
  try {
    const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? 'null') as Partial<AnalysisSettings> | null;
    const settings = { ...DEFAULT_SETTINGS, ...saved };
    if (!(settings.engineId in ENGINES)) settings.engineId = DEFAULT_SETTINGS.engineId;
    // Not locked on a position that's gone
    return { ...settings, locked: false };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

let state: AnalysisState = {
  ...loadSettings(),
  status: 'off',
  error: null,
  fen: null,
  boardFen: null,
  lines: [],
  depth: 0,
  nodes: 0,
  nps: 0,
  maxHashMb: null,
};
const listeners = new Set<() => void>();

function update(changes: Partial<AnalysisState>) {
  state = { ...state, ...changes };
  listeners.forEach((l) => l());
  if (
    'on' in changes ||
    'engineId' in changes ||
    'multiPv' in changes ||
    'threads' in changes ||
    'hashMb' in changes
  ) {
    try {
      const { on, engineId, multiPv, threads, hashMb, locked } = state;
      localStorage.setItem(SETTINGS_KEY, JSON.stringify({ on, engineId, multiPv, threads, hashMb, locked }));
    } catch {
      // ignore, it's a convenience
    }
  }
}

export function subscribeAnalysis(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getAnalysis(): AnalysisState {
  return state;
}

// ── The engine ───────────────────────────────────────────────────────────

let engine: { id: EngineId; uci: Promise<UciEngine> } | null = null;
// Options to set before the next search, as UCI takes them only between searches
let pendingOptions: Record<string, number> = {};

function engineOptions(): Record<string, number> {
  const options: Record<string, number> = { MultiPV: state.multiPv, Hash: state.hashMb };
  if (ENGINES[state.engineId].multiThreaded) options.Threads = state.threads;
  return options;
}

function loadEngine(): Promise<UciEngine> {
  if (engine?.id === state.engineId) return engine.uci;
  const id = state.engineId;
  const profile = ENGINES[id];
  const uci = (async () => {
    const reason = profile.unavailable?.();
    if (reason) throw new Error(reason);
    const uci = new UciEngine(profile.createTransport());
    uci.onError((message) => {
      if (engine?.id === id) engine = null;
      update({ status: 'error', error: message });
    });
    const identity = await uci.init();
    const hash = identity.options.find((o) => o.name === 'Hash');
    update({ maxHashMb: hash?.max ?? null });
    // All the settings go with its first search
    pendingOptions = engineOptions();
    await uci.isReady();
    return uci;
  })();
  engine = { id, uci };
  return uci;
}

/** Ends the engine, as when another is picked. */
function unloadEngine() {
  const old = engine;
  engine = null;
  void old?.uci.then((uci) => uci.quit(), () => {});
}

// ── The search ───────────────────────────────────────────────────────────

// The lines of the search going on, by multipv, until they're handed on
let found = new Map<number, AnalysisLine>();
let stats = { depth: 0, nodes: 0, nps: 0 };
let publishTimer: ReturnType<typeof setTimeout> | null = null;

function publish() {
  publishTimer = null;
  update({ lines: [...found.values()].sort((a, b) => a.multipv - b.multipv), ...stats });
}

function onInfo(info: EngineInfo) {
  if (info.depth) stats.depth = Math.max(stats.depth, info.depth);
  if (info.nodes) stats.nodes = info.nodes;
  if (info.nps) stats.nps = info.nps;
  // A line is only worth showing with a score and moves; a bound is replaced by the exact score
  if (info.score && info.pv?.length && info.depth && info.multipv <= state.multiPv) {
    const known = found.get(info.multipv);
    if (!(info.bound && known && known.depth === info.depth && !known.bound)) {
      found.set(info.multipv, {
        multipv: info.multipv,
        depth: info.depth,
        score: info.score,
        bound: info.bound,
        pv: info.pv,
      });
    }
  }
  publishTimer ??= setTimeout(publish, PUBLISH_MS);
}

/** Searches the position analysed afresh, with the options changed since the last search. */
function search() {
  const fen = state.fen;
  if (!fen || state.status !== 'running') return;
  found = new Map();
  stats = { depth: 0, nodes: 0, nps: 0 };
  update({ lines: [], depth: 0, nodes: 0, nps: 0 });
  void loadEngine().then((uci) => {
    const options = pendingOptions;
    pendingOptions = {};
    uci.analyse(fen, onInfo, options);
  });
}

function start() {
  update({ status: 'starting', error: null });
  const id = state.engineId;
  loadEngine().then(
    () => {
      if (!state.on || state.engineId !== id) return;
      // The board may have moved on while the engine was off
      const follow = !state.locked && state.boardFen && state.boardFen !== state.fen;
      update({ status: 'running', ...(follow ? { fen: state.boardFen } : {}) });
      search();
    },
    (err: unknown) => {
      if (engine?.id === id) engine = null;
      update({ status: 'error', error: err instanceof Error ? err.message : String(err) });
    }
  );
}

// ── What the user does ───────────────────────────────────────────────────

/** Turns the analysis on or off; off, the engine stays loaded, to start again quickly. */
export function setAnalysisOn(on: boolean) {
  update({ on });
  if (on) {
    if (state.status === 'off' || state.status === 'error') start();
  } else {
    if (state.status === 'running') void engine?.uci.then((uci) => uci.stop());
    update({ status: 'off' });
  }
}

/** The position on the board shown, which is analysed unless the analysis is locked or off. */
export function setAnalysisPosition(fen: string) {
  // Off, the lines found stay, with the position they were found for
  if (state.locked || !state.on) {
    if (fen !== state.boardFen) update({ boardFen: fen });
    // Nothing analysed yet: the first position is the one to keep
    if (state.fen) return;
  }
  if (fen === state.fen && fen === state.boardFen) return;
  update({ fen, boardFen: fen });
  search();
}

/** Keeps the analysis on its position, or lets it follow the board again. */
export function setLocked(locked: boolean) {
  update({ locked });
  // Unlocked away from the position analysed, the board's is analysed; at it, the search goes on
  if (!locked && state.boardFen && state.boardFen !== state.fen) {
    update({ fen: state.boardFen });
    search();
  }
}

export function setMultiPv(multiPv: number) {
  multiPv = Math.min(MAX_LINES, Math.max(MIN_LINES, multiPv));
  if (multiPv === state.multiPv) return;
  update({ multiPv });
  pendingOptions.MultiPV = multiPv;
  search();
}

export function setThreads(threads: number) {
  if (threads === state.threads) return;
  update({ threads });
  if (ENGINES[state.engineId].multiThreaded) {
    pendingOptions.Threads = threads;
    search();
  }
}

/** The engine's memory; setting it clears what the engine has learned, so only when it changes. */
export function setHashMb(hashMb: number) {
  if (hashMb === state.hashMb) return;
  update({ hashMb });
  pendingOptions.Hash = hashMb;
  search();
}

/** Picks another engine: it's loaded in place of the one there, if analysing. */
export function setEngine(engineId: EngineId) {
  if (engineId === state.engineId) return;
  unloadEngine();
  update({ engineId, maxHashMb: null, lines: [], depth: 0, nodes: 0, nps: 0 });
  if (state.on) start();
  else update({ status: 'off' });
}

/** Starts the analysis again if it was on when the app was left. */
export function resumeAnalysis() {
  if (state.on && state.status === 'off') start();
}
