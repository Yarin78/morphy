package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnMoveParser;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.entities.TournamentType;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.morphy.games.moves.GameQuotationMoveEncoder;
import se.yarin.morphy.games.moves.MoveEncoder;
import se.yarin.morphy.games.moves.MoveSerializer;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

public class GameQuotationAnnotation extends Annotation implements StatisticalAnnotation {

  private static final Logger log = LoggerFactory.getLogger(GameQuotationAnnotation.class);

  // TODO: The model should be read-only since annotations must be immutable
  private final GameHeaderModel header;

  public GameHeaderModel header() {
    return header;
  }

  // Deserialization is done lazily
  private byte[] setupPositionData;
  private byte[] gameData;

  private int unknown;

  public int unknown() {
    return unknown;
  }

  /**
   * Creates a new GameQuotation annotation that only contains the header information about the
   * game.
   *
   * @param header the header model of the game to quote
   */
  public GameQuotationAnnotation(@NotNull GameHeaderModel header) {
    this(header, 0);
  }

  public GameQuotationAnnotation(@NotNull GameHeaderModel header, int unknown) {
    this.header = header;
    this.setupPositionData = null;
    this.gameData = new byte[] { -75, -81 };  // This is an encoded null move
    this.unknown = unknown;
  }

  /**
   * Creates a new GameQuotation annotation that contains both header and game moves Annotations and
   * variations will be stripped
   *
   * @param game the game model of the game to quote
   */
  public GameQuotationAnnotation(@NotNull GameModel game) {
    this(game, 0);
  }

  public GameQuotationAnnotation(@NotNull GameModel game, int unknown) {
    this.header = game.header();
    this.unknown = unknown;

    // Only embed moves data if it's a regular chess game.
    // ChessBase doesn't seem to support embedding unorthodox games
    if (game.moves().root().position().isRegularChess()) {
      if (game.moves().isSetupPosition()) {
        this.setupPositionData = new byte[28];
        // TODO: DatabaseContext should be passed to MoveSerializer
        new MoveSerializer()
                .serializeInitialPosition(game.moves(), ByteBuffer.wrap(this.setupPositionData), false);
      }

      MoveEncoder moveEncoder = new GameQuotationMoveEncoder();
      // This allocation is a bit ugly since it uses knowledge of the underlying encoder
      ByteBuffer buf = ByteBuffer.allocate(game.moves().countPly(false) * 2 + 2);
      moveEncoder.encode(buf, game.moves());
      gameData = buf.array();
    }
  }

  private GameQuotationAnnotation(
      @NotNull GameHeaderModel header, byte[] setupPositionData, byte[] gameData, int unknown) {
    this.setupPositionData = setupPositionData;
    this.gameData = gameData;
    this.header = header;
    this.unknown = unknown;
  }

  public boolean hasGame() {
    return gameData != null;
  }

  public GameModel getGameModel() {
    if (hasGame()) {
      return new GameModel(header(), getMoves());
    }
    return new GameModel(header(), new GameMovesModel());
  }

  private GameMovesModel getMoves() {
    GameMovesModel moves;
    try {
      if (setupPositionData != null) {
        // TODO: DatabaseContext should be passed to MoveSerializer
        moves =
            new MoveSerializer().parseInitialPosition(ByteBuffer.wrap(setupPositionData), false, 0);
      } else {
        moves = new GameMovesModel();
      }
    } catch (MorphyMoveDecodingException e) {
      log.warn("Error parsing initial position in game quotation", e);
      return new GameMovesModel();
    }

    try {
      MoveEncoder encoder = new GameQuotationMoveEncoder();
      encoder.decode(ByteBuffer.wrap(gameData), moves, true);
    } catch (MorphyMoveDecodingException e) {
      log.warn("Error parsing move in game quotation", e);
      moves = e.getModel();
    }

    return moves;
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
    if (!Arrays.equals(setupPositionData, that.setupPositionData)) return false;
    return Arrays.equals(gameData, that.gameData);
  }

  @Override
  public int hashCode() {
    return header.hashCode() * 31 + unknown;
  }

  public static class Serializer implements AnnotationSerializer {

