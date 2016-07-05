package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.chess.annotations.Annotation;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GraphicalSquaresAnnotation extends Annotation {

    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Square {
        @Getter private GraphicalAnnotationColor color;
        @Getter private int sqi;
    }

    @Getter private Collection<Square> squares;

    public GraphicalSquaresAnnotation(@NonNull List<Square> squares) {
        this.squares = Collections.unmodifiableCollection(squares);
    }

    @Override
    public int priority() {
        return 6;
    }
}
