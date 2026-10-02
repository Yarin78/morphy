import { describe, expect, it } from 'vitest';
import { GameTree } from './GameTree';
import type { AnnotationDto } from './annotations';

const MOVES = '1. e4 c5 (1... c6 2. d4) 2. Nf3';

describe('GameTree', () => {
  it('numbers the moves in the order of the movetext', () => {
    const tree = GameTree.fromMoves({ pgn: MOVES });
    expect(tree.movesInOrder().map((m) => m.san)).toEqual(['e4', 'c5', 'c6', 'd4', 'Nf3']);
  });

  it('writes the movetext it reads', () => {
    for (const pgn of [
      MOVES,
      '1. e4 (1. d4 d5) (1. c4) 1... e5 2. Nf3 (2. f4 exf4 (2... d5)) 2... Nc6',
      '1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. O-O',
      '',
    ]) {
      expect(GameTree.fromMoves({ pgn }).movetext()).toBe(pgn);
    }
  });

  it('reads moves from a setup position', () => {
    const fen = '7k/8/6K1/8/8/8/8/5Q2 w - - 0 1';
    const tree = GameTree.fromMoves({ pgn: '1. Qf7', fen });
    expect(tree.movetext()).toBe('1. Qf7');
    expect(tree.toMoves().fen).toBe(fen);
    const black = GameTree.fromMoves({ pgn: '1... Kg8', fen: '7k/8/6K1/8/8/8/8/5Q2 b - - 0 1' });
    expect(black.movetext()).toBe('1... Kg8');
  });

  it('skips comments, NAGs, suffixes and the result', () => {
    const tree = GameTree.fromMoves({ pgn: '1. e4! { best } $1 1... c5 (1... c6?! ; why\n) 1-0' });
    expect(tree.movetext()).toBe('1. e4 c5 (1... c6)');
  });

  it('reads null moves', () => {
    const tree = GameTree.fromMoves({ pgn: '1. e4 -- 2. d4' });
    expect(tree.movesInOrder().map((m) => m.isNullMove)).toEqual([false, true, false]);
    expect(tree.movetext()).toBe('1. e4 -- 2. d4');
  });

  it('rejects illegal moves and unbalanced variations', () => {
    expect(() => GameTree.fromMoves({ pgn: '1. e5' })).toThrow();
    expect(() => GameTree.fromMoves({ pgn: '1. e4 (1. d4' })).toThrow();
    expect(() => GameTree.fromMoves({ pgn: '1. e4 )' })).toThrow();
  });

  it('attaches the annotations to their moves and gives them back', () => {
    const annotations: AnnotationDto[] = [
      { move: -1, type: 'textAfter', text: 'Intro' },
      { move: 0, type: 'symbols', nags: [1] },
      { move: 2, type: 'squares', squares: [{ color: 'red', square: 'd5' }] },
      { move: 3, type: 'arrows', arrows: [{ color: 'blue', from: 'e2', to: 'e4' }] },
    ];
    const tree = GameTree.fromMoves({ pgn: MOVES, annotations });

    expect(tree.root.annotations).toEqual([{ type: 'textAfter', text: 'Intro' }]);
    expect(tree.movesInOrder()[2].annotations).toEqual([
      { type: 'squares', squares: [{ color: 'red', square: 'd5' }] },
    ]);
    expect(tree.toMoves()).toEqual({ pgn: MOVES, fen: undefined, annotations });
  });

  it('rejects an annotation of a move that is not there', () => {
    expect(() =>
      GameTree.fromMoves({ pgn: '1. e4', annotations: [{ move: 1, type: 'textAfter', text: 'x' }] })
    ).toThrow();
  });

  it('plays moves as the main line, variations, or by going to them', () => {
    const tree = GameTree.fromMoves({ pgn: '1. e4 e5' });
    const e4 = tree.firstMove()!;

    tree.seek(e4);
    const c5 = tree.play({ from: 'c7', to: 'c5' })!;
    expect(tree.currentMove()).toBe(c5);
    expect(GameTree.alternatives(e4.children[0])).toEqual([c5]);

    tree.seek(e4);
    expect(tree.play({ from: 'e7', to: 'e5' })).toBe(e4.children[0]);

    tree.seek(c5);
    tree.play({ from: 'g1', to: 'f3' });
    expect(tree.movetext()).toBe('1. e4 e5 (1... c5 2. Nf3)');

    expect(tree.play({ from: 'e1', to: 'e8' })).toBeNull();
  });

  it('keeps the moves numbered when variations are added', () => {
    const tree = GameTree.fromMoves({
      pgn: '1. e4 e5 2. Nf3',
      annotations: [{ move: 2, type: 'textAfter', text: 'on Nf3' }],
    });
    tree.seek(tree.firstMove());
    tree.play({ from: 'c7', to: 'c5' });
    expect(tree.toMoves()).toEqual({
      pgn: '1. e4 e5 (1... c5) 2. Nf3',
      fen: undefined,
      annotations: [{ move: 3, type: 'textAfter', text: 'on Nf3' }],
    });
  });

  it('keeps tags in order and removes empty ones', () => {
    const tree = GameTree.fromMoves(undefined, [['White', 'A'], ['Black', 'B']]);
    tree.setTag('White', '');
    tree.setTag('Result', '1-0');
    expect(tree.tagValues()).toEqual({ Black: 'B', Result: '1-0' });
    expect(tree.getTag('White')).toBe('');
  });
});
