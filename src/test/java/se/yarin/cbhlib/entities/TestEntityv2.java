package se.yarin.cbhlib.entities;

import lombok.Getter;
import lombok.Setter;

class TestEntityv2 implements Entity, Comparable<TestEntityv2> {
    @Getter
    private int id;
    @Getter @Setter
    private String key;
    @Getter @Setter
    private int value;
    @Getter @Setter
    private int extraValue;
    @Getter @Setter
    private String extraString;

    TestEntityv2(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public TestEntityv2(String key) {
        this(-1, key);
    }

    @Override
    public int compareTo(TestEntityv2 o) {
        return key.compareTo(o.key);
    }
}
