package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.chess.annotations.Annotation;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GraphicalArrowsAnnotation extends Annotation {

    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Arrow {
        @Getter private GraphicalAnnotationColor color;
        @Getter private int fromSqi, toSqi;
    }

    @Getter private Collection<Arrow> arrows;

    public GraphicalArrowsAnnotation(@NonNull List<Arrow> arrows) {
        this.arrows = Collections.unmodifiableCollection(arrows);
    }

    @Override
    public int priority() {
        return 5;
    }
}
