import type { Chess } from '@jackstenglein/chess';
import {
  ANNOTATOR_ID_TAG,
  decodeEloType,
  ELO_TYPE_TAGS,
  encodeEloType,
  LINE_EVALUATION_TAG,
  PLAYER_ID_TAGS,
  SOURCE_TAGS,
  sourceFromTags,
  sourceToTags,
  splitPlayerName,
  teamFromTags,
  teamTags,
  teamToTags,
  TOURNAMENT_TAGS,
  tournamentFromTags,
  tournamentToTags,
} from 'game-view';
import type { SourceInfo, TeamInfo, TournamentInfo } from 'game-view';
import type { DateDto, GameDto, GameResultDto, PlayerDto, SourceDto, TeamDto, TournamentDto } from '../api/types';

// Bridges morphy-service's GameDto (flattened header fields + a movetext-only
// moves.pgn) and the single full-PGN-string (headers + movetext) that
// @jackstenglein/chess's Chess wants. Confirmed against the library's own
// .d.ts (node_modules/@jackstenglein/chess/dist/types/{Chess,Pgn,Header}.d.ts
// in yarin-chess's install): Chess#renderPgn({ skipHeader: true }) returns
// movetext only, and Chess#header().valueMap() returns every current tag.

// GameResult <-> PGN Result tag, from se.yarin.chess.GameResult#toString().
const RESULT_TO_PGN: Record<GameResultDto, string> = {
  WHITE_WINS: '1-0',
  BLACK_WINS: '0-1',
  DRAW: '1/2-1/2',
  NOT_FINISHED: '*',
  WHITE_WINS_ON_FORFEIT: '+:-',
  DRAW_ON_FORFEIT: '=:=',
  BLACK_WINS_ON_FORFEIT: '-:+',
  BOTH_LOST: '0-0',
};
const PGN_TO_RESULT: Record<string, GameResultDto> = Object.fromEntries(
  Object.entries(RESULT_TO_PGN).map(([result, pgn]) => [pgn, result as GameResultDto])
);

// GameDto.date <-> PGN Date tag, from se.yarin.chess.Date#toString().
function formatDateDto(date: DateDto | undefined): string {
  if (!date) return '????.??.??';
  const year = date.year === 0 ? '????' : String(date.year).padStart(4, '0');
  const month = date.month === 0 ? '??' : String(date.month).padStart(2, '0');
  const day = date.day === 0 ? '??' : String(date.day).padStart(2, '0');
  return `${year}.${month}.${day}`;
}

function parseDateTag(value: string | undefined, fallback: DateDto): DateDto {
  if (!value) return fallback;
  const match = value.match(/^(\d{4}|\?{4})\.(\d{2}|\?{2})\.(\d{2}|\?{2})$/);
  if (!match) return fallback;
  const [, y, m, d] = match;
  return {
    year: y === '????' ? 0 : parseInt(y, 10),
    month: m === '??' ? 0 : parseInt(m, 10),
    day: d === '??' ? 0 : parseInt(d, 10),
  };
}

/** "Lastname, Firstname" (or just "Lastname"), the usual PGN convention for player tags. */
function formatPlayerNameTag(player: { lastName?: string; firstName?: string } | undefined): string {
  const lastName = player?.lastName ?? '';
  return player?.firstName ? `${lastName}, ${player.firstName}` : lastName;
}

/**
 * A player from its name and id tags. With an id, the game is put with that existing player; without
 * one, the player is found by name, or created.
 */
function playerFromTags(name: string | undefined, id: string | undefined): PlayerDto | undefined {
  const { lastName, firstName } = splitPlayerName(name ?? '');
  if (!lastName && !firstName) return undefined;
  return {
    id: id && /^\d+$/.test(id) ? parseInt(id, 10) : null,
    lastName,
    ...(firstName ? { firstName } : {}),
  };
}

/** A tournament as the Edit Game Info dialog has it. */
export function tournamentInfo(dto: TournamentDto | undefined): TournamentInfo | null {
  if (!dto) return null;
  return {
    id: dto.id,
    title: dto.title ?? '',
    startDate: dto.startDate,
    endDate: dto.endDate,
    place: dto.place,
    nation: dto.nation,
    type: dto.type,
    timeControl: dto.timeControl,
    rounds: dto.rounds,
    category: dto.category,
    complete: dto.complete,
    teamTournament: dto.teamTournament,
    gameCount: dto.gameCount,
  };
}

/**
 * A tournament for the server. With an id, the game is put in that existing tournament and the
 * other fields are ignored; without one, the tournament with these fields is found, or created.
 */
