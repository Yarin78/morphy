import { type RefObject, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { GameTree } from '../model/GameTree';
import type { MoveNode } from '../model/GameTree';
import { generateNotationHtml } from '../utils/notationGenerator';
import type { CommentEdit, NotationHtmlResult, QuotationLink } from '../utils/notationGenerator';
import type { GameNode } from '../model/GameTree';
import type { CommentType } from '../model/comments';
import './GameNotation.css';

interface GameNotationProps {
  game: GameTree;
  version: number;
  /** The languages of the comments that are shown, besides those in no language. */
  languages: readonly string[];
  onMoveClick: (move: MoveNode) => void;
  /** When given, right-clicking a move calls it, with where the mouse is in the window. */
  onMoveContextMenu?: (move: MoveNode, x: number, y: number) => void;
  onNotationReady?: (reverseMoveMap: Map<number, MoveNode>) => void;
  /** The comment being edited in place, if one is. */
  editing?: CommentEdit | null;
  /** When given, double-clicking a comment calls it, to edit the comment. */
  onCommentDoubleClick?: (node: GameNode, type: CommentType, language: string | null) => void;
  /** The comment edited is done with: its new text, or null to leave it as it was. */
  onCommentEditDone?: (text: string | null) => void;
  /** When given, a quoted game that refers to another game can be clicked to open it. */
  onQuotationClick?: (link: QuotationLink) => void;
  /** Set to the element the moves are in, as for moving between them with the keys. */
  containerRef?: RefObject<HTMLDivElement | null>;
}

const NOTHING_FOLDED: ReadonlySet<MoveNode> = new Set();

/** Whether a move is one of those a variation hides when it's folded: any after its first. */
function isHiddenBy(first: MoveNode, move: MoveNode | null): boolean {
  for (let m = move; m; m = GameTree.previous(m)) {
    if (m.parent === first) return true;
  }
  return false;
}

export const GameNotation: React.FC<GameNotationProps> = ({
  game,
  version,
  languages,
  onMoveClick,
  onMoveContextMenu,
  editing = null,
  onCommentDoubleClick,
  onCommentEditDone,
  onNotationReady,
  onQuotationClick,
  containerRef: givenContainerRef,
}) => {
  const [notationResult, setNotationResult] = useState<NotationHtmlResult | null>(null);
  const ownContainerRef = useRef<HTMLDivElement>(null);
  const containerRef = givenContainerRef ?? ownContainerRef;
  // The first moves of the variations that are folded, kept with the game they're of
  const [folding, setFolding] = useState<{ game: GameTree; folded: ReadonlySet<MoveNode> }>({
    game,
    folded: new Set(),
  });
  const folded = folding.game === game ? folding.folded : NOTHING_FOLDED;

  // Generate HTML when chess game changes
  // Note: We include 'version' to ensure regeneration when chess state mutates,
  // even though generateNotationHtml generates the full game tree and doesn't
  // depend on current position. This ensures consistency with the mutation tracking pattern.
  // Now we also pass the current move so highlighting is done during HTML generation.
  useEffect(() => {
    try {
      const result = generateNotationHtml(game, languages, !!onQuotationClick, folded, editing);
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
  }, [game, version, languages, onNotationReady, onQuotationClick, folded, editing]);

  // The comment being edited: its text made editable, with the caret at its end. Enter keeps the
  // changes, as does leaving it, and Escape drops them.
  useLayoutEffect(() => {
    const editor = containerRef.current?.querySelector<HTMLElement>('[data-comment-editor]');
    if (!editor || !onCommentEditDone) return;
    editor.contentEditable = 'plaintext-only';
    editor.focus();
    const selection = window.getSelection();
    selection?.selectAllChildren(editor);
    selection?.collapseToEnd();

    let done = false;
    const finish = (text: string | null) => {
      if (done) return;
      done = true;
      onCommentEditDone(text);
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        finish(editor.innerText);
      } else if (e.key === 'Escape') {
        e.preventDefault();
        finish(null);
      }
      // The keys are the editor's, not the notation's
      e.stopPropagation();
    };
    const handleBlur = () => finish(editor.innerText);
    editor.addEventListener('keydown', handleKeyDown);
    editor.addEventListener('blur', handleBlur);
    return () => {
      done = true;
      editor.removeEventListener('keydown', handleKeyDown);
      editor.removeEventListener('blur', handleBlur);
    };
  }, [notationResult, onCommentEditDone]);

  // Scroll highlighted move into view if necessary when position changes
  useEffect(() => {
    if (!notationResult || !containerRef.current) return;

    try {
      const container = containerRef.current;
      const currentMove = game.currentMove();
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
  }, [notationResult, game, version]);

  // Handle click events on moves
  useEffect(() => {
    if (!notationResult || !containerRef.current) return;

    const handleClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement;

      // A quoted game that refers to another game
      const quoteElement: HTMLElement | null = target.closest('.cbquote-link');
      if (quoteElement && onQuotationClick) {
        const link = notationResult.quoteLinks[Number(quoteElement.getAttribute('data-quote-index'))];
        if (link) onQuotationClick(link);
        return;
      }

      // The button folding or unfolding a variation
      const foldElement: HTMLElement | null = target.closest('.cbfold');
      if (foldElement) {
        const first = notationResult.reverseMoveMap.get(Number(foldElement.getAttribute('data-fold-move')));
        if (first) toggleFold(first);
        return;
      }

      // Find the move element (could be the span itself or a child)
      const moveElement: HTMLElement | null = target.closest('.cbmove');

      if (!moveElement) return;

      const moveIndex = moveElement.getAttribute('data-global-move-index');
      if (!moveIndex) return;

      // Look up the move by its index
      const move = notationResult.reverseMoveMap.get(Number(moveIndex));
      if (move && onMoveClick) {
        onMoveClick(move);
      }
    };

    // Folds a variation, or unfolds it; the current move goes to its first move if it would be hidden
    const toggleFold = (first: MoveNode) => {
      const next = new Set(folded);
      if (next.has(first)) {
        next.delete(first);
      } else {
        next.add(first);
        if (isHiddenBy(first, game.currentMove())) onMoveClick(first);
      }
      setFolding({ game, folded: next });
    };

    // Right-clicking a move
    const handleContextMenu = (e: MouseEvent) => {
      if (!onMoveContextMenu) return;
      const moveElement: HTMLElement | null = (e.target as HTMLElement).closest('.cbmove');
      const move = moveElement && notationResult.reverseMoveMap.get(Number(moveElement.getAttribute('data-global-move-index')));
      if (!move) return;
      e.preventDefault();
      onMoveContextMenu(move, e.clientX, e.clientY);
    };

    // Double-clicking a comment, to edit it
    const handleDoubleClick = (e: MouseEvent) => {
      if (!onCommentDoubleClick) return;
      const commentElement: HTMLElement | null = (e.target as HTMLElement).closest('[data-comment-move]');
      if (!commentElement) return;
      const index = Number(commentElement.getAttribute('data-comment-move'));
      const node = index < 0 ? game.root : notationResult.reverseMoveMap.get(index);
      if (!node) return;
      e.preventDefault();
      window.getSelection()?.removeAllRanges();
      onCommentDoubleClick(
        node,
        commentElement.getAttribute('data-comment-type') as CommentType,
        commentElement.getAttribute('data-comment-language') || null
      );
    };

    const container = containerRef.current;
    container.addEventListener('click', handleClick);
    container.addEventListener('contextmenu', handleContextMenu);
    container.addEventListener('dblclick', handleDoubleClick);

    return () => {
      container.removeEventListener('click', handleClick);
      container.removeEventListener('contextmenu', handleContextMenu);
      container.removeEventListener('dblclick', handleDoubleClick);
    };
  }, [notationResult, onMoveClick, onMoveContextMenu, onCommentDoubleClick, onQuotationClick, folded, game]);

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
