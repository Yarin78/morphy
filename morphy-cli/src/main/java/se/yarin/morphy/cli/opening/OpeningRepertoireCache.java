package se.yarin.morphy.cli.opening;

import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.NAG;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.annotations.AnnotationConverter;
import se.yarin.morphy.games.annotations.TextAfterMoveAnnotation;
import se.yarin.morphy.games.annotations.TextBeforeMoveAnnotation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A preprocessed cache of an opening repertoire database: a (typically small) set of "opening
 * games" that each represent one branch of variations in an opening repertoire. Other games can
 * be classified against the cache to determine which branch they follow the longest, and have
 * their moves annotated against that branch.
 *
 * <p>Every position occurring anywhere in the repertoire (main lines and sub-variations) is
 * indexed with a score describing how deep into the repertoire it occurs. The score is an
 * increasing sequence of digit groups, one per variation level: the integer part is the number of
 * plies into the main line, and each subsequent pair of decimal digits is the number of plies into
 * the next-deeper variation, e.g. a position reached after 8 plies of the main line followed by 3
 * plies into a sideline has score 8.03; a further nested sideline of 1 ply has score 8.0201. A
 * game is classified by walking its actual moves and finding the highest-scoring position match.
 *
 * <p>The color the repertoire is prepared for ("me") is determined from the database name: it
 * must contain the word "white" or "black" (case-insensitively).
 */
public class OpeningRepertoireCache {
  // Newly added NAG/commentary annotations are in the format-independent se.yarin.chess
  // representation and must be converted to their ChessBase-native equivalents before the game
  // can be persisted.
  private static final AnnotationConverter ANNOTATION_CONVERTER =
      AnnotationConverter.getRoundTripConverter();

  /**
   * One "opening game" (branch of variations) in the repertoire database.
   *
   * @param title a human-readable label for the entry, e.g. for {@link #formatTag}
   * @param whiteName the White player name on the entry's own game in the repertoire database
   * @param blackName the Black player name on the entry's own game in the repertoire database
   * @param eventTitle the tournament/event title on the entry's own game in the repertoire
   *     database
   */
  public record Entry(
      int number, String title, String whiteName, String blackName, String eventTitle) {}

  private record Match(double score, Entry entry) {}

  private final String name;
  private final Player myColor;
  private final List<Entry> entries;
  private final Map<Position, Match> positionCache;
  private final Map<Entry, Map<Position, List<Move>>> bookMoves;
  private final Map<Entry, GameMovesModel> entryMoves;

  // Aggregated across calls to recordGame(), for later use by summarize().
  private final Map<Entry, Integer> gamesRecorded = new HashMap<>();
  private final Map<Entry, Map<Position, Map<Move, Integer>>> playCounts = new HashMap<>();
  private final Map<Entry, Map<Position, Integer>> endOfLineCounts = new HashMap<>();

  private OpeningRepertoireCache(
      String name,
      Player myColor,
      List<Entry> entries,
      Map<Position, Match> positionCache,
      Map<Entry, Map<Position, List<Move>>> bookMoves,
      Map<Entry, GameMovesModel> entryMoves) {
    this.name = name;
    this.myColor = myColor;
    this.entries = entries;
    this.positionCache = positionCache;
    this.bookMoves = bookMoves;
    this.entryMoves = entryMoves;
  }

  /** The base name of the opening database (without path or extension), e.g. "white-e4". */
  public String name() {
    return name;
  }

  /** The color the repertoire is prepared for, determined from {@link #name()}. */
  public Player myColor() {
    return myColor;
  }

  public List<Entry> entries() {
    return entries;
  }

  /**
   * Determines which entry in the repertoire the given game follows the longest, by walking the
   * main line of the game and finding the deepest matching position in the repertoire.
   */
  public Optional<Entry> classify(GameMovesModel moves) {
    Match best = null;
    GameMovesModel.Node node = moves.root();
    while (node != null) {
      Match match = positionCache.get(node.position());
      if (match != null && (best == null || match.score() > best.score())) {
        best = match;
      }
      node = node.mainNode();
    }
    return Optional.ofNullable(best).map(Match::entry);
  }

  /** Formats the tag to set on a game that was classified to the given entry. */
  public String formatTag(Entry entry) {
    return (myColor == Player.WHITE ? "White" : "Black") + " #" + entry.number() + " " + entry.title();
  }

