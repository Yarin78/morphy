import { Chess } from '@jackstenglein/chess';
import { describe, expect, it } from 'vitest';
import { readGameInfo, writeGameInfo } from 'game-view/src/utils/gameInfo';
import type { GameDto } from '../api/types';
import { gameDtoToPgn, pgnToGamePatch } from './gameDtoAdapter';

/** A game with something in every field the board tester edits. */
const GAME: GameDto = {
  id: 992,
  type: 'game',
  whitePlayer: { id: 5, lastName: 'Anand', firstName: 'Viswanathan', fideId: 5000017 },
  whiteElo: 2775,
  whiteEloType: { kind: 'INTERNATIONAL', timeControl: 'NORMAL', name: 'FIDE' },
  blackPlayer: { id: 3, lastName: 'Carlsen', firstName: 'Magnus' },
  blackElo: 2870,
  blackEloType: { kind: 'NATIONAL', timeControl: 'RAPID', nation: 'NOR' },
  whiteTeam: { id: null, title: 'India', teamNumber: 1, season: true, year: 2013, nation: 'IND' },
  result: 'BLACK_WINS',
  date: { year: 2013, month: 11, day: 21 },
  eco: 'E25',
  round: 9,
  subRound: 2,
  board: 1,
  timeControl: { periods: [{ seconds: 7200, increment: 0, moves: 40 }, { seconds: 1800, increment: 30 }] },
  tournament: {
    id: 1,
    title: 'World-ch Carlsen-Anand',
    startDate: { year: 2013, month: 11, day: 9 },
    place: 'Chennai',
    nation: 'IND',
    rounds: 12,
    complete: true,
  },
  source: { id: 2, title: 'CBM 158', publisher: 'ChessBase', version: 1 },
  annotator: { id: 6, name: 'Gutman,L' },
  gameTag: { id: null, title: 'Strategie', germanTitle: 'Strategie' },
  moves: { pgn: '1. d4 Nf6 2. c4 e6' },
};

/** The game as the board tester has it: its PGN in a Chess instance. */
function load(game: GameDto): Chess {
  return new Chess({ pgn: gameDtoToPgn(game) });
}

describe('the game as PGN tags', () => {
  it('comes back as it was', () => {
    const patch = pgnToGamePatch(load(GAME), GAME);
    const { moves, ...header } = patch;
    const { moves: originalMoves, ...originalHeader } = GAME;
    expect(header).toEqual({ ...originalHeader, setupPosition: false, extraTags: undefined });
    expect(moves?.pgn).toContain('1. d4 Nf6 2. c4 e6');
    expect(originalMoves).toBeDefined();
  });

  it('comes back as it was through the Edit Game Info form', () => {
    const chess = load(GAME);
    writeGameInfo(chess, readGameInfo(chess));
    expect(pgnToGamePatch(chess, GAME)).toEqual(pgnToGamePatch(load(GAME), GAME));
  });

  it('keeps the empty placeholder tag a game without a tag refers to', () => {
    const placeholder: GameDto = { ...GAME, gameTag: { id: 0, languageCount: 0 } };
    expect(pgnToGamePatch(load(placeholder), placeholder).gameTag).toEqual({ id: 0, languageCount: 0 });
  });

  it('keeps a time control tag it can not read', () => {
    const chess = load({ ...GAME, timeControl: undefined });
    chess.setHeader('TimeControl', '*180');
    const patch = pgnToGamePatch(chess, GAME);
    expect(patch.timeControl).toBeUndefined();
    expect(patch.extraTags).toEqual({ TimeControl: '*180' });
  });
});
