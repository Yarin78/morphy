import { useState, useCallback } from 'react';
import { Chess } from '@jackstenglein/chess';
import type { Move } from '@jackstenglein/chess';

export interface UseChessGameReturn {
  chess: Chess;
  version: number;
  triggerUpdate: () => void;
  getLastMove: () => [string, string] | null;
  canGoBack: () => boolean;
  canGoForward: () => boolean;
  goToNextMove: () => void;
  goToPreviousMove: () => void;
  goToStart: () => void;
  goToEnd: () => void;
  seekToMove: (move: Move | null) => void;
  loadPgn: (pgn: string, initialMoveSelector?: (chess: Chess) => Move | null) => boolean;
}

export const useChessGame = (): UseChessGameReturn => {
  /*
  This hook wraps the @jackstenglein/chess TypeScript chess
  library into a React hook.

  Since the Chess instance mutates its internal state (via seek(),
  loadPgn(), etc.) rather than creating new instances, React won't
  detect these changes automatically. We use a version counter to
  force re-renders whenever the chess state is mutated, ensuring
  components that depend on the chess state update correctly.
  */
  const [chess, setChess] = useState<Chess>(() => new Chess());
  const [version, setVersion] = useState(0);

  // Force re-render by incrementing version counter
  const triggerUpdate = useCallback(() => {
    setVersion(v => v + 1);
  }, []);

  // Get last move from chess instance
  const getLastMove = useCallback((): [string, string] | null => {
    try {
      const currentMove = chess.currentMove();
      if (currentMove && currentMove.from && currentMove.to && !currentMove.isNullMove) {
        return [currentMove.from, currentMove.to];
      }
    } catch {
      // Ignore errors
    }
    return null;
  }, [chess]);

  const canGoBack = useCallback((): boolean => {
    return chess.currentMove() !== null;
  }, [chess]);

  const canGoForward = useCallback((): boolean => {
    return chess.nextMove() !== null;
  }, [chess]);

  // Note: unlike yarin-chess's original useChessGame, this does not read
  // window.location.hash to deep-link to a move - a shared component
  // shouldn't assume any particular app's URL scheme. Callers that want
  // deep-linking can pass their own initialMoveSelector.
  const loadPgn = useCallback((pgn: string, initialMoveSelector?: (chess: Chess) => Move | null): boolean => {
    try {
      const chessInstance = new Chess({pgn});

      if (initialMoveSelector) {
        const initialMove = initialMoveSelector(chessInstance);
        chessInstance.seek(initialMove || null);
      } else {
        chessInstance.seek(null);
      }

      setChess(chessInstance);
      triggerUpdate();
      return true;
    } catch (error) {
      console.error('Error loading PGN:', error);
      return false;
    }
  }, [triggerUpdate]);

  const goToNextMove = useCallback(() => {
    try {
      const nextMove = chess.nextMove();
      if (nextMove) {
        // Navigate to the next move using seek()
        chess.seek(nextMove);
        triggerUpdate();
      }
    } catch (error) {
      console.error('Error going to next move:', error);
    }
  }, [chess, triggerUpdate]);

  const goToPreviousMove = useCallback(() => {
    try {
      const currentMove = chess.currentMove();
      if (currentMove && currentMove.previous) {
        // Navigate to the previous move
        chess.seek(currentMove.previous);
      } else {
        // If no previous move, go to start
        chess.seek(null);
      }
      triggerUpdate();
    } catch (error) {
      console.error('Error going to previous move:', error);
    }
  }, [chess, triggerUpdate]);

  const goToStart = useCallback(() => {
    try {
      chess.seek(null);
      triggerUpdate();
    } catch (error) {
      console.error('Error going to start:', error);
    }
  }, [chess, triggerUpdate]);

  const goToEnd = useCallback(() => {
    try {
      // Navigate to the end of the game
      chess.seek(null);
      let nextMove = chess.nextMove();

      while (nextMove) {
        chess.seek(nextMove);
        nextMove = chess.nextMove();
      }
      triggerUpdate();
    } catch (error) {
      console.error('Error going to end:', error);
    }
  }, [chess, triggerUpdate]);

  const seekToMove = useCallback((move: Move | null) => {
    try {
      // Navigate to the position after the move
      chess.seek(move);
      triggerUpdate();
    } catch (error) {
      console.error('Error seeking to move:', error);
    }
  }, [chess, triggerUpdate]);

  return {
    chess,
    version,
    triggerUpdate,
    getLastMove,
    canGoBack,
    canGoForward,
    goToNextMove,
    goToPreviousMove,
    goToStart,
    goToEnd,
    seekToMove,
    loadPgn,
  };
};
