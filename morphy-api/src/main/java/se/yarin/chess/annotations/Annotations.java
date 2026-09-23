package se.yarin.chess.annotations;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a list of {@link Annotation} on a {@link se.yarin.chess.GameMovesModel.Node}.
 * A node may have multiple annotations.
 */
public class Annotations extends ArrayList<Annotation> {
  public Annotations() {
  }

  public Annotations(List<Annotation> annotations) {
    super(annotations);
  }

  /**
   * Replaces an annotation. Any annotations of the same class as annotation will be removed.
   *
   * @param annotation the annotation to add
   */
  public void replace(@NotNull Annotation annotation) {
    removeByClass(annotation.getClass());
    add(annotation);
  }

  /**
   * Removes the annotation of the specified type
   *
   * @param annotationClass the type of annotation to remove
   * @return true if an annotation was removed; otherwise false
   */
  public <T extends Annotation> boolean removeByClass(Class<T> annotationClass) {
    return removeIf(a -> a.getClass() == annotationClass);
  }

  /**
   * Gets an annotation of the specified class. If there are multiple annotations with the class,
   * the first instance will be returned.
   *
   * @param clazz the annotation class to get
   * @return an annotation of the specified class, or null if none existed in this set
   */
  public <T extends Annotation> T getByClass(Class<T> clazz) {
    for (Annotation annotation : this) {
      if (annotation.getClass() == clazz || clazz.isInstance(annotation)) {
        return (T) annotation;
      }
    }
    return null;
  }

  /**
   * Gets all annotations of the specified class.
   *
   * @param clazz the annotation class to get
   * @return a list of annotations of the specified class
   */
  public <T extends Annotation> List<T> getAllByClass(Class<T> clazz) {
    ArrayList<T> classAnnotations = new ArrayList<>();
    for (Annotation annotation : this) {
      if (annotation.getClass() == clazz || clazz.isInstance(annotation)) {
        classAnnotations.add((T) annotation);
      }
    }
    return classAnnotations;
  }
}
