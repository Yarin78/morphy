package se.yarin.morphy.games.filters;

import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;

import java.util.List;

public interface GameEntityFilter<T extends Entity & Comparable<T>> extends GameFilter {
    List<Integer> entityIds();

    EntityType entityType();
}