  /**
   * Annotates my moves and my opponent's moves in {@code moves} against the given repertoire
   * entry, by walking the main line for as long as it stays within the entry's book:
   *
   * <ul>
   *   <li>a move of mine that isn't the entry's main move at that position is marked "?" and the
   *       main move is inserted as a variation
   *   <li>an opponent move that isn't in the entry at all (in no variation) gets a comment saying
   *       so
   *   <li>if neither of the above ever happens and the entry's book runs out of moves, the
   *       position where that happens gets a comment saying so
   * </ul>
   *
   * Once the game has left the entry's book (a move that matches no known continuation), it keeps
   * being walked without further comparison against the book, in case a later move transposes back
   * into a known position; when that happens, the move that brought it back is marked accordingly.
   *
   * <p>Any annotations already present in {@code moves} are cleared first, so the result only
   * reflects the classification against this entry.
   */
  public void annotate(GameMovesModel moves, Entry entry) {
    Map<Position, List<Move>> book = bookMoves.get(entry);
    if (book == null) {
      return;
    }

    moves.deleteAllAnnotations();

    GameMovesModel.Node node = moves.root();
    boolean deviated = false;
    while (node.hasMoves()) {
      Move actualMove = node.mainMove();
      GameMovesModel.Node nextNode = node.mainNode();

      if (!deviated) {
        List<Move> bookMovesHere = book.get(node.position());
        if (bookMovesHere == null || bookMovesHere.isEmpty()) {
          break;
        }

        boolean isMyMove = node.position().playerToMove() == myColor;
        if (isMyMove) {
          Move mainBookMove = bookMovesHere.get(0);
          if (!actualMove.equals(mainBookMove)) {
            nextNode.addAnnotation(new NAGAnnotation(NAG.BAD_MOVE));
            node.addMove(mainBookMove);
            ANNOTATION_CONVERTER.convertToChessBase(nextNode.getAnnotations());
          }
        } else if (!bookMovesHere.contains(actualMove)) {
          nextNode.addAnnotation(new CommentaryAfterMoveAnnotation("Not in repertoire"));
          ANNOTATION_CONVERTER.convertToChessBase(nextNode.getAnnotations());
        }

        if (!bookMovesHere.contains(actualMove)) {
          deviated = true;
        }
      }

      if (deviated && book.containsKey(nextNode.position())) {
        nextNode.addAnnotation(new CommentaryAfterMoveAnnotation("Back in book"));
        ANNOTATION_CONVERTER.convertToChessBase(nextNode.getAnnotations());
        deviated = false;
      }

      node = nextNode;
    }

    if (!deviated) {
      node.addAnnotation(new CommentaryAfterMoveAnnotation("End of line"));
      ANNOTATION_CONVERTER.convertToChessBase(node.getAnnotations());
    }
  }

  /**
   * Records how {@code moves} actually played out against {@code entry}'s book, for later
   * aggregation by {@link #summarize}. Uses the same book-matching rule as {@link #annotate}: a
   * move counts as staying in book if it's any known continuation at that position, not just the
   * main move. Once a move is found that doesn't match any known continuation at all, that move
   * itself is recorded (so {@link #summarize} can show it as a common mistake) but nothing past
   * it, since the entry can't provide any guidance beyond that point.
   */
  public void recordGame(GameMovesModel moves, Entry entry) {
    Map<Position, List<Move>> book = bookMoves.get(entry);
    if (book == null) {
      return;
    }

    gamesRecorded.merge(entry, 1, Integer::sum);
    Map<Position, Map<Move, Integer>> counts =
        playCounts.computeIfAbsent(entry, e -> new HashMap<>());
    Map<Position, Integer> endOfLine =
        endOfLineCounts.computeIfAbsent(entry, e -> new HashMap<>());

    GameMovesModel.Node node = moves.root();
    while (true) {
      List<Move> bookMovesHere = book.get(node.position());
      if (bookMovesHere == null || bookMovesHere.isEmpty()) {
        endOfLine.merge(node.position(), 1, Integer::sum);
        break;
      }
      if (!node.hasMoves()) {
        // The recorded game ended early, still nominally in book; nothing more to count.
        break;
      }

      Move actualMove = node.mainMove();
      counts.computeIfAbsent(node.position(), p -> new HashMap<>()).merge(actualMove, 1, Integer::sum);
      if (!bookMovesHere.contains(actualMove)) {
        break;
      }
      node = node.mainNode();
    }
  }

