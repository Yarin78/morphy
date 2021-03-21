package se.yarin.util.parser;

public class Interpreter implements Expr.Visitor<Object> {

    private final byte[] data;

    public Interpreter(byte[] data) {
        this.data = data;
    }

    public Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        return switch (expr.operator.type) {
            case PLUS -> (int) left + (int) right;
            case MINUS -> (int) left - (int) right;
            case SLASH -> (int) left / (int) right;
            case STAR -> (int) left * (int) right;
            case LOGICAL_AND -> (boolean) left && (boolean) right;
            case LOGICAL_OR -> (boolean) left || (boolean) right;
            case BITWISE_AND -> (int) left & (int) right;
            case BITWISE_OR -> (int) left | (int) right;
            case BITWISE_XOR -> (int) left ^ (int) right;
            case GREATER -> (int) left > (int) right;
            case GREATER_EQUAL -> (int) left >= (int) right;
            case LESS -> (int) left < (int) right;
            case LESS_EQUAL -> (int) left <= (int) right;
            case BANG_EQUAL -> !left.equals(right);
            case EQUAL_EQUAL -> left.equals(right);
            default -> throw new RuntimeException("Unexpected operator type: " + expr.operator.type);
        };
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        return switch (expr.operator.type) {
            case MINUS -> -(int) right;
            case BANG -> !(boolean) right;
            case TILDE -> ~(int) right;
            default -> throw new RuntimeException("Unexpected operator type: " + expr.operator.type);
        };
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        int index = (int) evaluate(expr.parameter);
        try {
            return expr.function.apply(data, index);
        } catch (ArrayIndexOutOfBoundsException e) {
            return 0;
        }
    }
}
