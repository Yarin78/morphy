import { describe, expect, it } from 'vitest';
import { commentWithDiagramsHtml, diagramHtml } from './diagram';

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
});
