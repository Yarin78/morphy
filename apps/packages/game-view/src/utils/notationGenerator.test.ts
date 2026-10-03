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

  describe('variations', () => {
    const PGN = '1.e4 e5 (1...c5 2.Nf3 (2.c3 d5) d6) 2.Nf3';
    const moveTexts = (html: string) => [...html.matchAll(/class="cbmove[^"]*"[^>]*>([^<]*)</g)].map((m) => m[1]);

    it('start with a button to fold them', () => {
      const { html } = generateNotationHtml(GameTree.fromMoves({ pgn: PGN }));
      expect(html.match(/class="cbfold"[^>]*aria-expanded="true"/g)).toHaveLength(2);
      expect(moveTexts(html)).toEqual(['1.e4', 'e5', '1...c5', '2.Nf3', '2.c3', 'd5', '2...d6', '2.Nf3']);
    });

    it('show only their first move when folded', () => {
      const tree = GameTree.fromMoves({ pgn: PGN });
      const c5 = tree.root.children[0].children[1];
      const { html } = generateNotationHtml(tree, [], false, new Set([c5]));
      expect(html).toMatch(/class="cbfold"[^>]*aria-expanded="false"/);
      expect(moveTexts(html)).toEqual(['1.e4', 'e5', '1...c5', '2.Nf3']);
    });

    it('are shown unfolded while the current move is hidden in them', () => {
      const tree = GameTree.fromMoves({ pgn: PGN });
      const c5 = tree.root.children[0].children[1];
      tree.seek(c5.children[0].children[0]);
      const { html } = generateNotationHtml(tree, [], false, new Set([c5]));
      expect(moveTexts(html)).toContain('2...d6');
    });
  });
});

