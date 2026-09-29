import type { Chess } from '@jackstenglein/chess';
import { LINE_EVALUATION_TAG } from 'game-view';
import type { DateDto, GameDto, GameResultDto } from '../api/types';

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

function parsePlayerNameTag(value: string | undefined): { lastName?: string; firstName?: string } | undefined {
  if (!value) return undefined;
  const [lastName = '', firstName] = value.split(',').map((s) => s.trim());
  if (!lastName && !firstName) return undefined;
  return firstName ? { lastName, firstName } : { lastName };
}

const KNOWN_TAGS = new Set([
  'Event', 'Site', 'Date', 'Round', 'White', 'Black', 'Result',
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

  pushIfSet('Event', game.tournament?.title);
  pushIfSet('Site', game.tournament?.place);
  tags.push(['Date', formatDateDto(game.date)]);
  pushIfSet(
    'Round',
    game.round == null ? undefined : game.subRound ? `${game.round}.${game.subRound}` : String(game.round)
  );
  pushIfSet('White', formatPlayerNameTag(game.whitePlayer) || undefined);
  pushIfSet('Black', formatPlayerNameTag(game.blackPlayer) || undefined);
  tags.push(['Result', RESULT_TO_PGN[game.result] ?? '*']);
  if (game.result === 'NOT_FINISHED') pushIfSet(LINE_EVALUATION_TAG, game.lineEvaluation);
  pushIfSet('WhiteElo', game.whiteElo == null ? undefined : String(game.whiteElo));
  pushIfSet('BlackElo', game.blackElo == null ? undefined : String(game.blackElo));
  pushIfSet('Board', game.board == null ? undefined : String(game.board));
  pushIfSet('ECO', game.eco);
  pushIfSet('Annotator', game.annotator?.name);
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

  const white = parsePlayerNameTag(tagValues.White);
  const black = parsePlayerNameTag(tagValues.Black);
  const whiteNameUnchanged = formatPlayerNameTag(white) === formatPlayerNameTag(base.whitePlayer);
  const blackNameUnchanged = formatPlayerNameTag(black) === formatPlayerNameTag(base.blackPlayer);

  const tournamentTitle = tagValues.Event || undefined;
  const tournamentUnchanged = tournamentTitle === base.tournament?.title;

  const annotatorName = tagValues.Annotator || undefined;
  const annotatorUnchanged = annotatorName === base.annotator?.name;

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
    whitePlayer: white
      ? { id: whiteNameUnchanged ? base.whitePlayer?.id ?? null : null, ...white }
      : undefined,
    blackPlayer: black
      ? { id: blackNameUnchanged ? base.blackPlayer?.id ?? null : null, ...black }
      : undefined,
    whiteElo: tagValues.WhiteElo ? parseInt(tagValues.WhiteElo, 10) : undefined,
    blackElo: tagValues.BlackElo ? parseInt(tagValues.BlackElo, 10) : undefined,
    result,
    lineEvaluation: result === 'NOT_FINISHED' ? tagValues[LINE_EVALUATION_TAG] || undefined : undefined,
    date: parseDateTag(tagValues.Date, base.date),
    eco: tagValues.ECO || undefined,
    round: round?.[0] ? parseInt(round[0], 10) : undefined,
    subRound: round?.[1] ? parseInt(round[1], 10) : undefined,
    board: tagValues.Board ? parseInt(tagValues.Board, 10) : undefined,
    tournament: tournamentTitle
      ? {
          ...base.tournament,
          id: tournamentUnchanged ? base.tournament?.id ?? null : null,
          title: tournamentTitle,
          place: tagValues.Site || undefined,
        }
      : undefined,
    annotator: annotatorName
      ? { id: annotatorUnchanged ? base.annotator?.id ?? null : null, name: annotatorName }
      : undefined,
    setupPosition,
    moves: {
      pgn: chess.renderPgn({ skipHeader: true }).trim(),
      fen: setupPosition ? tagValues.FEN : undefined,
    },
    extraTags: Object.keys(extraTags).length > 0 ? extraTags : undefined,
  };
}
