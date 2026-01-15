package se.yarin.util.parser;

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

  static class Binary extends Expr {
    final Expr left;
    final Token operator;
    final Expr right;

    Binary(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitBinaryExpr(this);
    }
  }

  static class Unary extends Expr {
    final Token operator;
    final Expr right;

    Unary(Token operator, Expr right) {
      this.operator = operator;
      this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitUnaryExpr(this);
    }
  }

  static class Literal extends Expr {
    final Object value;

    Literal(Object value) {
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitLiteralExpr(this);
    }
  }

  static class Grouping extends Expr {
    final Expr expression;

    Grouping(Expr expression) {
      this.expression = expression;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitGroupingExpr(this);
    }
  }

  static class Function extends Expr {
    final BiFunction<byte[], Integer, Integer> function;
    final String functionName;
    final Expr parameter;

    Function(BiFunction<byte[], Integer, Integer> function, String functionName, Expr parameter) {
      this.function = function;
      this.functionName = functionName;
      this.parameter = parameter;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitFunctionExpr(this);
    }
  }
}