    private <T> T valueOrDefault(T value, T defaultValue) {
      return value == null ? defaultValue : value;
    }

    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      GameQuotationAnnotation qa = (GameQuotationAnnotation) annotation;
      int start = buf.position();
      ByteBufferUtil.putShortB(buf, 0); // This is the size, will be filled in later
      ByteBufferUtil.putShortB(buf, qa.hasGame() ? 2 : 1);
      if (qa.setupPositionData != null) {
        ByteBufferUtil.putShortB(buf, 64);
        buf.put(qa.setupPositionData);
      } else {
        ByteBufferUtil.putShortB(buf, 0);
      }

      ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header.getWhite(), ""));
      ByteBufferUtil.putByte(buf, 0);
      ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header.getBlack(), ""));
      ByteBufferUtil.putByte(buf, 0);
      ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header.getWhiteElo(), 0));
      ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header.getBlackElo(), 0));
      ByteBufferUtil.putShortB(
          buf, CBUtil.encodeEco(valueOrDefault(qa.header.getEco(), Eco.unset())));
      ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header.getEvent(), ""));
      ByteBufferUtil.putByte(buf, 0);
      ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header.getEventSite(), ""));
      ByteBufferUtil.putByte(buf, 0);
      ByteBufferUtil.putIntB(
          buf, CBUtil.encodeDate(valueOrDefault(qa.header.getDate(), Date.unset())));

      TournamentTimeControl ttc = TournamentTimeControl.fromName(qa.header.getEventTimeControl());
      TournamentType tt = TournamentType.fromName(qa.header.getEventType());
      ByteBufferUtil.putShortB(buf, CBUtil.encodeTournamentType(tt, ttc));
      ByteBufferUtil.putShortB(
          buf,
          CBUtil.encodeNation(Nation.fromIOC(valueOrDefault(qa.header.getEventCountry(), ""))));

      ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header.getEventCategory(), 0));
      ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header.getEventRounds(), 0));
      ByteBufferUtil.putByte(buf, valueOrDefault(qa.header.getSubRound(), 0));
      ByteBufferUtil.putByte(buf, valueOrDefault(qa.header.getRound(), 0));
      ByteBufferUtil.putByte(
          buf,
          CBUtil.encodeGameResult(valueOrDefault(qa.header.getResult(), GameResult.NOT_FINISHED)));
      ByteBufferUtil.putShortB(buf, qa.unknown());

      if (qa.gameData != null) {
        buf.put(qa.gameData);
      }
      int end = buf.position();
      buf.position(start);
      ByteBufferUtil.putShortB(buf, end - start);
      buf.position(end);
    }

    @Override
    public GameQuotationAnnotation deserialize(ByteBuffer buf, int length) {
      int startPos = buf.position();
      int size = ByteBufferUtil.getUnsignedShortB(buf);

      int type = ByteBufferUtil.getUnsignedShortB(buf);
      if (type != 1 && type != 2) {
        log.warn("Unknown game quotation type: " + type);
      }

      byte[] setupPositionData = null;

      int flags = ByteBufferUtil.getUnsignedShortB(buf);
      if ((flags & 64) > 0) {
        setupPositionData = new byte[28];
        buf.get(setupPositionData);
        flags -= 64;
      }
      if (flags != 0) {
        log.warn("Unknown flag value parsing game quotation: " + flags);
      }

      GameHeaderModel header = new GameHeaderModel();
      header.setWhite(ByteBufferUtil.getByteString(buf));
      buf.get();
      header.setBlack(ByteBufferUtil.getByteString(buf));
      buf.get();
      header.setWhiteElo(ByteBufferUtil.getUnsignedShortB(buf));
      header.setBlackElo(ByteBufferUtil.getUnsignedShortB(buf));
      Eco eco = CBUtil.decodeEco(ByteBufferUtil.getUnsignedShortB(buf));
      header.setEco(eco);
      header.setEvent(ByteBufferUtil.getByteString(buf));
      buf.get();
      header.setEventSite(ByteBufferUtil.getByteString(buf));
      buf.get();
      Date date = CBUtil.decodeDate(ByteBufferUtil.getIntB(buf));
      header.setDate(date);

      int typeValue = ByteBufferUtil.getUnsignedShortB(buf);
      TournamentTimeControl timeControl = CBUtil.decodeTournamentTimeControl(typeValue);
      TournamentType tournamentType = CBUtil.decodeTournamentType(typeValue);
      Nation nation = CBUtil.decodeNation(ByteBufferUtil.getUnsignedShortB(buf));

      header.setEventTimeControl(timeControl.getName());
      header.setEventType(tournamentType.getName());
      header.setEventCountry(nation.getIocCode());
      header.setEventCategory(ByteBufferUtil.getUnsignedShortB(buf));
      header.setEventRounds(ByteBufferUtil.getUnsignedShortB(buf));

      header.setSubRound(ByteBufferUtil.getUnsignedByte(buf));
      header.setRound(ByteBufferUtil.getUnsignedByte(buf));
      header.setResult(CBUtil.decodeGameResult(ByteBufferUtil.getUnsignedByte(buf)));

      int unknown = ByteBufferUtil.getUnsignedShortB(buf);
      // This one is always set to some value. No idea what it does though.
      // log.warn(String.format("Unknown value in game quotation is %d (%04X), type is %d", unknown,
      // unknown, type));

      byte[] gameData = new byte[size - (buf.position() - startPos)];
      buf.get(gameData);

      if (gameData.length == 0) {
        gameData = null;
      }

      return new GameQuotationAnnotation(header, setupPositionData, gameData, unknown);
    }

    @Override
    public Class getAnnotationClass() {
      return GameQuotationAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x13;
    }
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
