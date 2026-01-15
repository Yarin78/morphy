package se.yarin.util.parser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ParserTest {
  @Test
  public void simpleTest() {
    Scanner scanner = new Scanner("!(shortb(2)&1) || (byte(3)&12) >= 0");
    List<Token> tokens = scanner.scanTokens();
    Parser parser = new Parser(tokens);
    Expr expr = parser.parse();

    AstPrinter printer = new AstPrinter();
    String expected = "(|| (! (group (& (func-shortb 2) 1))) (>= (group (& (func-byte 3) 12)) 0))";
    assertEquals(expected, printer.print(expr));
  }
}
