import { Chess, DEFAULT_POSITION } from 'chess.js';
import type { Square } from 'chess.js';
import type { Annotation, AnnotationDto } from './annotations';
import { GAME_ANNOTATION_INDEX } from './annotations';

/** A position in the game: the start position, or the one after a move. */
export interface GameNode {
  /** The position as FEN. */
  readonly fen: string;
  /** The number of half moves played to reach the position, counted from the game's start. */
  readonly ply: number;
  /** The moves played from the position; the first is the main line, the others variations. */
  readonly children: MoveNode[];
  /** The annotations of the move leading here, or of the game for the start position. */
  annotations: Annotation[];
}

/** A move, and the position after it. */
export interface MoveNode extends GameNode {
  readonly parent: GameNode;
  readonly san: string;
  readonly from: Square;
  readonly to: Square;
  readonly promotion?: string;
  readonly isNullMove: boolean;
}

/** The moves of a game as the server sends them. */
export interface GameMoves {
  /** PGN movetext: the moves and variations, without comments, NAGs or result. */
  pgn?: string;
  /** The start position, or absent for the standard one. */
  fen?: string;
  annotations?: AnnotationDto[];
}

const RESULT_TOKENS = new Set(['1-0', '0-1', '1/2-1/2', '*']);

function isMoveNode(node: GameNode): node is MoveNode {
  return 'parent' in node;
}

/** The ply of a position: even when White is to move. */
function plyOf(fen: string): number {
  const [, turn, , , , fullMove] = fen.split(' ');
  return (Math.max(1, parseInt(fullMove, 10) || 1) - 1) * 2 + (turn === 'b' ? 1 : 0);
}

/**
 * A game: its PGN tags, and its moves with their variations and annotations, with the position
 * shown on the board. The tree is changed in place; whoever shows it re-renders on its own.
 *
 * Moves are numbered the way the server numbers the moves annotations belong to: in the order they
 * appear in the movetext, each variation right after the move it's an alternative to, before the
 * line it branches from goes on.
 */
/** The moves of a game and the move shown, as GameTree.snapshot gives them. */
export interface TreeSnapshot {
  moves: Required<Pick<GameMoves, 'pgn' | 'annotations'>> & { fen?: string };
  /** The index of the move shown in the order of the movetext, or null for the start position. */
  current: number | null;
}

export class GameTree {
  readonly root: GameNode;
  private readonly tags: Map<string, string>;
  private current: MoveNode | null = null;

  private constructor(fen: string, tags: Iterable<[string, string]>) {
    this.root = { fen, ply: plyOf(fen), children: [], annotations: [] };
    this.tags = new Map(tags);
  }

  /** An empty game from the standard start position. */
  static empty(): GameTree {
    return new GameTree(DEFAULT_POSITION, []);
  }

  /**
   * A game from its moves as the server sends them.
   *
   * @throws Error if a move is illegal, the movetext can't be read, or an annotation belongs to a
   *   move that isn't there
   */
  static fromMoves(moves: GameMoves | undefined, tags: Iterable<[string, string]> = []): GameTree {
    const tree = new GameTree(moves?.fen ? new Chess(moves.fen).fen() : DEFAULT_POSITION, tags);
    parseMovetext(moves?.pgn ?? '', tree.root);
    tree.attachAnnotations(moves?.annotations ?? []);
    return tree;
  }

  // ── Tags ────────────────────────────────────────────────────────────────

  /** The value of a PGN tag, or '' if it isn't set. */
  getTag(name: string): string {
    return this.tags.get(name) ?? '';
  }

  /** Sets a PGN tag; an empty value removes it. */
  setTag(name: string, value: string) {
    if (value) {
      this.tags.set(name, value);
    } else {
      this.tags.delete(name);
    }
  }

  /** The PGN tags that are set, in the order they were set. */
  tagValues(): Record<string, string> {
    return Object.fromEntries(this.tags);
  }

  // ── Navigation ──────────────────────────────────────────────────────────

  /** The move that led to the position shown, or null at the start position. */
  currentMove(): MoveNode | null {
    return this.current;
  }

