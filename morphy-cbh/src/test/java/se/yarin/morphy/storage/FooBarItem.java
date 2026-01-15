package se.yarin.morphy.storage;

import org.immutables.value.Value;

@Value.Immutable
public interface FooBarItem {
  @Value.Parameter
  String foo();

  @Value.Parameter
  int bar();

  static FooBarItem empty() {
    return ImmutableFooBarItem.of("", 0);
  }
}
