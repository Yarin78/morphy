package se.yarin.cbhlib.entities;

import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class TestEntityv2 implements Entity, Comparable<TestEntityv2> {
    @Getter
    private int id;
    @Getter @NonNull
    private String key;
    @Getter
    private int value;
    @Getter
    private int extraValue;
    @Getter @NonNull
    private String extraString;

    public TestEntityv2(String key) {
        this.key = key;
    }

    @Override
    public int compareTo(TestEntityv2 o) {
        return key.compareTo(o.key);
    }

    @Override
    public Entity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public String toString() {
        return "TestEntityv2{" +
                "id=" + id +
                ", key='" + key + '\'' +
                ", value=" + value +
                ", extraValue=" + extraValue +
                ", extraString='" + extraString + '\'' +
                '}';
    }
}