  /**
   * Builds a summary of every game recorded against {@code entry} via {@link #recordGame}: a
   * clone of the entry's own move tree (same line order as the repertoire), pruned to the
   * positions that were actually reached by a recorded game:
   *
   * <ul>
   *   <li>a known continuation (the main move, or an existing sideline) that was never played by
   *       any recorded game is kept as a single reference leaf (to show the correct next move),
   *       but nothing beyond it
   *   <li>a move that was played and doesn't match any known continuation at that position is
   *       inserted as a new variation, annotated with how many times it was played; if it's my
   *       move it's also marked with {@link NAG#BAD_MOVE}
   *   <li>a position where the entry's own book runs out of moves is annotated with how many
   *       recorded games reached it
   * </ul>
   *
   * If {@code annotateAllMoves} is true, known continuations that were actually played also get a
   * count annotation; otherwise only deviations and end-of-book positions do.
   *
   * <p>Any pre-existing free-text comments in the repertoire itself are stripped, since they'd
   * read confusingly next to the aggregated counts; pre-existing move-quality symbols (e.g. "!" or
   * "?" already in the repertoire) are kept.
   *
   * @return empty if no game has been recorded against this entry
   */
  public Optional<GameModel> summarize(Entry entry, boolean annotateAllMoves) {
    if (gamesRecorded.getOrDefault(entry, 0) == 0) {
      return Optional.empty();
    }

    GameMovesModel entryModel = entryMoves.get(entry);
    GameMovesModel summary = new GameMovesModel(entryModel);
    for (GameMovesModel.Node n : summary.getAllNodes()) {
      // Annotations.removeByClass() does an exact getClass() == check, which wouldn't match
      // these @Value.Immutable types (real instances are e.g. ImmutableTextAfterMoveAnnotation).
      n.getAnnotations()
          .removeIf(a -> a instanceof TextAfterMoveAnnotation || a instanceof TextBeforeMoveAnnotation);
    }

    summarizeNode(
        summary.root(),
        entryModel.root(),
        playCounts.getOrDefault(entry, Map.of()),
        endOfLineCounts.getOrDefault(entry, Map.of()),
        annotateAllMoves);
    addCountAnnotation(
        summary.root(), entry.title() + " (" + gamesRecorded.get(entry) + " games recorded)");

    // White/Black/Event are backed by Player/Tournament entities that ChessBase stores with a
    // fixed byte length (30/20/40 bytes) and resolves/updates by that stored (possibly truncated)
    // value. Reusing the entry's own already-stored values here is safe (they already fit those
    // limits, or the repertoire database itself couldn't have stored them); it's synthesizing a
    // NEW unbounded string, e.g. concatenating title fields together, that's dangerous: two
    // entries with a long common prefix could then truncate to the exact same stored name, which
    // blows up at commit time once a third game's stats update can't tell the resulting
    // same-key duplicates apart. GameTag has a 200-byte limit, so formatTag() is safe there.
    GameHeaderModel header = new GameHeaderModel();
    header.setWhite(entry.whiteName());
    header.setBlack(entry.blackName());
    header.setGameTag(formatTag(entry));
    header.setEvent(entry.eventTitle());
    return Optional.of(new GameModel(header, summary));
  }

  private void summarizeNode(
      GameMovesModel.Node cloneNode,
      GameMovesModel.Node origNode,
      Map<Position, Map<Move, Integer>> counts,
      Map<Position, Integer> endOfLineCounts,
      boolean annotateAllMoves) {
    Position position = origNode.position();
    List<GameMovesModel.Node> origChildren = origNode.children();

    if (origChildren.isEmpty()) {
      int reached = endOfLineCounts.getOrDefault(position, 0);
      if (reached > 0) {
        addCountAnnotation(cloneNode, reachedText(reached));
      }
      return;
    }

    Map<Move, Integer> here = counts.getOrDefault(position, Map.of());
    List<GameMovesModel.Node> cloneChildren = cloneNode.children();
    for (int i = 0; i < origChildren.size(); i++) {
      GameMovesModel.Node origChild = origChildren.get(i);
      GameMovesModel.Node cloneChild = cloneChildren.get(i);
      Integer count = here.get(origChild.lastMove());
      if (count == null) {
        // Never played beyond this move; keep it as a "correct move" reference leaf.
        cloneChild.deleteRemainingMoves();
      } else {
        if (annotateAllMoves) {
          addCountAnnotation(cloneChild, playedText(count));
        }
        summarizeNode(cloneChild, origChild, counts, endOfLineCounts, annotateAllMoves);
      }
    }

    boolean isMyMove = position.playerToMove() == myColor;
    List<Move> knownMoves = origNode.moves();
    for (Map.Entry<Move, Integer> playedMove : here.entrySet()) {
      if (knownMoves.contains(playedMove.getKey())) {
        continue;
      }
      GameMovesModel.Node deviation = cloneNode.addMove(playedMove.getKey());
      if (isMyMove) {
        deviation.addAnnotation(new NAGAnnotation(NAG.BAD_MOVE));
      }
      addCountAnnotation(
          deviation, (isMyMove ? "" : "Not in repertoire, ") + playedText(playedMove.getValue()));
    }
  }

