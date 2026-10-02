package se.yarin.morphy.cb2.annotations;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.Annotation;

/**
 * Converts between the content of a game's {@code .2cba} record and the annotations of a {@link
 * GameMovesModel}.
 *
 * <p>The content is a series of position blocks in ascending order of position, each the position,
 * a count and that many annotations, ended by {@code 7fffffff}. Position −1 is the game as a whole;
 * otherwise a position is the index of a move in the order a PGN lists them, each alternative with
 * everything that follows it coming right after the move it is an alternative to. An annotation at
 * a position is attached to the node that move leads to.
 */
public final class AnnotationBlockCodec {
  private static final Logger log = LoggerFactory.getLogger(AnnotationBlockCodec.class);

  public static final int END = 0x7FFFFFFF;
  public static final int GAME = -1;

  /** The content of a record holding no annotations. */
  public static final byte[] EMPTY = {(byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f};

  /**
   * One annotation, and the position it's attached to.
   *
   * @param position -1 for the game, otherwise the index of the move in PGN order
   * @param annotation the annotation
   */
  public record Positioned(int position, @NotNull Annotation annotation) {}

  private AnnotationBlockCodec() {}

  /**
   * Reads the annotations of a record.
   *
   * @param content the record's content
   * @return the annotations in the order stored, or null if some annotation can't be read, in which
   *     case the record can only be kept as it is
   */
  public static @Nullable List<Positioned> read(byte @NotNull [] content) {
    ByteBuffer buf = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
    List<Positioned> out = new ArrayList<>();
    try {
      while (true) {
        int position = buf.getInt();
        if (position == END) {
          break;
        }
        int count = buf.getInt();
        for (int i = 0; i < count; i++) {
          out.add(new Positioned(position, AnnotationCodec.read(buf)));
        }
      }
    } catch (AnnotationCodec.UnreadableAnnotationException | BufferUnderflowException e) {
      log.warn("Annotations can't be read: {}", e.getMessage());
      return null;
    }
    if (buf.hasRemaining()) {
      log.warn("{} bytes after the end of the annotations", buf.remaining());
    }
    return out;
  }

  /**
   * Reads the annotations of a record and attaches them to the moves.
   *
   * @param content the record's content
   * @param moves the moves of the game, without annotations
   * @return false if the annotations can't be read, in which case none are attached
   */
  public static boolean decode(byte @NotNull [] content, @NotNull GameMovesModel moves) {
    List<Positioned> annotations = read(content);
    if (annotations == null) {
      return false;
    }
    List<GameMovesModel.Node> nodes = moves.getAllNodesPgnOrder();
    for (Positioned p : annotations) {
      if (p.position() == GAME) {
        moves.root().addAnnotation(p.annotation());
      } else if (p.position() >= 0 && p.position() < nodes.size()) {
        nodes.get(p.position()).addAnnotation(p.annotation());
      } else {
        log.warn("An annotation at position {}, beyond the {} moves", p.position(), nodes.size());
      }
    }
    return true;
  }

  /**
   * Encodes the annotations of a game. Annotations with no v2 form are left out.
   *
   * @param moves the moves and their annotations
   * @return the content of the record
   */
  public static byte @NotNull [] encode(@NotNull GameMovesModel moves) {
    List<GameMovesModel.Node> nodes = moves.getAllNodesPgnOrder();
    int size = 4;
    for (GameMovesModel.Node node : moves.getAllNodes()) {
      for (Annotation annotation : node.getAnnotations()) {
        size += AnnotationCodec.maxSize(annotation);
      }
      size += node.getAnnotations().isEmpty() ? 0 : 8;
    }
    ByteBuffer buf = AnnotationCodec.bufferFor(size);
    writeBlock(buf, GAME, moves.root());
    for (int i = 0; i < nodes.size(); i++) {
      writeBlock(buf, i, nodes.get(i));
    }
    buf.putInt(END);
    return Arrays.copyOf(buf.array(), buf.position());
  }

  private static void writeBlock(ByteBuffer buf, int position, GameMovesModel.Node node) {
    if (node.getAnnotations().isEmpty()) {
      return;
    }
    int start = buf.position();
    buf.putInt(position).putInt(0);
    int count = 0;
    for (Annotation annotation : node.getAnnotations()) {
      if (AnnotationCodec.write(buf, annotation)) {
        count++;
      } else {
        log.debug("{} has no v2 form and is not stored", annotation.getClass().getSimpleName());
      }
    }
    if (count == 0) {
      buf.position(start);
    } else {
      buf.putInt(start + 4, count);
    }
  }
}
