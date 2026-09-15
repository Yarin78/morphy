package se.yarin.morphy.cli.opening;

import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.NAG;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.annotations.AnnotationConverter;

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

  /** One "opening game" (branch of variations) in the repertoire database. */
  public record Entry(int number, String title) {}

  private record Match(double score, Entry entry) {}

  private final String name;
  private final Player myColor;
  private final List<Entry> entries;
  private final Map<Position, Match> positionCache;
  private final Map<Entry, Map<Position, List<Move>>> bookMoves;

  private OpeningRepertoireCache(
      String name,
      Player myColor,
      List<Entry> entries,
      Map<Position, Match> positionCache,
      Map<Entry, Map<Position, List<Move>>> bookMoves) {
    this.name = name;
    this.myColor = myColor;
    this.entries = entries;
    this.positionCache = positionCache;
    this.bookMoves = bookMoves;
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
   * </ul>
   *
   * Annotation stops as soon as the game leaves the entry's book (a move that matches no known
   * continuation), since the entry can't provide any guidance beyond that point.
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
    while (node.hasMoves()) {
      List<Move> bookMovesHere = book.get(node.position());
      if (bookMovesHere == null || bookMovesHere.isEmpty()) {
        break;
      }

      Move actualMove = node.mainMove();
      GameMovesModel.Node nextNode = node.mainNode();
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
        break;
      }
      node = nextNode;
    }
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
    try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
      try (DatabaseReadTransaction txn = new DatabaseReadTransaction(db)) {
        for (Game game : txn.iterable()) {
          if (game.guidingText() || game.deleted()) {
            continue;
          }
          String title = game.white().getFullName() + " - " + game.black().getFullName();
          Entry entry = new Entry(game.id(), title);
          entries.add(entry);

          Map<Position, List<Move>> book = new HashMap<>();
          bookMoves.put(entry, book);
          indexNode(game.getModel().moves().root(), 0.0, 1.0, entry, positionCache, book);
        }
      }
    }
    return new OpeningRepertoireCache(name, myColor, entries, positionCache, bookMoves);
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
