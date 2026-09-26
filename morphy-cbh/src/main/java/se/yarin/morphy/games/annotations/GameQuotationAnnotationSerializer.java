package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.morphy.chessbase.TournamentTimeControl;
import se.yarin.morphy.chessbase.TournamentType;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;
import se.yarin.morphy.games.moves.GameQuotationMoveEncoder;
import se.yarin.morphy.games.moves.MoveEncoder;
import se.yarin.morphy.games.moves.MoveSerializer;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link GameQuotationAnnotation} in the v1 {@code .cba} format. */
public class GameQuotationAnnotationSerializer implements AnnotationSerializer {
  private static final Logger log = LoggerFactory.getLogger(GameQuotationAnnotationSerializer.class);

  /**
   * The v1 encoding of a quotation's start position and moves, kept on a quotation read from a v1
   * database so it's written back exactly as read.
   *
   * @param setupPositionData the encoded start position, or null for the standard one
   * @param gameData the encoded moves
   */
  private record Encoding(byte @Nullable [] setupPositionData, byte @NotNull [] gameData) {
    @Override
    public boolean equals(Object o) {
      return o instanceof Encoding that
          && Arrays.equals(setupPositionData, that.setupPositionData)
          && Arrays.equals(gameData, that.gameData);
    }

    @Override
    public int hashCode() {
      return 31 * Arrays.hashCode(setupPositionData) + Arrays.hashCode(gameData);
    }
  }

  private static @Nullable Encoding encodingOf(@NotNull GameQuotationAnnotation quotation) {
    if (quotation.encoding() instanceof Encoding encoding) {
      return encoding;
    }
    if (!quotation.hasGame()) {
      return null;
    }
    GameMovesModel moves = quotation.getGameModel().moves();
    byte[] setupPositionData = null;
    if (moves.isSetupPosition()) {
      setupPositionData = new byte[28];
      // TODO: DatabaseContext should be passed to MoveSerializer
      new MoveSerializer()
          .serializeInitialPosition(moves, ByteBuffer.wrap(setupPositionData), false);
    }
    MoveEncoder moveEncoder = new GameQuotationMoveEncoder();
    // This allocation is a bit ugly since it uses knowledge of the underlying encoder
    ByteBuffer buf = ByteBuffer.allocate(moves.countPly(false) * 2 + 2);
    moveEncoder.encode(buf, moves);
    return new Encoding(setupPositionData, buf.array());
  }

  private static @NotNull GameMovesModel decodeMoves(@NotNull Encoding encoding) {
    GameMovesModel moves;
    try {
      if (encoding.setupPositionData() != null) {
        // TODO: DatabaseContext should be passed to MoveSerializer
        moves =
            new MoveSerializer()
                .parseInitialPosition(ByteBuffer.wrap(encoding.setupPositionData()), false, 0);
      } else {
        moves = new GameMovesModel();
      }
    } catch (MorphyMoveDecodingException e) {
      log.warn("Error parsing initial position in game quotation", e);
      return new GameMovesModel();
    }

    try {
      new GameQuotationMoveEncoder().decode(ByteBuffer.wrap(encoding.gameData()), moves, true);
    } catch (MorphyMoveDecodingException e) {
      log.warn("Error parsing move in game quotation", e);
      moves = e.getModel();
    }
    return moves;
  }

  private <T> T valueOrDefault(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }

  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    GameQuotationAnnotation qa = (GameQuotationAnnotation) annotation;
    Encoding encoding = encodingOf(qa);
    int start = buf.position();
    ByteBufferUtil.putShortB(buf, 0); // This is the size, will be filled in later
    ByteBufferUtil.putShortB(buf, encoding != null ? 2 : 1);
    if (encoding != null && encoding.setupPositionData() != null) {
      ByteBufferUtil.putShortB(buf, 64);
      buf.put(encoding.setupPositionData());
    } else {
      ByteBufferUtil.putShortB(buf, 0);
    }

    ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header().getWhite(), ""));
    ByteBufferUtil.putByte(buf, 0);
    ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header().getBlack(), ""));
    ByteBufferUtil.putByte(buf, 0);
    ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header().getWhiteElo(), 0));
    ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header().getBlackElo(), 0));
    ByteBufferUtil.putShortB(
        buf, CBUtil.encodeEco(valueOrDefault(qa.header().getEco(), Eco.unset())));
    ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header().getEvent(), ""));
    ByteBufferUtil.putByte(buf, 0);
    ByteBufferUtil.putByteString(buf, valueOrDefault(qa.header().getEventSite(), ""));
    ByteBufferUtil.putByte(buf, 0);
    ByteBufferUtil.putIntB(
        buf, CBUtil.encodeDate(valueOrDefault(qa.header().getDate(), Date.unset())));

    TournamentTimeControl ttc = TournamentTimeControl.fromName(qa.header().getEventTimeControl());
    TournamentType tt = TournamentType.fromName(qa.header().getEventType());
    ByteBufferUtil.putShortB(buf, CBUtil.encodeTournamentType(tt, ttc));
    ByteBufferUtil.putShortB(
        buf,
        CBUtil.encodeNation(Nation.fromIOC(valueOrDefault(qa.header().getEventCountry(), ""))));

    ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header().getEventCategory(), 0));
    ByteBufferUtil.putShortB(buf, valueOrDefault(qa.header().getEventRounds(), 0));
    ByteBufferUtil.putByte(buf, valueOrDefault(qa.header().getSubRound(), 0));
    ByteBufferUtil.putByte(buf, valueOrDefault(qa.header().getRound(), 0));
    ByteBufferUtil.putByte(
        buf,
        CBUtil.encodeGameResult(valueOrDefault(qa.header().getResult(), GameResult.NOT_FINISHED)));
    ByteBufferUtil.putShortB(buf, qa.unknown());

    if (encoding != null) {
      buf.put(encoding.gameData());
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
      log.warn("Unknown game quotation type: {}", type);
    }

    byte[] setupPositionData = null;

    int flags = ByteBufferUtil.getUnsignedShortB(buf);
    if ((flags & 64) > 0) {
      setupPositionData = new byte[28];
      buf.get(setupPositionData);
      flags -= 64;
    }
    if (flags != 0) {
      log.warn("Unknown flag value parsing game quotation: {}", flags);
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
      return new GameQuotationAnnotation(header, unknown, null, null);
    }
    Encoding encoding = new Encoding(setupPositionData, gameData);
    return new GameQuotationAnnotation(header, unknown, () -> decodeMoves(encoding), encoding);
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
