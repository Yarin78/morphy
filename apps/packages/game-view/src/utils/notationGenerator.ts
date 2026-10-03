import { GameTree } from '../model/GameTree';
import type { GameNode, MoveNode } from '../model/GameTree';
import type { Annotation, AnnotationOf } from '../model/annotations';
import { filterAnnotations, findAnnotation, GAME_ANNOTATION_INDEX } from '../model/annotations';
import { isCommentShown } from '../model/comments';
import type { CommentType } from '../model/comments';
import { annotationMarkers, annotationMarkersHtml } from './annotationMarkers';
import { figurinesToHtml } from './figurines';
import { escapeHtml } from './html';
import { commentWithDiagramsHtml, diagramHtml, pawnStructureHtml, piecePathHtml } from './diagram';
import { moveInfoHtml } from './moveInfo';
import { medalsHtml } from './medals';
import { quoteHtml, webLinkHtml } from './references';
import { isPrefixNag, nagInfo } from '../model/nags';

interface ConversionState {
  lineIndexByLevel: Map<number, number>;
  nodeIndex: number;
  moveMap: Map<MoveNode, number>; // Map move -> global move index
  reverseMoveMap: Map<number, MoveNode>; // Map global move index -> move (for reverse lookups)
  currentMove: MoveNode | null; // The move that should be highlighted
  languages: ReadonlySet<string>; // The languages of the comments that are shown
  quoteLinks: QuotationLink[] | null; // The quoted games that can be opened, or null if none can
  mainLineStart?: string; // HTML to start the main line with, on the row of its first moves
  folded: ReadonlySet<MoveNode>; // The first moves of the variations that are folded
  editing: CommentEdit | null; // The comment being edited, shown as an editor instead
}

/**
 * A comment being edited: of a move, or of the game for the start position, its kind, and its
 * language, null for none.
 */
export interface CommentEdit {
  node: GameNode;
  type: CommentType;
  language: string | null;
}

/** The editor of the comment being edited, with its text, to be made editable. */
function commentEditorHtml(text: string): string {
  return `<span class="cbcomment-editor" data-comment-editor>${escapeHtml(text)}</span>`;
}

/** The attributes telling which comment a span of comment text is of: its move's index, kind and language. */
function commentAttrs(moveIndex: number, type: CommentType, language: string | undefined): string {
  return ` data-comment-move="${moveIndex}" data-comment-type="${type}" data-comment-language="${language ?? ''}"`;
}

/**
 * The comments of a kind of a move, or of the game, that are shown, each on its own, with a
 * diagram where one asks for it. The one being edited is an editor instead, or one is added after
 * them for a new comment.
 *
 * @param moveIndex the index of the move, or -1 for the game
 * @param span the HTML of a span of comment text, given its HTML and the attributes telling which
 *     comment it is of
 * @param textHtml the HTML of the text of a comment
 * @param diagram the HTML of a diagram
 */
function commentsHtml(
  node: GameNode,
  type: CommentType,
  moveIndex: number,
  state: ConversionState,
  span: (html: string, attrs: string) => string,
  textHtml: (text: string) => string,
  diagram: () => string
): string {
  const editing = state.editing?.node === node && state.editing.type === type ? state.editing : null;
  const parts: string[] = [];
  let edited = false;
  for (const comment of filterAnnotations(node.annotations, type)) {
    if (editing && (comment.language ?? null) === editing.language && !edited) {
      parts.push(commentEditorHtml(comment.text));
      edited = true;
      continue;
    }
    const text = comment.text.trim();
    if (!text || !isCommentShown(comment, state.languages)) continue;
    const attrs = commentAttrs(moveIndex, type, comment.language);
    parts.push(commentWithDiagramsHtml(text, (piece) => span(textHtml(piece), attrs), diagram));
  }
  if (editing && !edited) parts.push(commentEditorHtml(''));
  return parts.join(' ');
}

