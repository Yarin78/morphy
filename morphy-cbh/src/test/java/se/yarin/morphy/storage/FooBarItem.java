package se.yarin.morphy.storage;

import org.immutables.value.Value;
import se.yarin.morphy.IdObject;

@Value.Immutable
public interface FooBarItem extends IdObject {
  @Value.Parameter
  int id();

  @Value.Parameter
  String foo();

  @Value.Parameter
  int bar();

  static FooBarItem empty(int id) {
    return ImmutableFooBarItem.of(id, "", 0);
  }
}
