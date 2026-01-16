package se.yarin.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class SimpleLRUCacheTest {

  @Test
  public void testBasicGetAndSet() {
    SimpleLRUCache<String, Integer> cache = new SimpleLRUCache<>(10);
    cache.set("a", 1);
    cache.set("b", 2);

    assertEquals(Integer.valueOf(1), cache.get("a"));
    assertEquals(Integer.valueOf(2), cache.get("b"));
    assertNull(cache.get("nonexistent"));
  }

  @Test
  public void testLeastRecentlyUsedIsEvictedFirst() {
    SimpleLRUCache<String, Integer> cache = new SimpleLRUCache<>(3);
    cache.set("a", 1);
    cache.set("b", 2);
    cache.set("c", 3);

    // Access "a" to make it recently used, so "b" becomes least recently used
    cache.get("a");

    // Adding "d" should evict "b"
    cache.set("d", 4);

    assertEquals(Integer.valueOf(1), cache.get("a"));
    assertNull(cache.get("b")); // evicted
    assertEquals(Integer.valueOf(3), cache.get("c"));
    assertEquals(Integer.valueOf(4), cache.get("d"));

    assertEquals(4, cache.getCacheHits());   // get("a") twice, get("c"), get("d")
    assertEquals(1, cache.getCacheMisses()); // get("b") was evicted
  }

  @Test
  public void testEvictAndClear() {
    SimpleLRUCache<String, Integer> cache = new SimpleLRUCache<>(10);
    cache.set("a", 1);
    cache.set("b", 2);

    cache.evict("a");
    assertNull(cache.get("a"));
    assertEquals(Integer.valueOf(2), cache.get("b"));

    cache.clear();
    assertEquals(0, cache.size());
    assertNull(cache.get("b"));
  }
}
