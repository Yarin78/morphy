import { describe, expect, it } from 'vitest';
import type { Annotation } from './annotations';
import { editedComment, hasDiagramComment, toggleDiagram, withComment } from './comments';

describe('editing comments', () => {
  const german: Annotation = { type: 'textAfter', text: 'Gut', language: 'GER' };
  const english: Annotation = { type: 'textAfter', text: 'Good', language: 'ENG' };
  const before: Annotation = { type: 'textBefore', text: 'Now' };
  const annotations = [german, english, before];

  it('edits the comment in the language', () => {
    expect(editedComment(annotations, 'textAfter', 'ENG')).toBe(english);
    expect(editedComment(annotations, 'textBefore', null)).toBe(before);
    expect(editedComment(annotations, 'textAfter', null)).toBeUndefined();
    expect(editedComment(annotations, 'textBefore', 'ENG')).toBeUndefined();
  });

  it('replaces it, or removes it when emptied', () => {
    expect(withComment(annotations, 'textAfter', 'ENG', ' Very good ')).toEqual([
      german,
      { type: 'textAfter', text: 'Very good', language: 'ENG' },
      before,
    ]);
    expect(withComment(annotations, 'textAfter', 'ENG', '  ')).toEqual([german, before]);
  });

  it('adds a comment in the language when there was none', () => {
    expect(withComment([before], 'textAfter', 'FRA', 'Bien')).toEqual([
      before,
      { type: 'textAfter', text: 'Bien', language: 'FRA' },
    ]);
    expect(withComment([before], 'textAfter', null, 'Best')).toEqual([before, { type: 'textAfter', text: 'Best' }]);
    expect(withComment([before], 'textAfter', null, '')).toEqual([before]);
  });

  it('asks for a diagram at the start of the comment after in no language, or in a new one', () => {
    expect(toggleDiagram([english])).toEqual([english, { type: 'textAfter', text: '[#]' }]);
    expect(toggleDiagram([{ type: 'textAfter', text: 'Black is lost' }, english])).toEqual([
      { type: 'textAfter', text: '[#] Black is lost' },
      english,
    ]);
  });

  it('takes the diagrams of that comment away again, and the comment if nothing else is left', () => {
    expect(toggleDiagram([{ type: 'textAfter', text: '[#] Black is lost' }])).toEqual([
      { type: 'textAfter', text: 'Black is lost' },
    ]);
    expect(toggleDiagram([{ type: 'textAfter', text: 'Mate. ž The end' }])).toEqual([
      { type: 'textAfter', text: 'Mate. The end' },
    ]);
    expect(toggleDiagram([english, { type: 'textAfter', text: '[#]' }])).toEqual([english]);
    expect(hasDiagramComment([{ type: 'textAfter', text: '[#]' }])).toBe(true);
    expect(hasDiagramComment([{ type: 'textAfter', text: '[#]', language: 'ENG' }])).toBe(false);
  });
});
