package se.yarin.cbhlib.entities;

import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class TestEntity implements Entity, Comparable<TestEntity> {
    @Getter
    private int id;
    @Getter @NonNull
    private String key;
    @Getter
    private int value;

    @Getter private int count;
    @Getter private int firstGameId;

    public TestEntity(String key) {
        this.key = key;
    }

    @Override
    public int compareTo(TestEntity o) {
        return key.compareTo(o.key);
    }

    @Override
    public TestEntity withNewId(int id) {
        return toBuilder().id(id).build();
    }

    @Override
    public TestEntity withNewStats(int count, int firstGameId) {
        return toBuilder().count(count).firstGameId(firstGameId).build();
    }

    @Override
    public String toString() {
        return "TestEntity{" +
                "id=" + id +
                ", key='" + key + '\'' +
                ", value=" + value +
                '}';
    }
}
