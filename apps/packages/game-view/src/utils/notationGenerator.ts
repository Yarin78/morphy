import { GameTree } from '../model/GameTree';
import type { MoveNode } from '../model/GameTree';
import type { Annotation } from '../model/annotations';
import { filterAnnotations, findAnnotation } from '../model/annotations';

// NAG (Numeric Annotation Glyph) mapping
// Based on PGN Standard NAGs: https://en.wikipedia.org/wiki/Portable_Game_Notation#Standard_NAGs
const NAG_MAP: Record<number, string> = {
  // Move quality annotations
  1: '!',      // good move
  2: '?',      // bad move
  3: '!!',     // brilliant move
  4: '??',     // blunder
  5: '!?',     // interesting move
  6: '?!',     // dubious move

  // Positional evaluations
  10: '=',     // equal position
  13: '∞',     // unclear position
  14: '⩲',     // white has a slight advantage
  15: '⩱',     // black has a slight advantage
  16: '±',     // white has a moderate advantage
  17: '∓',     // black has a moderate advantage
  18: '+−',    // white has a decisive advantage
  19: '−+',    // black has a decisive advantage

  22: '⨀',     // white is in zugzwang
  23: '⨀',     // black is in zugzwang

  26: '○',     // white has moderate space advantage
  27: '○',     // black has moderate space advantage

  32: '⟳',     // white has a slight development advantage
  33: '⟳',     // black has a slight development advantage

  // Initiative
  36: '↑',     // white has a slight initiative
  37: '↓',     // black has a slight initiative

  // Time pressure
  40: '→',     // white has the attack
  41: '→',     // black has the attack

  44: '⯹',     // white has sufficient compensation for material deficit
  45: '⯹',     // black has sufficient compensation for material deficit

  // Counterplay
  132: '⇆',    // white has moderate counterplay
  133: '⇆',    // black has moderate counterplay
};

interface ConversionState {
  lineIndexByLevel: Map<number, number>;
  nodeIndex: number;
  moveMap: Map<MoveNode, number>; // Map move -> global move index
  reverseMoveMap: Map<number, MoveNode>; // Map global move index -> move (for reverse lookups)
  currentMove: MoveNode | null; // The move that should be highlighted
}

export type NotationHtmlResult = {
  html: string;
  moveMap: Map<MoveNode, number>; // Map move -> global move index
  reverseMoveMap: Map<number, MoveNode>; // Map global move index -> move (for reverse lookups)
};

/**
 * Escapes HTML special characters
 */
function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * Converts NAG number to glyph symbol
 */
function nagToGlyph(nag: number): string {
  return NAG_MAP[nag] || '';
}

// TODO: Text in a language is shown only in English, and text in other languages is hidden. Make
// the language selectable.
const DISPLAY_LANGUAGE = 'ENG';

/** The text of the comments of a kind, in no language or the one shown. */
function commentText(annotations: readonly Annotation[], type: 'textBefore' | 'textAfter'): string {
  return filterAnnotations(annotations, type)
    .filter((a) => !a.language || a.language === DISPLAY_LANGUAGE)
    .map((a) => a.text.trim())
    .filter((t) => t)
    .join(' ');
}

/**
 * Processes comment text and converts [text](database-id:game-id:global-move-index) to a link
 * Returns HTML string with the comment text and link (if pattern found)
 * The global move index is optional. If present, it's added as a URL fragment.
 * The text part is used as the link text.
 */
function processCommentWithLink(comment: string): string {
  // Match [text](database-id:game-id:optional-global-move-index) at the end of the comment
  // Format: [link text](database-id:game-id:optional-global-move-index)
  const linkPattern = /\[([^\]]+)\]\(([^\]:]+):([^\]:]+)(?::(\d+))?\)$/;
  const match = comment.match(linkPattern);

  if (match) {
    const linkText = match[1]; // The text part inside the brackets
    const databaseId = match[2];
    const gameId = match[3];
    const globalMoveIndex = match[4]; // Optional fourth group
    const commentText = comment.slice(0, match.index).trim();

    // Build URL with optional fragment
    let linkUrl = `/game/${databaseId}/${gameId}`;
    if (globalMoveIndex) {
      linkUrl += `#${globalMoveIndex}`;
    }

    const html = commentText
      ? `${escapeHtml(commentText)} <a href="${escapeHtml(linkUrl)}" class="cbcomment-link">${escapeHtml(linkText)}</a>`
      : `<a href="${escapeHtml(linkUrl)}" class="cbcomment-link">${escapeHtml(linkText)}</a>`;

    return html;
  }

  // No pattern found, just escape the comment as before
  return escapeHtml(comment);
}