  /** The position shown. */
  currentNode(): GameNode {
    return this.current ?? this.root;
  }

  /** Shows the position after a move, or the start position for null. */
  seek(move: MoveNode | null) {
    this.current = move;
  }

  /** The main move from the position shown, or null at the end of a line. */
  nextMove(): MoveNode | null {
    return this.currentNode().children[0] ?? null;
  }

  /** The first move of the game, or null if it has none. */
  firstMove(): MoveNode | null {
    return this.root.children[0] ?? null;
  }

  /** The move before a move, or null for the first move of the game. */
  static previous(move: MoveNode): MoveNode | null {
    return isMoveNode(move.parent) ? move.parent : null;
  }

  /** The other moves that could have been played instead of a main move, in their order. */
  static alternatives(move: MoveNode): MoveNode[] {
    return move.parent.children[0] === move ? move.parent.children.slice(1) : [];
  }

  // ── The position shown ──────────────────────────────────────────────────

  fen(): string {
    return this.currentNode().fen;
  }

  turn(): 'w' | 'b' {
    return this.fen().split(' ')[1] === 'b' ? 'b' : 'w';
  }

  /** The legal moves in the position shown. */
  legalMoves() {
    return new Chess(this.fen()).moves({ verbose: true });
  }

  /** The piece on a square of the position shown. */
  pieceAt(square: Square) {
    return new Chess(this.fen()).get(square);
  }

  /**
   * Plays a move from the position shown and shows the position after it. A move that is already
   * there is gone to; another one becomes a new variation, or the main line if there are no moves.
   *
   * @returns the move, or null if it isn't legal
   */
  play(move: { from: string; to: string; promotion?: string }): MoveNode | null {
    const node = this.currentNode();
    const existing = node.children.find(
      (child) =>
        child.from === move.from && child.to === move.to && (child.promotion ?? '') === (move.promotion ?? '')
    );
    if (existing) {
      this.current = existing;
      return existing;
    }
    const chess = new Chess(node.fen);
    try {
      chess.move(move);
    } catch {
      return null;
    }
    this.current = addMove(node, chess);
    return this.current;
  }

  /**
   * The first move of the variation a move is in: the move itself or the nearest one before it that
   * is not the main move from its position. Null for a move of the main line of the game.
   */
  static variationStart(move: MoveNode): MoveNode | null {
    for (let m: MoveNode | null = move; m; m = GameTree.previous(m)) {
      if (m.parent.children[0] !== m) return m;
    }
    return null;
  }

  /**
   * Makes the variation a move is in the main line from the position it starts at; the main line
   * from there becomes its first variation, and the other variations keep their order.
   *
   * @returns false if the move is in the main line of the game, which is left as it is
   */
  static promoteVariation(move: MoveNode): boolean {
    const start = GameTree.variationStart(move);
    if (!start) return false;
    const siblings = start.parent.children;
    siblings.splice(siblings.indexOf(start), 1);
    siblings.unshift(start);
    return true;
  }

  /** Whether a position is a move or comes after it, in its line or a variation of it. */
  private static isWithin(node: GameNode, move: MoveNode): boolean {
    for (let n: GameNode | null = node; n; n = isMoveNode(n) ? n.parent : null) {
      if (n === move) return true;
    }
    return false;
  }

  /** Shows a position, of a move or the start position, after the moves shown were deleted. */
  private showAfterDelete(node: GameNode) {
    this.current = isMoveNode(node) ? node : null;
  }

  /**
   * Deletes the variation a move is in, from its first move on; see variationStart. If the move
   * shown was in it, the position the variation started from is shown.
   *
   * @returns false if the move is in the main line of the game, which is left as it is
   */
  deleteVariation(move: MoveNode): boolean {
    const start = GameTree.variationStart(move);
    if (!start) return false;
    const siblings = start.parent.children;
    siblings.splice(siblings.indexOf(start), 1);
    if (this.current && GameTree.isWithin(this.current, start)) this.showAfterDelete(start.parent);
    return true;
  }

