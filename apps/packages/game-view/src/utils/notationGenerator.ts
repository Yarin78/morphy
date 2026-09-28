import { Chess, CommentType } from '@jackstenglein/chess';
import type { Move } from '@jackstenglein/chess';

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
  globalMoveIndex: number;
  moveMap: Map<Move, number>; // Map Move object -> global move index
  reverseMoveMap: Map<number, Move>; // Map global move index -> Move object (for reverse lookups)
  currentMove: Move | null; // The move that should be highlighted
}

export type NotationHtmlResult = {
  html: string;
  moveMap: Map<Move, number>; // Map Move object -> global move index
  reverseMoveMap: Map<number, Move>; // Map global move index -> Move object (for reverse lookups)
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

// TODO: Language-tagged commentary ([%pre_XXX ...] and [%post_XXX ...], see
// morphy-cbh/docs/ANNOTATION-TEXT-ENCODING.md) is always shown in English, and text in other
// languages is hidden. Make the language selectable. Also, @jackstenglein/chess drops the commands
// of a comment at the start of a variation, so language-tagged text there is lost (and is lost
// when the game is saved); this goes away once the library is replaced.
const DISPLAY_LANGUAGE = 'ENG';

type CommandMap = Record<string, unknown> | undefined;

/**
 * Reverses the escaping the server applies to text inside a [%...] command:
 * \) is ']', \< is '{', \> is '}', and \x is x for any other character.
 */
function unescapeCommandText(text: string): string {
  return text.replace(/\\(.)/gs, (_, c: string) => (c === ')' ? ']' : c === '<' ? '{' : c === '>' ? '}' : c));
}

function commandText(commands: CommandMap, name: string): string {
  const value = commands?.[name];
  return typeof value === 'string' ? unescapeCommandText(value.trim()) : '';
}

function joinTexts(...texts: string[]): string {
  return texts.filter((t) => t).join(' ');
}

/** The before-move text held in the [%pre] and [%pre_XXX] commands of a comment. */
function beforeMoveCommandText(commands: CommandMap): string {
  return joinTexts(commandText(commands, 'pre'), commandText(commands, `pre_${DISPLAY_LANGUAGE}`));
}

/**
 * The text of the comment before a move. The chess library keeps only the plain text of such a
 * comment on the move itself; its commands end up on the previous move in the same line.
 */
function commentBeforeMove(chess: Chess, m: Move): string {
  const previousCommands = m.previous && m.previous.next === m ? m.previous.commentDiag : undefined;
  return joinTexts(chess.getComment(CommentType.Before, m), beforeMoveCommandText(previousCommands));
}

function commentAfterMove(chess: Chess, m: Move): string {
  return joinTexts(
    chess.getComment(CommentType.After, m),
    commandText(m.commentDiag, `post_${DISPLAY_LANGUAGE}`)
  );
}

/** The text of the comment before the game's first move. */
function gameComment(chess: Chess): string {
  return joinTexts(
    chess.getComment(CommentType.Before, null),
    beforeMoveCommandText(chess.pgn.gameComment)
  );
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
function formatMoveWithNumber(move: Move, moveIndex: number, hasVariationsBefore: boolean): string {
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

/**
 * Recursively traverses the game tree and generates HTML
 */
function traverseGameTree(
  chess: Chess,
  move: Move | null,
  level: number,
  lineIndex: number,
  nodeIndex: number,
  lineDepth: number,
  parentMoveIndex: number,
  isLast: boolean,
  state: ConversionState
): string {
  const parts: string[] = [];

  // Collect moves in this line
  const moves: Move[] = [];
  const variations: any[][] = [];

  let currentMove = move;
  const lineStartGlobalMoveIndex = state.globalMoveIndex;

  // Traverse the main line
  while (currentMove) {
    moves.push(currentMove);
    variations.push(currentMove.variations || []);
    currentMove = currentMove.next;
  }

  state.globalMoveIndex += moves.length;

  // Count moves and variations
  const moveCount = moves.length;
  const variationCount = variations.reduce((sum, v) => sum + v.length, 0);

  // Check if line has comments (before or after moves)
  // For the main line (level 0) starting at the first move, also check for game-level comment
  let hasComments = moves.some((m) => commentBeforeMove(chess, m) || commentAfterMove(chess, m));

  // If this is the main line and we're at the first move (parentMoveIndex === 0), check for game-level comment
  if (level === 0 && parentMoveIndex === 0 && moves.length > 0) {
    if (gameComment(chess)) {
      hasComments = true;
    }
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
    const moveVariations = variations[i] || [];

    state.moveMap.set(m, lineStartGlobalMoveIndex + i);
    state.reverseMoveMap.set(lineStartGlobalMoveIndex + i, m);

    // Check if previous move had variations (for formatting black moves after variations)
    // This applies to both main line and variations (for nested variations)
    const hasVariationsBefore = i > 0 && variations[i - 1]?.length > 0;

    // Get move text and format with move number
    const formattedMove = formatMoveWithNumber(m, localMoveIndex, hasVariationsBefore);
    const moveClasses = ['cbmove'];
    // Add highlight class if this is the current move
    // Also highlight first move if at start position (currentMove === null) and this is the main line
    if (state.currentMove === m || (state.currentMove === null && level === 0 && i === 0)) {
      moveClasses.push('cbcur-move');
    }
    let moveContent = escapeHtml(formattedMove);

    // Add NAG glyphs if present
    // NAGs are stored as strings like '$1', '$14' in the nags array
    if (m.nags && m.nags.length > 0) {
      for (const nagString of m.nags) {
        // Parse the NAG string (e.g., '$1' -> 1)
        const nagNumber = parseInt(nagString.replace('$', ''), 10);
        if (!isNaN(nagNumber)) {
          const glyph = nagToGlyph(nagNumber);
          if (glyph) {
            moveContent += `<span class="cbspec-glyph">${escapeHtml(glyph)}</span>`;
          }
        }
      }
    }

    // Add comment before move if present
    const commentBefore = commentBeforeMove(chess, m);
    if (commentBefore) {
      parts.push(
        `<span class="cbcomment" data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}">${escapeHtml(commentBefore)}</span>`
      );
    }

    parts.push(
      `<span class="${moveClasses.join(' ')}" data-inx-mv="${localMoveIndex}" data-linecnt="${lineDepth}" data-nodecnt="${currentNodeIndex}" data-global-move-index="${lineStartGlobalMoveIndex + i}">${moveContent}</span>`
    );

    // Add color marker for moves with graphical annotations (colored squares or arrows)
    // Graphical annotations are stored in commentDiag with colorFields (squares) or colorArrows (arrows)
    const hasGraphicalAnnotations = m.commentDiag && (
      (m.commentDiag.colorFields && m.commentDiag.colorFields.length > 0) ||
      (m.commentDiag.colorArrows && m.commentDiag.colorArrows.length > 0)
    );
    if (hasGraphicalAnnotations) {
      parts.push(`<span class="cbcol-marker" data-inx-mv="${localMoveIndex}"> </span>`);
    }

    // Add comment after move if present
    const commentAfter = commentAfterMove(chess, m);
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
      if (i === 0 || (i > 0 && variations[i - 1]?.length === 0)) {
        state.nodeIndex++;
        varNodeIndex = state.nodeIndex;
      }

      const varLevel = level + 1;
      let varLineIndex = state.lineIndexByLevel.get(varLevel) || 0;

      for (let vIdx = 0; vIdx < moveVariations.length; vIdx++) {
        const variation = moveVariations[vIdx];
        const isLastVar = vIdx === moveVariations.length - 1;

        state.lineIndexByLevel.set(varLevel, varLineIndex);

        // Variation is an array of moves - traverse them
        // We need to build a linked list structure for the variation
        // The Chess library stores variations as arrays, so we need to link them
        const varMoveHead = variation[0];
        if (variation.length > 1) {
          // Link moves in variation
          for (let j = 0; j < variation.length - 1; j++) {
            variation[j].next = variation[j + 1];
          }
        }

        // Recursively process variation
        const varHtml = traverseGameTree(
          chess,
          varMoveHead,
          varLevel,
          varLineIndex,
          varNodeIndex,
          lineDepth + 1,
          localMoveIndex,
          isLastVar,
          state
        );

        parts.push(varHtml);
        varLineIndex++;
      }

      state.lineIndexByLevel.set(varLevel, varLineIndex);
    }

    localMoveIndex++;
  }

  return `<span ${lineAttrs}>${parts.join('  ')}</span>`;
}

/**
 * Generates ChessBase-style HTML notation from a Chess game instance
 * The chess instance should already have the game loaded via loadPgn()
 * @param chess - The Chess instance with the game loaded
 * @param currentMove - Optional Move object to highlight. If null, highlights the first move (start position)
 */
export function generateNotationHtml(chess: Chess): NotationHtmlResult {
  const state: ConversionState = {
    lineIndexByLevel: new Map(),
    nodeIndex: 0,
    globalMoveIndex: 0,
    moveMap: new Map(),
    reverseMoveMap: new Map(),
    currentMove: chess.currentMove(),
  };
  // Get first move directly without modifying chess state
  const firstMove = chess.firstMove();

  if (!firstMove) {
    return {
      html: '<div class="nota-game" data-inx-game="0"></div>',
      moveMap: new Map(),
      reverseMoveMap: new Map(),
    };
  }

  // Check for game-level comment before the first move
  // Comments before the first move are stored as game comments, accessible via getComment(null)
  const gameCommentBefore = gameComment(chess);
  const parts: string[] = [];

  // Initialize line index for level 0
  state.lineIndexByLevel.set(0, 0);

  // Generate main line HTML
  const mainLineHtml = traverseGameTree(
    chess,
    firstMove,
    0,      // level
    0,      // lineIndex
    0,      // nodeIndex
    1,      // lineDepth
    0,      // parentMoveIndex
    false,  // isLast
    state
  );

  // If there's a game-level comment before the first move, prepend it
  if (gameCommentBefore) {
    parts.push(
      `<span class="cbcomment" data-inx-mv="0" data-linecnt="1">${escapeHtml(gameCommentBefore)}</span>`
    );
  }
  parts.push(mainLineHtml);

  // Add game result at the end if available
  const headers = chess.header() || {};
  const result = headers.getRawValue('Result');
  if (result && result !== '?' && result !== '???' && result.trim() !== '' && result !== '*') {
    // Format result similar to GameHeader component
    let formattedResult = result;
    if (result === '1/2-1/2') {
      formattedResult = '½-½';
    }
    parts.push(
      `<span class="cbnota-result">${escapeHtml(formattedResult)}</span>`
    );
  }

  const html = `<div class="nota-game" data-inx-game="0">${parts.join('  ')}</div>`;

  return {
    html,
    moveMap: state.moveMap,
    reverseMoveMap: state.reverseMoveMap,
  };
}