/**
 * Formats a move with move number
 */
function formatMoveWithNumber(move: MoveNode, moveIndex: number, hasVariationsBefore: boolean): string {
  // Calculate the actual move number (1-based)
  const moveNumber = Math.floor((move.ply + 1) / 2);

  const san = move.isNullMove ? "--" : move.san;

  if (move.ply % 2 == 0) {
    if (moveIndex == 0 || hasVariationsBefore) {
      return `${moveNumber}...${san}`;
    } else {
      return san;
    }
  } else {
    return `${moveNumber}.${san}`;
  }
}

/** The glyphs of the symbols of a move. */
function symbolsHtml(annotations: readonly Annotation[]): string {
  return (findAnnotation(annotations, 'symbols')?.nags ?? [])
    .map(nagToGlyph)
    .filter((glyph) => glyph)
    .map((glyph) => `<span class="cbspec-glyph">${escapeHtml(glyph)}</span>`)
    .join('');
}

function hasGraphics(annotations: readonly Annotation[]): boolean {
  return (
    (findAnnotation(annotations, 'squares')?.squares.length ?? 0) > 0 ||
    (findAnnotation(annotations, 'arrows')?.arrows.length ?? 0) > 0
  );
}

/**
 * Recursively traverses the game tree and generates HTML
 */
function traverseGameTree(
  game: GameTree,
  move: MoveNode,
  level: number,
  lineIndex: number,
  nodeIndex: number,
  lineDepth: number,
  parentMoveIndex: number,
  isLast: boolean,
  state: ConversionState
): string {
  const parts: string[] = [];

  // The moves of this line, and the alternatives to each of them
  const moves: MoveNode[] = [];
  let currentMove: MoveNode | undefined = move;
  while (currentMove) {
    moves.push(currentMove);
    currentMove = currentMove.children[0];
  }
  const variations = moves.map((m) => GameTree.alternatives(m));

  // Count moves and variations
  const moveCount = moves.length;
  const variationCount = variations.reduce((sum, v) => sum + v.length, 0);

  // Check if line has comments (before or after moves)
  // For the main line (level 0) starting at the first move, also check for game-level comment
  let hasComments = moves.some(
    (m) => commentText(m.annotations, 'textBefore') || commentText(m.annotations, 'textAfter')
  );
  if (level === 0 && parentMoveIndex === 0 && gameComment(game)) {
    hasComments = true;
  }

  // Generate line opening tag
  const lineAttrs = [
    `class="cbline"`,
    `data-level="${level}"`,
    `data-inx-line="${lineIndex}"`,
    `data-inx-mv-parent="${parentMoveIndex}"`,
    `data-commented="${hasComments ? '1' : '0'}"`,
    `data-linecnt-this="${moveCount}"`,
    `data-nodecnt-this="${variationCount}"`,
    `lastline="${isLast ? '1' : '0'}"`,
  ].join(' ');

  // Generate moves
  let localMoveIndex = 0;
  const currentNodeIndex = nodeIndex;

  for (let i = 0; i < moves.length; i++) {
    const m = moves[i];
    const moveVariations = variations[i];
    const globalMoveIndex = state.moveMap.get(m)!;

    // Check if previous move had variations (for formatting black moves after variations)
    // This applies to both main line and variations (for nested variations)
    const hasVariationsBefore = i > 0 && variations[i - 1].length > 0;

    // Get move text and format with move number
    const formattedMove = formatMoveWithNumber(m, localMoveIndex, hasVariationsBefore);
    const moveClasses = ['cbmove'];
    // Add highlight class if this is the current move
    // Also highlight first move if at start position (currentMove === null) and this is the main line
    if (state.currentMove === m || (state.currentMove === null && level === 0 && i === 0)) {
      moveClasses.push('cbcur-move');
    }
    const moveContent = escapeHtml(formattedMove) + symbolsHtml(m.annotations);

    const commentBefore = commentText(m.annotations, 'textBefore');
    if (commentBefore) {
      parts.push(
        `<span class="cbcomment" data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}">${escapeHtml(commentBefore)}</span>`
      );
    }

    parts.push(
      `<span class="${moveClasses.join(' ')}" data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}" data-nodecnt="${currentNodeIndex}" data-global-move-index="${globalMoveIndex}">${moveContent}</span>`
    );

    // Add color marker for moves with graphical annotations (colored squares or arrows)
    if (hasGraphics(m.annotations)) {
      parts.push(`<span class="cbcol-marker" data-inx-mv="${localMoveIndex}"> </span>`);
    }

    const commentAfter = commentText(m.annotations, 'textAfter');
    if (commentAfter) {
      parts.push(
        `<span class="cbcomment" data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}">${processCommentWithLink(commentAfter)}</span>`
      );
    }

    // Generate variations for this move
    if (moveVariations.length > 0) {
      // Assign node index for variations
      // All variations from the same parent move share the same nodeIndex
      let varNodeIndex = currentNodeIndex;
      if (i === 0 || variations[i - 1].length === 0) {
        state.nodeIndex++;
        varNodeIndex = state.nodeIndex;
      }

      const varLevel = level + 1;
      let varLineIndex = state.lineIndexByLevel.get(varLevel) || 0;

      for (let vIdx = 0; vIdx < moveVariations.length; vIdx++) {
        state.lineIndexByLevel.set(varLevel, varLineIndex);
        parts.push(
          traverseGameTree(
            game,
            moveVariations[vIdx],
            varLevel,
            varLineIndex,
            varNodeIndex,
            lineDepth + 1,
            localMoveIndex,
            vIdx === moveVariations.length - 1,
            state
          )
        );
        varLineIndex++;
      }

      state.lineIndexByLevel.set(varLevel, varLineIndex);
    }

    localMoveIndex++;
  }

  return `<span ${lineAttrs}>${parts.join('  ')}</span>`;
}

