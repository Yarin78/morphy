// eslint-disable-next-line @typescript-eslint/triple-slash-reference
/// <reference path="./types/react-chessground.d.ts" />
// Ambient module declarations (like the one above) are only picked up by a
// tsconfig whose own `include` covers the file - they don't travel with a
// normal import the way a type export does. This package has no build step
// (consumers compile this source directly under their own tsconfig), so the
// triple-slash reference is what makes 'react-chessground' resolve for them.

export { GameView } from './components/GameView';
export type { GameViewProps } from './components/GameView';
export type { ChessGame, GameHeader as GameHeaderData } from './types/chess';
export { LINE_EVALUATION_TAG } from './utils/gameInfo';
export { TOURNAMENT_TAGS, tournamentFromTags, tournamentToTags } from './utils/tournament';
export type { DateParts, TournamentInfo, TournamentService } from './utils/tournament';
