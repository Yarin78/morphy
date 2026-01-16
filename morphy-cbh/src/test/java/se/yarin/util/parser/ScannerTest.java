package se.yarin.util.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static se.yarin.util.parser.TokenType.*;

public class ScannerTest {
  @Test
  public void simpleTest() {
    Scanner scanner = new Scanner("!(shortb(2)&1) || (byte(3)&12) >= 0");
    List<Token> tokens = scanner.scanTokens();

    List<TokenType> expected =
        Arrays.asList(
            BANG,
            LEFT_PAREN,
            IDENTIFIER,
            LEFT_PAREN,
            INTEGER,
            RIGHT_PAREN,
            BITWISE_AND,
            INTEGER,
            RIGHT_PAREN,
            LOGICAL_OR,
            LEFT_PAREN,
            IDENTIFIER,
            LEFT_PAREN,
            INTEGER,
            RIGHT_PAREN,
            BITWISE_AND,
            INTEGER,
            RIGHT_PAREN,
            GREATER_EQUAL,
            INTEGER,
            EOF);

    assertEquals(expected, tokens.stream().map(Token::type).collect(Collectors.toList()));
    assertEquals("shortb", tokens.get(2).lexeme());
    assertEquals(2, tokens.get(4).literal());
    assertEquals(12, tokens.get(16).literal());
  }
}
