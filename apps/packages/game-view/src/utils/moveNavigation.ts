import type { MoveNode } from '../model/GameTree';

const LINE_THRESHOLD = 5; // pixels - consider moves on same line if within this threshold

interface CandidateMove {
  element: HTMLElement;
  top: number;
  left: number;
  verticalDistance: number; // Vertical distance from current move
  horizontalDistance: number; // Horizontal distance from current move
}

/**
 * Sorts moves by visual position (top to bottom, left to right)
 */
function sortMovesByPosition(moves: HTMLElement[]): HTMLElement[] {
  return moves.sort((a, b) => {
    const rectA = a.getBoundingClientRect();
    const rectB = b.getBoundingClientRect();
    const topDiff = rectA.top - rectB.top;
    if (Math.abs(topDiff) > LINE_THRESHOLD) {
      return topDiff;
    }
    return rectA.left - rectB.left;
  });
}

/**
 * Finds all move elements in a notation. It's the game's own, not just any on the page: with
 * several games open, the others' are there too, hidden.
 */
function getAllMoveElements(notation: HTMLElement | null): HTMLElement[] {
  if (!notation) return [];
  return Array.from(notation.querySelectorAll('.cbmove')) as HTMLElement[];
}

/**
 * Finds candidate moves in a given direction relative to the current move
 */
function findCandidateMoves(
  allMoves: HTMLElement[],
  currentMoveElement: HTMLElement,
  direction: 'up' | 'down'
): CandidateMove[] {
  const currentRect = currentMoveElement.getBoundingClientRect();
  const currentTop = currentRect.top;
  const currentLeft = currentRect.left;

  const candidateMoves: CandidateMove[] = [];

  for (const move of allMoves) {
    if (move === currentMoveElement) continue;

    const rect = move.getBoundingClientRect();
    const moveTop = rect.top;
    const moveLeft = rect.left;
    const horizontalDistance = Math.abs(moveLeft - currentLeft);

    if (direction === 'down') {
      // For down: find moves that are below (have greater top value)
      if (moveTop > currentTop + LINE_THRESHOLD) {
        // Definitely on a line below
        candidateMoves.push({
          element: move,
          top: moveTop,
          left: moveLeft,
          verticalDistance: moveTop - currentTop,
          horizontalDistance,
        });
      } else if (Math.abs(moveTop - currentTop) <= LINE_THRESHOLD && moveLeft > currentLeft) {
        // On the same line but to the right
        candidateMoves.push({
          element: move,
          top: moveTop,
          left: moveLeft,
          verticalDistance: 0, // Same line
          horizontalDistance: moveLeft - currentLeft,
        });
      }
    } else {
      // For up: find moves that are above (have smaller top value)
      if (moveTop < currentTop - LINE_THRESHOLD) {
        // Definitely on a line above
        candidateMoves.push({
          element: move,
          top: moveTop,
          left: moveLeft,
          verticalDistance: currentTop - moveTop,
          horizontalDistance,
        });
      } else if (Math.abs(moveTop - currentTop) <= LINE_THRESHOLD && moveLeft < currentLeft) {
        // On the same line but to the left
        candidateMoves.push({
          element: move,
          top: moveTop,
          left: moveLeft,
          verticalDistance: 0, // Same line
          horizontalDistance: currentLeft - moveLeft,
        });
      }
    }
  }

  return candidateMoves;
}

/**
 * Sorts candidate moves, prioritizing moves on different lines over same line.
 * For moves on different lines, groups by line and prefers the one closest horizontally.
 */
function sortCandidateMoves(
  candidates: CandidateMove[],
  currentTop: number
): CandidateMove[] {
  return candidates.sort((a, b) => {
    const aIsDifferentLine = Math.abs(a.top - currentTop) > LINE_THRESHOLD;
    const bIsDifferentLine = Math.abs(b.top - currentTop) > LINE_THRESHOLD;

    // Prefer moves on different lines (vertical navigation)
    if (aIsDifferentLine && !bIsDifferentLine) return -1;
    if (!aIsDifferentLine && bIsDifferentLine) return 1;

    // If both on different lines, group by line (top position) and prefer horizontal proximity
    if (aIsDifferentLine && bIsDifferentLine) {
      // Check if a and b are on the same line as each other
      const areOnSameLine = Math.abs(a.top - b.top) <= LINE_THRESHOLD;

      if (areOnSameLine) {
        // Same target line - prefer horizontally closest
        return a.horizontalDistance - b.horizontalDistance;
      } else {
        // Different target lines - prefer the closer line (smaller vertical distance)
        return a.verticalDistance - b.verticalDistance;
      }
    }

    // Both on same line as current move - sort by horizontal distance
    return a.horizontalDistance - b.horizontalDistance;
  });
}

/**
 * Navigates to the move visually above or below the current move
 */
export function navigateToAdjacentMove(
  direction: 'up' | 'down',
  notation: HTMLElement | null,
  reverseMoveMap: Map<number, MoveNode>,
  seekToMove: (move: MoveNode | null) => void
): void {
  const allMoves = getAllMoveElements(notation);
  if (allMoves.length === 0) return;

  const currentMoveElement = allMoves.find((move) => move.classList.contains('cbcur-move')) ?? null;

  if (!currentMoveElement) {
    // If no current move is highlighted, find first/last move by vertical position
    const sortedMoves = sortMovesByPosition(allMoves);
    const targetMove = direction === 'down' ? sortedMoves[0] : sortedMoves[sortedMoves.length - 1];
    const moveIndex = targetMove.getAttribute('data-global-move-index');
    if (moveIndex) {
      const move = reverseMoveMap.get(Number(moveIndex));
      if (move) {
        seekToMove(move);
      }
    }
    return;
  }

  // Get current move's position
  const currentRect = currentMoveElement.getBoundingClientRect();
  const currentTop = currentRect.top;

  // Find candidate moves
  const candidateMoves = findCandidateMoves(allMoves, currentMoveElement, direction);
  if (candidateMoves.length === 0) return; // No moves in that direction

  // Sort candidates: prioritize different lines over same line, then by distance
  const sortedCandidates = sortCandidateMoves(candidateMoves, currentTop);

  // Navigate to the closest candidate
  const targetMove = sortedCandidates[0].element;
  const moveIndex = targetMove.getAttribute('data-global-move-index');
  if (moveIndex) {
    const move = reverseMoveMap.get(Number(moveIndex));
    if (move) {
      seekToMove(move);
    }
  }
}
