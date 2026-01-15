package se.yarin.morphy.entities;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.util.CBUtil;

@Value.Immutable
public abstract class Player extends Entity implements Comparable<Player> {
  @Value.Default
  @NotNull
  public String lastName() {
    return "";
  }

  @Value.Default
  @NotNull
  public String firstName() {
    return "";
  }

  @Override
  public Entity withCountAndFirstGameId(int count, int firstGameId) {
    return ImmutablePlayer.builder().from(this).count(count).firstGameId(firstGameId).build();
  }

  public static Player ofFullName(@Nullable String fullName) {
    ImmutablePlayer.Builder builder = ImmutablePlayer.builder();
    if (fullName != null) {
      String firstName = "", lastName = fullName.trim();
      int comma = lastName.indexOf(",");
      if (comma > 0) {
        firstName = lastName.substring(comma + 1).trim();
        lastName = lastName.substring(0, comma).trim();
      }
      builder.lastName(lastName).firstName(firstName);
    }
    return builder.build();
  }

  public static Player of(String lastName, String firstName) {
    return ImmutablePlayer.builder().lastName(lastName).firstName(firstName).build();
  }

  public String getFullName() {
    if (lastName().length() == 0) {
      return firstName();
    }
    if (firstName().length() == 0) {
      return lastName();
    }
    return lastName() + ", " + firstName();
  }

  public String getFullNameShort() {
    if (lastName().length() == 0) {
      return firstName();
    }
    if (firstName().length() == 0) {
      return lastName();
    }
    return lastName() + ", " + firstName().charAt(0);
  }

  public String toString() {
    return getFullName();
  }

  public int compareTo(Player o) {
    int comp = CBUtil.compareStringUnsigned(lastName(), o.lastName());
    if (comp != 0) return comp;
    return CBUtil.compareStringUnsigned(firstName(), o.firstName());
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Player that = (Player) o;

    if (!lastName().equals(that.lastName())) return false;
    return firstName().equals(that.firstName());
  }
}
