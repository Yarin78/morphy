// The numbers of the games from a position, as the Games pane shows them

/** A number of games, to two digits from a thousand up: 747, 1.7k, 57k, 120k, 1.2M. */
export function formatCount(n: number): string {
  if (n < 1000) return String(n);
  const rounded = Number(n.toPrecision(2));
  return rounded < 1e6 ? `${rounded / 1e3}k` : `${rounded / 1e6}M`;
}

interface Results {
  games: number;
  whiteWins: number;
  draws: number;
  blackWins: number;
}

/** The share of the points a side got in the games that were decided, 0–1. */
export function scoreOf(results: Results, white: boolean): number {
  const wins = white ? results.whiteWins : results.blackWins;
  const decided = results.whiteWins + results.draws + results.blackWins;
  return decided > 0 ? (wins + results.draws / 2) / decided : 0;
}

/** Whether White is to move in a position. */
export function whiteToMove(fen: string): boolean {
  return fen.split(' ')[1] !== 'b';
}
