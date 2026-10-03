import { Chess } from 'chess.js';
import wK from 'react-chessground/dist/images/pieces/merida/wK.svg';
import wQ from 'react-chessground/dist/images/pieces/merida/wQ.svg';
import wR from 'react-chessground/dist/images/pieces/merida/wR.svg';
import wB from 'react-chessground/dist/images/pieces/merida/wB.svg';
import wN from 'react-chessground/dist/images/pieces/merida/wN.svg';
import wP from 'react-chessground/dist/images/pieces/merida/wP.svg';
import bK from 'react-chessground/dist/images/pieces/merida/bK.svg';
import bQ from 'react-chessground/dist/images/pieces/merida/bQ.svg';
import bR from 'react-chessground/dist/images/pieces/merida/bR.svg';
import bB from 'react-chessground/dist/images/pieces/merida/bB.svg';
import bN from 'react-chessground/dist/images/pieces/merida/bN.svg';
import bP from 'react-chessground/dist/images/pieces/merida/bP.svg';
import type { Annotation } from '../model/annotations';
import { findAnnotation } from '../model/annotations';
import { ANNOTATION_BRUSHES } from './drawableConverter';

/**
 * Diagrams in the notation: a picture of the position, as ChessBase puts one where a comment has
 * "[#]". It has the board and pieces of the board beside the notation, and the colored squares and
 * arrows of the position, but nothing showing the last move. White is at the bottom. It's a block
 * of its own, indented like the variation it's in.
 */

/**
 * Where a comment asks for a diagram: "[#]", or the symbol ChessBase writes for one, which is the
 * private-use character U+E005 in newer comments and the cp1252 byte 9E, read as "ž", in older
 * ones. A "ž" counts only standing on its own, as it's a letter in some languages.
 */
const DIAGRAM_MARKER = /\[#\]|\uE005|(?<=^|\s)ž(?=\s|$)/;

const PIECES: Record<string, string> = { wK, wQ, wR, wB, wN, wP, bK, bQ, bR, bB, bN, bP };

// The colors of the board beside the notation (react-chessground's brown board)
const LIGHT = '#f0d9b5';
const DARK = '#b58863';

/** The x and y of a square's top left corner, with White at the bottom. */
function squareXY(square: string): [number, number] {
  return [square.charCodeAt(0) - 97, 8 - Number(square[1])];
}

function isSquare(square: string): boolean {
  return /^[a-h][1-8]$/.test(square);
}

function brushColor(color: string): string {
  return (ANNOTATION_BRUSHES as Record<string, { color: string }>)[color]?.color ?? ANNOTATION_BRUSHES.green.color;
}

/**
 * A diagram of a position as HTML: a block with the board.
 *
 * @param fen the position
 * @param annotations the annotations of the position, whose colored squares and arrows are drawn
 */
export function diagramHtml(fen: string, annotations: readonly Annotation[]): string {
  let board: ReturnType<Chess['board']>;
  try {
    board = new Chess(fen, { skipValidation: true }).board();
  } catch {
    return '';
  }

  const parts: string[] = [`<rect width="8" height="8" fill="${DARK}"/>`];
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      if ((x + y) % 2 === 0) parts.push(`<rect x="${x}" y="${y}" width="1" height="1" fill="${LIGHT}"/>`);
    }
  }

  for (const { color, square } of findAnnotation(annotations, 'squares')?.squares ?? []) {
    if (!isSquare(square)) continue;
    const [x, y] = squareXY(square);
    parts.push(`<rect x="${x}" y="${y}" width="1" height="1" fill="${brushColor(color)}" opacity="0.55"/>`);
  }

  board.forEach((row, y) =>
    row.forEach((piece, x) => {
      if (piece) {
        const href = PIECES[piece.color + piece.type.toUpperCase()];
        parts.push(`<image href="${href}" x="${x}" y="${y}" width="1" height="1"/>`);
      }
    })
  );

  const arrows = (findAnnotation(annotations, 'arrows')?.arrows ?? []).filter(
    (a) => isSquare(a.from) && isSquare(a.to) && a.from !== a.to
  );
  for (const { color, from, to } of arrows) {
    const [x1, y1] = squareXY(from).map((c) => c + 0.5);
    const [x2, y2] = squareXY(to).map((c) => c + 0.5);
    // Stop the line short of the target square's center, where the arrow head goes
    const length = Math.hypot(x2 - x1, y2 - y1);
    const head = 0.32;
    const [ex, ey] = [x2 - ((x2 - x1) / length) * head, y2 - ((y2 - y1) / length) * head];
    const [ux, uy] = [(x2 - x1) / length, (y2 - y1) / length];
    const [px, py] = [-uy * 0.2, ux * 0.2];
    const fill = brushColor(color);
    parts.push(
      `<g opacity="0.8"><line x1="${x1}" y1="${y1}" x2="${ex}" y2="${ey}" stroke="${fill}" stroke-width="0.16" stroke-linecap="round"/>` +
        `<polygon points="${x2},${y2} ${ex + px},${ey + py} ${ex - px},${ey - py}" fill="${fill}"/></g>`
    );
  }

  return (
    `<div class="cbdiagram" role="img" aria-label="Diagram">` +
    `<svg viewBox="0 0 8 8" xmlns="http://www.w3.org/2000/svg">${parts.join('')}</svg></div>`
  );
}

/**
 * A comment as HTML, with a diagram wherever it asks for one, the text around it in a comment
 * span of its own.
 *
 * @param text the comment
 * @param commentSpan the HTML of a span of comment text
 * @param diagram the HTML of the diagram
 */
export function commentWithDiagramsHtml(
  text: string,
  commentSpan: (text: string) => string,
  diagram: () => string
): string {
  const pieces = text.split(new RegExp(DIAGRAM_MARKER, 'g'));
  return pieces
    .map((piece, i) => {
      const trimmed = piece.trim();
      return (trimmed ? commentSpan(trimmed) : '') + (i < pieces.length - 1 ? diagram() : '');
    })
    .join('');
}
