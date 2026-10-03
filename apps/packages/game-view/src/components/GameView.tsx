import { useState, useRef, useLayoutEffect, useCallback, useEffect, useMemo } from 'react';
import Chessground from 'react-chessground';
import 'react-chessground/dist/styles/chessground.css';
import { GameNotation } from './GameNotation';
import { GameHeader } from './GameHeader';
import { GameInfoDialog } from './GameInfoDialog';
import { PromotionDialog } from './PromotionDialog';
import { NagBar } from './NagBar';
import { EvalGraph } from './EvalGraph';
import { LanguageSelector } from './LanguageSelector';
import { VariationChooser } from './VariationChooser';
import { ContextMenu } from './ContextMenu';
import type { ContextMenuItem } from './ContextMenu';
import { moveActions } from './moveActions';
import './NotationBar.css';
import { commentLanguages, defaultLanguage } from '../model/languages';
import { NAG_PALETTE, nagInfo, toggleNag } from '../model/nags';
import type { NagType } from '../model/nags';
import { toggleCritical, togglePawnStructure, togglePiecePath } from '../model/specialAnnotations';
import type { CriticalPhase } from '../model/specialAnnotations';
import { annotationsToShapes, ANNOTATION_BRUSHES, LAST_MOVE_BRUSH, shapesToAnnotations } from '../utils/drawableConverter';
import type { DrawShape } from '../utils/drawableConverter';
import { createMovedPieceFen } from '../utils/fenUtils';
import { evalBars } from '../utils/evalGraph';
import { nextMoveChoices } from '../utils/variationChoice';
import { findAnnotation } from '../model/annotations';
import { TbArrowBackUp, TbArrowForwardUp, TbCircleOff, TbRoute, TbStar } from 'react-icons/tb';
import { IoPlaySkipBack, IoChevronBack, IoChevronForward, IoPlaySkipForward, IoReload, IoMenu, IoClose } from 'react-icons/io5';
import type { ChessGame } from '../types/chess';
import { useChessGame } from '../hooks/useChessGame';
import { useKeyboardNavigation } from '../hooks/useKeyboardNavigation';
import { NAG_KEYS, useNagKeys } from '../hooks/useNagKeys';
import { COMMENT_KEYS, useCommentKeys } from '../hooks/useCommentKeys';
import { useMoveActionKeys } from '../hooks/useMoveActionKeys';
import { UNDO_SHORTCUTS, useUndoKeys } from '../hooks/useUndoKeys';
import { EditHistory } from '../model/editHistory';
import { withComment } from '../model/comments';
import type { CommentType } from '../model/comments';
import { readGameInfo, writeGameInfo } from '../utils/gameInfo';
import type { GameInfo } from '../utils/gameInfo';
import type { GameInfoServices } from '../utils/gameInfo';
import { GameTree } from '../model/GameTree';
import type { GameNode, MoveNode } from '../model/GameTree';
import type { Square } from 'chess.js';
import type { CommentEdit, QuotationLink } from '../utils/notationGenerator';
import './GameView.css';

export interface GameViewProps {
  selectedGame: ChessGame | null;
  /** When provided, a hamburger button toggling the sidebar is rendered in the navigation bar. */
  sidebarOpen?: boolean;
  onToggleSidebar?: () => void;
  initialOrientation: 'white' | 'black';
  initialMoveToShow?: (game: GameTree) => MoveNode | null;
  /** Opens the board read-only instead of the default editable mode. */
  readOnly?: boolean;
  /**
   * Called with the live (mutable) game whenever it's (re)created - i.e. whenever selectedGame
   * changes. GameView never lifts the board state itself, so a caller that wants to save edits
   * should stash this reference and read game.toMoves()/game.tagValues() from it on demand (e.g.
   * on a Save button click), rather than re-rendering on every move.
   */
  onGameReady?: (game: GameTree) => void;
  /** Lets the Edit Game Info dialog pick existing players and tournaments. */
  gameInfoServices?: GameInfoServices;
  /**
   * When given, a quoted game that refers to another game, as a repertoire refers to its other
   * chapters, can be clicked; this is called with the quotation and the position its move leads
   * to, for the caller to open that game (see quotedPosition).
   */
  onQuotationClick?: (link: QuotationLink) => void;
}

// The colors of the moves leading to critical positions, as in GameNotation.css
const CRITICAL_COLORS: Record<CriticalPhase, string> = {
  opening: '#2b5f94',
  middlegame: '#cc4a2c',
  endgame: '#2f7a3c',
};

/** The key that does something, of those that do things, if one does it. */
function shortcutOf<T>(keys: Record<string, T>, value: T): string | undefined {
  return Object.entries(keys).find(([, v]) => v === value)?.[0];
}

