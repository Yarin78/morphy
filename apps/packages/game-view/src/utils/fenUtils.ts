import type { Square } from '@jackstenglein/chess';

/**
 * Creates a preview FEN string with a piece moved from one square to another.
 * This is useful for showing a visual preview before a move is committed.
 *
 * @param currentFen - The current FEN string
 * @param from - The source square (e.g., 'e7')
 * @param to - The destination square (e.g., 'e8')
 * @returns A new FEN string with the piece moved
 */
export function createMovedPieceFen(currentFen: string, from: Square, to: Square): string {
  const fenParts = currentFen.split(' ');
  const board = fenParts[0];

  // Convert board to 2D array for manipulation
  const ranks = board.split('/');
  const boardArray: (string | null)[][] = ranks.map(rank => {
    const row: (string | null)[] = [];
    for (const char of rank) {
      if (char >= '1' && char <= '8') {
        // Empty squares
        for (let i = 0; i < parseInt(char); i++) {
          row.push(null);
        }
      } else {
        row.push(char);
      }
    }
    return row;
  });

  // Convert square notation to board indices
  const fromFile = from.charCodeAt(0) - 'a'.charCodeAt(0);
  const fromRank = 8 - parseInt(from[1]);
  const toFile = to.charCodeAt(0) - 'a'.charCodeAt(0);
  const toRank = 8 - parseInt(to[1]);

  // Move the piece
  const piece = boardArray[fromRank][fromFile];
  boardArray[fromRank][fromFile] = null;
  boardArray[toRank][toFile] = piece;

  // Convert back to FEN notation
  const newRanks = boardArray.map(rank => {
    let rankStr = '';
    let emptyCount = 0;
    for (const square of rank) {
      if (square === null) {
        emptyCount++;
      } else {
        if (emptyCount > 0) {
          rankStr += emptyCount;
          emptyCount = 0;
        }
        rankStr += square;
      }
    }
    if (emptyCount > 0) {
      rankStr += emptyCount;
    }
    return rankStr;
  });

  fenParts[0] = newRanks.join('/');
  return fenParts.join(' ');
}
