package se.yarin.morphy.games;

import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class RatingType {
  @Value.Parameter
  @Nullable
  public abstract TournamentTimeControl international();

  @Value.Parameter
  @Nullable
  public abstract TournamentTimeControl national();

  @Value.Parameter
  @Nullable
  public abstract Nation nation();

  @Value.Parameter
  @Nullable
  public abstract String name();

  public static RatingType unspecified() {
    return RatingType.international(TournamentTimeControl.NORMAL);
  }

  public boolean isInternational() {
    return international() != null;
  }

  public boolean isNational() {
    return national() != null;
  }

  public static RatingType international(TournamentTimeControl timeControl) {
    String name = timeControl == TournamentTimeControl.CORRESPONDENCE ? "ICCF" : "FIDE";
    return ImmutableRatingType.of(timeControl, null, null, name);
  }

  public static RatingType national(TournamentTimeControl timeControl, Nation nation) {
    return ImmutableRatingType.of(null, timeControl, nation, null);
  }

  public void serialize(ByteBuffer buf) {
    buf.put((byte) 0);
    buf.put((byte) (isInternational() ? 1 : 2));
    buf.put((byte) (national() == null ? 0 : national().ordinal()));
    buf.put((byte) (international() == null ? 0 : (international().ordinal() + 1)));
    buf.put((byte) (nation() == null ? 0 : CBUtil.encodeNation(nation())));
    ByteBufferUtil.putFixedSizeByteString(buf, name() == null ? "" : name(), 11);
  }

  public static RatingType deserialize(ByteBuffer buf) {
    byte b = buf.get();
    int type = buf.get() & 7;
    int nationalTC = buf.get();
    int internationalTC = buf.get() - 1;
    Nation nation = CBUtil.decodeNation(ByteBufferUtil.getUnsignedByte(buf));
    String name = ByteBufferUtil.getFixedSizeByteString(buf, 11);

    // Some checks to avoid setting wrong values in case of trash data
    if (b == 0 && type == 1 && nation == Nation.NONE) {
      if (internationalTC >= 0 && internationalTC < TournamentTimeControl.values().length) {
        TournamentTimeControl timeControl = TournamentTimeControl.values()[internationalTC];
        return ImmutableRatingType.of(timeControl, null, Nation.NONE, name);
      }
    }
    if (b == 0 && type == 2 && nation != Nation.NONE) {
      if (nationalTC >= 0 && nationalTC < TournamentTimeControl.values().length) {
        TournamentTimeControl timeControl = TournamentTimeControl.values()[nationalTC];
        return ImmutableRatingType.of(null, timeControl, nation, name);
      }
    }

    return RatingType.international(TournamentTimeControl.NORMAL);
  }

  @Override
  public String toString() {
    if (isInternational()) {
      return "International " + international().getName() + " " + name();
    } else {
      return "National " + national().getName() + " " + nation().getName() + " " + name();
    }
  }
}