export const GameView: React.FC<GameViewProps> = ({
  selectedGame,
  sidebarOpen,
  onToggleSidebar,
  initialOrientation,
  initialMoveToShow,
  readOnly = false,
  onGameReady,
  gameInfoServices,
  onQuotationClick,
}) => {
  const {
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
  } = useChessGame();

  useEffect(() => {
    onGameReady?.(game);
  }, [game, onGameReady]);

  const [boardOrientation, setBoardOrientation] = useState<'white' | 'black'>('white');
  const [isEditMode, setIsEditMode] = useState(false);
  const [promotionPending, setPromotionPending] = useState<{ from: Square; to: Square } | null>(null);
  const [promotionPreviewFen, setPromotionPreviewFen] = useState<string | null>(null);
  // The game info being edited in the Edit Game Info dialog, or null when it isn't open
  const [editingGameInfo, setEditingGameInfo] = useState<GameInfo | null>(null);

  // Load the game when one is selected and set edit mode
  useEffect(() => {
    if (selectedGame) {
      const success = loadGame(selectedGame.moves, selectedGame.tags, initialMoveToShow);
      if (!success) {
        console.error('Failed to load game:', selectedGame.header.id);
      }
      // A game is editable by default; readOnly opens it in view mode instead.
      setIsEditMode(!readOnly);
    }
  }, [selectedGame, loadGame, initialMoveToShow, readOnly]);

  // Set board orientation from parent when game changes
  useEffect(() => {
    setBoardOrientation(initialOrientation);
  }, [initialOrientation]);
  const previousMoveRef = useRef<MoveNode | null>(null);
  const [autoShapes, setAutoShapes] = useState<DrawShape[]>([]);
  const [userDrawnShapes, setUserDrawnShapes] = useState<DrawShape[]>([]);
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
  const boardContainerRef = useRef<HTMLDivElement>(null);
  const navigationRef = useRef<HTMLDivElement>(null);
  const [reverseMoveMap, setReverseMoveMap] = useState<Map<number, MoveNode>>(new Map());

  // The moves to choose the next move from when there are variations, while they're shown
  const [moveChoice, setMoveChoice] = useState<{ game: GameTree; moves: MoveNode[] } | null>(null);
  const choosingMove = moveChoice?.game === game ? moveChoice.moves : null;

  // The next move, or a choice of it when there are variations
  const handleNextMove = useCallback(() => {
    const choices = nextMoveChoices(game.currentNode());
    if (choices.length > 1) {
      setMoveChoice({ game, moves: choices });
    } else {
      goToNextMove();
    }
  }, [game, goToNextMove]);

  const handleMoveChosen = useCallback(
    (move: MoveNode) => {
      setMoveChoice(null);
      seekToMove(move);
    },
    [seekToMove]
  );

  const handleMoveChoiceCancel = useCallback(() => setMoveChoice(null), []);

  const handleMoveClick = useCallback((move: MoveNode) => {
    seekToMove(move);
  }, [seekToMove]);

  // Compute legal moves for edit mode
  // Chessground expects dests to be a Map<Square, Square[]>
  const legalMoves = useMemo(() => {
    if (!isEditMode) return new Map<Square, Square[]>();

    const dests = new Map<Square, Square[]>();

    game.legalMoves().forEach((move) => {
      if (!dests.has(move.from)) {
        dests.set(move.from, []);
      }
      dests.get(move.from)!.push(move.to);
    });

    return dests;
  }, [game, version, isEditMode]);

  // The edits made to the moves and annotations, to undo and redo, kept with the game they're of
  const historyRef = useRef<{ game: GameTree; history: EditHistory } | null>(null);
  const editHistory = useCallback(() => {
    if (historyRef.current?.game !== game) historyRef.current = { game, history: new EditHistory() };
    return historyRef.current.history;
  }, [game]);

  // Makes an edit to the moves or annotations, which can then be undone, unless it changed nothing
  const edit = useCallback(
    (change: () => void) => {
      const before = game.snapshot();
      change();
      if (JSON.stringify(before.moves) !== JSON.stringify(game.toMoves())) editHistory().record(before);
      triggerUpdate();
    },
    [game, editHistory, triggerUpdate]
  );

  // Handle move execution in edit mode
  const handleMove = useCallback((from: Square, to: Square) => {
    if (!isEditMode) return;

    try {
      // Check if this is a pawn promotion move
      const piece = game.pieceAt(from);
      const isPawn = piece?.type === 'p';
      const isPromotionRank = to[1] === '8' || to[1] === '1';

      if (isPawn && isPromotionRank) {
        // Create a preview FEN with the pawn moved to the destination square
        const previewFen = createMovedPieceFen(game.fen(), from, to);
        setPromotionPreviewFen(previewFen);
        setPromotionPending({ from, to });
      } else {
        // Normal move (not a promotion)
        edit(() => {
          if (!game.play({ from, to })) console.error('Invalid move:', from, to);
        });
      }
    } catch (error) {
      console.error('Error making move:', error);
    }
  }, [game, isEditMode, edit]);

  // Handle promotion piece selection
  const handlePromotionSelect = useCallback((piece: 'q' | 'r' | 'b' | 'n') => {
    if (!promotionPending) return;

    try {
      edit(() => {
        const move = game.play({ from: promotionPending.from, to: promotionPending.to, promotion: piece });
        if (!move) console.error('Invalid promotion move:', promotionPending);
      });
    } catch (error) {
      console.error('Error making promotion move:', error);
    } finally {
      setPromotionPending(null);
      setPromotionPreviewFen(null);
    }
  }, [game, promotionPending, edit]);

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
  const handleDrawableChange = useCallback((newShapes: DrawShape[]) => {
    if (!isEditMode) return;

    // The last move arrow isn't an annotation
    const filteredNewShapes = newShapes.filter((shape) => shape.brush !== LAST_MOVE_BRUSH);

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

    // Keep them as the annotations of the current move, or of the game at the start position
    edit(() => {
      const node = game.currentNode();
      node.annotations = shapesToAnnotations(node.annotations, updatedShapes);
    });
  }, [isEditMode, game, edit, userDrawnShapes]);

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
  const handleNotationReady = useCallback((moveMap: Map<number, MoveNode>) => {
    setReverseMoveMap(moveMap);
  }, []);

  const handleGameInfoSave = useCallback((info: GameInfo) => {
    writeGameInfo(game, info);
    setEditingGameInfo(null);
    triggerUpdate();
  }, [game, triggerUpdate]);

  const handleGameInfoCancel = useCallback(() => setEditingGameInfo(null), []);

  // Adds a symbol to the current move, or takes it away
  const handleNagToggle = useCallback((nag: number) => {
    const move = game.currentMove();
    if (!move) return;
    edit(() => {
      move.annotations = toggleNag(move.annotations, nag);
    });
  }, [game, edit]);

  // The language whose comments are shown, besides those in no language, and that comments are
  // written in, or null for all: every comment shown, and comments written in no language. By
  // default the preferred one of the game's. The choice is kept with the game it was made for, so
  // another game starts from its own default.
  const languages = useMemo(() => commentLanguages(game), [game, version]);
  const [languageChoice, setLanguageChoice] = useState<{ game: typeof game; language: string | null } | null>(null);
  const language = languageChoice?.game === game ? languageChoice.language : defaultLanguage(languages);
  const shownLanguages = useMemo(() => (language ? [language] : languages), [language, languages]);
  const handleLanguageSelect = useCallback(
    (selected: string | null) => setLanguageChoice({ game, language: selected }),
    [game]
  );

  // The comment being edited in place, kept with the game it's of
  const [commentEdit, setCommentEdit] = useState<{ game: GameTree; edit: CommentEdit } | null>(null);
  const editingComment = commentEdit?.game === game ? commentEdit.edit : null;

  // A comment in a language, by default the one being written in
  const handleCommentEdit = useCallback(
    (node: GameNode, type: CommentType, commentLanguage: string | null = language) =>
      setCommentEdit({ game, edit: { node, type, language: commentLanguage } }),
    [game, language]
  );

  // A comment of the current move, or of the game at the start position
  const handleCurrentCommentEdit = useCallback(
    (type: CommentType) => handleCommentEdit(game.currentNode(), type),
    [game, handleCommentEdit]
  );

  // The comment edited gets the new text, unless the editing was cancelled
  const handleCommentEditDone = useCallback(
    (text: string | null) => {
      if (editingComment && text !== null) {
        const { node, type, language } = editingComment;
        edit(() => {
          node.annotations = withComment(node.annotations, type, language, text);
        });
      }
      setCommentEdit(null);
    },
    [editingComment, edit]
  );

  // The context menu of a move, while it's open: the move goes to the one right-clicked
  const [moveMenu, setMoveMenu] = useState<{ x: number; y: number } | null>(null);

  const handleMoveContextMenu = useCallback(
    (move: MoveNode, x: number, y: number) => {
      seekToMove(move);
      setMoveMenu({ x, y });
    },
    [seekToMove]
  );

  const handleMoveMenuClose = useCallback(() => setMoveMenu(null), []);

  // Goes back to the moves before the last edit, or forward again to those after the last undone
  const handleUndoRedo = useCallback(
    (redo: boolean) => {
      const history = editHistory();
      const snapshot = redo ? history.redo(game.snapshot()) : history.undo(game.snapshot());
      if (!snapshot) return;
      game.restore(snapshot);
      setCommentEdit(null);
      setMoveMenu(null);
      triggerUpdate();
    },
    [game, editHistory, triggerUpdate]
  );
  const handleUndo = useCallback(() => handleUndoRedo(false), [handleUndoRedo]);
  const handleRedo = useCallback(() => handleUndoRedo(true), [handleUndoRedo]);

  const moveMenuItems = useMemo((): ContextMenuItem[] => {
    const move = game.currentMove();
    if (!moveMenu || !move) return [];
    // Changes the annotations of the move
    const annotate = (change: (annotations: MoveNode['annotations']) => MoveNode['annotations']) => () =>
      edit(() => {
        move.annotations = change(move.annotations);
      });
    const nagItems = (type: NagType): ContextMenuItem[] =>
      NAG_PALETTE.find((group) => group.type === type)!.nags.map((nag) => ({
        label: nagInfo(nag)!.name,
        symbol: nagInfo(nag)!.symbol,
        shortcut: shortcutOf(NAG_KEYS, nag),
        onSelect: annotate((annotations) => toggleNag(annotations, nag)),
      }));
    // A dot in the color of the moves leading to such a position
    const critical = (name: string, p: CriticalPhase): ContextMenuItem => ({
      label: `Critical ${name} position`,
      symbol: <span style={{ color: CRITICAL_COLORS[p] }}>●</span>,
      onSelect: annotate((annotations) => toggleCritical(annotations, p)),
    });
    const history = editHistory();
    return [
      {
        label: 'Undo',
        symbol: <TbArrowBackUp />,
        shortcut: UNDO_SHORTCUTS.undo,
        disabled: !history.canUndo,
        onSelect: handleUndo,
      },
      {
        label: 'Redo',
        symbol: <TbArrowForwardUp />,
        shortcut: UNDO_SHORTCUTS.redo,
        disabled: !history.canRedo,
        onSelect: handleRedo,
      },
      'separator',
      ...moveActions(game, move, edit).map(
        (action): ContextMenuItem => ({
          label: action.label,
          symbol: action.icon,
          shortcut: action.shortcut,
          disabled: action.disabled,
          onSelect: action.run,
        })
      ),
      'separator',
      {
        label: 'Add Comment Before Move',
        symbol: '{',
        shortcut: shortcutOf(COMMENT_KEYS, 'textBefore'),
        onSelect: () => handleCommentEdit(move, 'textBefore'),
      },
      {
        label: 'Add Comment After Move',
        symbol: '}',
        shortcut: shortcutOf(COMMENT_KEYS, 'textAfter'),
        onSelect: () => handleCommentEdit(move, 'textAfter'),
      },
      'separator',
      {
        label: 'Insert Null Move',
        symbol: <TbCircleOff />,
        disabled: !game.canPlayNullMove(),
        onSelect: () => {
          edit(() => game.playNullMove());
        },
      },
      'separator',
      { label: 'Move Annotations', symbol: '!?', submenu: nagItems('moveComment') },
      { label: 'Position Annotations', symbol: '±', submenu: nagItems('lineEvaluation') },
      { label: 'Other Annotations', symbol: 'Δ', submenu: nagItems('movePrefix') },
      {
        label: 'Special Annotations',
        symbol: <TbStar />,
        submenu: [
          critical('opening', 'opening'),
          critical('middlegame', 'middlegame'),
          critical('endgame', 'endgame'),
          'separator',
          {
            label: 'Pawn structure',
            symbol: '♟',
            onSelect: annotate(togglePawnStructure),
          },
          {
            label: 'Piece path',
            symbol: <TbRoute />,
            disabled: move.isNullMove,
            onSelect: annotate((annotations) => togglePiecePath(annotations, move.to)),
          },
        ],
      },
    ];
  }, [game, version, moveMenu, edit, editHistory, handleUndo, handleRedo, handleCommentEdit]);

  // The evaluations of the main line, shown as a graph above the bar
  const evaluationBars = useMemo(() => {
    const evaluations = findAnnotation(game.root.annotations, 'evaluations');
    return evaluations ? evalBars(game.root, evaluations) : null;
  }, [game, version]);

  // !, ? and = toggle those symbols on the current move, when editing
  useNagKeys(isEditMode && !!selectedGame && !editingGameInfo, handleNagToggle);
  useCommentKeys(isEditMode && !!selectedGame && !editingGameInfo && !editingComment, handleCurrentCommentEdit);
  // Cmd+↑, Delete, ] and [ promote or delete the variation, or delete the moves after or before
  const currentMoveActions = useCallback(
    () => moveActions(game, game.currentMove(), edit),
    [game, edit]
  );
  useMoveActionKeys(isEditMode && !!selectedGame && !editingGameInfo && !editingComment, currentMoveActions);
  useUndoKeys(isEditMode && !!selectedGame && !editingGameInfo && !editingComment, handleUndo, handleRedo);

  // Keyboard navigation
  useKeyboardNavigation({
    enabled: !!selectedGame && !editingGameInfo,
    canGoBack,
    canGoForward,
    goToPreviousMove,
    goToNextMove: handleNextMove,
    goToStart,
    goToEnd,
    seekToMove,
    reverseMoveMap,
  });

  // This useLayoutEffect manages board shapes (arrows, highlights) in sync with animations.
  // - Yellow arrow: highlights the last move played
  // - Annotation shapes: the colored squares and arrows of the move (loaded into userDrawnShapes
  //   for editing)
  useLayoutEffect(() => {
    const currentMove = game.currentMove();
    const moveChanged = previousMoveRef.current !== currentMove;
    const ANIMATION_DURATION = 200; // Match the animation duration in Chessground

    // Clear any pending timeout
    if (animationTimeoutRef.current) {
      clearTimeout(animationTimeoutRef.current);
    }

    // Helper to compute shapes for the current position
    const updateShapes = () => {
      const annotationShapes = annotationsToShapes(game.currentNode().annotations);
      const lastMove = getLastMove();
      const lastMoveArrow: DrawShape[] = lastMove
        ? [{ orig: lastMove[0], dest: lastMove[1], brush: LAST_MOVE_BRUSH }]
        : [];

      // userDrawnShapes: editable in edit mode, passed to Chessground's drawable.shapes
      // autoShapes: read-only shapes (the last move arrow)
      setUserDrawnShapes(annotationShapes);
      setAutoShapes(lastMoveArrow);
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
  }, [game, version, getLastMove]); // Removed isEditMode - shapes are loaded the same way regardless

  return (
    <div className="game-view-container" ref={containerRef}>
      <div
        ref={boardWrapperRef}
        className="chess-board-wrapper"
        style={{ width: `${leftPanelWidth}px`, minWidth: '320px', maxWidth: 'calc(100% - 8px - 320px)' }}
      >
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
            <button onClick={handleNextMove} disabled={!selectedGame || !canGoForward()} className="nav-button" title="Next move">
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
            <GameHeader game={game} onClick={() => setEditingGameInfo(readGameInfo(game))} />
            <div className="notation-divider"></div>
            <GameNotation
              game={game}
              version={version}
              languages={shownLanguages}
              onMoveClick={handleMoveClick}
              onMoveContextMenu={isEditMode ? handleMoveContextMenu : undefined}
              editing={editingComment}
              onCommentDoubleClick={isEditMode ? handleCommentEdit : undefined}
              onCommentEditDone={handleCommentEditDone}
              onNotationReady={handleNotationReady}
              onQuotationClick={onQuotationClick}
            />
            {moveMenu && moveMenuItems.length > 0 && (
              <ContextMenu x={moveMenu.x} y={moveMenu.y} items={moveMenuItems} onClose={handleMoveMenuClose} />
            )}
            {choosingMove && (
              <VariationChooser moves={choosingMove} onChoose={handleMoveChosen} onCancel={handleMoveChoiceCancel} />
            )}
            {evaluationBars && (
              <EvalGraph
                bars={evaluationBars}
                current={game.currentNode()}
                onSelect={(node) => seekToMove('san' in node ? (node as MoveNode) : null)}
              />
            )}
            {(isEditMode || languages.length > 0) && (
              <div className="notation-bar">
                {isEditMode && (
                  <NagBar
                    annotations={game.currentMove()?.annotations ?? null}
                    onToggle={handleNagToggle}
                    actions={moveActions(game, game.currentMove(), edit)}
                  />
                )}
                <LanguageSelector
                  language={language}
                  gameLanguages={languages}
                  onSelect={handleLanguageSelect}
                  disabled={!!editingComment}
                />
              </div>
            )}
          </>
        ) : (
          <></>
        )}
      </div>

      {editingGameInfo && (
        <GameInfoDialog
          initial={editingGameInfo}
          services={gameInfoServices}
          onSave={handleGameInfoSave}
          onCancel={handleGameInfoCancel}
        />
      )}
    </div>
  );
};
