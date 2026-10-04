import { useEffect, useRef, useState } from 'react';
import Chessground from 'react-chessground';
import 'react-chessground/dist/styles/chessground.css';
import { IoPlaySkipBack, IoChevronBack, IoChevronForward, IoPlaySkipForward, IoReload } from 'react-icons/io5';
import { PromotionDialog } from './PromotionDialog';
import { ANNOTATION_BRUSHES } from '../utils/drawableConverter';
import type { GameViewState } from './useGameView';
import './GameView.css';

// The board sizes, in pixels, besides fitting the space it's given
const MIN_BOARD_SIZE = 160;
const MAX_BOARD_SIZE = 1200;
// The space around the board: chess-board-container's padding, both sides
const BOARD_PADDING = 32;

/**
 * The board of a game being viewed, with the buttons to move through it below. It fills the
 * space it's given, the board as large a square as fits.
 */
export function GameBoard({ view }: { view: GameViewState }) {
  const {
    selectedGame,
    game,
    isEditMode,
    boardOrientation,
    flipBoard,
    promotionPending,
    promotionPreviewFen,
    legalMoves,
    autoShapes,
    userDrawnShapes,
    handleMove,
    handlePromotionSelect,
    handlePromotionCancel,
    handleDrawableChange,
    canGoBack,
    canGoForward,
    goToStart,
    goToPreviousMove,
    handleNextMove,
    goToEnd,
  } = view;
  const boardWrapperRef = useRef<HTMLDivElement>(null);
  const boardContainerRef = useRef<HTMLDivElement>(null);
  const navigationRef = useRef<HTMLDivElement>(null);
  const [boardSize, setBoardSize] = useState(512);

  // The largest square that fits above the buttons
  useEffect(() => {
    const wrapper = boardWrapperRef.current;
    if (!wrapper) return;
    const calculateBoardSize = () => {
      // Hidden (as in an inactive tab), it has no size; keep the last one
      if (wrapper.clientWidth === 0 || wrapper.clientHeight === 0) return;
      const navigationHeight = navigationRef.current?.offsetHeight ?? 60;
      const fits = Math.min(
        wrapper.clientWidth - BOARD_PADDING,
        wrapper.clientHeight - navigationHeight - BOARD_PADDING
      );
      setBoardSize(Math.max(MIN_BOARD_SIZE, Math.min(fits, MAX_BOARD_SIZE)));
    };
    calculateBoardSize();
    const resizeObserver = new ResizeObserver(calculateBoardSize);
    resizeObserver.observe(wrapper);
    if (navigationRef.current) resizeObserver.observe(navigationRef.current);
    return () => resizeObserver.disconnect();
  }, []);

  // Drawing with the Option key held down: green, with Ctrl too yellow, and with Shift too red.
  // Chessground draws only with the right button (or Shift), and picks the color from the
  // modifiers itself, so a press with Option is handed to it as the right-button press that gives
  // the color: none for green, Shift for red, and Shift+Alt for yellow. Chessground doesn't look at
  // the buttons after that, so the rest of the drag draws as usual.
  useEffect(() => {
    const container = boardContainerRef.current;
    if (!container || !isEditMode) return;
    const handleMouseDown = (e: MouseEvent) => {
      if (!e.isTrusted || !e.altKey || !(e.target instanceof Element) || !e.target.closest('cg-board')) return;
      e.stopPropagation();
      e.preventDefault();
      const red = e.shiftKey;
      const yellow = !red && e.ctrlKey;
      e.target.dispatchEvent(
        new MouseEvent('mousedown', {
          bubbles: true,
          cancelable: true,
          clientX: e.clientX,
          clientY: e.clientY,
          button: 2,
          buttons: 2,
          shiftKey: red || yellow,
          altKey: yellow,
        })
      );
    };
    container.addEventListener('mousedown', handleMouseDown, { capture: true });
    return () => container.removeEventListener('mousedown', handleMouseDown, { capture: true });
  }, [isEditMode]);

  return (
    <div ref={boardWrapperRef} className="chess-board-wrapper">
      <div className="chess-board-container" ref={boardContainerRef}>
        <Chessground
          key={`chessground-${isEditMode ? 'edit' : 'view'}`}
          width={boardSize}
          height={boardSize}
          fen={promotionPreviewFen || game.fen()}
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
            brushes: ANNOTATION_BRUSHES,
            onChange: handleDrawableChange,
          }}
          addDimensionsCssVarsTo={document.body}
        />
        {promotionPending && (
          <PromotionDialog
            color={game.turn() === 'w' ? 'white' : 'black'}
            onSelect={handlePromotionSelect}
            onCancel={handlePromotionCancel}
          />
        )}
      </div>

      <div ref={navigationRef} className="game-navigation">
        <div className="navigation-controls">
          <button onClick={goToStart} disabled={!selectedGame || !canGoBack()} className="nav-button" title="First move">
            <IoPlaySkipBack />
          </button>
          <button onClick={goToPreviousMove} disabled={!selectedGame || !canGoBack()} className="nav-button" title="Previous move">
            <IoChevronBack />
          </button>
          <button onClick={handleNextMove} disabled={!selectedGame || !canGoForward()} className="nav-button" title="Next move">
            <IoChevronForward />
          </button>
          <button onClick={goToEnd} disabled={!selectedGame || !canGoForward()} className="nav-button" title="Last move">
            <IoPlaySkipForward />
          </button>
          <button onClick={flipBoard} className="nav-button" title="Flip board">
            <IoReload style={{ transform: 'rotate(90deg) scaleX(-1)' }} />
          </button>
        </div>
      </div>
    </div>
  );
}
