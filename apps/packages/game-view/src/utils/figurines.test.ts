import { describe, expect, it } from 'vitest';
import { figurinesToHtml, figurinesToUnicode } from './figurines';

describe('figurines', () => {
  it('are shown as chess pieces in older text', () => {
    expect(figurinesToUnicode('17.¤xf2 ¤xf2, 18.¢g1 £e4 ¥e6 ¦ad1 le § isolé')).toBe(
      '17.♘xf2 ♘xf2, 18.♔g1 ♕e4 ♗e6 ♖ad1 le ♙ isolé'
    );
  });

  it('are shown as chess pieces in UTF-8 text', () => {
    expect(figurinesToUnicode('7.f1 9.a4+ ag8 xe6 ce7 c3')).toBe(
      '7.♔f1 9.♕a4+ ♖ag8 ♗xe6 ♘ce7 ♙c3'
    );
  });

  it('get a span of their own in HTML, leaving the rest as it is', () => {
    expect(figurinesToHtml('&lt;b&gt; 18.¢g1')).toBe('&lt;b&gt; 18.<span class="cbfigurine">♔</span>g1');
  });
});
