/**
 * Medals, the reasons a game or a move is of interest, shown as ChessBase does: a small rectangle
 * after the move, or before the first move for the game as a whole, split into a part in the color
 * of each medal.
 */

// In the order ChessBase shows them, which has sacrifice before defence, with the colors of
// ChessBase's gradient, from the top down
const MEDALS: readonly { medal: string; name: string; top: string; bottom: string }[] = [
  { medal: 'BEST_GAME', name: 'Best game', top: '#FDCA31', bottom: '#CF9C03' },
  { medal: 'DECIDED_TOURNAMENT', name: 'Decided tournament', top: '#FD31FD', bottom: '#CF03CF' },
  { medal: 'MODEL_GAME', name: 'Model game', top: '#323298', bottom: '#030369' },
  { medal: 'NOVELTY', name: 'Novelty', top: '#3131FD', bottom: '#0303CF' },
  { medal: 'PAWN_STRUCTURE', name: 'Pawn structure', top: '#319797', bottom: '#036969' },
  { medal: 'STRATEGY', name: 'Strategy', top: '#9B6831', bottom: '#6D3A03' },
  { medal: 'TACTICS', name: 'Tactics', top: '#973131', bottom: '#690303' },
  { medal: 'WITH_ATTACK', name: 'With attack', top: '#FEFE32', bottom: '#CFCF03' },
  { medal: 'SACRIFICE', name: 'Sacrifice', top: '#FE3232', bottom: '#CE0202' },
  { medal: 'DEFENSE', name: 'Defence', top: '#FDFDFD', bottom: '#CFCFCF' },
  { medal: 'MATERIAL', name: 'Material', top: '#963096', bottom: '#6A046A' },
  { medal: 'PIECE_PLAY', name: 'Piece play', top: '#31FD31', bottom: '#03CF03' },
  { medal: 'ENDGAME', name: 'Endgame', top: '#319731', bottom: '#046A04' },
  { medal: 'TACTICAL_BLUNDER', name: 'Tactical blunder', top: '#323232', bottom: '#030303' },
  { medal: 'STRATEGICAL_BLUNDER', name: 'Strategical blunder', top: '#979797', bottom: '#696969' },
  { medal: 'USER', name: 'User', top: '#30FCFC', bottom: '#03CFCF' },
];

/** Every medal, in the order ChessBase shows them. */
export const MEDAL_NAMES: readonly string[] = MEDALS.map((m) => m.medal);

/** The colors of a medal's gradient, from the top down; grey if the medal isn't known. */
export function medalColors(medal: string): [string, string] {
  const info = MEDALS.find((m) => m.medal === medal);
  return info ? [info.top, info.bottom] : ['#999999', '#666666'];
}

/** The name of a medal, or the medal itself if it isn't known. */
export function medalName(medal: string): string {
  return MEDALS.find((m) => m.medal === medal)?.name ?? medal;
}

/** The medals as HTML: a rectangle split into a part for each, in the order ChessBase shows them. */
export function medalsHtml(medals: readonly string[]): string {
  if (medals.length === 0) return '';
  const rank = (medal: string) => {
    const index = MEDALS.findIndex((m) => m.medal === medal);
    return index < 0 ? MEDALS.length : index;
  };
  const sorted = [...medals].sort((a, b) => rank(a) - rank(b));
  const parts = sorted
    .map((medal) => {
      const [top, bottom] = medalColors(medal);
      return `<span class="cbmedal-part" style="background: linear-gradient(to bottom, ${top}, ${bottom})"></span>`;
    })
    .join('');
  const title = `Medal${sorted.length > 1 ? 's' : ''}: ${sorted.map(medalName).join(', ')}`;
  return `<span class="cbmedal" title="${title}">${parts}</span>`;
}
