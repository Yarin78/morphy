package se.yarin.chess;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.annotations.Annotation;

import java.util.*;

/**
 * Compares two {@link GameModel} instances and provides a detailed comparison summary.
 */
public class GameModelComparator {

    /**
     * Compares two GameModel instances.
     *
     * @param model1 the first game model
     * @param model2 the second game model
     * @return a comparison result with detailed information about differences
     */
    @NotNull
    public static ComparisonResult compare(@NotNull GameModel model1, @NotNull GameModel model2) {
        ComparisonResult result = new ComparisonResult();

        // Compare headers
        compareHeaders(model1.header(), model2.header(), result);

        // Compare moves
        compareMoves(model1.moves(), model2.moves(), result);

        return result;
    }

    private static void compareHeaders(@NotNull GameHeaderModel header1,
                                       @NotNull GameHeaderModel header2,
                                       @NotNull ComparisonResult result) {
        Map<String, Object> fields1 = header1.getAllFields();
        Map<String, Object> fields2 = header2.getAllFields();

        Set<String> allFields = new HashSet<>();
        allFields.addAll(fields1.keySet());
        allFields.addAll(fields2.keySet());

        for (String field : allFields) {
            Object value1 = fields1.get(field);
            Object value2 = fields2.get(field);

            if (!Objects.equals(value1, value2) && (value1 != null && value2 != null && !value1.toString().equals(value2.toString()))) {
                result.headerDifferences.add(new FieldDifference(field, value1, value2));
            }
        }
    }

    private static void compareMoves(@NotNull GameMovesModel moves1,
                                     @NotNull GameMovesModel moves2,
                                     @NotNull ComparisonResult result) {
        // Compare setup positions
        if (!moves1.root().position().equals(moves2.root().position())) {
            result.setupPositionsDiffer = true;
        }

        if (moves1.root().ply() != moves2.root().ply()) {
            result.setupPlyDiffers = true;
        }

        // Compare move trees
        compareNodes(moves1.root(), moves2.root(), result, new ArrayList<>());
    }

    private static void compareNodes(@NotNull GameMovesModel.Node node1,
                                     @NotNull GameMovesModel.Node node2,
                                     @NotNull ComparisonResult result,
                                     @NotNull List<String> path) {
        // Compare number of moves (variations)
        if (node1.numMoves() != node2.numMoves()) {
            result.addVariationCountDifference(path, node1.numMoves(), node2.numMoves());
            // Continue comparing as many variations as possible
        }

        int minMoves = Math.min(node1.numMoves(), node2.numMoves());

        for (int i = 0; i < minMoves; i++) {
            GameMovesModel.Node child1 = node1.children().get(i);
            GameMovesModel.Node child2 = node2.children().get(i);

            // Compare the moves themselves
            Move move1 = child1.lastMove();
            Move move2 = child2.lastMove();

            List<String> childPath = new ArrayList<>(path);
            childPath.add(move1.toSAN());

            if (!move1.equals(move2)) {
                result.addMoveDifference(childPath, move1, move2);
            } else {
                // Compare annotations on this move
                compareAnnotations(child1, child2, result, childPath);

                // Recursively compare the subtree
                compareNodes(child1, child2, result, childPath);
            }
        }
    }

    private static void compareAnnotations(@NotNull GameMovesModel.Node node1,
                                           @NotNull GameMovesModel.Node node2,
                                           @NotNull ComparisonResult result,
                                           @NotNull List<String> path) {
        // Group annotations by class type
        Map<Class<? extends Annotation>, List<Annotation>> annotationsByClass1 = groupAnnotationsByClass(node1);
        Map<Class<? extends Annotation>, List<Annotation>> annotationsByClass2 = groupAnnotationsByClass(node2);

        // Get all annotation classes from both nodes
        Set<Class<? extends Annotation>> allClasses = new HashSet<>();
        allClasses.addAll(annotationsByClass1.keySet());
        allClasses.addAll(annotationsByClass2.keySet());

        // Compare annotations for each class type
        for (Class<? extends Annotation> annotationClass : allClasses) {
            List<Annotation> list1 = annotationsByClass1.getOrDefault(annotationClass, new ArrayList<>());
            List<Annotation> list2 = annotationsByClass2.getOrDefault(annotationClass, new ArrayList<>());

            if (!annotationListsEqual(list1, list2)) {
                // Count differences
                int onlyInModel1 = countOnlyInFirst(list1, list2);
                int onlyInModel2 = countOnlyInFirst(list2, list1);
                int inBothButDifferent = Math.min(list1.size(), list2.size()) -
                                        countInBoth(list1, list2);

                result.addAnnotationDifference(path, annotationClass,
                    list1, list2, onlyInModel1, onlyInModel2, inBothButDifferent);
            }
        }
    }