  /**
   * Deletes the moves after a move, the variations from the positions after it included. If the
   * move shown was one of them, the move is shown.
   */
  deleteRemainingMoves(move: MoveNode) {
    if (this.current && this.current !== move && GameTree.isWithin(this.current, move)) {
      this.showAfterDelete(move);
    }
    move.children.splice(0);
  }

  /**
   * Deletes the moves before a move, and the variations from them, so the game starts from the
   * position before it: a set-up position, with the move numbers going on from there. The move
   * becomes the first move of the game, the other moves from that position its variations. The
   * evaluations of the game, of the positions of the old main line, go too, and so do the comments
   * of the game, which are likely about the moves deleted; its other annotations stay. The start
   * position is shown, unless the move shown is still there.
   *
   * @returns false if the move is a first move of the game, with nothing before it
   */
  deletePreviousMoves(move: MoveNode): boolean {
    const start = move.parent;
    if (!isMoveNode(start)) return false;
    const keep = this.current && this.current !== start && GameTree.isWithin(this.current, start);
    const root = this.root as { fen: string; ply: number; children: MoveNode[]; annotations: Annotation[] };
    root.fen = start.fen;
    root.ply = start.ply;
    root.children = [move, ...start.children.filter((child) => child !== move)];
    for (const child of root.children) (child as { parent: GameNode }).parent = root;
    // The evaluations were of the old main line, and the comments likely about the moves deleted
    root.annotations = root.annotations.filter(
      (a) => a.type !== 'evaluations' && a.type !== 'textBefore' && a.type !== 'textAfter'
    );
    if (!keep) this.current = null;
    return true;
  }

  /** Whether a null move can be played from the position shown: not when in check. */
  canPlayNullMove(): boolean {
    return !new Chess(this.currentNode().fen).inCheck();
  }

  /**
   * Plays a null move, passing the turn, from the position shown and shows the position after it.
   * One that is already there is gone to; otherwise it's added like a move, see play.
   *
   * @returns the null move, or null if there can't be one, as when the side to move is in check
   */
  playNullMove(): MoveNode | null {
    const node = this.currentNode();
    const existing = node.children.find((child) => child.isNullMove);
    if (existing) {
      this.current = existing;
      return existing;
    }
    if (!this.canPlayNullMove()) return null;
    const chess = new Chess(node.fen);
    try {
      chess.move('--');
    } catch {
      return null;
    }
    this.current = addMove(node, chess);
    return this.current;
  }

  // ── Moves and annotations ───────────────────────────────────────────────

  /** All moves of the game, in the order of the movetext. */
  movesInOrder(): MoveNode[] {
    const moves: MoveNode[] = [];
    const line = (node: GameNode) => {
      let current: GameNode = node;
      while (current.children.length > 0) {
        const [main, ...variations] = current.children;
        moves.push(main);
        for (const variation of variations) {
          moves.push(variation);
          line(variation);
        }
        current = main;
      }
    };
    line(this.root);
    return moves;
  }

  /** The moves as PGN movetext, without comments, NAGs or result. */
  movetext(): string {
    const parts: string[] = [];
    const moveText = (move: MoveNode, withNumber: boolean) => {
      const moveNumber = Math.floor(move.parent.ply / 2) + 1;
      const white = move.parent.ply % 2 === 0;
      const prefix = white ? `${moveNumber}. ` : withNumber ? `${moveNumber}... ` : '';
      return prefix + move.san;
    };
    const line = (node: GameNode, numberFirst: boolean) => {
      let current: GameNode = node;
      let withNumber = numberFirst;
      while (current.children.length > 0) {
        const [main, ...variations] = current.children;
        parts.push(moveText(main, withNumber));
        for (const variation of variations) {
          parts.push('(' + moveText(variation, true));
          line(variation, false);
          parts[parts.length - 1] += ')';
        }
        withNumber = variations.length > 0;
        current = main;
      }
    };
    line(this.root, true);
    return parts.join(' ');
  }

