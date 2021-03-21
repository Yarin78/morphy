package se.yarin.util.parser;

import lombok.AllArgsConstructor;

import java.util.function.BiFunction;

public abstract class Expr {

    interface Visitor<R> {
        R visitBinaryExpr(Binary expr);
        R visitUnaryExpr(Unary expr);
        R visitLiteralExpr(Literal expr);
        R visitGroupingExpr(Grouping expr);
        R visitFunctionExpr(Function expr);
    }

    abstract <R> R accept(Visitor<R> visitor);


    @AllArgsConstructor
    static class Binary extends Expr {
        final Expr left;
        final Token operator;
        final Expr right;

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinaryExpr(this);
        }
    }

    @AllArgsConstructor
    static class Unary extends Expr {
        final Token operator;
        final Expr right;

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnaryExpr(this);
        }
    }

    @AllArgsConstructor
    static class Literal extends Expr {
        final Object value;

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteralExpr(this);
        }
    }

    @AllArgsConstructor
    static class Grouping extends Expr {
        final Expr expression;

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitGroupingExpr(this);
        }
    }

    @AllArgsConstructor
    static class Function extends Expr {
        final BiFunction<byte[], Integer, Integer> function;
        final String functionName;
        final Expr parameter;

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionExpr(this);
        }
    }
}

