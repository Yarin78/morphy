package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.morphy.chessbase.annotations.TimeControlAnnotation.TimeSerie;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link TimeControlAnnotation} in the v1 {@code .cba} format. */
public class TimeControlAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    TimeControlAnnotation tca = (TimeControlAnnotation) annotation;
    for (int i = 0; i < 3; i++) {
      TimeSerie ts =
          i < tca.timeSeries().size()
              ? tca.timeSeries().get(i)
              : ImmutableTimeSerie.of(0, 0, 0, 0);
      ByteBufferUtil.putIntB(buf, ts.start());
      ByteBufferUtil.putIntB(buf, ts.increment());
      ByteBufferUtil.putShortB(buf, ts.moves());
      ByteBufferUtil.putByte(buf, ts.type());
    }
  }

  @Override
  public Annotation deserialize(ByteBuffer buf, int length) {
    ArrayList<TimeSerie> timeSeries = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      int start = ByteBufferUtil.getIntB(buf);
      int increment = ByteBufferUtil.getIntB(buf);
      int moves = ByteBufferUtil.getUnsignedShortB(buf);
      int type = ByteBufferUtil.getUnsignedByte(buf);
      if (start == 0 && increment == 0 && moves == 0 && type == 0) {
        continue; // An unused serie
      }
      timeSeries.add(ImmutableTimeSerie.of(start, increment, moves, type));
      if (moves == 1000) break;
    }

    return ImmutableTimeControlAnnotation.of(timeSeries);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableTimeControlAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x24;
  }
}
