package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.Setter;

class TestEntity implements Entity, Comparable<TestEntity> {
    @Getter
    private int id;
    @Getter @Setter
    private String key;
    @Getter @Setter
    private int value;

    TestEntity(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public TestEntity(String key) {
        this(-1, key);
    }

    @Override
    public int compareTo(TestEntity o) {
        return key.compareTo(o.key);
    }
}
