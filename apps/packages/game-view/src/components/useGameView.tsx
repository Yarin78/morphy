import { useState, useRef, useLayoutEffect, useCallback, useEffect, useMemo } from 'react';
import type { ContextMenuItem } from './ContextMenu';
import { moveActions } from './moveActions';
import type { MoveAction } from './moveActions';
import type { NotationBarGroup } from './NagBar';
import type { MoveNotation } from '../utils/moveNotation';
import { MoveTimeDialog } from './MoveTimeDialog';
import { MedalDialog } from './MedalDialog';
import { WebLinkDialog } from './WebLinkDialog';
import { VariationColorDialog } from './VariationColorDialog';
import { commentLanguages, defaultLanguage } from '../model/languages';
import { NAG_PALETTE, nagInfo, stepEvaluation, toggleNag, typeMoveComment } from '../model/nags';
import type { NagType } from '../model/nags';
import { toggleCritical, togglePawnStructure, togglePiecePath } from '../model/specialAnnotations';
import type { CriticalPhase } from '../model/specialAnnotations';
import { annotationsToShapes, LAST_MOVE_BRUSH, shapesToAnnotations } from '../utils/drawableConverter';
import type { DrawShape } from '../utils/drawableConverter';
import { createMovedPieceFen } from '../utils/fenUtils';
import { evalBars } from '../utils/evalGraph';
import { nextMoveChoices } from '../utils/variationChoice';
import { findAnnotation } from '../model/annotations';
import type { Annotation } from '../model/annotations';
import { TbArrowBackUp, TbArrowForwardUp, TbBorderAll, TbCircleOff, TbClock, TbEraser, TbLink, TbMedal, TbPalette, TbRoute, TbStar } from 'react-icons/tb';
import type { ChessGame } from '../types/chess';
import { useChessGame } from '../hooks/useChessGame';
import { useKeyboardNavigation } from '../hooks/useKeyboardNavigation';
import { NAG_KEYS, useNagKeys } from '../hooks/useNagKeys';
import { COMMENT_KEYS, DIAGRAM_KEY, useCommentKeys } from '../hooks/useCommentKeys';
import { useMoveActionKeys } from '../hooks/useMoveActionKeys';
import { useKeyShortcut } from '../hooks/useKeyShortcut';
import { UNDO_SHORTCUTS, useUndoKeys } from '../hooks/useUndoKeys';
import { EditHistory } from '../model/editHistory';
import { hasDiagramComment, toggleDiagram, withComment } from '../model/comments';
import type { CommentType } from '../model/comments';
import { readGameInfo, writeGameInfo } from '../utils/gameInfo';
import type { GameInfo } from '../utils/gameInfo';
import type { GameInfoServices } from '../utils/gameInfo';
import { GameTree } from '../model/GameTree';
import type { GameNode, MoveNode } from '../model/GameTree';
import type { Square } from 'chess.js';
import type { CommentEdit, QuotationLink } from '../utils/notationGenerator';

// The state of a game being viewed and edited, shared by its parts: the board (GameBoard), the
// notation (GameNotationPanel) and the dialogs (GameDialogs). GameView lays them out side by
// side; an app can instead put them in panes of its own.

export interface GameViewOptions {
  selectedGame: ChessGame | null;
  initialOrientation: 'white' | 'black';
  initialMoveToShow?: (game: GameTree) => MoveNode | null;
  /** Opens the board read-only instead of the default editable mode. */
  readOnly?: boolean;
  /**
   * Called with the live (mutable) game whenever it's (re)created - i.e. whenever selectedGame
   * changes. The view never lifts the board state itself, so a caller that wants to save edits
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
  /**
   * Called with the game when one is loaded, before it's shown: the caller may change it, as when
   * putting back changes that weren't saved.
   */
  onGameLoaded?: (game: GameTree) => void;
  /**
   * Whether the keyboard moves through and edits this game; true by default. An app showing
   * several games at once turns it off for all but the active one, as the keys are the page's.
   */
  keysEnabled?: boolean;
  /** How the pieces of the moves are written; English letters by default */
  notation?: MoveNotation;
  /** Whether the keys that annotate the current move (!, ?, comments, diagram) do; true by default */
  annotationKeys?: boolean;
  /** The groups of the bar below the notation that are shown, when editing; all by default */
  notationBarGroups?: ReadonlySet<NotationBarGroup>;
}

