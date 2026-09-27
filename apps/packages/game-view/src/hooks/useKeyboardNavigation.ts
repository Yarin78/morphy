import { useEffect, useRef } from 'react';
import { navigateToAdjacentMove } from '../utils/moveNavigation';
import type { Move } from '@jackstenglein/chess';

interface UseKeyboardNavigationOptions {
  enabled: boolean;
  canGoBack: () => boolean;
  canGoForward: () => boolean;
  goToPreviousMove: () => void;
  goToNextMove: () => void;
  goToStart: () => void;
  goToEnd: () => void;
  seekToMove: (move: Move | null) => void;
  reverseMoveMap: Map<number, Move>;
}

/**
 * Custom hook for handling keyboard navigation in the chess game viewer
 */
export function useKeyboardNavigation({
  enabled,
  canGoBack,
  canGoForward,
  goToPreviousMove,
  goToNextMove,
  goToStart,
  goToEnd,
  seekToMove,
  reverseMoveMap,
}: UseKeyboardNavigationOptions): void {
  const reverseMoveMapRef = useRef<Map<number, Move>>(reverseMoveMap);

  // Update ref when reverseMoveMap changes
  useEffect(() => {
    reverseMoveMapRef.current = reverseMoveMap;
  }, [reverseMoveMap]);

  // Keyboard navigation handler
  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const activeElement = document.activeElement;

      // Don't handle keyboard shortcuts if user is typing
      if (
        activeElement instanceof HTMLInputElement ||
        activeElement instanceof HTMLTextAreaElement ||
        (activeElement instanceof HTMLElement && activeElement.isContentEditable)
      ) {
        return;
      }

      switch (e.key) {
        case 'ArrowLeft':
          e.preventDefault();
          if (canGoBack()) {
            goToPreviousMove();
          }
          break;
        case 'ArrowRight':
          e.preventDefault();
          if (canGoForward()) {
            goToNextMove();
          }
          break;
        case 'ArrowUp':
          e.preventDefault();
          navigateToAdjacentMove('up', reverseMoveMapRef.current, seekToMove);
          break;
        case 'ArrowDown':
          e.preventDefault();
          navigateToAdjacentMove('down', reverseMoveMapRef.current, seekToMove);
          break;
        case 'Home':
          e.preventDefault();
          if (canGoBack()) {
            goToStart();
          }
          break;
        case 'End':
          e.preventDefault();
          if (canGoForward()) {
            goToEnd();
          }
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [
    enabled,
    canGoBack,
    canGoForward,
    goToPreviousMove,
    goToNextMove,
    goToStart,
    goToEnd,
    seekToMove,
  ]);
}