  /** The moves and their annotations as the server takes them. */
  toMoves(): Required<Pick<GameMoves, 'pgn' | 'annotations'>> & { fen?: string } {
    const annotations: AnnotationDto[] = this.root.annotations.map((a) => ({ ...a, move: GAME_ANNOTATION_INDEX }));
    this.movesInOrder().forEach((move, index) => {
      annotations.push(...move.annotations.map((a) => ({ ...a, move: index })));
    });
    return {
      pgn: this.movetext(),
      fen: this.root.fen === DEFAULT_POSITION ? undefined : this.root.fen,
      annotations,
    };
  }

  /** The moves and the move shown, to go back to with restore. */
  snapshot(): TreeSnapshot {
    const current = this.current ? this.movesInOrder().indexOf(this.current) : -1;
    return { moves: this.toMoves(), current: current < 0 ? null : current };
  }

  /** Makes the moves and the move shown those of a snapshot. The tags are left as they are. */
  restore(snapshot: TreeSnapshot) {
    const restored = GameTree.fromMoves(snapshot.moves);
    const root = this.root as { fen: string; ply: number; children: MoveNode[]; annotations: Annotation[] };
    root.fen = restored.root.fen;
    root.ply = restored.root.ply;
    root.children = restored.root.children;
    root.annotations = restored.root.annotations;
    for (const child of root.children) (child as { parent: GameNode }).parent = root;
    this.current = snapshot.current === null ? null : (this.movesInOrder()[snapshot.current] ?? null);
  }

  private attachAnnotations(annotations: AnnotationDto[]) {
    if (annotations.length === 0) return;
    const moves = this.movesInOrder();
    for (const { move, ...annotation } of annotations) {
      const node = move === GAME_ANNOTATION_INDEX ? this.root : moves[move];
      if (!node) {
        throw new Error(`Annotation of move ${move}, but there are only ${moves.length} moves`);
      }
      node.annotations.push(annotation as Annotation);
    }
  }
}

function addMove(parent: GameNode, chessAfter: Chess): MoveNode {
  const played = chessAfter.history({ verbose: true }).at(-1)!;
  const isNullMove = played.san === '--';
  const node: MoveNode = {
    parent,
    san: played.san,
    from: played.from,
    to: played.to,
    promotion: played.promotion,
    isNullMove,
    fen: chessAfter.fen(),
    ply: parent.ply + 1,
    children: [],
    annotations: [],
  };
  parent.children.push(node);
  return node;
}

/**
 * Reads movetext into the moves from a position. Comments, NAGs, move suffixes like '!?' and a
 * result are skipped; the server sends none of them, but PGN pasted from elsewhere may have them.
 */
function parseMovetext(movetext: string, start: GameNode) {
  const tokens = movetext
    .replace(/\{[^}]*\}/g, ' ')
    .replace(/;[^\n]*/g, ' ')
    .replace(/[()]/g, ' $& ')
    .split(/\s+/)
    .filter((t) => t);

  let i = 0;
  // Reads one line; its first move is played from the position, each further one from the one
  // before. A variation is an alternative to the line's last move so far.
  const line = (from: GameNode, depth: number) => {
    let position = from;
    let last: MoveNode | null = null;
    while (i < tokens.length) {
      const token = tokens[i++];
      if (token === '(') {
        if (!last) throw new Error('A variation before any move');
        line(last.parent, depth + 1);
        continue;
      }
      if (token === ')') {
        if (depth === 0) throw new Error("A ')' without a '('");
        return;
      }
      if (RESULT_TOKENS.has(token) || /^\$\d+$/.test(token)) continue;
      const san = token
        .replace(/^\d+\.*/, '')
        .replace(/[!?]+$/, '')
        .replace(/0-0-0/, 'O-O-O')
        .replace(/0-0/, 'O-O');
      if (!san) continue;
      const chess = new Chess(position.fen);
      try {
        chess.move(san);
      } catch {
        throw new Error(`Illegal move ${token} in ${position.fen}`);
      }
      last = addMove(position, chess);
      position = last;
    }
    if (depth > 0) throw new Error("A '(' without a ')'");
  };
  line(start, 0);
}
