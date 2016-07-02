package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;
import se.yarin.chess.annotations.Annotation;

import java.util.Collections;
import java.util.List;

/**
 * Event that adds an annotation at the current node
 */
public class AddAnnotationsEvent extends GameEvent {
    private List<Annotation> annotations;

    public AddAnnotationsEvent(@NonNull List<Annotation> annotations) {
        this.annotations = Collections.unmodifiableList(annotations);
    }

    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
        annotations.forEach(model::addAnnotation);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Annotation annotation : annotations) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(annotation.toString());
        }
        return super.toString() + ": " + sb.toString();
    }
}
