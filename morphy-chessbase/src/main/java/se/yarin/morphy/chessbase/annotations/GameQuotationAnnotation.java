package se.yarin.morphy.chessbase.annotations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnMoveParser;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;

import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A complete game quoted in a comment: its header, and optionally the main line of its moves.
 *
 * <p>The moves are decoded lazily, since most quotations are never looked at. A format that reads
 * a quotation may attach its own {@link #encoding()} of it, so that a quotation written back to
 * the same format is written exactly as it was read.
 */
public class GameQuotationAnnotation extends Annotation implements StatisticalAnnotation {

  // TODO: The model should be read-only since annotations must be immutable
  private final @NotNull GameHeaderModel header;
  private final int unknown;
  private final @Nullable Supplier<GameMovesModel> moves;
  private final @Nullable Object encoding;
  private @Nullable GameMovesModel decodedMoves;

  /**
   * Creates a quotation holding only the header of a game, and no moves.
   *
   * @param header the header model of the game to quote
   */
  public GameQuotationAnnotation(@NotNull GameHeaderModel header) {
    this(header, 0);
  }

  public GameQuotationAnnotation(@NotNull GameHeaderModel header, int unknown) {
    this(header, unknown, GameMovesModel::new, null);
  }

  /**
   * Creates a quotation of a game including its moves. Annotations and variations are stripped,
   * and the moves of a game that isn't regular chess are left out.
   *
   * @param game the game model of the game to quote
   */
  public GameQuotationAnnotation(@NotNull GameModel game) {
    this(game, 0);
  }

  public GameQuotationAnnotation(@NotNull GameModel game, int unknown) {
    this(
        game.header(),
        unknown,
        game.moves().root().position().isRegularChess() ? mainLineOf(game.moves()) : null,
        null);
  }

  /**
   * Creates a quotation as read from a database.
   *
   * @param header the header of the quoted game
   * @param unknown a value of unknown meaning, kept so it can be written back
   * @param moves supplies the main line of the quoted game when first asked for, or null if the
   *     quotation holds no moves
   * @param encoding the reading format's own encoding of the quotation, or null
   */
  public GameQuotationAnnotation(
      @NotNull GameHeaderModel header,
      int unknown,
      @Nullable Supplier<GameMovesModel> moves,
      @Nullable Object encoding) {
    this.header = header;
    this.unknown = unknown;
    this.moves = moves;
    this.encoding = encoding;
  }

  private static Supplier<GameMovesModel> mainLineOf(@NotNull GameMovesModel source) {
    GameMovesModel mainLine =
        source.isSetupPosition()
            ? new GameMovesModel(
                source.root().position(), Chess.plyToMoveNumber(source.root().ply()))
            : new GameMovesModel();
    GameMovesModel.Node from = source.root();
    GameMovesModel.Node to = mainLine.root();
    while (from.hasMoves()) {
      from = from.mainNode();
      to = to.addMove(from.lastMove());
    }
    return () -> mainLine;
  }

  public @NotNull GameHeaderModel header() {
    return header;
  }

  public int unknown() {
    return unknown;
  }

  /**
   * The encoding of this quotation in the format it was read from, or null if it was not read
   * from a database. Only that format knows what it is.
   */
  public @Nullable Object encoding() {
    return encoding;
  }

  public boolean hasGame() {
    return moves != null;
  }

  public @NotNull GameModel getGameModel() {
    return new GameModel(header, hasGame() ? getMoves() : new GameMovesModel());
  }

  private synchronized @NotNull GameMovesModel getMoves() {
    if (decodedMoves == null) {
      decodedMoves = moves.get();
    }
    return decodedMoves;
  }

  @Override
  public String toString() {
    return "GameQuotationAnnotation: " + header.toString();
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.GAME_QUOTATION);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GameQuotationAnnotation that = (GameQuotationAnnotation) o;

    if (unknown != that.unknown) return false;
    if (!header.equals(that.header)) return false;
    if (hasGame() != that.hasGame()) return false;
    if (!hasGame()) return true;
    if (encoding != null && that.encoding != null && encoding.getClass() == that.encoding.getClass()) {
      return encoding.equals(that.encoding);
    }
    return getMoves().toString().equals(that.getMoves().toString());
  }

  @Override
  public int hashCode() {
    return header.hashCode() * 31 + unknown;
  }

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern QUOTE_PATTERN = Pattern.compile("\\[%quote\\s+(.+?)\\](?=\\s*(?:\\[%|$|[^\\[]))", Pattern.DOTALL);

    @Override
    @NotNull
    public Pattern getPattern() {
      return QUOTE_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      GameQuotationAnnotation a = (GameQuotationAnnotation) annotation;
      StringBuilder sb = new StringBuilder("[%quote");
      GameHeaderModel h = a.header();

      // Serialize all header fields as key="value" pairs (no curly braces to avoid PGN comment conflicts)
      for (Map.Entry<String, Object> entry : h.getAllFields().entrySet()) {
        String fieldName = entry.getKey();
        Object value = entry.getValue();

        if (value == null) {
          continue; // Skip null values
        }

        sb.append(" ");
        sb.append(fieldName);
        sb.append("=\"");
        sb.append(AnnotationPgnUtil.escapeString(AnnotationPgnUtil.serializeHeaderValue(value)));
        sb.append("\"");
      }

      if (a.unknown() != 0) {
        sb.append(" unknown=\"").append(a.unknown()).append("\"");
      }

      // Moves (if present) - add as a special "moves" field
      if (a.hasGame()) {
        sb.append(" moves=\"");
        GameModel game = a.getGameModel();
        GameMovesModel.Node node = game.moves().root();
        boolean firstMove = true;
        while (node.hasMoves()) {
          node = node.mainNode();
          Move move = node.lastMove();
          if (!firstMove) sb.append(" ");
          firstMove = false;

          int ply = node.ply();
          if (ply % 2 == 1) {
            sb.append((ply + 1) / 2).append(". ");
          }
          sb.append(move.toSAN());
        }
        sb.append("\"");
      }

      sb.append("]");
      return sb.toString();
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      GameHeaderModel header = new GameHeaderModel();
      Map<String, String> fields = AnnotationPgnUtil.parseKeyValuePairs(data);

      // Extract moves if present
      String movesStr = fields.remove("moves");

      String unknownStr = fields.remove("unknown");
      int unknown = unknownStr != null ? Integer.parseInt(unknownStr) : 0;

      // Set all other fields in the header
      for (Map.Entry<String, String> entry : fields.entrySet()) {
        Object value = AnnotationPgnUtil.deserializeHeaderValue(entry.getKey(), entry.getValue());
        if (value != null) {
          header.setField(entry.getKey(), value);
        }
      }

      // Parse moves if present
      if (movesStr != null && !movesStr.isEmpty()) {
        GameMovesModel moves = new GameMovesModel();
        String[] moveTokens = movesStr.split("\\s+");
        GameMovesModel.Node current = moves.root();

        for (String moveToken : moveTokens) {
          if (moveToken.matches("\\d+\\.+")) continue;
          if (moveToken.isEmpty()) continue;

          try {
            PgnMoveParser moveParser = new PgnMoveParser(current.position());
            Move move = moveParser.parseMove(moveToken);
            current = current.addMove(move);
          } catch (PgnFormatException e) {
            log.debug("Failed to parse move in quotation: {}", moveToken);
            break;
          }
        }

        return new GameQuotationAnnotation(new GameModel(header, moves), unknown);
      }

      return new GameQuotationAnnotation(header, unknown);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return GameQuotationAnnotation.class;
    }
  }
}
