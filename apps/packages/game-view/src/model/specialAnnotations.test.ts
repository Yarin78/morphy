import { describe, expect, it } from 'vitest';
import type { Annotation } from './annotations';
import { criticalPhase, toggleCritical, togglePawnStructure, togglePiecePath } from './specialAnnotations';

describe('special annotations', () => {
  const text: Annotation = { type: 'textAfter', text: 'Good' };

  it('mark a critical position in one phase at a time, and unmark it again', () => {
    const opening = toggleCritical([text], 'opening');
    expect(criticalPhase(opening)).toBe('opening');
    const endgame = toggleCritical(opening, 'endgame');
    expect(endgame).toEqual([text, { type: 'critical', phase: 'endgame' }]);
    expect(toggleCritical(endgame, 'endgame')).toEqual([text]);
  });

  it('show the pawn structure and the path of a piece, and stop showing them', () => {
    const shown = togglePiecePath(togglePawnStructure([text]), 'e4');
    expect(shown).toEqual([
      text,
      { type: 'pawnStructure', pawnStructureType: 3 },
      { type: 'piecePath', pathType: 3, square: 'e4' },
    ]);
    expect(togglePiecePath(togglePawnStructure(shown), 'e4')).toEqual([text]);
  });
});
