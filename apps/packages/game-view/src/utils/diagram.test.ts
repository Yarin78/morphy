import { describe, expect, it } from 'vitest';
import { GameTree } from '../model/GameTree';
import type { GameNode } from '../model/GameTree';
import { commentWithDiagramsHtml, diagramHtml, pawnStructureHtml, piecePath, piecePathHtml } from './diagram';

/** The position at the end of the main line of the moves. */
function lastPosition(pgn: string): GameNode {
  let node = GameTree.fromMoves({ pgn }).root;
  while (node.children.length > 0) node = node.children[0];
  return node;
}

const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

describe('diagrams', () => {
  it('have the pieces of the position', () => {
    const html = diagramHtml(START, []);
    expect(html.startsWith('<div class="cbdiagram"')).toBe(true);
    expect(html.match(/<image /g)).toHaveLength(32);
  });

  it('have the colored squares and arrows of the position', () => {
    const html = diagramHtml(START, [
      { type: 'squares', squares: [{ color: 'red', square: 'd5' }] },
      { type: 'arrows', arrows: [{ color: 'green', from: 'e2', to: 'e4' }] },
    ]);
    // d5 is x 3, y 3, with White at the bottom
    expect(html).toContain('<rect x="3" y="3" width="1" height="1" fill="#882020" opacity="0.55"/>');
    expect(html).toContain('<line x1="4.5" y1="6.5"');
    expect(html).toContain('<polygon points="4.5,4.5');
  });

  it('are put where the comment asks for them, between the text around it', () => {
    const html = commentWithDiagramsHtml(
      'Now [#] White wins. [#]',
      (text) => `<span>${text}</span>`,
      () => '<div>D</div>'
    );
    expect(html).toBe('<span>Now</span><div>D</div><span>White wins.</span><div>D</div>');
    expect(commentWithDiagramsHtml('No diagram', (t) => t, () => 'D')).toBe('No diagram');
  });

  it('are asked for by the diagram symbol of ChessBase too, but not by a ž in a word', () => {
    const span = (text: string) => `<${text}>`;
    expect(commentWithDiagramsHtml('ž Our start position', span, () => 'D')).toBe('D<Our start position>');
    expect(commentWithDiagramsHtml('\uE005 The start position', span, () => 'D')).toBe('D<The start position>');
    expect(commentWithDiagramsHtml('Mate.\r\nž\r\nBy all standards', span, () => 'D')).toBe(
      '<Mate.>D<By all standards>'
    );
    expect(commentWithDiagramsHtml('Myslím, že je to dobré', span, () => 'D')).toBe('<Myslím, že je to dobré>');
  });

  it('of the pawn structure have only the pawns, in a popup', () => {
    const html = pawnStructureHtml(START);
    expect(html.startsWith('<span class="cbpopupboard cbpawnstructure"')).toBe(true);
    expect(html).toContain('<span class="cbpopupboard-popup"');
    expect(html.match(/<image /g)).toHaveLength(16);
    expect(html).not.toMatch(/wK|bK|wQ/);
  });

  it('of a piece path have only the piece, and arrows of its moves since the start', () => {
    const node = lastPosition('1.Nf3 d5 2.Nd4 e5 3.Nb5 c6');
    expect(piecePath(node, 'b5').map((a) => a.from + a.to)).toEqual(['g1f3', 'f3d4', 'd4b5']);
    const html = piecePathHtml(node, 'b5');
    expect(html.startsWith('<span class="cbpopupboard cbpiecepath"')).toBe(true);
    expect(html).toContain('♘');
    expect(html.match(/<image /g)).toHaveLength(1);
    expect(html.match(/<polygon /g)).toHaveLength(3);
    // Alternating in color, each fading in
    expect(html.match(/<polygon [^>]*fill="(#\w+)"/g)?.map((p) => p.slice(-8, -1))).toEqual([
      '#c41e1e',
      '#1a1a1a',
      '#c41e1e',
    ]);
    expect(html.match(/<linearGradient /g)).toHaveLength(3);
  });

  it('of a piece path follow a rook through castling, and a piece captured on its square no further', () => {
    const rook = lastPosition('1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5 4.O-O Nf6 5.Re1 O-O');
    expect(piecePath(rook, 'e1').map((a) => a.from + a.to)).toEqual(['h1f1', 'f1e1']);
    const knight = lastPosition('1.e4 d5 2.exd5 Nf6 3.Nc3 Nxd5 4.Nxd5');
    expect(piecePath(knight, 'd5').map((a) => a.from + a.to)).toEqual(['b1c3', 'c3d5']);
    expect(piecePathHtml(knight, 'e4')).toBe('');
  });
});