  private static void addCountAnnotation(GameMovesModel.Node node, String text) {
    node.addAnnotation(new CommentaryAfterMoveAnnotation(text));
    ANNOTATION_CONVERTER.convertToChessBase(node.getAnnotations());
  }

  private static String playedText(int count) {
    return "Played " + count + " time" + (count == 1 ? "" : "s");
  }

  private static String reachedText(int count) {
    return "Reached end of line " + count + " time" + (count == 1 ? "" : "s");
  }

  /** Loads and preprocesses an opening repertoire database into a cache. */
  public static OpeningRepertoireCache load(File file) throws IOException {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    Player myColor = determineColor(name);

    List<Entry> entries = new ArrayList<>();
    Map<Position, Match> positionCache = new HashMap<>();
    Map<Entry, Map<Position, List<Move>>> bookMoves = new HashMap<>();
    Map<Entry, GameMovesModel> entryMoves = new HashMap<>();
    try (DatabaseCbh db = DatabaseCbh.open(file, DatabaseMode.READ_ONLY)) {
      try (DatabaseReadTransaction txn = new DatabaseReadTransaction(db)) {
        for (Game game : txn.iterable()) {
          if (game.guidingText() || game.deleted()) {
            continue;
          }
          String whiteName = game.white().getFullName();
          String blackName = game.black().getFullName();
          String title = whiteName + " - " + blackName;
          Entry entry =
              new Entry(game.id(), title, whiteName, blackName, game.tournament().title());
          entries.add(entry);

          GameMovesModel moves = game.getModel().moves();
          entryMoves.put(entry, moves);

          Map<Position, List<Move>> book = new HashMap<>();
          bookMoves.put(entry, book);
          indexNode(moves.root(), 0.0, 1.0, entry, positionCache, book);
        }
      }
    }
    return new OpeningRepertoireCache(name, myColor, entries, positionCache, bookMoves, entryMoves);
  }

  /**
   * Determines which color the repertoire is prepared for, based on whether "white" or "black"
   * occurs in its name (case-insensitively).
   */
  private static Player determineColor(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.contains("white")) {
      return Player.WHITE;
    }
    if (lower.contains("black")) {
      return Player.BLACK;
    }
    throw new IllegalArgumentException(
        "Opening database name \""
            + name
            + "\" doesn't contain \"white\" or \"black\"; can't determine which color the "
            + "repertoire is for");
  }

  /**
   * Recursively indexes every position in the subtree rooted at {@code node}: {@code
   * positionCache} keeps the highest-scoring match for each position seen so far (across this and
   * all other entries), preferring the later entry on a tied score, while {@code book} records,
   * for every position in this entry alone, the moves known at that position (main move first).
   *
   * @param score the score of {@code node} itself (0 for the root)
   * @param levelUnit the score contribution of one more ply at the current variation level
   */
  private static void indexNode(
      GameMovesModel.Node node,
      double score,
      double levelUnit,
      Entry entry,
      Map<Position, Match> positionCache,
      Map<Position, List<Move>> book) {
    List<GameMovesModel.Node> children = node.children();
    if (!children.isEmpty()) {
      book.put(node.position(), node.moves());
    }

    for (int i = 0; i < children.size(); i++) {
      GameMovesModel.Node child = children.get(i);
      double childLevelUnit = i == 0 ? levelUnit : levelUnit * 0.01;
      double childScore = score + childLevelUnit;

      Match existing = positionCache.get(child.position());
      if (existing == null || childScore >= existing.score()) {
        positionCache.put(child.position(), new Match(childScore, entry));
      }

      indexNode(child, childScore, childLevelUnit, entry, positionCache, book);
    }
  }
}
