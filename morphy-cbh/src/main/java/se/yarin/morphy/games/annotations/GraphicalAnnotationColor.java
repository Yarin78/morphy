package se.yarin.morphy.games.annotations;

public enum GraphicalAnnotationColor {
  NONE(0),
  NOT_USED(1),
  GREEN(2),
  YELLOW(3),
  RED(4),
  // The colors below are very rare but do occur in e.g. Mega Database 2021
  UNKNOWN_5(5),
  UNKNOWN_6(6),
  BLUE(7),
  CYAN(8),
  ORANGE(9);

  private final int colorId;

  public int getColorId() {
    return colorId;
  }

  GraphicalAnnotationColor(int colorId) {
    this.colorId = colorId;
  }

  public static GraphicalAnnotationColor fromInt(int data) {
    return GraphicalAnnotationColor.values()[data];
  }

  public static int maxColor() {
    return GraphicalAnnotationColor.values().length - 1;
  }
}
