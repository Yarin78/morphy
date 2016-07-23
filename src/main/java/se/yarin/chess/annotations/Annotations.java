package se.yarin.chess.annotations;

import lombok.NonNull;

import java.util.*;

/**
 * Represents a set of {@link Annotation} on a {@link se.yarin.chess.GameMovesModel.Node}.
 * A node may have multiple annotations, but only one annotation per class.
 * If an annotation is added that has the same type as an existing annotation,
 * {@link Annotation#combine(Annotation)} is used to determine the resulting annotation.
 */
public class Annotations {

    private TreeSet<Annotation> annotations = new TreeSet<>((o1, o2) -> {
        int prioDif = o2.priority() - o1.priority();
        if (prioDif != 0) return prioDif;
        return o1.getClass().toString().compareTo(o2.getClass().toString());
    });

    /**
     * Adds an annotation. If an existing annotation with the same type exists,
     * there will be a conflict resolution done using {@link Annotation#combine(Annotation)}.
     * @param annotation the annotation to add
     */
    public void add(@NonNull Annotation annotation) {
        Annotation oldAnno = null;
        for (Annotation anno : annotations) {
            if (anno.getClass() == annotation.getClass()) {
                oldAnno = anno;
            }
        }
        if (oldAnno != null) {
            annotation = annotation.combine(oldAnno);
            annotations.remove(oldAnno);
        }
        if (!annotation.isEmptyAnnotation()) {
            annotations.add(annotation);
        }
    }

    /**
     * Adds all annotations from one set of annotations to this one
     * @param annotations the annotations to add to this set
     */
    public void addAll(@NonNull Annotations annotations) {
        annotations.getAll().forEach(this::add);
    }

    /**
     * Removes the annotation of the specified type
     * @param annotationType the type of annotation to remove
     * @return true if an annotation was removed; otherwise false
     */
    public <T extends Annotation> boolean remove(Class<T> annotationType) {
        T annotation = getAnnotation(annotationType);
        if (annotation != null) {
            annotations.remove(annotation);
            return true;
        }
        return false;
    }

    /**
     * Removes all annotations
     */
    public void clear() {
        annotations.clear();
    }

    /**
     * Returns the number of annotations in this set.
     * @return the number of annotations in this set
     */
    public int size() {
        return annotations.size();
    }

    /**
     * Returns a read-only set of all annotations at this position
     * @return a set of annotations
     */
    public Set<Annotation> getAll() {
        return Collections.unmodifiableSet(annotations);
    }

    /**
     * Gets an annotation of the specified class
     * @param clazz the annotation class to get
     * @return an annotation of the specified class, or null if none existed in this set
     */
    public <T extends Annotation> T getAnnotation(Class<T> clazz) {
        for (Annotation annotation : annotations) {
            if (annotation.getClass() == clazz) {
                return (T) annotation;
            }
        }
        return null;
    }

    /**
     * Decorates a move with annotations.
     * @param moveText the move to decorate with annotations, typically in SAN or LAN
     * @param ascii if true, only ASCII characters will be outputted
     * @return the move decorated with annotations
     */
    public String format(@NonNull String moveText, boolean ascii) {
        for (Annotation annotation : annotations) {
            moveText = annotation.format(moveText, ascii);
        }
        return moveText;
    }
}
