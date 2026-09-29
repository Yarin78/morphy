import type { Chess } from '@jackstenglein/chess';

/**
 * The game header information edited in the Edit Game Info dialog, as form values. Everything is
 * a string, as typed; an empty string means "not set". It's read from and written back to the
 * Chess instance's PGN tags, so whoever saves the game picks the changes up from there.
 */
export interface GameInfo {
  whiteLastName: string;
  whiteFirstName: string;
  whiteElo: string;
  blackLastName: string;
  blackFirstName: string;
  blackElo: string;
  /** A PGN Result tag value: one of the RESULTS values. */
  result: string;
  /** For a line (result '*'), a NAG name like 'WHITE_SLIGHT_ADVANTAGE'; see LINE_EVALUATIONS. */
  lineEvaluation: string;
  year: string;
  month: string;
  day: string;
  tournament: string;
  round: string;
  subRound: string;
  board: string;
}

/**
 * Not a standard PGN tag: holds the evaluation of a line (a game with result '*') as a NAG name,
 * the way morphy-service's GameDto.lineEvaluation does.
 */
export const LINE_EVALUATION_TAG = 'LineEvaluation';

export const LINE_RESULT = '*';

export const RESULTS: { value: string; label: string }[] = [
  { value: '1-0', label: '1-0' },
  { value: '1/2-1/2', label: '½-½' },
  { value: '0-1', label: '0-1' },
  { value: LINE_RESULT, label: 'Line' },
  // Forfeits, and a game both players lost
  { value: '+:-', label: '+ -' },
  { value: '=:=', label: '= =' },
  { value: '-:+', label: '- +' },
  { value: '0-0', label: '0-0' },
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
function tagValue(chess: Chess, name: string): string {
  const value = chess.header().getRawValue(name).trim();
  return /^\?*$/.test(value) ? '' : value;
}

/** Splits a "Lastname, Firstname" player tag. */
function splitName(name: string): [string, string] {
  const comma = name.indexOf(',');
  return comma < 0 ? [name, ''] : [name.slice(0, comma).trim(), name.slice(comma + 1).trim()];
}

function joinName(lastName: string, firstName: string): string {
  const last = lastName.trim();
  const first = firstName.trim();
  return first ? `${last}, ${first}` : last;
}

/**
 * A number in a tag, e.g. a date part like '1886' or '??', as a form value: '' when unknown,
 * without leading zeros.
 */
function numberPart(part: string | undefined): string {
  return part && /^\d+$/.test(part) && parseInt(part, 10) > 0 ? String(parseInt(part, 10)) : '';
}

export function readGameInfo(chess: Chess): GameInfo {
  const [whiteLastName, whiteFirstName] = splitName(tagValue(chess, 'White'));
  const [blackLastName, blackFirstName] = splitName(tagValue(chess, 'Black'));
  const [year, month, day] = tagValue(chess, 'Date').split('.');
  const [round, subRound] = tagValue(chess, 'Round').split('.');
  const result = tagValue(chess, 'Result') || LINE_RESULT;
  return {
    whiteLastName,
    whiteFirstName,
    whiteElo: tagValue(chess, 'WhiteElo'),
    blackLastName,
    blackFirstName,
    blackElo: tagValue(chess, 'BlackElo'),
    result: RESULTS.some((r) => r.value === result) ? result : LINE_RESULT,
    lineEvaluation: tagValue(chess, LINE_EVALUATION_TAG),
    year: numberPart(year),
    month: numberPart(month),
    day: numberPart(day),
    tournament: tagValue(chess, 'Event'),
    round: numberPart(round),
    subRound: numberPart(subRound),
    board: numberPart(tagValue(chess, 'Board')),
  };
}

/** A problem with a form value, keyed by the GameInfo field it's about. */
export type GameInfoErrors = Partial<Record<keyof GameInfo, string>>;

function checkNumber(
  errors: GameInfoErrors,
  info: GameInfo,
  field: keyof GameInfo,
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
  checkNumber(errors, info, 'whiteElo', 0, 9999);
  checkNumber(errors, info, 'blackElo', 0, 9999);
  checkNumber(errors, info, 'year', 1, 9999);
  checkNumber(errors, info, 'month', 1, 12);
  checkNumber(errors, info, 'day', 1, 31);
  checkNumber(errors, info, 'round', 1, 255);
  checkNumber(errors, info, 'subRound', 1, 255);
  checkNumber(errors, info, 'board', 1, 32767);
  if (info.subRound.trim() && !info.round.trim()) {
    errors.subRound = 'Needs a round';
  }
  return errors;
}

/** Writes validated form values back to the Chess instance's PGN tags. */
export function writeGameInfo(chess: Chess, info: GameInfo) {
  const pad = (value: string, width: number) =>
    value.trim() ? value.trim().padStart(width, '0') : '?'.repeat(width);
  const round = info.round.trim();
  const subRound = info.subRound.trim();

  chess.setHeader('White', joinName(info.whiteLastName, info.whiteFirstName));
  chess.setHeader('WhiteElo', info.whiteElo.trim());
  chess.setHeader('Black', joinName(info.blackLastName, info.blackFirstName));
  chess.setHeader('BlackElo', info.blackElo.trim());
  chess.setHeader('Result', info.result);
  chess.setHeader(LINE_EVALUATION_TAG, info.result === LINE_RESULT ? info.lineEvaluation : '');
  chess.setHeader('Date', `${pad(info.year, 4)}.${pad(info.month, 2)}.${pad(info.day, 2)}`);
  chess.setHeader('Event', info.tournament.trim());
  chess.setHeader('Round', round && subRound ? `${round}.${subRound}` : round);
  chess.setHeader('Board', info.board.trim());
}
