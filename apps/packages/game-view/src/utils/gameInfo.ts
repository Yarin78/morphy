import type { GameTree } from '../model/GameTree';
import { dateTextError, formatDateTag, formatDateText, parseDateTag, parseDateText } from './date';
import { decodeEloType, ELO_TYPE_TAGS, encodeEloType } from './eloType';
import type { EloTypeInfo } from './eloType';
import { FIDE_ID_TAGS, normalizePlayerName, PLAYER_ID_TAGS } from './player';
import type { PlayerInfo, PlayerService } from './player';
import { sourceFromTags, sourceToTags } from './source';
import type { SourceInfo, SourceService } from './source';
import { teamFromTags, teamToTags } from './team';
import type { TeamInfo, TeamService } from './team';
import { gameTagFromTags, gameTagToTags } from './gameTag';
import {
  formatTimeControl,
  parseTimeControl,
  TIME_CONTROL_TAG,
  timeControlError,
  toPgnTimeControl,
} from './timeControl';
import type { GameTagInfo, GameTagService } from './gameTag';
import { tournamentFromTags, tournamentToTags } from './tournament';
import type { TournamentInfo, TournamentService } from './tournament';

/**
 * How the Edit Game Info dialog finds existing entities; without one, that kind of entity is just
 * a name.
 */
export interface GameInfoServices {
  players?: PlayerService;
  /** The annotators, who are players too. */
  annotators?: PlayerService;
  tournaments?: TournamentService;
  sources?: SourceService;
  teams?: TeamService;
  gameTags?: GameTagService;
}

/** Not a standard PGN tag: the id of the existing annotator. */
export const ANNOTATOR_ID_TAG = 'AnnotatorId';

/**
 * The game header information edited in the Edit Game Info dialog, as form values. Everything is
 * a string, as typed; an empty string means "not set". It's read from and written back to the
 * game's PGN tags, so whoever saves the game picks the changes up from there.
 */
export interface GameInfo {
  white: PlayerInfo;
  /** The player's FIDE id, as typed: usually 8 digits, or empty. */
  whiteFideId: string;
  whiteElo: string;
  /** Null when not known, which is saved as FIDE. */
  whiteEloType: EloTypeInfo | null;
  black: PlayerInfo;
  blackFideId: string;
  blackElo: string;
  blackEloType: EloTypeInfo | null;
  /** A PGN Result tag value: one of the RESULTS values. */
  result: string;
  /** For a line (result '*'), a NAG name like 'WHITE_SLIGHT_ADVANTAGE'; see LINE_EVALUATIONS. */
  lineEvaluation: string;
  /** As typed: yyyy-mm-dd, or yyyy-mm or yyyy when not all of it is known. */
  date: string;
  /** The time control as typed; see formatTimeControl. */
  timeControl: string;
  /** Null when the game has no tournament. */
  tournament: TournamentInfo | null;
  round: string;
  subRound: string;
  board: string;
  /** An ECO code like 'E25', or with a sub-code, 'E25/03'. */
  eco: string;
  /** The opening's name, the PGN Opening tag; ChessBase has no field for it. */
  opening: string;
  /** Its name as typed, not split into last and first name like a player's. */
  annotator: PlayerInfo;
  /** Null when the game has no source. */
  source: SourceInfo | null;
  /** Null when the player has no team, as usual. */
  whiteTeam: TeamInfo | null;
  blackTeam: TeamInfo | null;
  /** Null when the game has no tag, as usual. */
  gameTag: GameTagInfo | null;
}

/**
 * Not a standard PGN tag: holds the evaluation of a line (a game with result '*') as a NAG name,
 * the way morphy-service's GameDto.lineEvaluation does.
 */
export const LINE_EVALUATION_TAG = 'LineEvaluation';

export const LINE_RESULT = '*';

/** A game's result: a played game's, a line's (which has an evaluation instead), or another kind. */
export type ResultGroup = 'game' | 'line' | 'other';

export const RESULTS: { value: string; label: string; group: ResultGroup }[] = [
  { value: '1-0', label: '1-0', group: 'game' },
  { value: '1/2-1/2', label: '½-½', group: 'game' },
  { value: '0-1', label: '0-1', group: 'game' },
  { value: LINE_RESULT, label: 'Line', group: 'line' },
  // Forfeits, and a game both players lost
  { value: '+:-', label: '+ -', group: 'other' },
  { value: '=:=', label: '= =', group: 'other' },
  { value: '-:+', label: '- +', group: 'other' },
  { value: '0-0', label: '0-0', group: 'other' },
];

