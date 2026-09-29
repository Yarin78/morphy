import { useState, useRef, useLayoutEffect, useCallback, useEffect, useMemo } from 'react';
import Chessground from 'react-chessground';
import 'react-chessground/dist/styles/chessground.css';
import { GameNotation } from './GameNotation';
import { GameHeader } from './GameHeader';
import { GameInfoDialog } from './GameInfoDialog';
import { PromotionDialog } from './PromotionDialog';
import { convertMoveDrawablesToAutoShapes, convertShapesToPGN } from '../utils/drawableConverter';
import { createMovedPieceFen } from '../utils/fenUtils';
import { IoPlaySkipBack, IoChevronBack, IoChevronForward, IoPlaySkipForward, IoReload, IoMenu, IoClose, IoPencil } from 'react-icons/io5';
import type { ChessGame } from '../types/chess';
import { useChessGame } from '../hooks/useChessGame';
import { useKeyboardNavigation } from '../hooks/useKeyboardNavigation';
import { readGameInfo, writeGameInfo } from '../utils/gameInfo';
import type { GameInfo } from '../utils/gameInfo';
import { Chess } from '@jackstenglein/chess';
import type { Move, Square } from '@jackstenglein/chess';
import './GameView.css';

export interface GameViewProps {
  selectedGame: ChessGame | null;
  /** When provided, a hamburger button toggling the sidebar is rendered in the navigation bar. */
  sidebarOpen?: boolean;
  onToggleSidebar?: () => void;
  initialOrientation: 'white' | 'black';
  initialMoveToShow?: (chess: Chess) => Move | null;
  startInEditMode?: boolean;
  /**
   * Called with the live (mutable) Chess instance whenever it's (re)created - i.e. whenever
   * selectedGame changes. GameView never lifts the board state itself, so a caller that wants
   * to save edits should stash this reference and read chess.renderPgn()/chess.header() from
   * it on demand (e.g. on a Save button click), rather than re-rendering on every move.
   */
  onChessReady?: (chess: Chess) => void;
}

