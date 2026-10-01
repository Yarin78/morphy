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
export { FIDE_ID_TAGS, PLAYER_ID_TAGS, splitPlayerName } from './utils/player';
export type { PlayerInfo, PlayerService } from './utils/player';
export type { GameInfoServices } from './utils/gameInfo';
export { ANNOTATOR_ID_TAG } from './utils/gameInfo';
export { SOURCE_TAGS, sourceFromTags, sourceToTags } from './utils/source';
export type { SourceInfo, SourceService } from './utils/source';
export { teamFromTags, teamTags, teamToTags } from './utils/team';
export {
  GAME_TAG_LANGUAGES,
  GAME_TAG_TAGS,
  gameTagFromTags,
  gameTagLanguages,
  gameTagTitle,
  gameTagToTags,
} from './utils/gameTag';
export type { GameTagInfo, GameTagLanguage, GameTagService } from './utils/gameTag';
export type { TeamColor, TeamInfo, TeamService } from './utils/team';
export { decodeEloType, ELO_TYPE_TAGS, encodeEloType } from './utils/eloType';
export type { EloTypeInfo } from './utils/eloType';
export {
  formatTimeControl,
  parseTimeControl,
  TIME_CONTROL_TAG,
  toPgnTimeControl,
} from './utils/timeControl';
export type { TimeControlPeriod } from './utils/timeControl';
