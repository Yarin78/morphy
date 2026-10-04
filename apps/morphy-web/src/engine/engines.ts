import { WorkerTransport, type UciTransport } from './transport';

// The engines the app can use, each started when it's first needed. They all speak UCI; where
// they run is up to their transport. Remote engines, reached over the network, would be added
// here as more profiles with a transport of their own.

export interface EngineProfile {
  id: string;
  /** The engine's name, as shown */
  name: string;
  /** Which build of it, when there are several */
  variant: string;
  /** Whether it can search on several cores (the Threads option) */
  multiThreaded: boolean;
  createTransport: () => UciTransport;
  /** Why it can't run here, if it can't */
  unavailable?: () => string | null;
}

// The multi-threaded WebAssembly builds need SharedArrayBuffer, which only a cross-origin
// isolated page has
const needsIsolation = () =>
  crossOriginIsolated
    ? null
    : 'The page isn’t cross-origin isolated, which the multi-threaded engine needs (the server must send the COOP and COEP headers).';

/** A Stockfish 19 build in a web worker, from the files served at /engines/ */
function stockfish(id: string, file: string, variant: string, multiThreaded: boolean): EngineProfile {
  return {
    id,
    name: 'Stockfish 19',
    variant,
    multiThreaded,
    createTransport: () => new WorkerTransport(`/engines/${file}.js`),
    unavailable: multiThreaded ? needsIsolation : undefined,
  };
}

/**
 * The full builds have the large network, about 100 MB to load the first time; the lite ones a
 * small one, quick to load and much weaker, still far stronger than any human.
 */
export const ENGINES = {
  stockfish: stockfish('stockfish', 'stockfish-19', 'Full, multi-threaded', true),
  stockfishSingle: stockfish('stockfishSingle', 'stockfish-19-single', 'Full, single-threaded', false),
  stockfishLite: stockfish('stockfishLite', 'stockfish-19-lite', 'Lite, multi-threaded', true),
  stockfishLiteSingle: stockfish('stockfishLiteSingle', 'stockfish-19-lite-single', 'Lite, single-threaded', false),
} satisfies Record<string, EngineProfile>;

export type EngineId = keyof typeof ENGINES;