/** The text of the comments of the game as a whole, shown before the first move. */
function gameComment(game: GameTree): string {
  return [commentText(game.root.annotations, 'textBefore'), commentText(game.root.annotations, 'textAfter')]
    .filter((t) => t)
    .join(' ');
}

/**
 * Generates ChessBase-style HTML notation of a game, with the current move highlighted.
 *
 * Every move gets the index annotations use for it (see GameTree), as data-global-move-index.
 */
export function generateNotationHtml(game: GameTree): NotationHtmlResult {
  const state: ConversionState = {
    lineIndexByLevel: new Map(),
    nodeIndex: 0,
    moveMap: new Map(),
    reverseMoveMap: new Map(),
    currentMove: game.currentMove(),
  };
  game.movesInOrder().forEach((move, index) => {
    state.moveMap.set(move, index);
    state.reverseMoveMap.set(index, move);
  });

  const parts: string[] = [];
  const comment = gameComment(game);
  if (comment) {
    parts.push(`<span class="cbcomment" data-inx-mv="0" data-linecnt="1">${processCommentWithLink(comment)}</span>`);
  }

  const firstMove = game.firstMove();
  if (firstMove) {
    state.lineIndexByLevel.set(0, 0);
    parts.push(traverseGameTree(game, firstMove, 0, 0, 0, 1, 0, false, state));
  }

  // Add game result at the end if available
  const result = game.getTag('Result');
  if (result && result !== '?' && result !== '???' && result.trim() !== '' && result !== '*') {
    // Format result similar to GameHeader component
    const formattedResult = result === '1/2-1/2' ? '½-½' : result;
    parts.push(`<span class="cbnota-result">${escapeHtml(formattedResult)}</span>`);
  }

  return {
    html: `<div class="nota-game" data-inx-game="0">${parts.join('  ')}</div>`,
    moveMap: state.moveMap,
    reverseMoveMap: state.reverseMoveMap,
  };
}