// The dialogs for the annotations of a move, by kind
export const ANNOTATION_DIALOGS = {
  time: MoveTimeDialog,
  medals: MedalDialog,
  webLink: WebLinkDialog,
  variationColor: VariationColorDialog,
};
type AnnotationDialogKind = keyof typeof ANNOTATION_DIALOGS;

// The key that plays a null move from the position shown
const NULL_MOVE_KEY = '0';

// The NAGs after which their menu has a line: the evaluations of the position from the rest
const NAG_MENU_BREAKS: ReadonlySet<number> = new Set([44]);

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

export type GameViewState = ReturnType<typeof useGameView>;

export function useGameView({
  selectedGame,
  initialOrientation,
  initialMoveToShow,
  readOnly = false,
  onGameReady,
  gameInfoServices,
  onQuotationClick,
  onGameLoaded,
  keysEnabled = true,
  notation = 'en',
  annotationKeys = true,
  notationBarGroups,
}: GameViewOptions) {
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

  // The latest onGameLoaded, which isn't a reason to load the game again
  const onGameLoadedRef = useRef(onGameLoaded);
  useEffect(() => {
    onGameLoadedRef.current = onGameLoaded;
  });

  // Load the game when one is selected and set edit mode
  useEffect(() => {
    if (selectedGame) {
      const loaded = loadGame(selectedGame.moves, selectedGame.tags, initialMoveToShow);
      if (loaded) {
        onGameLoadedRef.current?.(loaded);
      } else {
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
  const [reverseMoveMap, setReverseMoveMap] = useState<Map<number, MoveNode>>(new Map());
  // The notation's moves, to move between them with the keys: those of this game, of all on the page
  const notationRef = useRef<HTMLDivElement | null>(null);

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

  // Plays a move picked elsewhere than on the board, as from a list of moves, promotion and all
  const playMove = useCallback(
    (move: { from: string; to: string; promotion?: string }) => {
      if (!isEditMode) return;
      edit(() => {
        if (!game.play(move)) console.error('Invalid move:', move);
      });
    },
    [game, isEditMode, edit]
  );

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

  // Takes every annotation of the current move away
  const handleClearAnnotations = useCallback(() => {
    const move = game.currentMove();
    if (!move) return;
    edit(() => {
      move.annotations = [];
    });
  }, [game, edit]);

  // '+' and '-' step the evaluation of the position after the current move
  const handleEvaluationStep = useCallback(
    (step: 1 | -1) => {
      const move = game.currentMove();
      if (!move) return;
      edit(() => {
        move.annotations = stepEvaluation(move.annotations, step);
      });
    },
    [game, edit]
  );

  // '!' and '?' typed on the current move, and what was typed on it just before, to type on
  const typingRef = useRef<{ move: MoveNode; typed: string } | null>(null);
  const handleMoveCommentType = useCallback(
    (key: '!' | '?') => {
      const move = game.currentMove();
      if (!move) return;
      const typed = typingRef.current?.move === move ? typingRef.current.typed : null;
      let next: string | null = null;
      edit(() => {
        const result = typeMoveComment(move.annotations, key, typed);
        move.annotations = result.annotations;
        next = result.typed;
      });
      typingRef.current = next ? { move, typed: next } : null;
    },
    [game, edit]
  );
  const handleTypingInterrupt = useCallback(() => {
    typingRef.current = null;
  }, []);

  // Plays a null move from the position shown
  const handleNullMove = useCallback(() => edit(() => game.playNullMove()), [game, edit]);


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

  // The dialog for an annotation of a move, while it's open: the kind and the move
  const [annotationDialog, setAnnotationDialog] = useState<{ kind: AnnotationDialogKind; move: MoveNode } | null>(
    null
  );
  const handleAnnotationDialogSave = useCallback(
    (annotations: Annotation[]) => {
      const move = annotationDialog?.move;
      setAnnotationDialog(null);
      if (move) {
        edit(() => {
          move.annotations = annotations;
        });
      }
    },
    [annotationDialog, edit]
  );
  const handleAnnotationDialogCancel = useCallback(() => setAnnotationDialog(null), []);

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
      NAG_PALETTE.find((group) => group.type === type)!.nags.flatMap((nag): ContextMenuItem[] => [
        {
          label: nagInfo(nag)!.name,
          symbol: nagInfo(nag)!.symbol,
          shortcut: shortcutOf(NAG_KEYS, nag),
          onSelect: annotate((annotations) => toggleNag(annotations, nag)),
        },
        ...(NAG_MENU_BREAKS.has(nag) ? ['separator' as const] : []),
      ]);
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
      {
        // In the comment after in no language
        label: hasDiagramComment(move.annotations) ? 'Remove Diagram' : 'Insert Diagram',
        symbol: <TbBorderAll />,
        shortcut: DIAGRAM_KEY,
        onSelect: annotate(toggleDiagram),
      },
      'separator',
      {
        label: 'Insert Null Move',
        symbol: <TbCircleOff />,
        shortcut: NULL_MOVE_KEY,
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
          'separator',
          {
            label: 'Move Time…',
            symbol: <TbClock />,
            onSelect: () => setAnnotationDialog({ kind: 'time', move }),
          },
          {
            label: 'Set Medal…',
            symbol: <TbMedal />,
            onSelect: () => setAnnotationDialog({ kind: 'medals', move }),
          },
          {
            label: 'Enter Web Link…',
            symbol: <TbLink />,
            onSelect: () => setAnnotationDialog({ kind: 'webLink', move }),
          },
          {
            label: 'Variation Colour…',
            symbol: <TbPalette />,
            onSelect: () => setAnnotationDialog({ kind: 'variationColor', move }),
          },
        ],
      },
      'separator',
      {
        // Every annotation of the move: comments in every language, symbols, squares and the rest
        label: 'Clear Annotations',
        symbol: <TbEraser />,
        disabled: move.annotations.length === 0,
        onSelect: annotate(() => []),
      },
    ];
  }, [game, version, moveMenu, edit, editHistory, handleUndo, handleRedo, handleCommentEdit]);

  // The evaluations of the main line, shown as a graph above the bar
  const evaluationBars = useMemo(() => {
    const evaluations = findAnnotation(game.root.annotations, 'evaluations');
    return evaluations ? evalBars(game.root, evaluations, notation) : null;
  }, [game, version, notation]);

  // The things that can be done from the current move in the bar below the notation: to the
  // moves, then to the annotations
  const barActionGroups = useMemo((): { id: NotationBarGroup; actions: MoveAction[] }[] => {
    const move = game.currentMove();
    return [
      { id: 'moves', actions: moveActions(game, move, edit) },
      { id: 'comments', actions: [
        {
          label: 'Add Comment Before Move',
          icon: '{',
          shortcut: shortcutOf(COMMENT_KEYS, 'textBefore'),
          disabled: false,
          run: () => handleCurrentCommentEdit('textBefore'),
        },
        {
          label: 'Add Comment After Move',
          icon: '}',
          shortcut: shortcutOf(COMMENT_KEYS, 'textAfter'),
          disabled: false,
          run: () => handleCurrentCommentEdit('textAfter'),
        },
        {
          label: 'Insert Null Move',
          icon: <TbCircleOff />,
          shortcut: NULL_MOVE_KEY,
          disabled: !game.canPlayNullMove(),
          run: handleNullMove,
        },
        {
          label: 'Clear Annotations',
          icon: <TbEraser />,
          disabled: !move || move.annotations.length === 0,
          run: handleClearAnnotations,
        },
      ] },
    ];
  }, [game, version, edit, handleCurrentCommentEdit, handleNullMove, handleClearAnnotations]);

  // The keys are the dialog's while one is open, and no one's while the view isn't the active one
  const keysActive = keysEnabled && !editingGameInfo && !annotationDialog;

  // !, ? and = toggle those symbols on the current move, when editing
  useNagKeys(isEditMode && !!selectedGame && keysActive && annotationKeys, {
    onToggle: handleNagToggle,
    onStep: handleEvaluationStep,
    onType: handleMoveCommentType,
    onInterrupt: handleTypingInterrupt,
  });
  useKeyShortcut(isEditMode && !!selectedGame && keysActive && !editingComment, NULL_MOVE_KEY, handleNullMove);
  // A diagram of the position shown, or none if it has one
  const handleCurrentDiagram = useCallback(
    () =>
      edit(() => {
        const node = game.currentNode();
        node.annotations = toggleDiagram(node.annotations);
      }),
    [game, edit]
  );
  useCommentKeys(
    isEditMode && !!selectedGame && keysActive && annotationKeys && !editingComment,
    handleCurrentCommentEdit,
    handleCurrentDiagram
  );
  // Cmd+↑, Delete, ] and [ promote or delete the variation, or delete the moves after or before
  const currentMoveActions = useCallback(
    () => moveActions(game, game.currentMove(), edit),
    [game, edit]
  );
  useMoveActionKeys(isEditMode && !!selectedGame && keysActive && !editingComment, currentMoveActions);
  useUndoKeys(isEditMode && !!selectedGame && keysActive && !editingComment, handleUndo, handleRedo);

  // Keyboard navigation
  useKeyboardNavigation({
    enabled: !!selectedGame && keysActive,
    canGoBack,
    canGoForward,
    goToPreviousMove,
    goToNextMove: handleNextMove,
    goToStart,
    goToEnd,
    seekToMove,
    reverseMoveMap,
    notationRef,
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

  return {
    selectedGame,
    game,
    version,
    /** Shows the game again after it was changed from outside, as by GameTree.restore */
    refresh: triggerUpdate,
    gameInfoServices,
    onQuotationClick,
    // The board
    isEditMode,
    boardOrientation,
    flipBoard: () => setBoardOrientation((o) => (o === 'white' ? 'black' : 'white')),
    promotionPending,
    promotionPreviewFen,
    legalMoves,
    autoShapes,
    userDrawnShapes,
    handleMove,
    playMove,
    handlePromotionSelect,
    handlePromotionCancel,
    handleDrawableChange,
    canGoBack,
    canGoForward,
    goToStart,
    goToPreviousMove,
    handleNextMove,
    goToEnd,
    seekToMove,
    notationRef,
    /** The squares the last move went from and to, or null at the start */
    getLastMove,
    // Undoing and redoing the edits; whether they can be done is asked when it's needed, as the
    // history isn't state
    handleUndo,
    handleRedo,
    canUndo: () => editHistory().canUndo,
    canRedo: () => editHistory().canRedo,
    // The notation
    openGameInfo: () => setEditingGameInfo(readGameInfo(game)),
    languages,
    shownLanguages,
    language,
    handleLanguageSelect,
    handleMoveClick,
    handleMoveContextMenu,
    editingComment,
    handleCommentEdit,
    handleCommentEditDone,
    handleNotationReady,
    moveMenu,
    moveMenuItems,
    handleMoveMenuClose,
    choosingMove,
    handleMoveChosen,
    handleMoveChoiceCancel,
    evaluationBars,
    handleNagToggle,
    barActionGroups,
    notationBarGroups,
    notation,
    // The dialogs
    annotationDialog,
    handleAnnotationDialogSave,
    handleAnnotationDialogCancel,
    editingGameInfo,
    handleGameInfoSave,
    handleGameInfoCancel,
  };
}
