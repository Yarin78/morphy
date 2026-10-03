import { GameTree } from '../model/GameTree';
import type { MoveNode } from '../model/GameTree';
import type { Annotation } from '../model/annotations';
import { filterAnnotations, findAnnotation } from '../model/annotations';
import { annotationMarkers, annotationMarkersHtml } from './annotationMarkers';
import { figurinesToHtml } from './figurines';
import { moveInfoHtml } from './moveInfo';
import { isPrefixNag, nagInfo } from '../model/nags';

interface ConversionState {
  lineIndexByLevel: Map<number, number>;
  nodeIndex: number;
  moveMap: Map<MoveNode, number>; // Map move -> global move index
  reverseMoveMap: Map<number, MoveNode>; // Map global move index -> move (for reverse lookups)
  currentMove: MoveNode | null; // The move that should be highlighted
  languages: ReadonlySet<string>; // The languages of the comments that are shown
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
  return nagInfo(nag)?.symbol ?? '';
}

function hasGlyph(nag: number): boolean {
  return nagInfo(nag) !== undefined;
}

/** The text of the comments of a kind, in no language or one of those shown. */
function commentText(
  annotations: readonly Annotation[],
  type: 'textBefore' | 'textAfter',
  languages: ReadonlySet<string>
): string {
  return filterAnnotations(annotations, type)
    .filter((a) => !a.language || languages.has(a.language))
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

/** The glyphs of the symbols of a move, either those before the move or those after it. */
function symbolsHtml(annotations: readonly Annotation[], prefixes: boolean): string {
  return (findAnnotation(annotations, 'symbols')?.nags ?? [])
    .filter((nag) => isPrefixNag(nag) === prefixes)
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

/** A color given to the moves of a line from some move on, see the variationColor annotation. */
interface LineColor {
  color: string;
  onlyMoves: boolean;
  onlyMainline: boolean;
}

/** The color a move gives the line from it on, if it has one. */
function lineColor(annotations: readonly Annotation[]): LineColor | null {
  const annotation = findAnnotation(annotations, 'variationColor');
  return annotation && /^#[0-9a-fA-F]{6}$/.test(annotation.color) ? annotation : null;
}

/** The style attribute giving an element a line's color, through a custom property. */
function colorStyle(color: LineColor | null): string {
  return color ? ` style="--line-color: ${color.color}"` : '';
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
  state: ConversionState,
  inheritedColor: LineColor | null = null
): string {
  const parts: string[] = [];
  // The color of the moves from here on, if a variation color applies
  let color = inheritedColor;

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
    (m) => commentText(m.annotations, 'textBefore', state.languages) || commentText(m.annotations, 'textAfter', state.languages)
  );
  if (level === 0 && parentMoveIndex === 0 && gameComment(game, state.languages)) {
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
    color = lineColor(m.annotations) ?? color;
    const commentColor = color && !color.onlyMoves ? color : null;

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
    // A critical position is shown by the color of the move leading to it
    const critical = findAnnotation(m.annotations, 'critical');
    const criticalTitle = critical && critical.phase !== 'none' ? ` title="Critical position: ${critical.phase}"` : '';
    if (criticalTitle) {
      moveClasses.push(`cbcrit-${critical!.phase}`);
    }
    const prefix = symbolsHtml(m.annotations, true);
    const moveContent = (prefix ? prefix + ' ' : '') + escapeHtml(formattedMove) + symbolsHtml(m.annotations, false);
    if (color) {
      moveClasses.push('cbvarcolor');
    }

    const commentBefore = commentText(m.annotations, 'textBefore', state.languages);
    if (commentBefore) {
      parts.push(
        `<span class="cbcomment${commentColor ? ' cbvarcolor' : ''}"${colorStyle(commentColor)} data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}">${figurinesToHtml(escapeHtml(commentBefore))}</span>`
      );
    }

    parts.push(
      `<span class="${moveClasses.join(' ')}"${colorStyle(color)} data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}" data-nodecnt="${currentNodeIndex}" data-global-move-index="${globalMoveIndex}"${criticalTitle}>${moveContent}</span>`
    );

    // Add color marker for moves with graphical annotations (colored squares or arrows)
    if (hasGraphics(m.annotations)) {
      parts.push(`<span class="cbcol-marker" data-inx-mv="${localMoveIndex}"> </span>`);
    }

    // Clock times, the time spent and evaluations
    const moveInfo = moveInfoHtml(m.annotations);
    if (moveInfo) {
      parts.push(moveInfo);
    }

    // The annotations not shown otherwise
    const markers = annotationMarkersHtml(annotationMarkers(m.annotations, hasGlyph));
    if (markers) {
      parts.push(markers);
    }

    const commentAfter = commentText(m.annotations, 'textAfter', state.languages);
    if (commentAfter) {
      parts.push(
        `<span class="cbcomment${commentColor ? ' cbvarcolor' : ''}"${colorStyle(commentColor)} data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}">${figurinesToHtml(processCommentWithLink(commentAfter))}</span>`
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
            state,
            // The sublines take the color too, unless it's for its own line only
            color && !color.onlyMainline ? color : null
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
function gameComment(game: GameTree, languages: ReadonlySet<string>): string {
  return [
    commentText(game.root.annotations, 'textBefore', languages),
    commentText(game.root.annotations, 'textAfter', languages),
  ]
    .filter((t) => t)
    .join(' ');
}

/**
 * Generates ChessBase-style HTML notation of a game, with the current move highlighted.
 *
 * Every move gets the index annotations use for it (see GameTree), as data-global-move-index.
 */
export function generateNotationHtml(game: GameTree, languages: readonly string[] = []): NotationHtmlResult {
  const state: ConversionState = {
    lineIndexByLevel: new Map(),
    nodeIndex: 0,
    moveMap: new Map(),
    reverseMoveMap: new Map(),
    currentMove: game.currentMove(),
    languages: new Set(languages),
  };
  game.movesInOrder().forEach((move, index) => {
    state.moveMap.set(move, index);
    state.reverseMoveMap.set(index, move);
  });

  const parts: string[] = [];
  const comment = gameComment(game, state.languages);
  if (comment) {
    parts.push(`<span class="cbcomment" data-inx-mv="0" data-linecnt="1">${figurinesToHtml(processCommentWithLink(comment))}</span>`);
  }

  const gameInfo = moveInfoHtml(game.root.annotations, true);
  if (gameInfo) {
    parts.push(gameInfo);
  }
  const gameMarkers = annotationMarkersHtml(annotationMarkers(game.root.annotations, hasGlyph));
  if (gameMarkers) {
    parts.push(gameMarkers);
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
