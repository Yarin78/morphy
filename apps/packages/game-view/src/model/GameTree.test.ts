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

  it('plays a null move, but not in check', () => {
    const tree = GameTree.fromMoves({ pgn: '1.e4 e5' });
    tree.seek(tree.root.children[0]);
    const nullMove = tree.playNullMove()!;
    expect(nullMove.isNullMove).toBe(true);
    expect(tree.root.children[0].children.map((m) => m.san)).toEqual(['e5', '--']);
    tree.seek(tree.root.children[0]);
    expect(tree.playNullMove()).toBe(nullMove);

    const check = GameTree.fromMoves({ pgn: '1.e4 f5 2.Qh5+' });
    check.seek(check.root.children[0].children[0].children[0]);
    expect(check.playNullMove()).toBeNull();
  });

  it('promotes the variation a move is in to the main line from where it starts', () => {
    const tree = GameTree.fromMoves({ pgn: '1.e4 e5 (1...c5 2.Nf3 (2.Nc3 Nc6) d6) (1...e6) 2.Nf3' });
    const e4 = tree.root.children[0];
    const nc6 = e4.children[1].children[1].children[0];
    expect(GameTree.variationStart(nc6)?.san).toBe('Nc3');

    // From inside a nested variation, the nested one is promoted
    expect(GameTree.promoteVariation(nc6)).toBe(true);
    expect(tree.movetext()).toBe('1. e4 e5 (1... c5 2. Nc3 (2. Nf3 d6) 2... Nc6) (1... e6) 2. Nf3');

    // Then the one it's now the main line of
    expect(GameTree.promoteVariation(nc6)).toBe(true);
    expect(tree.movetext()).toBe('1. e4 c5 (1... e5 2. Nf3) (1... e6) 2. Nc3 (2. Nf3 d6) 2... Nc6');

    // The main line of the game stays
    expect(GameTree.variationStart(nc6)).toBeNull();
    expect(GameTree.promoteVariation(nc6)).toBe(false);
  });

  describe('deleting moves', () => {
    const PGN = '1.e4 e5 (1...c5 2.Nf3 (2.Nc3 Nc6) d6) 2.Nf3 Nc6 3.Bb5';

    it('deletes the variation a move is in, showing where it started if the move shown was in it', () => {
      const tree = GameTree.fromMoves({ pgn: PGN });
      const e4 = tree.root.children[0];
      const nc6 = e4.children[1].children[1].children[0];
      tree.seek(nc6);
      expect(tree.deleteVariation(nc6)).toBe(true);
      expect(tree.movetext()).toBe('1. e4 e5 (1... c5 2. Nf3 d6) 2. Nf3 Nc6 3. Bb5');
      expect(tree.currentMove()?.san).toBe('c5');
      expect(tree.deleteVariation(e4.children[0])).toBe(false);
    });

    it('deletes the moves after a move, with their variations', () => {
      const tree = GameTree.fromMoves({ pgn: PGN });
      const e4 = tree.root.children[0];
      tree.seek(e4.children[0].children[0].children[0]);
      tree.deleteRemainingMoves(e4);
      expect(tree.movetext()).toBe('1. e4');
      expect(tree.currentMove()).toBe(e4);
    });

    it('deletes the moves before a move, starting the game from the position before it', () => {
      const tree = GameTree.fromMoves({
        pgn: PGN,
        annotations: [
          { move: -1, type: 'evaluations', evaluations: [] },
          { move: -1, type: 'medals', medals: ['BEST_GAME'] },
        ],
      });
      const e4 = tree.root.children[0];
      const nf3 = e4.children[0].children[0];
      const bb5 = nf3.children[0].children[0];
      tree.seek(bb5);
      expect(tree.deletePreviousMoves(nf3)).toBe(true);
      expect(tree.root.fen).toBe(e4.children[0].fen);
      expect(tree.movetext()).toBe('2. Nf3 Nc6 3. Bb5');
      expect(tree.root.children[0].parent).toBe(tree.root);
      expect(tree.root.annotations).toEqual([{ type: 'medals', medals: ['BEST_GAME'] }]);
      expect(tree.currentMove()).toBe(bb5);
      expect(tree.toMoves().fen).toBe(e4.children[0].fen);
      // Nothing comes before a first move
      expect(tree.deletePreviousMoves(nf3)).toBe(false);
    });

    it('makes a move of a variation the first move, the others from its position its variations', () => {
      const tree = GameTree.fromMoves({ pgn: PGN });
      const c5 = tree.root.children[0].children[1];
      const nc3 = c5.children[1];
      tree.seek(tree.root.children[0]);
      expect(tree.deletePreviousMoves(nc3)).toBe(true);
      expect(tree.movetext()).toBe('2. Nc3 (2. Nf3 d6) 2... Nc6');
      expect(tree.currentMove()).toBeNull();
    });
  });
});