export const GameView: React.FC<GameViewProps> = ({
  selectedGame,
  sidebarOpen,
  onToggleSidebar,
  initialOrientation,
  initialMoveToShow,
  startInEditMode = false,
  onChessReady,
}) => {
  const {
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
  } = useChessGame();

  useEffect(() => {
    onChessReady?.(chess);
  }, [chess, onChessReady]);

  const [boardOrientation, setBoardOrientation] = useState<'white' | 'black'>('white');
  const [isEditMode, setIsEditMode] = useState(false);
  const [promotionPending, setPromotionPending] = useState<{ from: Square; to: Square } | null>(null);
  const [promotionPreviewFen, setPromotionPreviewFen] = useState<string | null>(null);
  // The game info being edited in the Edit Game Info dialog, or null when it isn't open
  const [editingGameInfo, setEditingGameInfo] = useState<GameInfo | null>(null);

  // Load PGN when a game is selected and set edit mode
  useEffect(() => {
    if (selectedGame) {
      if (selectedGame.pgn) {
        const success = loadPgn(selectedGame.pgn, initialMoveToShow);
        if (!success) {
          console.error('Failed to load PGN for game:', selectedGame.header.id);
        }
      } else {
        // New game with no PGN - load empty position
        loadPgn('', undefined);
      }
      // Set edit mode based on startInEditMode prop (for new games)
      setIsEditMode(startInEditMode);
    }
  }, [selectedGame, loadPgn, initialMoveToShow, startInEditMode]);

  // Set board orientation from parent when game changes
  useEffect(() => {
    setBoardOrientation(initialOrientation);
  }, [initialOrientation]);
  const previousMoveRef = useRef<any>(null);
  const [autoShapes, setAutoShapes] = useState<any[]>([]);
  const [userDrawnShapes, setUserDrawnShapes] = useState<any[]>([]);
  // ReturnType<typeof setTimeout>, not NodeJS.Timeout: this is browser code, and setTimeout's
  // return type depends on which lib is in scope for whoever compiles this file.
  const animationTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [leftPanelWidth, setLeftPanelWidth] = useState<number>(550);
  const [boardSize, setBoardSize] = useState<number>(512);
  const [isResizing, setIsResizing] = useState(false);
  const [isMobile, setIsMobile] = useState<boolean>(window.innerWidth < 768);
  const containerRef = useRef<HTMLDivElement>(null);
  const resizeStartXRef = useRef<number>(0);
  const resizeStartWidthRef = useRef<number>(0);
  const boardWrapperRef = useRef<HTMLDivElement>(null);
  const navigationRef = useRef<HTMLDivElement>(null);
  const [reverseMoveMap, setReverseMoveMap] = useState<Map<number, any>>(new Map());

  const handleMoveClick = useCallback((move: any) => {
    seekToMove(move);
  }, [seekToMove]);

  // Compute legal moves for edit mode
  // Chessground expects dests to be a Map<Square, Square[]>
  const legalMoves = useMemo(() => {
    if (!isEditMode) return new Map<Square, Square[]>();

    const moves = chess.moves();
    const dests = new Map<Square, Square[]>();

    moves.forEach((move) => {
      if (!dests.has(move.from)) {
        dests.set(move.from, []);
      }
      dests.get(move.from)!.push(move.to);
    });

    return dests;
  }, [chess, version, isEditMode]);

  // Handle move execution in edit mode
  const handleMove = useCallback((from: Square, to: Square) => {
    if (!isEditMode) return;

    try {
      // Check if this is a pawn promotion move
      const piece = chess.get(from);
      const isPawn = piece?.type === 'p';
      const isPromotionRank = to[1] === '8' || to[1] === '1';

      if (isPawn && isPromotionRank) {
        // Create a preview FEN with the pawn moved to the destination square
        const previewFen = createMovedPieceFen(chess.fen(), from, to);
        setPromotionPreviewFen(previewFen);
        setPromotionPending({ from, to });
      } else {
        // Normal move (not a promotion)
        const move = chess.move({ from, to });
        if (move) {
          triggerUpdate();
        } else {
          console.error('Invalid move:', from, to);
        }
      }
    } catch (error) {
      console.error('Error making move:', error);
    }
  }, [chess, isEditMode, triggerUpdate]);

  // Handle promotion piece selection
  const handlePromotionSelect = useCallback((piece: 'q' | 'r' | 'b' | 'n') => {
    if (!promotionPending) return;

    try {
      const move = chess.move({
        from: promotionPending.from,
        to: promotionPending.to,
        promotion: piece,
      });

      if (move) {
        triggerUpdate();
      } else {
        console.error('Invalid promotion move:', promotionPending);
      }
    } catch (error) {
      console.error('Error making promotion move:', error);
    } finally {
      setPromotionPending(null);
      setPromotionPreviewFen(null);
    }
  }, [chess, promotionPending, triggerUpdate]);

  // Handle promotion dialog cancellation
  const handlePromotionCancel = useCallback(() => {
    setPromotionPending(null);
    setPromotionPreviewFen(null);
    // Trigger update to reset the board position
    triggerUpdate();
  }, [triggerUpdate]);

  // Handle drawable changes (arrows and square highlights)
  // Chessground's onChange returns only the newly drawn shapes, so we need to merge
  // them with existing shapes, handling duplicates appropriately:
  // - Same orig/dest with same color: remove (toggle off)
  // - Same orig/dest with different color: replace
  // - New orig/dest: add
  const handleDrawableChange = useCallback((newShapes: any[]) => {
    if (!isEditMode) return;

    // Filter out the yellow arrow (last move indicator) from new shapes
    const filteredNewShapes = newShapes.filter(shape =>
      shape.brush !== 'yellow'
    );

    // Start with existing user shapes
    const updatedShapes = [...userDrawnShapes];

    filteredNewShapes.forEach(newShape => {
      // Find if there's an existing shape with same orig/dest
      const existingIndex = updatedShapes.findIndex(existing =>
        existing.orig === newShape.orig && existing.dest === newShape.dest
      );

      if (existingIndex !== -1) {
        const existingShape = updatedShapes[existingIndex];
        if (existingShape.brush === newShape.brush) {
          // Same color: toggle off (remove)
          updatedShapes.splice(existingIndex, 1);
        } else {
          // Different color: replace
          updatedShapes[existingIndex] = newShape;
        }
      } else {
        // New shape: add
        updatedShapes.push(newShape);
      }
    });

    setUserDrawnShapes(updatedShapes);

    // Save to PGN using chess.setDrawables()
    const currentMove = chess.currentMove();
    if (currentMove) {
      const { colorArrows, colorFields } = convertShapesToPGN(updatedShapes);

      // setDrawables expects undefined for empty arrays, not empty arrays
      chess.setDrawables(
        colorArrows.length > 0 ? colorArrows : undefined,
        colorFields.length > 0 ? colorFields : undefined,
        currentMove
      );

      // Trigger re-render to update notation
      triggerUpdate();
    }
  }, [isEditMode, chess, triggerUpdate, userDrawnShapes]);

  // Resize handler functions
  const handleResizeStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsResizing(true);
    resizeStartXRef.current = e.clientX;
    resizeStartWidthRef.current = leftPanelWidth;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, [leftPanelWidth]);

  const handleResizeMove = useCallback((e: MouseEvent) => {
    if (!isResizing || !containerRef.current) return;

    const deltaX = e.clientX - resizeStartXRef.current;
    const containerWidth = containerRef.current.offsetWidth;
    const RESIZE_HANDLE_WIDTH = 8; // Width of the resize handle
    const MIN_LEFT_WIDTH = 320;
    const MIN_RIGHT_WIDTH = 320;
    const maxLeftWidth = containerWidth - RESIZE_HANDLE_WIDTH - MIN_RIGHT_WIDTH;
    const newWidth = Math.max(MIN_LEFT_WIDTH, Math.min(maxLeftWidth, resizeStartWidthRef.current + deltaX));
    setLeftPanelWidth(newWidth);
  }, [isResizing]);

  const handleResizeEnd = useCallback(() => {
    setIsResizing(false);
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
  }, []);

  useEffect(() => {
    if (isResizing) {
      window.addEventListener('mousemove', handleResizeMove);
      window.addEventListener('mouseup', handleResizeEnd);
      return () => {
        window.removeEventListener('mousemove', handleResizeMove);
        window.removeEventListener('mouseup', handleResizeEnd);
      };
    }
  }, [isResizing, handleResizeMove, handleResizeEnd]);

  // Track mobile state for responsive behavior
  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth < 768);
    };

    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // Calculate board size based on available space
  useEffect(() => {
    const calculateBoardSize = () => {
      if (!boardWrapperRef.current || !containerRef.current) return;

      if (isMobile) {
        // On mobile, use full container width (no padding, no coordinates)
        const containerWidth = containerRef.current.clientWidth;
        const maxHorizontalSize = containerWidth;

        // Take the minimum to keep it square, but prefer width to fill screen
        const MAX_BOARD_SIZE = 768;
        const MIN_BOARD_SIZE = 280;
        const calculatedSize = Math.max(MIN_BOARD_SIZE, Math.min(maxHorizontalSize, MAX_BOARD_SIZE));
        setBoardSize(calculatedSize);
      } else {
        // Desktop/tablet: use actual rendered width of board wrapper (may be constrained by CSS)
        const actualWrapperWidth = boardWrapperRef.current.clientWidth;
        const horizontalPadding = 32; // 16px * 2
        const maxHorizontalSize = actualWrapperWidth - horizontalPadding;

        // Vertical constraint: container height minus padding and navigation controls
        // Note: this only applies to desktop, as otherwise we might not fill out
        // the entire width on the mobile
        const containerHeight = containerRef.current.clientHeight;
        const containerPadding = 32; // 1rem padding from chess-board-container
        const navigationHeight = navigationRef.current?.offsetHeight || 60;
        const maxVerticalSize = containerHeight - containerPadding - navigationHeight;

        // Take the minimum to keep it square and fit both constraints
        // Also ensure it doesn't exceed 768px maximum
        const MAX_BOARD_SIZE = 768;
        const MIN_BOARD_SIZE = 300;
        const calculatedSize = Math.max(MIN_BOARD_SIZE, Math.min(maxHorizontalSize, maxVerticalSize, MAX_BOARD_SIZE));
        setBoardSize(calculatedSize);
      }
    };

    calculateBoardSize();

    // Use ResizeObserver to update when container or navigation size changes
    const resizeObserver = new ResizeObserver(() => {
      calculateBoardSize();
    });

    // Also listen to window resize for mobile breakpoint changes
    const handleResize = () => {
      calculateBoardSize();
    };
    window.addEventListener('resize', handleResize);

    if (containerRef.current) {
      resizeObserver.observe(containerRef.current);
    }
    if (boardWrapperRef.current) {
      resizeObserver.observe(boardWrapperRef.current);
    }
    if (navigationRef.current) {
      resizeObserver.observe(navigationRef.current);
    }

    return () => {
      resizeObserver.disconnect();
      window.removeEventListener('resize', handleResize);
    };
  }, [leftPanelWidth, isMobile]);

  // Callback to receive notation reverse move map
  const handleNotationReady = useCallback((moveMap: Map<number, any>) => {
    setReverseMoveMap(moveMap);
  }, []);

  const handleGameInfoSave = useCallback((info: GameInfo) => {
    writeGameInfo(chess, info);
    setEditingGameInfo(null);
    triggerUpdate();
  }, [chess, triggerUpdate]);

  const handleGameInfoCancel = useCallback(() => setEditingGameInfo(null), []);

  // Keyboard navigation
  useKeyboardNavigation({
    enabled: !!selectedGame && !editingGameInfo,
    canGoBack,
    canGoForward,
    goToPreviousMove,
    goToNextMove,
    goToStart,
    goToEnd,
    seekToMove,
    reverseMoveMap,
  });

  // This useLayoutEffect manages board shapes (arrows, highlights) in sync with animations.
  // - Yellow arrow: highlights the last move played
  // - PGN shapes: arrows/highlights stored in the PGN (loaded into userDrawnShapes for editing)
  useLayoutEffect(() => {
    const currentMove = chess.currentMove();
    const moveChanged = previousMoveRef.current !== currentMove;
    const ANIMATION_DURATION = 200; // Match the animation duration in Chessground

    // Clear any pending timeout
    if (animationTimeoutRef.current) {
      clearTimeout(animationTimeoutRef.current);
    }

    // Helper to compute shapes for the current position
    const updateShapes = () => {
      const pgnShapes = convertMoveDrawablesToAutoShapes(currentMove);
      const lastMove = getLastMove();
      const yellowArrow = lastMove ? [{
        orig: lastMove[0],
        dest: lastMove[1],
        brush: 'yellow',
      }] : [];

      // userDrawnShapes: editable in edit mode, passed to Chessground's drawable.shapes
      // autoShapes: read-only shapes (yellow arrow, and PGN shapes in view mode)
      setUserDrawnShapes(pgnShapes);
      setAutoShapes(yellowArrow);
    };

    if (moveChanged) {
      // Immediately clear drawables during animation
      setAutoShapes([]);
      setUserDrawnShapes([]);
      previousMoveRef.current = currentMove;

      // Wait for piece animation to complete before showing shapes
      animationTimeoutRef.current = setTimeout(updateShapes, ANIMATION_DURATION);
    } else {
      // No animation needed, update immediately
      updateShapes();
    }

    // Cleanup timeout on unmount or when dependencies change
    return () => {
      if (animationTimeoutRef.current) {
        clearTimeout(animationTimeoutRef.current);
      }
    };
  }, [chess, version, getLastMove]); // Removed isEditMode - shapes are loaded the same way regardless

  return (
    <div className="game-view-container" ref={containerRef}>
      <div
        ref={boardWrapperRef}
        className="chess-board-wrapper"
        style={{ width: `${leftPanelWidth}px`, minWidth: '320px', maxWidth: 'calc(100% - 8px - 320px)' }}
      >
        <div className="chess-board-container">
          <Chessground
            key={`chessground-${isEditMode ? 'edit' : 'view'}`}
            width={boardSize}
            height={boardSize}
            fen={promotionPreviewFen || chess.fen()}
            orientation={boardOrientation}
            coordinates={true}
            viewOnly={!isEditMode}
            animation={{ duration: 200, enabled: true }}
            movable={isEditMode ? {
              free: false,
              color: 'both',
              dests: legalMoves,
              showDests: false,
              events: {
                after: handleMove,
              },
            } : undefined}
            drawable={{
              enabled: isEditMode,
              visible: true,
              eraseOnClick: true,
              autoShapes: [...autoShapes, ...userDrawnShapes],
              onChange: handleDrawableChange,
            }}
            addDimensionsCssVarsTo={document.body}
          />
          {promotionPending && (
            <PromotionDialog
              color={chess.turn() === 'w' ? 'white' : 'black'}
              onSelect={handlePromotionSelect}
              onCancel={handlePromotionCancel}
            />
          )}
        </div>

        <div ref={navigationRef} className="game-navigation">
          <div className="navigation-controls">
            {onToggleSidebar && (
              <button
                className="sidebar-menu-button"
                onClick={onToggleSidebar}
                aria-label="Toggle sidebar"
              >
                {sidebarOpen ? <IoClose /> : <IoMenu />}
              </button>
            )}
            <button onClick={goToStart} disabled={!selectedGame || !canGoBack()} className="nav-button" title="First move">
              <IoPlaySkipBack />
            </button>
            <button onClick={goToPreviousMove} disabled={!selectedGame || !canGoBack()} className="nav-button" title="Previous move">
              <IoChevronBack />
            </button>
            <button onClick={goToNextMove} disabled={!selectedGame || !canGoForward()} className="nav-button" title="Next move">
              <IoChevronForward />
            </button>
            <button onClick={goToEnd} disabled={!selectedGame || !canGoForward()} className="nav-button" title="Last move">
              <IoPlaySkipForward />
            </button>
            <button
              onClick={() => setBoardOrientation(boardOrientation === 'white' ? 'black' : 'white')}
              className="nav-button"
              title="Flip board"
            >
              <IoReload style={{ transform: 'rotate(90deg) scaleX(-1)' }} />
            </button>
            <button
              onClick={() => {
                setIsEditMode(!isEditMode);
              }}
              className={`edit-mode-button ${isEditMode ? 'active' : ''}`}
              title={isEditMode ? "Switch to view mode" : "Switch to edit mode"}
            >
              <IoPencil />
            </button>
          </div>
        </div>
      </div>

      <div
        className={`resize-handle ${isResizing ? 'resizing' : ''}`}
        onMouseDown={handleResizeStart}
      />

      <div className="notation-area">
        {selectedGame ? (
          <>
            <GameHeader chess={chess} onClick={() => setEditingGameInfo(readGameInfo(chess))} />
            <div className="notation-divider"></div>
            <GameNotation
              chess={chess}
              version={version}
              onMoveClick={handleMoveClick}
              onNotationReady={handleNotationReady}
            />
          </>
        ) : (
          <></>
        )}
      </div>

      {editingGameInfo && (
        <GameInfoDialog
          initial={editingGameInfo}
          onSave={handleGameInfoSave}
          onCancel={handleGameInfoCancel}
        />
      )}
    </div>
  );
};