    private static Map<Class<? extends Annotation>, List<Annotation>> groupAnnotationsByClass(
            @NotNull GameMovesModel.Node node) {
        Map<Class<? extends Annotation>, List<Annotation>> result = new HashMap<>();
        for (Annotation annotation : node.getAnnotations()) {
            result.computeIfAbsent(annotation.getClass(), k -> new ArrayList<>()).add(annotation);
        }
        return result;
    }

    private static int countOnlyInFirst(List<Annotation> list1, List<Annotation> list2) {
        int count = 0;
        List<Annotation> remaining = new ArrayList<>(list2);
        for (Annotation ann : list1) {
            if (!remaining.remove(ann)) {
                count++;
            }
        }
        return count;
    }

    private static int countInBoth(List<Annotation> list1, List<Annotation> list2) {
        int count = 0;
        List<Annotation> remaining = new ArrayList<>(list2);
        for (Annotation ann : list1) {
            if (remaining.remove(ann)) {
                count++;
            }
        }
        return count;
    }

    private static boolean annotationListsEqual(List<? extends Annotation> list1,
                                                List<? extends Annotation> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        // Compare annotations as multisets (order doesn't matter, but duplicates do)
        // Count occurrences of each annotation
        Map<Annotation, Integer> counts1 = countAnnotations(list1);
        Map<Annotation, Integer> counts2 = countAnnotations(list2);

        return counts1.equals(counts2);
    }

