import { Chess } from 'chess.js';
import type {
  GameSearchResponse,
  PositionMovePlayer,
  PositionSearchRequest,
  PositionSearchResponse,
  PositionSummary,
} from './types';

// A stand-in for the service's position search until it has one. The summary is made up, the same
// every time for a position: the earlier in a game, the more games reach it and the more moves are
// played from it. The games are the database's, all of them, as a game search finds them, whatever
// the position.

// The games of the last five years count as recent
const RECENT_YEARS = 5;

const PLAYERS: PositionMovePlayer[] = [
  { name: 'Carlsen, Magnus', rating: 2882 },
  { name: 'Caruana, Fabiano', rating: 2844 },
  { name: 'Kasparov, Garry', rating: 2851 },
  { name: 'Nakamura, Hikaru', rating: 2816 },
  { name: 'Ding, Liren', rating: 2816 },
  { name: 'Firouzja, Alireza', rating: 2804 },
  { name: 'Anand, Viswanathan', rating: 2817 },
  { name: 'Kramnik, Vladimir', rating: 2817 },
  { name: 'Nepomniachtchi, Ian', rating: 2795 },
  { name: 'Aronian, Levon', rating: 2830 },
  { name: 'Gukesh, D', rating: 2794 },
  { name: 'So, Wesley', rating: 2822 },
  { name: 'Karpov, Anatoly', rating: 2780 },
  { name: 'Topalov, Veselin', rating: 2816 },
  { name: 'Erigaisi, Arjun', rating: 2801 },
  { name: 'Giri, Anish', rating: 2798 },
];

/** A generator of numbers in [0, 1), seeded by a string. */
function seededRandom(seed: string): () => number {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) h = Math.imul(h ^ seed.charCodeAt(i), 16777619);
  return () => {
    h = Math.imul(h ^ (h >>> 15), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  };
}

export async function mockSearchPosition(
  databaseId: string,
  request: PositionSearchRequest,
  searchGames: (databaseId: string, request: object) => Promise<GameSearchResponse>
): Promise<PositionSearchResponse> {
  const { fen, ...page } = request;
  const games = await searchGames(databaseId, page);
  return { summary: (page.offset ?? 0) === 0 ? mockSummary(databaseId, fen) : null, games };
}

function mockSummary(databaseId: string, fen: string): PositionSummary {
  const random = seededRandom(`${databaseId} ${fen}`);

  const thisYear = new Date().getFullYear();
  const recentSince = thisYear - RECENT_YEARS + 1;
  const chess = new Chess(fen);
  const [, side, , , , fullMove] = fen.split(' ');
  const ply = (Number(fullMove) - 1) * 2 + (side === 'b' ? 1 : 0);
  const games = Math.floor(80000 * Math.pow(0.55, ply) * (0.5 + random()));
  if (games === 0) return { fen, games, whiteWins: 0, draws: 0, blackWins: 0, recentSince, moves: [] };

  // A few of the legal moves, in a random order, each played less than the one before
  const legal = chess.moves().sort(() => random() - 0.5);
  const count = Math.min(legal.length, Math.max(1, Math.round(Math.min(12, Math.log2(games)) * (0.5 + random() / 2))));
  const weights = legal.slice(0, count).map((_, i) => Math.pow(0.45 + random() * 0.2, i));
  const total = weights.reduce((a, b) => a + b, 0);
  // Some games end here
  const continued = Math.round(games * (0.97 + random() * 0.03));
  let left = continued;
  const moves = legal.slice(0, count).flatMap((san, i) => {
    const played = i === count - 1 ? left : Math.min(left, Math.round((continued * weights[i]) / total));
    left -= played;
    if (played === 0) return [];
    const whiteWins = Math.round(played * (0.25 + random() * 0.25));
    const draws = Math.min(played - whiteWins, Math.round(played * (0.2 + random() * 0.25)));
    // A fifth of the games are recent, give or take: some moves are in fashion, some out of it
    const recentGames = Math.min(played, Math.round(played * 0.2 * Math.pow(4, random() * 2 - 1)));
    const lastPlayed =
      recentGames > 0
        ? thisYear - Math.floor(random() * Math.min(RECENT_YEARS, 1 + 20 / played))
        : recentSince - 1 - Math.floor(random() * 30);
    const strongest = [...PLAYERS]
      .sort(() => random() - 0.5)
      .slice(0, Math.min(3, Math.ceil(played / 20)))
      .map((p) => ({ ...p, rating: (p.rating ?? 2700) - Math.floor(random() * 60) }))
      .sort((a, b) => b.rating - a.rating);
    return [
      {
        san,
        games: played,
        whiteWins,
        draws,
        blackWins: played - whiteWins - draws,
        recentGames,
        lastPlayed,
        // The more often played, the stronger the players, more or less
        averageRating: Math.round(2150 + 120 * Math.log10(played) + random() * 150),
        topPlayers: strongest,
      },
    ];
  });
  moves.sort((a, b) => b.games - a.games);
  // The games that ended here were drawn, as most are
  const whiteWins = moves.reduce((sum, m) => sum + m.whiteWins, 0);
  const blackWins = moves.reduce((sum, m) => sum + m.blackWins, 0);
  return { fen, games, whiteWins, draws: games - whiteWins - blackWins, blackWins, recentSince, moves };
}
