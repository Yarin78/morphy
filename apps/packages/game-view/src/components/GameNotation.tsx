import { useEffect, useRef, useState } from 'react';
import { Chess } from '@jackstenglein/chess';
import { generateNotationHtml } from '../utils/notationGenerator';
import type { NotationHtmlResult } from '../utils/notationGenerator';
import './GameNotation.css';

interface GameNotationProps {
  chess: Chess;
  version: number;
  onMoveClick: (move: any) => void;
  onNotationReady?: (reverseMoveMap: Map<number, any>) => void;
}

export const GameNotation: React.FC<GameNotationProps> = ({
  chess,
  version,
  onMoveClick,
  onNotationReady,
}) => {
  const [notationResult, setNotationResult] = useState<NotationHtmlResult | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Generate HTML when chess game changes
  // Note: We include 'version' to ensure regeneration when chess state mutates,
  // even though generateNotationHtml generates the full game tree and doesn't
  // depend on current position. This ensures consistency with the mutation tracking pattern.
  // Now we also pass the current move so highlighting is done during HTML generation.
  useEffect(() => {
    try {
      const result = generateNotationHtml(chess);
      setNotationResult(result);
      // Notify parent component that notation is ready
      if (onNotationReady) {
        onNotationReady(result.reverseMoveMap);
      }
    } catch (error) {
      console.error('Error generating notation HTML:', error);
      setNotationResult(null);
      if (onNotationReady) {
        onNotationReady(new Map());
      }
    }
  }, [chess, version, onNotationReady]);

  // Scroll highlighted move into view if necessary when position changes
  useEffect(() => {
    if (!notationResult || !containerRef.current) return;

    try {
      const container = containerRef.current;
      const currentMove = chess.currentMove();
      let targetElement: Element | null = null;

      if (currentMove) {
        // Look up the move index
        const currentMoveIndex = notationResult.moveMap.get(currentMove);
        if (currentMoveIndex !== undefined) {
          targetElement = container.querySelector(
            `[data-global-move-index="${currentMoveIndex}"].cbcur-move`
          );
        }
      } else {
        // At start position, scroll first move into view
        targetElement = container.querySelector('.cbmove.cbcur-move');
      }

      if (targetElement) {
        const containerRect = container.getBoundingClientRect();
        const elementRect = targetElement.getBoundingClientRect();

        // Check if element is already visible in the viewport
        const elementTop = elementRect.top;
        const elementBottom = elementRect.bottom;
        const containerTop = containerRect.top;
        const containerBottom = containerRect.bottom;

        // Add a small margin (e.g., 20px) to avoid scrolling if element is just barely visible
        const margin = 20;
        const isVisible =
          elementTop >= containerTop + margin &&
          elementBottom <= containerBottom - margin;

        // Only scroll if element is not fully visible
        if (!isVisible) {
          // Calculate the relative position within the container
          const relativeTop = elementRect.top - containerRect.top + container.scrollTop;

          // Center the element vertically in the container
          const scrollPosition = relativeTop - (containerRect.height / 2) + (elementRect.height / 2);

          // Smooth scroll to the calculated position
          container.scrollTo({
            top: Math.max(0, scrollPosition),
            behavior: 'smooth'
          });
        }
      }
    } catch (error) {
      console.error('Error scrolling to current move:', error);
    }
  }, [notationResult, chess, version]);

  // Handle click events on moves
  useEffect(() => {
    if (!notationResult || !containerRef.current) return;

    const handleClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement;

      // Find the move element (could be the span itself or a child)
      const moveElement: HTMLElement | null = target.closest('.cbmove');

      if (!moveElement) return;

      const moveIndex = moveElement.getAttribute('data-global-move-index');
      if (!moveIndex) return;

      // Look up the Move object using the path
      const move = notationResult.reverseMoveMap.get(Number(moveIndex));
      if (move && onMoveClick) {
        onMoveClick(move);
      }
    };

    const container = containerRef.current;
    container.addEventListener('click', handleClick);

    return () => {
      container.removeEventListener('click', handleClick);
    };
  }, [notationResult, onMoveClick]);

  if (!notationResult) {
    return (
      <div className="game-notation-container">
        <p>No game notation available.</p>
      </div>
    );
  }

  return (
    <div className="game-notation-container" ref={containerRef}>
      <div
        className="game-notation-content"
        dangerouslySetInnerHTML={{ __html: notationResult.html }}
      />
    </div>
  );
};