export function tournamentDto(t: TournamentInfo): TournamentDto {
  return {
    id: t.id,
    title: t.title,
    startDate: t.startDate,
    endDate: t.endDate,
    place: t.place,
    nation: t.nation,
    type: t.type,
    timeControl: t.timeControl,
    rounds: t.rounds,
    category: t.category,
    complete: t.complete,
    teamTournament: t.teamTournament,
  };
}

/** A source as the Edit Game Info dialog has it. */
export function sourceInfo(dto: SourceDto | undefined): SourceInfo | null {
  if (!dto) return null;
  return {
    id: dto.id,
    title: dto.title ?? '',
    publisher: dto.publisher,
    publication: dto.publication,
    date: dto.date,
    version: dto.version,
    quality: dto.quality,
    gameCount: dto.gameCount,
  };
}

/**
 * A source for the server. With an id, the game is put with that existing source and the other
 * fields are ignored; without one, the source with these fields is found, or created.
 */
export function sourceDto(s: SourceInfo): SourceDto {
  return {
    id: s.id,
    title: s.title,
    publisher: s.publisher,
    publication: s.publication,
    date: s.date,
    version: s.version,
    quality: s.quality,
  };
}

/** A team as the Edit Game Info dialog has it. */
export function teamInfo(dto: TeamDto | undefined): TeamInfo | null {
  if (!dto) return null;
  return {
    id: dto.id,
    title: dto.title ?? '',
    number: dto.teamNumber,
    season: dto.season,
    year: dto.year,
    nation: dto.nation,
    gameCount: dto.gameCount,
  };
}

/**
 * A team for the server. With an id, the player is put in that existing team and the other fields
 * are ignored; without one, the team with these fields is found, or created.
 */
export function teamDto(t: TeamInfo): TeamDto {
  return { id: t.id, title: t.title, teamNumber: t.number, season: t.season, year: t.year, nation: t.nation };
}

const KNOWN_TAGS = new Set<string>([
  ...Object.values(teamTags('white')), ...Object.values(teamTags('black')),
  ...Object.values(SOURCE_TAGS), ANNOTATOR_ID_TAG,
  ...Object.values(TOURNAMENT_TAGS), ...Object.values(PLAYER_ID_TAGS), ...Object.values(ELO_TYPE_TAGS), 'Date', 'Round', 'White', 'Black', 'Result',
  'WhiteElo', 'BlackElo', 'Board', 'ECO', 'Annotator', 'FEN', 'SetUp', LINE_EVALUATION_TAG,
]);

/**
 * Builds a full PGN string (headers + movetext) from a GameDto, suitable for
 * @jackstenglein/chess's `new Chess({ pgn })` / GameView's `ChessGame.pgn`.
 */
export function gameDtoToPgn(game: GameDto): string {
  const tags: [string, string][] = [];
  const pushIfSet = (name: string, value: string | undefined) => {
    if (value) tags.push([name, value]);
  };

  for (const [name, value] of Object.entries(tournamentToTags(tournamentInfo(game.tournament)))) {
    pushIfSet(name, value);
  }
  tags.push(['Date', formatDateDto(game.date)]);
  pushIfSet(
    'Round',
    game.round == null ? undefined : game.subRound ? `${game.round}.${game.subRound}` : String(game.round)
  );
  pushIfSet('White', formatPlayerNameTag(game.whitePlayer) || undefined);
  pushIfSet(PLAYER_ID_TAGS.white, game.whitePlayer?.id == null ? undefined : String(game.whitePlayer.id));
  pushIfSet('Black', formatPlayerNameTag(game.blackPlayer) || undefined);
  pushIfSet(PLAYER_ID_TAGS.black, game.blackPlayer?.id == null ? undefined : String(game.blackPlayer.id));
  tags.push(['Result', RESULT_TO_PGN[game.result] ?? '*']);
  if (game.result === 'NOT_FINISHED') pushIfSet(LINE_EVALUATION_TAG, game.lineEvaluation);
  pushIfSet('WhiteElo', game.whiteElo == null ? undefined : String(game.whiteElo));
  pushIfSet(ELO_TYPE_TAGS.white, encodeEloType(game.whiteEloType ?? null));
  pushIfSet('BlackElo', game.blackElo == null ? undefined : String(game.blackElo));
  pushIfSet(ELO_TYPE_TAGS.black, encodeEloType(game.blackEloType ?? null));
  pushIfSet('Board', game.board == null ? undefined : String(game.board));
  pushIfSet('ECO', game.eco);
  pushIfSet('Annotator', game.annotator?.name);
  pushIfSet(ANNOTATOR_ID_TAG, game.annotator?.id == null ? undefined : String(game.annotator.id));
  for (const [name, value] of Object.entries(sourceToTags(sourceInfo(game.source)))) {
    pushIfSet(name, value);
  }
  for (const [name, value] of Object.entries(teamToTags('white', teamInfo(game.whiteTeam)))) {
    pushIfSet(name, value);
  }
  for (const [name, value] of Object.entries(teamToTags('black', teamInfo(game.blackTeam)))) {
    pushIfSet(name, value);
  }
  if (game.setupPosition && game.moves?.fen) {
    tags.push(['FEN', game.moves.fen]);
    tags.push(['SetUp', '1']);
  }
  if (game.extraTags) {
    for (const [name, value] of Object.entries(game.extraTags)) {
      pushIfSet(name, value);
    }
  }

  const header = tags.map(([name, value]) => `[${name} "${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"]`).join('\n');
  const movetext = game.moves?.pgn?.trim();
  const resultToken = RESULT_TO_PGN[game.result] ?? '*';
  const moves = movetext ? `${movetext} ${resultToken}` : resultToken;

  return `${header}\n\n${moves}\n`;
}

