import { useState, useCallback } from 'react';
import { GameTree } from '../model/GameTree';
import type { GameMoves, MoveNode } from '../model/GameTree';

export interface UseChessGameReturn {
  game: GameTree;
  version: number;
  triggerUpdate: () => void;
  getLastMove: () => [string, string] | null;
  canGoBack: () => boolean;
  canGoForward: () => boolean;
  goToNextMove: () => void;
  goToPreviousMove: () => void;
  goToStart: () => void;
  goToEnd: () => void;
  seekToMove: (move: MoveNode | null) => void;
  loadGame: (
    moves: GameMoves | undefined,
    tags: [string, string][],
    initialMoveSelector?: (game: GameTree) => MoveNode | null
  ) => GameTree | null;
}

export const useChessGame = (): UseChessGameReturn => {
  /*
  The GameTree is changed in place (by seek(), play(), and annotation edits) rather than
  replaced, so React won't see the changes by itself. A version counter is bumped whenever the
  game changes, to re-render whatever depends on it.
  */
  const [game, setGame] = useState<GameTree>(() => GameTree.empty());
  const [version, setVersion] = useState(0);

  const triggerUpdate = useCallback(() => {
    setVersion((v) => v + 1);
  }, []);

  const getLastMove = useCallback((): [string, string] | null => {
    const currentMove = game.currentMove();
    return currentMove && !currentMove.isNullMove ? [currentMove.from, currentMove.to] : null;
  }, [game]);

  const canGoBack = useCallback((): boolean => game.currentMove() !== null, [game]);

  const canGoForward = useCallback((): boolean => game.nextMove() !== null, [game]);

  // Note: this does not read window.location.hash to deep-link to a move - a shared component
  // shouldn't assume any particular app's URL scheme. Callers that want deep-linking can pass
  // their own initialMoveSelector.
  const loadGame = useCallback(
    (
      moves: GameMoves | undefined,
      tags: [string, string][],
      initialMoveSelector?: (game: GameTree) => MoveNode | null
    ): GameTree | null => {
      try {
        const loaded = GameTree.fromMoves(moves, tags);
        loaded.seek(initialMoveSelector?.(loaded) ?? null);
        setGame(loaded);
        triggerUpdate();
        return loaded;
      } catch (error) {
        console.error('Error loading game:', error);
        return null;
      }
    },
    [triggerUpdate]
  );

  const goToNextMove = useCallback(() => {
    const nextMove = game.nextMove();
    if (nextMove) {
      game.seek(nextMove);
      triggerUpdate();
    }
  }, [game, triggerUpdate]);

  const goToPreviousMove = useCallback(() => {
    const currentMove = game.currentMove();
    game.seek(currentMove ? GameTree.previous(currentMove) : null);
    triggerUpdate();
  }, [game, triggerUpdate]);

  const goToStart = useCallback(() => {
    game.seek(null);
    triggerUpdate();
  }, [game, triggerUpdate]);

  const goToEnd = useCallback(() => {
    let nextMove = game.nextMove();
    while (nextMove) {
      game.seek(nextMove);
      nextMove = game.nextMove();
    }
    triggerUpdate();
  }, [game, triggerUpdate]);

  const seekToMove = useCallback(
    (move: MoveNode | null) => {
      game.seek(move);
      triggerUpdate();
    },
    [game, triggerUpdate]
  );

  return {
    game,
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
    loadGame,
  };
};
