package se.yarin.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CollectionsTest {

  @Test
  public void testGeneratePermutationsEmpty() {
    var permutations = Collections.generatePermutations(List.of());
    assertEquals(1, permutations.size());
    assertEquals(List.of(), permutations.get(0));
  }

  @Test
  public void testGeneratePermutationsOneElement() {
    var permutations = Collections.generatePermutations(List.of("z"));
    assertEquals(1, permutations.size());
    assertEquals(List.of("z"), permutations.get(0));
  }

  @Test
  public void testGeneratePermutationsTwoElements() {
    var permutations = Collections.generatePermutations(List.of("f", "d"));
    assertEquals(2, permutations.size());
    assertEquals(List.of("f", "d"), permutations.get(0));
    assertEquals(List.of("d", "f"), permutations.get(1));
  }

  @Test
  public void testGeneratePermutationsThreeElements() {
    var permutations = Collections.generatePermutations(List.of("a", "b", "c"));
    assertEquals(6, permutations.size());
    assertEquals(List.of("a", "b", "c"), permutations.get(0));
    assertEquals(List.of("b", "a", "c"), permutations.get(1));
    assertEquals(List.of("c", "a", "b"), permutations.get(2));
    assertEquals(List.of("a", "c", "b"), permutations.get(3));
    assertEquals(List.of("b", "c", "a"), permutations.get(4));
    assertEquals(List.of("c", "b", "a"), permutations.get(5));
  }

  @Test
  public void testGeneratePermutationsManyElements() {
    var permutations = Collections.generatePermutations(List.of("a", "b", "c", "d", "e"));
    assertEquals(120, permutations.size());
  }
}