/**
 * Reads the current PGN headers and moves off a live Chess instance and folds them into a
 * copy of `base` (preserving anything the adapter doesn't understand, e.g. ids of unrelated
 * entities). Used to build the request body for createGame/replaceGame after editing.
 *
 * NOT YET VERIFIED against a real backend: whether sending a name-only PlayerDto/TournamentDto
 * (id: null) is enough for morphy-service's find-or-create to kick in for a *new* game is
 * confirmed by Database#addGame's contract, but the exact minimal shape wasn't exercised
 * against a live database while writing this - see the plan's open items.
 */
export function pgnToGamePatch(chess: Chess, base: GameDto): GameDto {
  const tagValues = chess.header().valueMap();


  const tournament = tournamentFromTags((name) => tagValues[name] ?? '');

  const annotatorName = tagValues.Annotator?.trim() || undefined;
  const annotatorId = tagValues[ANNOTATOR_ID_TAG];
  const source = sourceFromTags((name) => tagValues[name] ?? '');
  const whiteTeam = teamFromTags('white', (name) => tagValues[name] ?? '');
  const blackTeam = teamFromTags('black', (name) => tagValues[name] ?? '');

  const result = PGN_TO_RESULT[tagValues.Result ?? ''] ?? base.result;
  const round = tagValues.Round?.split('.');
  const setupPosition = tagValues.SetUp === '1';

  const extraTags: Record<string, string> = {};
  for (const [name, value] of Object.entries(tagValues)) {
    if (!KNOWN_TAGS.has(name) && value) {
      extraTags[name] = value;
    }
  }

  return {
    ...base,
    whitePlayer: playerFromTags(tagValues.White, tagValues[PLAYER_ID_TAGS.white]),
    blackPlayer: playerFromTags(tagValues.Black, tagValues[PLAYER_ID_TAGS.black]),
    whiteElo: tagValues.WhiteElo ? parseInt(tagValues.WhiteElo, 10) : undefined,
    whiteEloType: tagValues.WhiteElo ? decodeEloType(tagValues[ELO_TYPE_TAGS.white] ?? '') ?? undefined : undefined,
    blackElo: tagValues.BlackElo ? parseInt(tagValues.BlackElo, 10) : undefined,
    blackEloType: tagValues.BlackElo ? decodeEloType(tagValues[ELO_TYPE_TAGS.black] ?? '') ?? undefined : undefined,
    result,
    lineEvaluation: result === 'NOT_FINISHED' ? tagValues[LINE_EVALUATION_TAG] || undefined : undefined,
    date: parseDateTag(tagValues.Date, base.date),
    eco: tagValues.ECO || undefined,
    round: round?.[0] ? parseInt(round[0], 10) : undefined,
    subRound: round?.[1] ? parseInt(round[1], 10) : undefined,
    board: tagValues.Board ? parseInt(tagValues.Board, 10) : undefined,
    tournament: tournament ? tournamentDto(tournament) : undefined,
    annotator: annotatorName
      ? { id: annotatorId && /^\d+$/.test(annotatorId) ? parseInt(annotatorId, 10) : null, name: annotatorName }
      : undefined,
    source: source ? sourceDto(source) : undefined,
    // A player without a team tag has no team
    whiteTeam: whiteTeam ? teamDto(whiteTeam) : undefined,
    blackTeam: blackTeam ? teamDto(blackTeam) : undefined,
    setupPosition,
    moves: {
      pgn: chess.renderPgn({ skipHeader: true }).trim(),
      fen: setupPosition ? tagValues.FEN : undefined,
    },
    extraTags: Object.keys(extraTags).length > 0 ? extraTags : undefined,
  };
}
