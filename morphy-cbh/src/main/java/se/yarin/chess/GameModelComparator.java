package se.yarin.chess;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;

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
        // Compare NAG annotations
        List<NAGAnnotation> nags1 = new ArrayList<>();
        List<NAGAnnotation> nags2 = new ArrayList<>();

        for (Annotation ann : node1.getAnnotations()) {
            if (ann instanceof NAGAnnotation) {
                nags1.add((NAGAnnotation) ann);
            }
        }

        for (Annotation ann : node2.getAnnotations()) {
            if (ann instanceof NAGAnnotation) {
                nags2.add((NAGAnnotation) ann);
            }
        }

        if (!annotationListsEqual(nags1, nags2)) {
            result.addNAGDifference(path, nags1, nags2);
        }

        // Compare commentary before move
        CommentaryBeforeMoveAnnotation before1 =
                node1.getAnnotations().getByClass(CommentaryBeforeMoveAnnotation.class);
        CommentaryBeforeMoveAnnotation before2 =
                node2.getAnnotations().getByClass(CommentaryBeforeMoveAnnotation.class);

        if (!Objects.equals(before1, before2)) {
            String comment1 = before1 != null ? before1.getCommentary() : null;
            String comment2 = before2 != null ? before2.getCommentary() : null;
            if (!Objects.equals(comment1, comment2)) {
                result.addCommentDifference(path, "before", comment1, comment2);
            }
        }

        // Compare commentary after move
        CommentaryAfterMoveAnnotation after1 =
                node1.getAnnotations().getByClass(CommentaryAfterMoveAnnotation.class);
        CommentaryAfterMoveAnnotation after2 =
                node2.getAnnotations().getByClass(CommentaryAfterMoveAnnotation.class);

        if (!Objects.equals(after1, after2)) {
            String comment1 = after1 != null ? after1.getCommentary() : null;
            String comment2 = after2 != null ? after2.getCommentary() : null;
            if (!Objects.equals(comment1, comment2)) {
                result.addCommentDifference(path, "after", comment1, comment2);
            }
        }

        // Compare other annotations
        List<Annotation> other1 = new ArrayList<>();
        List<Annotation> other2 = new ArrayList<>();

        for (Annotation ann : node1.getAnnotations()) {
            if (!(ann instanceof NAGAnnotation) &&
                    !(ann instanceof CommentaryBeforeMoveAnnotation) &&
                    !(ann instanceof CommentaryAfterMoveAnnotation)) {
                other1.add(ann);
            }
        }

        for (Annotation ann : node2.getAnnotations()) {
            if (!(ann instanceof NAGAnnotation) &&
                    !(ann instanceof CommentaryBeforeMoveAnnotation) &&
                    !(ann instanceof CommentaryAfterMoveAnnotation)) {
                other2.add(ann);
            }
        }

        if (!annotationListsEqual(other1, other2)) {
            result.addOtherAnnotationDifference(path, other1, other2);
        }
    }

    private static boolean annotationListsEqual(List<? extends Annotation> list1,
                                                List<? extends Annotation> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        // Compare annotations as sets (order doesn't matter)
        Set<Annotation> set1 = new HashSet<>(list1);
        Set<Annotation> set2 = new HashSet<>(list2);

        return set1.equals(set2);
    }

    /**
     * Represents the result of comparing two GameModel instances.
     */
    public static class ComparisonResult {
        private final List<FieldDifference> headerDifferences = new ArrayList<>();
        private final List<MoveDifference> moveDifferences = new ArrayList<>();
        private final List<VariationCountDifference> variationCountDifferences = new ArrayList<>();
        private final List<NAGDifference> nagDifferences = new ArrayList<>();
        private final List<CommentDifference> commentDifferences = new ArrayList<>();
        private final List<AnnotationDifference> otherAnnotationDifferences = new ArrayList<>();
        private boolean setupPositionsDiffer = false;
        private boolean setupPlyDiffers = false;

        public boolean isIdentical() {
            return headerDifferences.isEmpty() &&
                    moveDifferences.isEmpty() &&
                    variationCountDifferences.isEmpty() &&
                    nagDifferences.isEmpty() &&
                    commentDifferences.isEmpty() &&
                    otherAnnotationDifferences.isEmpty() &&
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

        public boolean nagsMatch() {
            return nagDifferences.isEmpty();
        }

        public boolean commentsMatch() {
            return commentDifferences.isEmpty();
        }

        public boolean otherAnnotationsMatch() {
            return otherAnnotationDifferences.isEmpty();
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

        public List<NAGDifference> getNAGDifferences() {
            return Collections.unmodifiableList(nagDifferences);
        }

        public List<CommentDifference> getCommentDifferences() {
            return Collections.unmodifiableList(commentDifferences);
        }

        public List<AnnotationDifference> getOtherAnnotationDifferences() {
            return Collections.unmodifiableList(otherAnnotationDifferences);
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

        void addNAGDifference(List<String> path, List<NAGAnnotation> nags1, List<NAGAnnotation> nags2) {
            nagDifferences.add(new NAGDifference(path, nags1, nags2));
        }

        void addCommentDifference(List<String> path, String position, String comment1, String comment2) {
            commentDifferences.add(new CommentDifference(path, position, comment1, comment2));
        }

        void addOtherAnnotationDifference(List<String> path, List<Annotation> annotations1, List<Annotation> annotations2) {
            otherAnnotationDifferences.add(new AnnotationDifference(path, annotations1, annotations2));
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

            if (!nagDifferences.isEmpty()) {
                sb.append("NAG annotation differences (").append(nagDifferences.size()).append("):\n");
                for (NAGDifference diff : nagDifferences) {
                    sb.append("  ").append(diff).append("\n");
                }
                sb.append("\n");
            }

            if (!commentDifferences.isEmpty()) {
                sb.append("Comment differences (").append(commentDifferences.size()).append("):\n");
                for (CommentDifference diff : commentDifferences) {
                    sb.append("  ").append(diff).append("\n");
                }
                sb.append("\n");
            }

            if (!otherAnnotationDifferences.isEmpty()) {
                sb.append("Other annotation differences (").append(otherAnnotationDifferences.size()).append("):\n");
                for (AnnotationDifference diff : otherAnnotationDifferences) {
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

    public record NAGDifference(List<String> path, List<NAGAnnotation> nags1, List<NAGAnnotation> nags2) {
        public NAGDifference(List<String> path, List<NAGAnnotation> nags1, List<NAGAnnotation> nags2) {
            this.path = new ArrayList<>(path);
            this.nags1 = new ArrayList<>(nags1);
            this.nags2 = new ArrayList<>(nags2);
        }

        @Override
        public List<String> path() {
            return Collections.unmodifiableList(path);
        }

        @Override
        public List<NAGAnnotation> nags1() {
            return Collections.unmodifiableList(nags1);
        }

        @Override
        public List<NAGAnnotation> nags2() {
            return Collections.unmodifiableList(nags2);
        }

        @Override
        public String toString() {
            return "At " + String.join(" ", path) + ": NAGs differ - " + nags1 + " != " + nags2;
        }
    }

    /**
     * @param position "before" or "after"
     */
    public record CommentDifference(List<String> path, String position, String comment1, String comment2) {
        public CommentDifference(List<String> path, String position, String comment1, String comment2) {
            this.path = new ArrayList<>(path);
            this.position = position;
            this.comment1 = comment1;
            this.comment2 = comment2;
        }

        @Override
        public List<String> path() {
            return Collections.unmodifiableList(path);
        }

        @Override
        public String toString() {
            return "At " + String.join(" ", path) + " (" + position + "): \"" + comment1 + "\" != \"" + comment2 + "\"";
        }
    }

    public record AnnotationDifference(List<String> path, List<Annotation> annotations1,
                                       List<Annotation> annotations2) {
        public AnnotationDifference(List<String> path, List<Annotation> annotations1, List<Annotation> annotations2) {
            this.path = new ArrayList<>(path);
            this.annotations1 = new ArrayList<>(annotations1);
            this.annotations2 = new ArrayList<>(annotations2);
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
            return "At " + String.join(" ", path) + ": Other annotations differ - " +
                    annotations1.size() + " vs " + annotations2.size();
        }
    }
}
