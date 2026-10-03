import { describe, expect, it } from 'vitest';
import { GameTree } from '../model/GameTree';
import { generateNotationHtml } from './notationGenerator';

describe('the notation', () => {
  it('marks the colored squares and arrows of the start position just before the first move', () => {
    const tree = GameTree.fromMoves({
      pgn: '1.e4 e5',
      annotations: [{ move: -1, type: 'squares', squares: [{ color: 'red', square: 'd5' }] }],
    });
    const { html } = generateNotationHtml(tree);
    // In the main line, so it's on the same row as the first moves
    expect(html).toMatch(/<span class="cbline"[^>]*><span class="cbcol-marker"[^>]*> <\/span>\s*<span [^>]*>1\.e4/);
    expect(html.match(/cbcol-marker/g)).toHaveLength(1);
  });

  it('has no color marker before the first move without colored squares or arrows', () => {
    expect(generateNotationHtml(GameTree.fromMoves({ pgn: '1.e4 e5' })).html).not.toContain('cbcol-marker');
  });
});