export const LINE_EVALUATIONS: { value: string; symbol: string }[] = [
  { value: 'EQUAL', symbol: '=' },
  { value: 'UNCLEAR', symbol: '∞' },
  { value: 'WHITE_SLIGHT_ADVANTAGE', symbol: '⩲' },
  { value: 'BLACK_SLIGHT_ADVANTAGE', symbol: '⩱' },
  { value: 'WHITE_MODERATE_ADVANTAGE', symbol: '±' },
  { value: 'BLACK_MODERATE_ADVANTAGE', symbol: '∓' },
  { value: 'WHITE_DECISIVE_ADVANTAGE', symbol: '+−' },
  { value: 'BLACK_DECISIVE_ADVANTAGE', symbol: '−+' },
];

/** The symbol for a line evaluation, or its NAG name if it isn't one of LINE_EVALUATIONS. */
export function lineEvaluationSymbol(nag: string): string {
  return LINE_EVALUATIONS.find((e) => e.value === nag)?.symbol ?? nag;
}

/** Treats the PGN placeholders for an unknown value ('?', '????') as empty. */
function tagValue(game: GameTree, name: string): string {
  const value = game.getTag(name).trim();
  return /^\?*$/.test(value) ? '' : value;
}

function readPlayer(game: GameTree, nameTag: string, idTag: string): PlayerInfo {
  const id = tagValue(game, idTag);
  return { id: /^\d+$/.test(id) ? parseInt(id, 10) : null, name: tagValue(game, nameTag) };
}

function writePlayer(game: GameTree, nameTag: string, idTag: string, player: PlayerInfo) {
  game.setTag(nameTag, normalizePlayerName(player.name));
  game.setTag(idTag, player.id != null ? String(player.id) : '');
}

/**
 * A number in a tag, e.g. a date part like '1886' or '??', as a form value: '' when unknown,
 * without leading zeros.
 */
function numberPart(part: string | undefined): string {
  return part && /^\d+$/.test(part) && parseInt(part, 10) > 0 ? String(parseInt(part, 10)) : '';
}

export function readGameInfo(game: GameTree): GameInfo {
  const [round, subRound] = tagValue(game, 'Round').split('.');
  const result = tagValue(game, 'Result') || LINE_RESULT;
  return {
    white: readPlayer(game, 'White', PLAYER_ID_TAGS.white),
    whiteFideId: tagValue(game, FIDE_ID_TAGS.white),
    whiteElo: tagValue(game, 'WhiteElo'),
    whiteEloType: decodeEloType(tagValue(game, ELO_TYPE_TAGS.white)),
    black: readPlayer(game, 'Black', PLAYER_ID_TAGS.black),
    blackFideId: tagValue(game, FIDE_ID_TAGS.black),
    blackElo: tagValue(game, 'BlackElo'),
    blackEloType: decodeEloType(tagValue(game, ELO_TYPE_TAGS.black)),
    result: RESULTS.some((r) => r.value === result) ? result : LINE_RESULT,
    lineEvaluation: tagValue(game, LINE_EVALUATION_TAG),
    date: formatDateText(parseDateTag(tagValue(game, 'Date'))),
    // A tag that can't be read is shown as it is
    timeControl: (() => {
      const tag = tagValue(game, TIME_CONTROL_TAG);
      const periods = parseTimeControl(tag);
      return periods === null ? tag : formatTimeControl(periods);
    })(),
    tournament: tournamentFromTags((name) => game.getTag(name)),
    round: numberPart(round),
    subRound: numberPart(subRound),
    board: numberPart(tagValue(game, 'Board')),
    eco: tagValue(game, 'ECO'),
    opening: tagValue(game, 'Opening'),
    annotator: readPlayer(game, 'Annotator', ANNOTATOR_ID_TAG),
    source: sourceFromTags((name) => game.getTag(name)),
    whiteTeam: teamFromTags('white', (name) => game.getTag(name)),
    blackTeam: teamFromTags('black', (name) => game.getTag(name)),
    gameTag: gameTagFromTags((name) => game.getTag(name)),
  };
}

/** The GameInfo fields that are typed in as text. */
export type GameInfoTextField = { [K in keyof GameInfo]: GameInfo[K] extends string ? K : never }[keyof GameInfo];

/** A problem with a form value, keyed by the GameInfo field it's about. */
export type GameInfoErrors = Partial<Record<GameInfoTextField, string>>;

function checkNumber(
  errors: GameInfoErrors,
  info: GameInfo,
  field: GameInfoTextField,
  min: number,
  max: number
) {
  const value = info[field].trim();
  if (value && !(/^\d+$/.test(value) && +value >= min && +value <= max)) {
    errors[field] = `Must be ${min}–${max}`;
  }
}