    private static Map<Annotation, Integer> countAnnotations(List<? extends Annotation> annotations) {
        Map<Annotation, Integer> counts = new HashMap<>();
        for (Annotation annotation : annotations) {
            counts.merge(annotation, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Represents the result of comparing two GameModel instances.
     */
    public static class ComparisonResult {
        private final List<FieldDifference> headerDifferences = new ArrayList<>();
        private final List<MoveDifference> moveDifferences = new ArrayList<>();
        private final List<VariationCountDifference> variationCountDifferences = new ArrayList<>();
        private final List<GenericAnnotationDifference> annotationDifferences = new ArrayList<>();
        private boolean setupPositionsDiffer = false;
        private boolean setupPlyDiffers = false;

        public boolean isIdentical() {
            return headerDifferences.isEmpty() &&
                    moveDifferences.isEmpty() &&
                    variationCountDifferences.isEmpty() &&
                    annotationDifferences.isEmpty() &&
                    !setupPositionsDiffer &&
                    !setupPlyDiffers;
        }

        public boolean headersMatch() {
            return headerDifferences.isEmpty();
        }

        public boolean mainLineMovesMatch() {
            return moveDifferences.isEmpty();
        }

        public boolean variationsMatch() {
            return variationCountDifferences.isEmpty() && moveDifferences.isEmpty();
        }

        public boolean annotationsMatch() {
            return annotationDifferences.isEmpty();
        }

        public List<FieldDifference> getHeaderDifferences() {
            return Collections.unmodifiableList(headerDifferences);
        }

        public List<MoveDifference> getMoveDifferences() {
            return Collections.unmodifiableList(moveDifferences);
        }

        public List<VariationCountDifference> getVariationCountDifferences() {
            return Collections.unmodifiableList(variationCountDifferences);
        }

        public List<GenericAnnotationDifference> getAnnotationDifferences() {
            return Collections.unmodifiableList(annotationDifferences);
        }

        public boolean setupPositionsDiffer() {
            return setupPositionsDiffer;
        }

        public boolean setupPlyDiffers() {
            return setupPlyDiffers;
        }

        void addVariationCountDifference(List<String> path, int count1, int count2) {
            variationCountDifferences.add(new VariationCountDifference(path, count1, count2));
        }

        void addMoveDifference(List<String> path, Move move1, Move move2) {
            moveDifferences.add(new MoveDifference(path, move1, move2));
        }

        void addAnnotationDifference(List<String> path, Class<? extends Annotation> annotationClass,
                                      List<Annotation> annotations1, List<Annotation> annotations2,
                                      int onlyInModel1, int onlyInModel2, int inBothButDifferent) {
            annotationDifferences.add(new GenericAnnotationDifference(path, annotationClass,
                annotations1, annotations2, onlyInModel1, onlyInModel2, inBothButDifferent));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("GameModel Comparison Result:\n");
            sb.append("===========================\n");

            if (isIdentical()) {
                sb.append("Models are IDENTICAL\n");
                return sb.toString();
            }

            sb.append("Models DIFFER:\n\n");

            if (setupPositionsDiffer) {
                sb.append("Setup positions differ\n");
            }

            if (setupPlyDiffers) {
                sb.append("Setup ply differs\n");
            }

            if (!headerDifferences.isEmpty()) {
                sb.append("Header differences (").append(headerDifferences.size()).append("):\n");
                for (FieldDifference diff : headerDifferences) {
                    sb.append("  ").append(diff).append("\n");
                }
                sb.append("\n");
            }

            if (!moveDifferences.isEmpty()) {
                sb.append("Move differences (").append(moveDifferences.size()).append("):\n");
                for (MoveDifference diff : moveDifferences) {
                    sb.append("  ").append(diff).append("\n");
                }
                sb.append("\n");
            }

            if (!variationCountDifferences.isEmpty()) {
                sb.append("Variation count differences (").append(variationCountDifferences.size()).append("):\n");
                for (VariationCountDifference diff : variationCountDifferences) {
                    sb.append("  ").append(diff).append("\n");
                }
                sb.append("\n");
            }

            if (!annotationDifferences.isEmpty()) {
                sb.append("Annotation differences (").append(annotationDifferences.size()).append("):\n");
                for (GenericAnnotationDifference diff : annotationDifferences) {
                    sb.append("  ").append(diff).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();
        }
    }

    public record FieldDifference(String fieldName, Object value1, Object value2) {

        @Override
        public @NotNull String toString() {
            return fieldName + ": " + value1 + " != " + value2;
        }
    }

    public record MoveDifference(List<String> path, Move move1, Move move2) {
        public MoveDifference(List<String> path, Move move1, Move move2) {
            this.path = new ArrayList<>(path);
            this.move1 = move1;
            this.move2 = move2;
        }

        @Override
        public List<String> path() {
            return Collections.unmodifiableList(path);
        }

        @Override
        public String toString() {
            return "At " + String.join(" ", path) + ": " + move1.toSAN() + " != " + move2.toSAN();
        }
    }

    public record VariationCountDifference(List<String> path, int count1, int count2) {
        public VariationCountDifference(List<String> path, int count1, int count2) {
            this.path = new ArrayList<>(path);
            this.count1 = count1;
            this.count2 = count2;
        }

        @Override
        public List<String> path() {
            return Collections.unmodifiableList(path);
        }

        @Override
        public String toString() {
            return "At " + String.join(" ", path) + ": " + count1 + " variations != " + count2 + " variations";
        }
    }

    /**
     * Represents a difference in annotations of a specific type at a node.
     * Provides detailed counts of differences for that annotation type.
     */
    public record GenericAnnotationDifference(
            List<String> path,
            Class<? extends Annotation> annotationClass,
            List<Annotation> annotations1,
            List<Annotation> annotations2,
            int onlyInModel1,
            int onlyInModel2,
            int inBothButDifferent) {

        public GenericAnnotationDifference(List<String> path,
                                           Class<? extends Annotation> annotationClass,
                                           List<Annotation> annotations1,
                                           List<Annotation> annotations2,
                                           int onlyInModel1,
                                           int onlyInModel2,
                                           int inBothButDifferent) {
            this.path = new ArrayList<>(path);
            this.annotationClass = annotationClass;
            this.annotations1 = new ArrayList<>(annotations1);
            this.annotations2 = new ArrayList<>(annotations2);
            this.onlyInModel1 = onlyInModel1;
            this.onlyInModel2 = onlyInModel2;
            this.inBothButDifferent = inBothButDifferent;
        }

        @Override
        public List<String> path() {
            return Collections.unmodifiableList(path);
        }

        @Override
        public List<Annotation> annotations1() {
            return Collections.unmodifiableList(annotations1);
        }

        @Override
        public List<Annotation> annotations2() {
            return Collections.unmodifiableList(annotations2);
        }

        @Override
        public String toString() {
            String className = annotationClass.getSimpleName();
            StringBuilder sb = new StringBuilder();
            sb.append("At ").append(String.join(" ", path)).append(": ")
              .append(className).append(" differs");

            if (onlyInModel1 > 0 || onlyInModel2 > 0 || inBothButDifferent > 0) {
                sb.append(" (");
                List<String> details = new ArrayList<>();
                if (onlyInModel1 > 0) {
                    details.add(onlyInModel1 + " only in model1");
                }
                if (onlyInModel2 > 0) {
                    details.add(onlyInModel2 + " only in model2");
                }
                if (inBothButDifferent > 0) {
                    details.add(inBothButDifferent + " different");
                }
                sb.append(String.join(", ", details));
                sb.append(")");
            }

            return sb.toString();
        }
    }
}
