import { describe, expect, it } from 'vitest';
import { medalName, medalsHtml } from './medals';

describe('medals', () => {
  it('are a rectangle with a part for each, in the order ChessBase shows them', () => {
    const html = medalsHtml(['DEFENSE', 'NOVELTY', 'SACRIFICE']);
    expect(html).toContain('title="Medals: Novelty, Sacrifice, Defence"');
    expect(html.match(/cbmedal-part/g)).toHaveLength(3);
    expect(html.indexOf('#FE3232')).toBeLessThan(html.indexOf('#FDFDFD'));
  });

  it('are nothing when there are none', () => {
    expect(medalsHtml([])).toBe('');
  });

  it('have names, or else their own', () => {
    expect(medalName('BEST_GAME')).toBe('Best game');
    expect(medalName('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(medalsHtml(['BEST_GAME'])).toContain('title="Medal: Best game"');
  });
});