/**
 * Whether a variation is shown folded, as just its first move: when it's folded, unless the
 * current move is one of the moves hidden by it, which are shown so the current move can be seen.
 */
function isFolded(first: MoveNode, state: ConversionState): boolean {
  if (!state.folded.has(first)) return false;
  for (let m = state.currentMove; m; m = GameTree.previous(m)) {
    if (m.parent === first) return false;
  }
  return true;
}

/** A quoted game that can be opened: the quotation, and the position its move leads to. */
export interface QuotationLink {
  quote: AnnotationOf<'quote'>;
  fen: string;
}

export type NotationHtmlResult = {
  html: string;
  moveMap: Map<MoveNode, number>; // Map move -> global move index
  reverseMoveMap: Map<number, MoveNode>; // Map global move index -> move (for reverse lookups)
  quoteLinks: QuotationLink[]; // The quoted games that can be opened, by data-quote-index
};

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

/** The web links and quoted games of a move, in the order they're in. */
/** The web links of a move, in the order they're in. */
function webLinksHtml(annotations: readonly Annotation[]): string {
  return annotations.map((a) => (a.type === 'webLink' ? webLinkHtml(a) : '')).join('');
}

/** The quoted games of a move, in the order they're in. */
function quotesHtml(annotations: readonly Annotation[], fen: string, state: ConversionState): string {
  return annotations
    .map((a) => {
      if (a.type !== 'quote') return '';
      // A quotation without moves refers to another game, which can be opened if links are wanted
      if (state.quoteLinks && !a.moves?.trim()) {
        state.quoteLinks.push({ quote: a, fen });
        return quoteHtml(a, state.quoteLinks.length - 1);
      }
      return quoteHtml(a);
    })
    .join('');
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
  if (level === 0 && state.mainLineStart) {
    parts.push(state.mainLineStart);
    state.mainLineStart = undefined;
  }
  // A variation can be folded to just its first move, with a button at its start
  const folded = level > 0 && isFolded(move, state);
  if (level > 0) {
    parts.push(
      `<span class="cbfold" role="button" tabindex="-1" data-fold-move="${state.moveMap.get(move)}" ` +
        `aria-expanded="${!folded}" title="${folded ? 'Unfold' : 'Fold'} the variation"></span>`
    );
  }
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

    const commentSpan = (html: string, attrs: string) =>
      `<span class="cbcomment${commentColor ? ' cbvarcolor' : ''}"${colorStyle(commentColor)} data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}"${attrs}>${html}</span>`;

    // A diagram in a comment before the move is of the position before it
    const commentsBefore = folded
      ? ''
      : commentsHtml(
          m,
          'textBefore',
          globalMoveIndex,
          state,
          commentSpan,
          (text) => figurinesToHtml(escapeHtml(text)),
          () => diagramHtml(m.parent.fen, m.parent.annotations)
        );
    if (commentsBefore) {
      parts.push(commentsBefore);
    }

    parts.push(
      `<span class="${moveClasses.join(' ')}"${colorStyle(color)} data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}" data-nodecnt="${currentNodeIndex}" data-global-move-index="${globalMoveIndex}"${criticalTitle}>${moveContent}</span>`
    );

    // A folded variation shows nothing after its first move
    if (folded) break;

    // Add color marker for moves with graphical annotations (colored squares or arrows)
    if (hasGraphics(m.annotations)) {
      parts.push(`<span class="cbcol-marker" data-inx-mv="${localMoveIndex}"> </span>`);
    }

    // Medals
    const medals = medalsHtml(findAnnotation(m.annotations, 'medals')?.medals ?? []);
    if (medals) {
      parts.push(medals);
    }

    // The pawn structure, shown when its symbol is hovered over
    if (findAnnotation(m.annotations, 'pawnStructure')) {
      parts.push(pawnStructureHtml(m.fen));
    }

    // The path of a piece, shown when its symbol is hovered over
    const path = findAnnotation(m.annotations, 'piecePath');
    if (path) {
      parts.push(piecePathHtml(m, path.square));
    }

    // Clock times, the time spent and evaluations
    const moveInfo = moveInfoHtml(m.annotations);
    if (moveInfo) {
      parts.push(moveInfo);
    }

    // Web links
    const webLinks = webLinksHtml(m.annotations);
    if (webLinks) {
      parts.push(webLinks);
    }

    // The annotations not shown otherwise
    const markers = annotationMarkersHtml(annotationMarkers(m.annotations, hasGlyph));
    if (markers) {
      parts.push(markers);
    }

    const commentsAfter = commentsHtml(
      m,
      'textAfter',
      globalMoveIndex,
      state,
      commentSpan,
      (text) => figurinesToHtml(processCommentWithLink(text)),
      () => diagramHtml(m.fen, m.annotations)
    );
    if (commentsAfter) {
      parts.push(commentsAfter);
    }

    // Quoted games, after the comments
    const quotes = quotesHtml(m.annotations, m.fen, state);
    if (quotes) {
      parts.push(quotes);
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
 * Every variation starts with a button to fold or unfold it, with the index of its first move as
 * data-fold-move.
 *
 * @param folded the first moves of the variations shown folded, as just their first move; one
 *     the current move is hidden in is shown unfolded
 * @param editing the comment being edited, shown as an element with data-comment-editor holding
 *     its text, to be made editable. Every other comment's text has the index of its move (-1 for
 *     the game) as data-comment-move, its kind as data-comment-type, and its language, or '' for
 *     none, as data-comment-language
 */
export function generateNotationHtml(
  game: GameTree,
  languages: readonly string[] = [],
  linkQuotes = false,
  folded: ReadonlySet<MoveNode> = new Set(),
  editing: CommentEdit | null = null
): NotationHtmlResult {
  const state: ConversionState = {
    lineIndexByLevel: new Map(),
    nodeIndex: 0,
    moveMap: new Map(),
    reverseMoveMap: new Map(),
    currentMove: game.currentMove(),
    languages: new Set(languages),
    quoteLinks: linkQuotes ? [] : null,
    folded,
    editing,
  };
  game.movesInOrder().forEach((move, index) => {
    state.moveMap.set(move, index);
    state.reverseMoveMap.set(index, move);
  });

  const parts: string[] = [];
  // The comments of the game, before the first move
  for (const type of ['textBefore', 'textAfter'] as const) {
    const comments = commentsHtml(
      game.root,
      type,
      GAME_ANNOTATION_INDEX,
      state,
      (html, attrs) => `<span class="cbcomment" data-inx-mv="0" data-linecnt="1"${attrs}>${html}</span>`,
      (text) => figurinesToHtml(processCommentWithLink(text)),
      () => diagramHtml(game.root.fen, game.root.annotations)
    );
    if (comments) {
      parts.push(comments);
    }
  }

  const gameMedals = medalsHtml(findAnnotation(game.root.annotations, 'medals')?.medals ?? []);
  if (gameMedals) {
    parts.push(gameMedals);
  }
  if (findAnnotation(game.root.annotations, 'pawnStructure')) {
    parts.push(pawnStructureHtml(game.root.fen));
  }
  const gameInfo = moveInfoHtml(game.root.annotations, true);
  if (gameInfo) {
    parts.push(gameInfo);
  }
  const gameReferences =
    webLinksHtml(game.root.annotations) + quotesHtml(game.root.annotations, game.root.fen, state);
  if (gameReferences) {
    parts.push(gameReferences);
  }
  const gameMarkers = annotationMarkersHtml(annotationMarkers(game.root.annotations, hasGlyph));
  if (gameMarkers) {
    parts.push(gameMarkers);
  }

  // The color marker of the colored squares and arrows of the start position, just before the
  // first move, in the main line so it's on the same row
  if (hasGraphics(game.root.annotations)) {
    state.mainLineStart = `<span class="cbcol-marker" data-inx-mv="0"> </span>`;
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
    quoteLinks: state.quoteLinks ?? [],
  };
}
