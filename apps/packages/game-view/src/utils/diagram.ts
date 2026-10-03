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
import type { Annotation, ColoredSquare } from '../model/annotations';
import { findAnnotation } from '../model/annotations';
import type { GameNode, MoveNode } from '../model/GameTree';
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

type BoardPiece = NonNullable<ReturnType<Chess['board']>[number][number]>;

/** A move from one square to another. */
export interface SquareMove {
  from: string;
  to: string;
}

/** An arrow to draw: its color as '#rrggbb', its width in squares, and whether it fades in. */
interface Arrow extends SquareMove {
  color: string;
  width: number;
  fadeIn?: boolean;
}

// For ids unique in the page, of the gradients of arrows that fade in
let gradientCount = 0;

/** The pieces of a position by square, or null if the FEN can't be read. */
function readBoard(fen: string): ReturnType<Chess['board']> | null {
  try {
    return new Chess(fen, { skipValidation: true }).board();
  } catch {
    return null;
  }
}

/**
 * A board of a position as an SVG.
 *
 * @param board the pieces of the position
 * @param squares the colored squares to draw
 * @param arrows the arrows to draw, over the pieces, each over those before it
 * @param include whether to draw a piece; all are drawn if not given
 */
function boardSvg(
  board: ReturnType<Chess['board']>,
  squares: readonly ColoredSquare[],
  arrows: readonly Arrow[],
  include: (piece: BoardPiece, square: string) => boolean = () => true
): string {
  const parts: string[] = [`<rect width="8" height="8" fill="${DARK}"/>`];
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      if ((x + y) % 2 === 0) parts.push(`<rect x="${x}" y="${y}" width="1" height="1" fill="${LIGHT}"/>`);
    }
  }

  for (const { color, square } of squares) {
    if (!isSquare(square)) continue;
    const [x, y] = squareXY(square);
    parts.push(`<rect x="${x}" y="${y}" width="1" height="1" fill="${brushColor(color)}" opacity="0.55"/>`);
  }

  board.forEach((row, y) =>
    row.forEach((piece, x) => {
      if (piece && include(piece, piece.square)) {
        const href = PIECES[piece.color + piece.type.toUpperCase()];
        parts.push(`<image href="${href}" x="${x}" y="${y}" width="1" height="1"/>`);
      }
    })
  );

  for (const { color, width, fadeIn, from, to } of arrows) {
    if (!isSquare(from) || !isSquare(to) || from === to) continue;
    const [x1, y1] = squareXY(from).map((c) => c + 0.5);
    const [x2, y2] = squareXY(to).map((c) => c + 0.5);
    // Stop the line short of the target square's center, where the arrow head goes
    const length = Math.hypot(x2 - x1, y2 - y1);
    const head = width * 2;
    const [ex, ey] = [x2 - ((x2 - x1) / length) * head, y2 - ((y2 - y1) / length) * head];
    const [ux, uy] = [(x2 - x1) / length, (y2 - y1) / length];
    const [px, py] = [-uy * width * 1.25, ux * width * 1.25];
    let stroke = color;
    if (fadeIn) {
      // From faint at the start to full at the head
      const id = `cbarrowfade${++gradientCount}`;
      parts.push(
        `<defs><linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${x1}" y1="${y1}" x2="${ex}" y2="${ey}">` +
          `<stop offset="0" stop-color="${color}" stop-opacity="0.25"/><stop offset="1" stop-color="${color}"/>` +
          `</linearGradient></defs>`
      );
      stroke = `url(#${id})`;
    }
    parts.push(
      `<g opacity="0.8"><line x1="${x1}" y1="${y1}" x2="${ex}" y2="${ey}" stroke="${stroke}" stroke-width="${width}" stroke-linecap="round"/>` +
        `<polygon points="${x2},${y2} ${ex + px},${ey + py} ${ex - px},${ey - py}" fill="${color}"/></g>`
    );
  }

  return `<svg viewBox="0 0 8 8" xmlns="http://www.w3.org/2000/svg">${parts.join('')}</svg>`;
}

/**
 * A diagram of a position as HTML: a block with the board.
 *
 * @param fen the position
 * @param annotations the annotations of the position, whose colored squares and arrows are drawn
 */
