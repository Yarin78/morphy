package se.yarin.morphy.entities;

import org.immutables.value.Value;

@Value.Immutable
public abstract class FooBarEntity extends Entity implements Comparable<FooBarEntity> {
    public abstract String key();
    public abstract int value();
    public abstract int extraValue();
    public abstract String extraString();

    public static FooBarEntity of(String key) {
        return ImmutableFooBarEntity.builder().key(key).build();
    }

    @Override
    public int compareTo(FooBarEntity o) {
        return key().compareTo(o.key());
    }

    @Override
    public String toString() {
        return "TestEntityv2{" +
                "id=" + id() +
                ", key='" + key() + '\'' +
                ", value=" + value() +
                ", extraValue=" + extraValue() +
                ", extraString='" + extraString() + '\'' +
                '}';
    }
}
