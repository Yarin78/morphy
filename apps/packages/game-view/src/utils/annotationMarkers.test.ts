import { describe, expect, it } from 'vitest';
import type { Annotation } from '../model/annotations';
import { annotationMarkers, annotationMarkersHtml } from './annotationMarkers';

const hasGlyph = (nag: number) => nag <= 6;

describe('annotation markers', () => {
  it('leave out what the notation shows', () => {
    const shown: Annotation[] = [
      { type: 'textAfter', text: 'plain' },
      { type: 'textBefore', text: 'english', language: 'ENG' },
      { type: 'symbols', nags: [1, 5] },
      { type: 'squares', squares: [{ color: 'red', square: 'd5' }] },
      { type: 'arrows', arrows: [{ color: 'green', from: 'e2', to: 'e4' }] },
    ];
    expect(annotationMarkers(shown, hasGlyph)).toEqual([]);
  });

  it('name the kind of the others, with their data as details', () => {
    const markers = annotationMarkers(
      [
        { type: 'whiteClock', centiseconds: 543210 },
        { type: 'eval', eval: 7, evalType: 3, depth: 0 },
        { type: 'medals', medals: ['NOVELTY', 'TACTICS'] },
      ],
      hasGlyph
    );
    // Clocks, known evaluations and medals are shown after the move instead
    expect(markers.map((m) => m.label)).toEqual(['eval']);
    expect(markers[0].details).toBe('eval\neval: 7\nevalType: 3\ndepth: 0');
  });

  it('mark symbols without a glyph, but not text in any language', () => {
    const markers = annotationMarkers(
      [
        { type: 'textAfter', text: 'Gut', language: 'GER' },
        { type: 'symbols', nags: [1, 146] },
      ],
      hasGlyph
    );
    expect(markers.map((m) => m.label)).toEqual(['$146']);
  });

  it('cut binary data short', () => {
    const [marker] = annotationMarkers(
      [{ type: 'raw', annotationType: 26, data: 'A'.repeat(100), invalid: false }],
      hasGlyph
    );
    expect(marker.details).toContain('data: 75 bytes, base64 ' + 'A'.repeat(48) + '…');
  });

  it('escape the details for HTML', () => {
    const html = annotationMarkersHtml([{ label: 'link', details: 'url: "a<b>&c"' }]);
    expect(html).toBe('<span class="cbanno-marker" title="url: &quot;a&lt;b&gt;&amp;c&quot;">link</span>');
  });
});
