package se.yarin.morphy.games.annotations;

import lombok.Getter;

public enum GraphicalAnnotationColor {
    NONE(0),
    NOT_USED(1),
    GREEN(2),
    YELLOW(3),
    RED(4);

    @Getter
    private final int colorId;

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
