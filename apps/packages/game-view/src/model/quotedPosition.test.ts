import { describe, expect, it } from 'vitest';
import { GameTree } from './GameTree';
import { quotedPosition } from './quotedPosition';

// Depth-first: 0 root, 1 e4, 2 e5, 3 Nf3, 4 Nc6, 5 c5 (a variation of e5), 6 Nf3 after c5
const game = GameTree.fromMoves({ pgn: '1. e4 e5 (1... c5 2. Nf3) 2. Nf3 Nc6' });
const after = (san: string[]) => {
  const g = GameTree.fromMoves({ pgn: '' });
  for (const m of san) g.play(m === 'e4' ? { from: 'e2', to: 'e4' } : m === 'c5' ? { from: 'c7', to: 'c5' } : m === 'Nf3' ? { from: 'g1', to: 'f3' } : { from: 'e7', to: 'e5' });
  return g.fen();
};

describe('quoted positions', () => {
  it('are found at their index when the position there is the quoted one', () => {
    expect(quotedPosition(game, after(['e4', 'c5', 'Nf3']), 6)?.san).toBe('Nf3');
    expect(quotedPosition(game, after(['e4', 'c5', 'Nf3']), 6)?.parent).toBe(game.movesInOrder()[2]);
  });

  it('are looked for when the index is stale', () => {
    const move = quotedPosition(game, after(['e4', 'c5', 'Nf3']), 3);
    expect(move?.san).toBe('Nf3');
    expect((move?.parent as { san?: string }).san).toBe('c5');
  });

  it('are the start of the game for index 0, or when not found', () => {
    expect(quotedPosition(game, after(['e4']), 0)).toBeNull();
    expect(quotedPosition(game, '8/8/8/8/8/8/8/K6k w - - 0 1', 2)).toBeNull();
  });
});
