package se.yarin.morphy.entities;

import org.immutables.value.Value;

public abstract class Entity {
    @Value.Default
    public int id() {
        return -1;
    }

    @Value.Default
    public int count() {
        return 0;
    }

    @Value.Default
    public int firstGameId() {
        return 0;
    }

    public abstract Entity withCountAndFirstGameId(int count, int firstGameId);
}
