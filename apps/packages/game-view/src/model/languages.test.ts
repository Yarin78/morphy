import { describe, expect, it } from 'vitest';
import { GameTree } from './GameTree';
import { commentLanguages, defaultLanguage, languageName } from './languages';

describe('comment languages', () => {
  const game = GameTree.fromMoves({
    pgn: '1. e4 e5 2. Nf3',
    annotations: [
      { move: -1, type: 'textAfter', text: 'Intro' },
      { move: 0, type: 'textAfter', text: 'Bon', language: 'FRA' },
      { move: 1, type: 'textBefore', text: 'Gut', language: 'GER' },
      { move: 2, type: 'textAfter', text: 'Xyz', language: 'XYZ' },
      { move: 2, type: 'textAfter', text: 'Good', language: 'ENG' },
    ],
  });

  it('are those of the comments in a language, in the order they are preferred', () => {
    expect(commentLanguages(game)).toEqual(['ENG', 'GER', 'FRA', 'XYZ']);
  });

  it('show only the preferred one by default', () => {
    expect(defaultLanguage(commentLanguages(game))).toBe('ENG');
    expect(defaultLanguage(['GER', 'FRA'])).toBe('GER');
    expect(defaultLanguage([])).toBeNull();
  });

  it('have names, or else their codes', () => {
    expect(languageName('GER')).toBe('German');
    expect(languageName('XYZ')).toBe('XYZ');
  });
});
