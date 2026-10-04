import { describe, expect, it } from 'vitest';
import { forgetEntityIds, GameTree } from 'game-view';
import { readGameInfo, writeGameInfo } from 'game-view/src/utils/gameInfo';
import type { GameDto } from '../api/types';
import { gameDtoToTags, gameToGamePatch } from './gameDtoAdapter';

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

/** The game as the board tester has it. */
function load(game: GameDto): GameTree {
  return GameTree.fromMoves(game.moves, gameDtoToTags(game));
}

describe('the game as PGN tags', () => {
  it('comes back as it was', () => {
    const patch = gameToGamePatch(load(GAME), GAME);
    const { moves, ...header } = patch;
    const { moves: originalMoves, ...originalHeader } = GAME;
    expect(header).toEqual({ ...originalHeader, setupPosition: false, extraTags: undefined });
    expect(moves).toEqual({ pgn: '1. d4 Nf6 2. c4 e6', fen: undefined, annotations: undefined });
    expect(originalMoves).toBeDefined();
  });

  it('comes back as it was through the Edit Game Info form', () => {
    const game = load(GAME);
    writeGameInfo(game, readGameInfo(game));
    expect(gameToGamePatch(game, GAME)).toEqual(gameToGamePatch(load(GAME), GAME));
  });

  it('keeps the empty placeholder tag a game without a tag refers to', () => {
    const placeholder: GameDto = { ...GAME, gameTag: { id: 0, languageCount: 0 } };
    expect(gameToGamePatch(load(placeholder), placeholder).gameTag).toEqual({ id: 0, languageCount: 0 });
  });

  it('keeps a time control tag it can not read', () => {
    const game = load({ ...GAME, timeControl: undefined });
    game.setTag('TimeControl', '*180');
    const patch = gameToGamePatch(game, GAME);
    expect(patch.timeControl).toBeUndefined();
    expect(patch.extraTags).toEqual({ TimeControl: '*180' });
  });
});

describe('the moves and annotations', () => {
  it('come back as they were', () => {
    const fen = '4k3/8/8/8/8/8/4P3/4K3 w - - 0 1';
    const game: GameDto = {
      ...GAME,
      setupPosition: true,
      moves: {
        pgn: '1. e4 Kd7 (1... Kf7 2. e5) 2. e5',
        fen,
        annotations: [
          { move: -1, type: 'textAfter', text: 'Intro', language: 'ENG' },
          { move: 0, type: 'symbols', nags: [1] },
          { move: 2, type: 'arrows', arrows: [{ color: 'orange', from: 'f7', to: 'f6' }] },
          { move: 4, type: 'raw', annotationType: 26, data: 'AQID', invalid: false },
        ],
      },
    };
    const patch = gameToGamePatch(load(game), game);
    expect(patch.moves).toEqual(game.moves);
    expect(patch.setupPosition).toBe(true);
  });

  it('keeps the entities but not their ids once the ids are forgotten', () => {
    const game = load(GAME);
    forgetEntityIds(game);
    const patch = gameToGamePatch(game, GAME);
    expect(patch.whitePlayer).toEqual({ ...GAME.whitePlayer, id: null });
    expect(patch.blackPlayer).toEqual({ ...GAME.blackPlayer, id: null });
    expect(patch.annotator).toEqual({ ...GAME.annotator, id: null });
    expect(patch.tournament).toEqual({ ...GAME.tournament, id: null });
    expect(patch.source).toEqual({ ...GAME.source, id: null });
    expect(patch.whiteTeam).toEqual({ ...GAME.whiteTeam, id: null });
    expect(patch.gameTag).toEqual({ ...GAME.gameTag, id: null });
    // The rest is as it was
    expect({ ...patch, whitePlayer: 0, blackPlayer: 0, annotator: 0, tournament: 0, source: 0, whiteTeam: 0, gameTag: 0 }).toEqual({
      ...gameToGamePatch(load(GAME), GAME),
      whitePlayer: 0,
      blackPlayer: 0,
      annotator: 0,
      tournament: 0,
      source: 0,
      whiteTeam: 0,
      gameTag: 0,
    });
  });
});
