import { type RefObject, useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { DrawShape } from '../utils/drawableConverter';

/**
 * Guesses which piece is meant to go to a square: given the position, the square, and the squares
 * of the pieces that can go there, the square of the one meant, or null if it can't tell.
 */
export type MoveGuesser = (fen: string, to: string, froms: string[]) => Promise<string | null>;

/** The brush of the circle around the piece that's to move. */
export const GUESS_BRUSHES = {
  moveGuess: { key: 'mg', color: '#15781B', opacity: 0.8, lineWidth: 10 },
};

const FILES = 'abcdefgh';
// Without a guess, the least valuable piece is the one meant
const PIECE_ORDER = 'pnbrqk';

interface Options {
  /** Whether moves can be made this way, as when editing */
  enabled: boolean;
  /** The element the board is in */
  containerRef: RefObject<HTMLElement | null>;
  /** Chessground's own API, to see and take away the piece selected */
  chessground: () => { state: { selected?: string }; selectSquare(key: string | null): void } | undefined;
  orientation: 'white' | 'black';
  fen: string;
  /** The squares each piece that can move can go to, as chessground has them */
  legalMoves: Map<string, string[]>;
  /** The type of the piece on a square ('p', 'n', ...), for a guess without the guesser */
  pieceType: (square: string) => string | undefined;
  guess?: MoveGuesser;
  /** Makes the move */
  onMove: (from: string, to: string) => void;
}

// A move being made, square it goes to first
interface Gesture {
  to: string;
  /** The squares of the pieces that can go there */
  froms: string[];
  /** The piece guessed, once it's known */
  guessed: string | null;
  guessing: Promise<string>;
  /** The piece pointed at, which goes instead of the one guessed */
  pointed: string | null;
  /** Whether the pointer is off the board, where letting go makes no move */
  outside: boolean;
  fen: string;
}

/** The square at a point on the board, or null off it. */
function squareAt(board: Element, x: number, y: number, orientation: 'white' | 'black'): string | null {
  const r = board.getBoundingClientRect();
  const file = Math.floor(((x - r.left) / r.width) * 8);
  const row = Math.floor(((y - r.top) / r.height) * 8);
  if (file < 0 || file > 7 || row < 0 || row > 7) return null;
  return orientation === 'white' ? `${FILES[file]}${8 - row}` : `${FILES[7 - file]}${row + 1}`;
}

function shapesOf(g: Gesture | null): DrawShape[] {
  if (!g || g.outside) return [];
  const from = g.pointed ?? g.guessed;
  return from ? [{ orig: from, brush: 'moveGuess' }] : [];
}

/**
 * Moves made square first: pressing the mouse on a square no piece of the side to move is on, but
 * that one can go to, circles the piece guessed to be meant, and letting
 * go makes the move. Pointing at another of the pieces that can go there, while the button is
 * held, moves that one instead; letting go off the board, or Escape, makes no move. Pressing on a
 * piece of the side to move, or on a square the piece selected can go to, is left to chessground,
 * as moves made the usual way.
 *
 * Returns the shapes to draw on the board while a move is being made.
 */
export function useDestinationMoves(options: Options): DrawShape[] {
  const optionsRef = useRef(options);
  useLayoutEffect(() => {
    optionsRef.current = options;
  });
  const [shapes, setShapes] = useState<DrawShape[]>([]);
  const { enabled, containerRef } = options;

  useEffect(() => {
    const container = containerRef.current;
    if (!container || !enabled) return;
    let gesture: Gesture | null = null;
    const show = () => setShapes(shapesOf(gesture));
    const board = () => container.querySelector('cg-board');

    const end = () => {
      window.removeEventListener('mousemove', onMove, true);
      window.removeEventListener('mouseup', onUp, true);
      window.removeEventListener('keydown', onKey, true);
      gesture = null;
      show();
    };

    const onMove = (e: MouseEvent) => {
      const g = gesture;
      const cgBoard = board();
      if (!g || !cgBoard) return;
      const square = squareAt(cgBoard, e.clientX, e.clientY, optionsRef.current.orientation);
      g.outside = square === null;
      if (square && g.froms.includes(square)) g.pointed = square;
      // Back on the square it goes to: the piece guessed again
      else if (square === g.to) g.pointed = null;
      show();
    };

    const onUp = (e: MouseEvent) => {
      if (e.button !== 0) return;
      const g = gesture;
      end();
      if (!g || g.outside) return;
      const play = (from: string) => {
        // Not if the game moved on meanwhile
        const { fen, onMove } = optionsRef.current;
        if (fen === g.fen) onMove(from, g.to);
      };
      const from = g.pointed ?? g.guessed;
      if (from) play(from);
      else void g.guessing.then(play);
    };

    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      e.preventDefault();
      e.stopPropagation();
      end();
    };

    const onDown = (e: MouseEvent) => {
      if (e.button !== 0 || e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
      const cgBoard = board();
      if (!cgBoard || !(e.target instanceof Node) || !cgBoard.contains(e.target)) return;
      const { orientation, legalMoves, fen, chessground, pieceType, guess } = optionsRef.current;
      const to = squareAt(cgBoard, e.clientX, e.clientY, orientation);
      // A piece of the side to move is chessground's to move
      if (!to || legalMoves.has(to)) return;
      // As is a square the piece selected can go to
      const cg = chessground();
      const selected = cg?.state.selected;
      if (selected && legalMoves.get(selected)?.includes(to)) return;
      const froms = [...legalMoves].filter(([, dests]) => dests.includes(to)).map(([from]) => from);
      if (froms.length === 0) return;

      e.preventDefault();
      e.stopPropagation();
      cg?.selectSquare(null);
      const fallback = () =>
        [...froms].sort(
          (a, b) => PIECE_ORDER.indexOf(pieceType(a) ?? 'k') - PIECE_ORDER.indexOf(pieceType(b) ?? 'k')
        )[0];
      const g: Gesture = {
        to,
        froms,
        guessed: froms.length === 1 ? froms[0] : null,
        guessing: Promise.resolve(froms[0]),
        pointed: null,
        outside: false,
        fen,
      };
      if (!g.guessed) {
        g.guessing = (guess ? guess(fen, to, froms) : Promise.resolve(null))
          .catch(() => null)
          .then((from) => from ?? fallback())
          .then((from) => {
            g.guessed = from;
            if (gesture === g) show();
            return from;
          });
      }
      gesture = g;
      show();
      window.addEventListener('mousemove', onMove, true);
      window.addEventListener('mouseup', onUp, true);
      window.addEventListener('keydown', onKey, true);
    };

    container.addEventListener('mousedown', onDown, { capture: true });
    return () => {
      container.removeEventListener('mousedown', onDown, { capture: true });
      if (gesture) end();
    };
  }, [enabled, containerRef]);

  return shapes;
}
