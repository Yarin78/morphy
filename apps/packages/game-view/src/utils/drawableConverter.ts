import type { Move } from '@jackstenglein/chess';

// DrawShape type matching Chessground's autoShapes format
// Note: This matches the DrawShape interface in react-chessground.d.ts
// We keep it separate here since module declarations can't export types for import
type Square = string;
type Color = 'white' | 'black';

interface DrawShape {
  orig: Square;
  dest?: Square;
  brush?: string;
  label?: { text: string };
  modifiers?: {
    hilite?: boolean;
    lineWidth?: number;
    [key: string]: any;
  };
  piece?: {
    role: 'king' | 'queen' | 'rook' | 'bishop' | 'knight' | 'pawn';
    color: Color;
    scale?: number;
  };
  customSvg?: string;
}

export interface PGNDrawables {
  colorArrows: string[];
  colorFields: string[];
}

/**
 * Color code mapping between PGN annotation format and Chessground brush colors
 * PGN uses single letter codes: R=Red, G=Green, Y=Yellow, B=Blue
 */
const COLOR_CODE_MAP: Record<string, string> = {
  R: 'red',
  G: 'green',
  Y: 'yellow',
  B: 'blue',
};

const BRUSH_TO_PGN_MAP: Record<string, string> = {
  'red': 'R',
  'green': 'G',
  'yellow': 'Y',
  'blue': 'B',
};

// =============================================================================
// PGN to Chessground (for displaying stored annotations)
// =============================================================================

/**
 * Converts colorFields (colored squares) from chess move to Chessground DrawShape format
 * @param colorFields Array of strings in format "Rsquare" where R is color code and square is like "d5"
 * @returns Array of DrawShape objects for squares
 */
function convertColorFields(colorFields: string[]): DrawShape[] {
  const shapes: DrawShape[] = [];

  for (const field of colorFields) {
    if (field.length < 2) {
      console.warn(`Invalid colorField format: ${field}`);
      continue;
    }

    const colorCode = field[0];
    const square = field.slice(1);

    // Validate square format (should be 2 characters like "d5")
    if (square.length !== 2) {
      console.warn(`Invalid square format in colorField: ${field}`);
      continue;
    }

    const brush = COLOR_CODE_MAP[colorCode] || 'green'; // Default to green if unknown

    shapes.push({
      orig: square,
      brush,
      modifiers: {
        hilite: true,
      },
    });
  }

  return shapes;
}

/**
 * Converts colorArrows (colored arrows) from chess move to Chessground DrawShape format
 * @param colorArrows Array of strings in format "Gfromto" where G is color code, from is like "f2", to is like "f3"
 * @returns Array of DrawShape objects for arrows
 */
function convertColorArrows(colorArrows: string[]): DrawShape[] {
  const shapes: DrawShape[] = [];

  for (const arrow of colorArrows) {
    if (arrow.length < 5) {
      console.warn(`Invalid colorArrow format: ${arrow}`);
      continue;
    }

    const colorCode = arrow[0];
    const from = arrow.slice(1, 3);
    const to = arrow.slice(3, 5);

    // Validate square formats
    if (from.length !== 2 || to.length !== 2) {
      console.warn(`Invalid square format in colorArrow: ${arrow}`);
      continue;
    }

    const brush = COLOR_CODE_MAP[colorCode] || 'green'; // Default to green if unknown

    shapes.push({
      orig: from,
      dest: to,
      brush,
    });
  }

  return shapes;
}

/**
 * Converts drawables from a chess move (colorFields and colorArrows) to Chessground autoShapes format
 * @param move The chess move object that may contain commentDiag with colorFields and/or colorArrows
 * @returns Array of DrawShape objects ready for Chessground's autoShapes property
 */
export function convertMoveDrawablesToAutoShapes(move: Move | null): DrawShape[] {
  if (!move || !move.commentDiag) {
    return [];
  }

  const shapes: DrawShape[] = [];
  const { colorFields, colorArrows } = move.commentDiag;

  // Convert colored squares (fields)
  if (colorFields && Array.isArray(colorFields) && colorFields.length > 0) {
    shapes.push(...convertColorFields(colorFields));
  }

  // Convert colored arrows
  if (colorArrows && Array.isArray(colorArrows) && colorArrows.length > 0) {
    shapes.push(...convertColorArrows(colorArrows));
  }

  return shapes;
}

// =============================================================================
// Chessground to PGN (for saving user drawings)
// =============================================================================

/**
 * Converts Chessground DrawShape objects to PGN annotation format
 * @param shapes Array of DrawShape objects from Chessground
 * @returns Object containing colorArrows and colorFields arrays in PGN format
 *
 * @example
 * const shapes = [
 *   { orig: 'd5', brush: 'red' },           // Highlighted square
 *   { orig: 'f2', dest: 'f3', brush: 'green' }  // Arrow
 * ];
 * const result = convertShapesToPGN(shapes);
 * // result = { colorArrows: ['Gf2f3'], colorFields: ['Rd5'] }
 */
export function convertShapesToPGN(shapes: DrawShape[]): PGNDrawables {
  const colorArrows: string[] = [];
  const colorFields: string[] = [];

  shapes.forEach(shape => {
    const brush = shape.brush || 'green';
    const pgnColor = BRUSH_TO_PGN_MAP[brush] || 'G';

    if (shape.dest) {
      // It's an arrow: format = "ColorCodeFromSquareToSquare" (e.g., "Gf2f3")
      colorArrows.push(`${pgnColor}${shape.orig}${shape.dest}`);
    } else {
      // It's a highlighted square: format = "ColorCodeSquare" (e.g., "Rd5")
      colorFields.push(`${pgnColor}${shape.orig}`);
    }
  });

  return { colorArrows, colorFields };
}