export function validateGameInfo(info: GameInfo): GameInfoErrors {
  const errors: GameInfoErrors = {};
  for (const field of ['whiteFideId', 'blackFideId'] as const) {
    const value = info[field].trim();
    if (value && !/^[1-9]\d{0,9}$/.test(value)) errors[field] = 'Digits only';
  }
  checkNumber(errors, info, 'whiteElo', 0, 9999);
  checkNumber(errors, info, 'blackElo', 0, 9999);
  for (const [elo, type] of [['whiteElo', info.whiteEloType], ['blackElo', info.blackEloType]] as const) {
    if (!errors[elo] && info[elo].trim() && type?.kind === 'NATIONAL' && !/^[A-Z0-9]{3}$/.test(type.nation ?? '')) {
      errors[elo] = 'Needs a nation';
    }
  }
  const dateError = dateTextError(info.date);
  if (dateError) errors.date = dateError;
  const timeControlProblem = timeControlError(info.timeControl);
  if (timeControlProblem) errors.timeControl = timeControlProblem;
  checkNumber(errors, info, 'round', 1, 255);
  checkNumber(errors, info, 'subRound', 1, 255);
  checkNumber(errors, info, 'board', 1, 32767);
  if (info.subRound.trim() && !info.round.trim()) {
    errors.subRound = 'Needs a round';
  }
  if (info.eco.trim() && !/^[A-E]\d\d(\/\d\d)?$/i.test(info.eco.trim())) {
    errors.eco = 'Like E25 or E25/03';
  }
  return errors;
}

/** Writes validated form values back to the game's PGN tags. */
export function writeGameInfo(game: GameTree, info: GameInfo) {
  const round = info.round.trim();
  const subRound = info.subRound.trim();

  writePlayer(game, 'White', PLAYER_ID_TAGS.white, info.white);
  game.setTag(FIDE_ID_TAGS.white, info.whiteFideId.trim());
  game.setTag('WhiteElo', info.whiteElo.trim());
  // A type only means something with an elo
  game.setTag(ELO_TYPE_TAGS.white, info.whiteElo.trim() ? encodeEloType(info.whiteEloType) : '');
  writePlayer(game, 'Black', PLAYER_ID_TAGS.black, info.black);
  game.setTag(FIDE_ID_TAGS.black, info.blackFideId.trim());
  game.setTag('BlackElo', info.blackElo.trim());
  game.setTag(ELO_TYPE_TAGS.black, info.blackElo.trim() ? encodeEloType(info.blackEloType) : '');
  game.setTag('Result', info.result);
  game.setTag(LINE_EVALUATION_TAG, info.result === LINE_RESULT ? info.lineEvaluation : '');
  game.setTag('Date', formatDateTag(parseDateText(info.date) ?? undefined));
  game.setTag(TIME_CONTROL_TAG, toPgnTimeControl(parseTimeControl(info.timeControl) ?? undefined));
  const t = info.tournament;
  const tournament = t && (t.title.trim() || t.id != null || t.place?.trim()) ? { ...t, title: t.title.trim() } : null;
  for (const [name, value] of Object.entries(tournamentToTags(tournament))) {
    game.setTag(name, value);
  }
  game.setTag('Round', round && subRound ? `${round}.${subRound}` : round);
  game.setTag('Board', info.board.trim());
  game.setTag('ECO', info.eco.trim().toUpperCase());
  game.setTag('Opening', info.opening.trim());
  // Not normalized like a player's name: ChessBase has annotators like "Gutman,L"
  game.setTag('Annotator', info.annotator.name.trim());
  game.setTag(ANNOTATOR_ID_TAG, info.annotator.id != null ? String(info.annotator.id) : '');
  const source = info.source && (info.source.title.trim() || info.source.id != null || info.source.publisher?.trim())
    ? { ...info.source, title: info.source.title.trim() }
    : null;
  for (const [name, value] of Object.entries(sourceToTags(source))) {
    game.setTag(name, value);
  }
  for (const color of ['white', 'black'] as const) {
    const t = info[`${color}Team`];
    const team = t && (t.title.trim() || t.id != null) ? { ...t, title: t.title.trim() } : null;
    for (const [name, value] of Object.entries(teamToTags(color, team))) {
      game.setTag(name, value);
    }
  }
  for (const [name, value] of Object.entries(gameTagToTags(info.gameTag))) {
    game.setTag(name, value.trim());
  }
}

/**
 * Forgets which entities of its database the game refers to: its players, annotator,
 * tournament, source, teams and tag keep their names and details, without ids. A game copied to
 * another database is then matched there by name, or creates them.
 */
export function forgetEntityIds(game: GameTree) {
  const info = readGameInfo(game);
  const noId = <T extends { id: number | null }>(entity: T | null): T | null => entity && { ...entity, id: null };
  writeGameInfo(game, {
    ...info,
    white: { ...info.white, id: null },
    black: { ...info.black, id: null },
    annotator: { ...info.annotator, id: null },
    tournament: noId(info.tournament),
    source: noId(info.source),
    whiteTeam: noId(info.whiteTeam),
    blackTeam: noId(info.blackTeam),
    gameTag: noId(info.gameTag),
  });
}
