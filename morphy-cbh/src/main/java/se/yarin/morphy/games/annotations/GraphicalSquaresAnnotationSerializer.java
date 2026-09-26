package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import java.util.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.morphy.chessbase.annotations.GraphicalSquaresAnnotation.Square;
import se.yarin.morphy.exceptions.MorphyAnnotationExecption;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link GraphicalSquaresAnnotation} in the v1 {@code .cba} format. */
public class GraphicalSquaresAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    for (Square square : ((GraphicalSquaresAnnotation) annotation).squares()) {
      ByteBufferUtil.putByte(buf, square.color().getColorId());
      ByteBufferUtil.putByte(buf, square.sqi() + 1);
    }
  }

  @Override
  public GraphicalSquaresAnnotation deserialize(ByteBuffer buf, int length)
      throws MorphyAnnotationExecption {
    ArrayList<Square> squares = new ArrayList<>();
    for (int i = 0; i < length / 2; i++) {
      int color = ByteBufferUtil.getUnsignedByte(buf);
      int sqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
      if (sqi < 0 || sqi > 63 || color < 0 || color > GraphicalAnnotationColor.maxColor())
        throw new MorphyAnnotationExecption("Invalid graphical squares annotation");
      squares.add(ImmutableSquare.of(GraphicalAnnotationColor.fromInt(color), sqi));
    }
    return ImmutableGraphicalSquaresAnnotation.of(squares);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableGraphicalSquaresAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x04;
  }
}
