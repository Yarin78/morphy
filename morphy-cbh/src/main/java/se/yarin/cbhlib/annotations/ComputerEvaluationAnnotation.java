package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class ComputerEvaluationAnnotation extends Annotation {
    @Getter
    private int eval; // centipoints

    @Getter
    private int evalType; // 0 = ordinary eval, 1 = number of moves to mate, 3 = ??

    @Getter
    private int ply;

    public ComputerEvaluationAnnotation(int eval, int evalType, int ply) {
        this.eval = eval;
        this.evalType = evalType;
        this.ply = ply;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (evalType) {
            case 0 :
                if (eval >= 0) sb.append("+");
                sb.append(String.format("%.2f", eval / 100.0));
                break;
            case 1:
                sb.append(String.format("#%d", eval));
                break;
            case 3:
                return ""; // There are a few games with this annotation but ChessBase doesn't show anything for them
            default:
                sb.append(String.format("?? %d %d %d", eval, evalType, ply));
        }

        if (ply > 0) {
            sb.append(String.format("/%d", ply));
        }

        return sb.toString();
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            ComputerEvaluationAnnotation cea = (ComputerEvaluationAnnotation) annotation;
            ByteBufferUtil.putShortL(buf, cea.getEval());
            ByteBufferUtil.putShortL(buf, cea.getEvalType());
            ByteBufferUtil.putShortL(buf, cea.getPly());
        }

        @Override
        public ComputerEvaluationAnnotation deserialize(ByteBuffer buf, int length) {
            short eval = ByteBufferUtil.getSignedShortL(buf);
            short type = ByteBufferUtil.getSignedShortL(buf);
            short depth = ByteBufferUtil.getSignedShortL(buf);
            return new ComputerEvaluationAnnotation(eval, type, depth);
        }

        @Override
        public Class getAnnotationClass() {
            return ComputerEvaluationAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x21;
        }
    }
}
