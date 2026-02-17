package se.yarin.morphy.queries.filter;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class FilterQueryParserTest {

  // --- Basic structured conditions ---

  @Test
  public void parseSingleColonCondition() {
    List<FilterCondition> result = new FilterQueryParser().parse("result:1-0");
    assertEquals(1, result.size());
    assertEquals("result", result.get(0).field());
    assertEquals(":", result.get(0).operator());
    assertEquals("1-0", result.get(0).value());
    assertTrue(result.get(0).modifiers().isEmpty());
  }

  @Test
  public void parseValueWithEmbeddedRange() {
    List<FilterCondition> result = new FilterQueryParser().parse("rating:2600..3000");
    assertEquals(1, result.size());
    assertEquals("rating", result.get(0).field());
    assertEquals(":", result.get(0).operator());
    assertEquals("2600..3000", result.get(0).value());
  }

  @Test
  public void parseDottedFieldName() {
    List<FilterCondition> result = new FilterQueryParser().parse("player.name:Carlsen");
    assertEquals(1, result.size());
    assertEquals("player.name", result.get(0).field());
    assertEquals(":", result.get(0).operator());
    assertEquals("Carlsen", result.get(0).value());
  }

  @Test
  public void parseConditionWithModifiers() {
    List<FilterCondition> result =
        new FilterQueryParser().parse("player.name:Carlsen,position=white");
    assertEquals(1, result.size());
    assertEquals("player.name", result.get(0).field());
    assertEquals("Carlsen", result.get(0).value());
    assertEquals("white", result.get(0).modifiers().get("position"));
  }

  @Test
  public void parseConditionWithMultipleModifiers() {
    List<FilterCondition> result =
        new FilterQueryParser().parse("rating:2600,mode=average,position=white");
    assertEquals(1, result.size());
    assertEquals("2600", result.get(0).value());
    assertEquals("average", result.get(0).modifiers().get("mode"));
    assertEquals("white", result.get(0).modifiers().get("position"));
  }

  // --- Multiple conditions ---

  @Test
  public void parseMultipleConditionsWithExplicitAnd() {
    List<FilterCondition> result =
        new FilterQueryParser().parse("result:1-0 AND player.name:Carlsen");
    assertEquals(2, result.size());
    assertEquals("result", result.get(0).field());
    assertEquals("player.name", result.get(1).field());
  }

  @Test
  public void parseMultipleConditionsWithImplicitAnd() {
    List<FilterCondition> result =
        new FilterQueryParser().parse("result:1-0 player.name:Carlsen");
    assertEquals(2, result.size());
    assertEquals("result", result.get(0).field());
    assertEquals("player.name", result.get(1).field());
  }

  @Test
  public void parseThreeConditionsWithMixedAndSyntax() {
    List<FilterCondition> result =
        new FilterQueryParser().parse("result:1-0 AND eco:B90 date:2024");
    assertEquals(3, result.size());
    assertEquals("result", result.get(0).field());
    assertEquals("eco", result.get(1).field());
    assertEquals("date", result.get(2).field());
  }

  @Test
  public void andIsCaseInsensitive() {
    List<FilterCondition> result =
        new FilterQueryParser().parse("result:1-0 and eco:B90 And date:2024");
    assertEquals(3, result.size());
  }

  // --- Bare search terms ---

  @Test
  public void bareTerm() {
    List<FilterCondition> result = new FilterQueryParser("player.name").parse("Carlsen");
    assertEquals(1, result.size());
    assertEquals("player.name", result.get(0).field());
    assertEquals(":", result.get(0).operator());
    assertEquals("Carlsen", result.get(0).value());
  }

  @Test
  public void bareTermMixedWithStructuredCondition() {
    List<FilterCondition> result =
        new FilterQueryParser("player.name").parse("Carlsen date:2024");
    assertEquals(2, result.size());
    assertEquals("player.name", result.get(0).field());
    assertEquals("Carlsen", result.get(0).value());
    assertEquals("date", result.get(1).field());
    assertEquals("2024", result.get(1).value());
  }

  @Test
  public void bareTermAfterStructuredCondition() {
    List<FilterCondition> result =
        new FilterQueryParser("name").parse("date:2024 Candidates");
    assertEquals(2, result.size());
    assertEquals("date", result.get(0).field());
    assertEquals("name", result.get(1).field());
    assertEquals("Candidates", result.get(1).value());
  }

  @Test(expected = IllegalArgumentException.class)
  public void bareTermWithoutDefaultFieldThrows() {
    new FilterQueryParser().parse("Carlsen");
  }

  // --- Edge cases ---

  @Test
  public void emptyQueryReturnsEmptyList() {
    assertTrue(new FilterQueryParser().parse("").isEmpty());
  }

  @Test
  public void blankQueryReturnsEmptyList() {
    assertTrue(new FilterQueryParser().parse("   ").isEmpty());
  }

  @Test
  public void extraWhitespaceBetweenTokens() {
    List<FilterCondition> result =
        new FilterQueryParser().parse("  result:1-0   eco:B90  ");
    assertEquals(2, result.size());
    assertEquals("result", result.get(0).field());
    assertEquals("eco", result.get(1).field());
  }

  @Test
  public void wildcardValue() {
    List<FilterCondition> result = new FilterQueryParser().parse("eco:B9*");
    assertEquals(1, result.size());
    assertEquals("B9*", result.get(0).value());
  }

  @Test
  public void pipeValue() {
    List<FilterCondition> result =
        new FilterQueryParser().parse("player.name:Carlsen|Caruana");
    assertEquals(1, result.size());
    assertEquals("Carlsen|Caruana", result.get(0).value());
  }

  @Test(expected = IllegalArgumentException.class)
  public void bareTermWithNoDefaultFieldThrowsEvenWithStructuredCondition() {
    new FilterQueryParser().parse("result:1-0 Carlsen");
  }

  @Test
  public void standaloneAndIsSkipped() {
    // "AND" alone between conditions is just a noise word
    List<FilterCondition> result =
        new FilterQueryParser().parse("result:1-0 AND AND eco:B90");
    assertEquals(2, result.size());
  }
}
