package se.yarin.morphy.entities;

import org.immutables.value.Value;

@Value.Immutable
public abstract class FooEntity extends Entity implements Comparable<FooEntity> {

    public abstract String key();
    public abstract int value();

    public static FooEntity of(String key) {
        return ImmutableFooEntity.builder().key(key).value(0).build();
    }

    public static FooEntity of(String key, int value) {
        return ImmutableFooEntity.builder().key(key).value(value).build();
    }

    @Override
    public int compareTo(FooEntity o) {
        return key().compareTo(o.key());
    }

    @Override
    public String toString() {
        return "TestEntity{" +
                "id=" + id() +
                ", key='" + key() + '\'' +
                ", value=" + value() +
                '}';
    }
}