export function diagramHtml(fen: string, annotations: readonly Annotation[]): string {
  const board = readBoard(fen);
  if (!board) return '';
  const squares = findAnnotation(annotations, 'squares')?.squares ?? [];
  const arrows = (findAnnotation(annotations, 'arrows')?.arrows ?? []).map((a) => ({
    from: a.from,
    to: a.to,
    color: brushColor(a.color),
    width: 0.16,
  }));
  return `<div class="cbdiagram" role="img" aria-label="Diagram">${boardSvg(board, squares, arrows)}</div>`;
}

/**
 * A symbol after the move with a board that pops up while it's hovered over.
 *
 * @param kind the class of this kind of popup board
 * @param symbol the symbol
 * @param label what the board shows
 * @param svg the board
 */
function popupBoardHtml(kind: string, symbol: string, label: string, svg: string): string {
  return (
    `<span class="cbpopupboard ${kind}" tabindex="0" aria-label="${label}">${symbol}` +
    `<span class="cbpopupboard-popup" role="img" aria-label="${label}">${svg}</span></span>`
  );
}

/**
 * The pawn structure of a position, as ChessBase shows it for a pawn structure annotation: two
 * pawns after the move, which shows a diagram of only the pawns while it's hovered over.
 *
 * @param fen the position
 */
export function pawnStructureHtml(fen: string): string {
  const board = readBoard(fen);
  if (!board) return '';
  const svg = boardSvg(board, [], [], (piece) => piece.type === 'p');
  // A black and a white pawn, overlapping, to tell it from the path of a pawn
  const symbol = '<span class="cbpawnstructure-black">♟</span><span class="cbpawnstructure-white">♟</span>';
  return popupBoardHtml('cbpawnstructure', symbol, 'Pawn structure', svg);
}

// The colors the arrows of a piece path alternate between, as in ChessBase, to make it easier to
// follow, and their width in squares
const PIECE_PATH_COLORS = ['#c41e1e', '#1a1a1a'];
const PIECE_PATH_WIDTH = 0.1;

const PIECE_SYMBOLS: Record<string, string> = {
  wk: '♔', wq: '♕', wr: '♖', wb: '♗', wn: '♘', wp: '♙',
  bk: '♚', bq: '♛', br: '♜', bb: '♝', bn: '♞', bp: '♟',
};

function isMove(node: GameNode): node is MoveNode {
  return 'parent' in node;
}

/**
 * The moves a piece has made since the start of the game, from the first: following the moves
 * leading to the position back, from the square the piece is on. A castling counts as a move of
 * the rook too, and a promoted piece's path goes back through the pawn's.
 *
 * @param node the position
 * @param square the square of the piece
 */
export function piecePath(node: GameNode, square: string): SquareMove[] {
  const moves: SquareMove[] = [];
  let current = square;
  for (let n: GameNode = node; isMove(n); n = n.parent) {
    if (n.isNullMove) continue;
    let [from, to] = [n.from as string, n.to as string];
    const rank = from[1];
    if (n.san.startsWith('O-O') && current !== to) {
      // The rook of a castling
      [from, to] = n.san.startsWith('O-O-O') ? [`a${rank}`, `d${rank}`] : [`h${rank}`, `f${rank}`];
    }
    if (to === current) {
      moves.push({ from, to });
      current = from;
    }
  }
  return moves.reverse();
}

/**
 * The path of a piece, as ChessBase shows it for a piece path annotation: a symbol of the piece
 * after the move, which shows a board with only that piece, and arrows of the moves it has made
 * since the start of the game, while it's hovered over. The arrows alternate in color, and each
 * fades in from its start.
 *
 * @param node the position after the move
 * @param square the square of the piece
 */
export function piecePathHtml(node: GameNode, square: string): string {
  const board = readBoard(node.fen);
  const piece = board?.flat().find((p) => p?.square === square);
  if (!board || !piece) return '';
  const arrows = piecePath(node, square).map((move, i) => ({
    ...move,
    color: PIECE_PATH_COLORS[i % PIECE_PATH_COLORS.length],
    width: PIECE_PATH_WIDTH,
    fadeIn: true,
  }));
  const svg = boardSvg(board, [], arrows, (p) => p.square === square);
  return popupBoardHtml('cbpiecepath', PIECE_SYMBOLS[piece.color + piece.type], 'Piece path', svg);
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
