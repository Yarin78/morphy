package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class ComputerEvaluationAnnotation extends Annotation {
    @Value.Parameter
    public abstract int eval(); // centipoints

    @Value.Parameter
    public abstract int evalType(); // 0 = ordinary eval, 1 = number of moves to mate, 3 = ??

    @Value.Parameter
    public abstract int ply();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (evalType()) {
            case 0 :
                if (eval() >= 0) sb.append("+");
                sb.append(String.format("%.2f", eval() / 100.0));
                break;
            case 1:
                sb.append(String.format("#%d", eval()));
                break;
            case 3:
                return ""; // There are a few games with this annotation but ChessBase doesn't show anything for them
            default:
                sb.append(String.format("?? %d %d %d", eval(), evalType(), ply()));
        }

        if (ply() > 0) {
            sb.append(String.format("/%d", ply()));
        }

        return sb.toString();
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            ComputerEvaluationAnnotation cea = (ComputerEvaluationAnnotation) annotation;
            ByteBufferUtil.putShortL(buf, cea.eval());
            ByteBufferUtil.putShortL(buf, cea.evalType());
            ByteBufferUtil.putShortL(buf, cea.ply());
        }

        @Override
        public ComputerEvaluationAnnotation deserialize(ByteBuffer buf, int length) {
            short eval = ByteBufferUtil.getSignedShortL(buf);
            short type = ByteBufferUtil.getSignedShortL(buf);
            short depth = ByteBufferUtil.getSignedShortL(buf);
            return ImmutableComputerEvaluationAnnotation.of(eval, type, depth);
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutableComputerEvaluationAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x21;
        }
    }
}
