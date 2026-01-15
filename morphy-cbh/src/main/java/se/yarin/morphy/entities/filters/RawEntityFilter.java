package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.util.parser.Expr;
import se.yarin.util.parser.Interpreter;
import se.yarin.util.parser.Parser;
import se.yarin.util.parser.Scanner;

public class RawEntityFilter<T> implements EntityFilter<T> {
  @NotNull private final Expr expr;
  @NotNull private final String filterExpression;
  @NotNull private final EntityType entityType;

  public RawEntityFilter(@NotNull String filterExpression, @NotNull EntityType entityType) {
    Scanner scanner = new Scanner(filterExpression);
    this.filterExpression = filterExpression;
    this.expr = new Parser(scanner.scanTokens()).parse();
    this.entityType = entityType;
  }

  @Override
  public boolean matches(@NotNull T entity) {
    // The raw filter is a bit special as the filter can only happen when scanning a range of
    // entities
    // It means that it will not work when looking at individual entities
    return true;
  }

  @Override
  public boolean matchesSerialized(byte[] buf) {
    Interpreter interpreter = new Interpreter(buf);
    return (boolean) interpreter.evaluate(expr);
  }

  @Override
  public String toString() {
    return "raw(" + filterExpression + ")";
  }

  @Override
  public EntityType entityType() {
    return this.entityType;
  }
}
