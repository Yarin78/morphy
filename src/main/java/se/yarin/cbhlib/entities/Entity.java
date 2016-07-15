package se.yarin.cbhlib.entities;

/**
 * Base interface for an entity. Implementations should be immutable.
 */
public interface Entity {
    int getId();
    <T extends Entity> T withNewId(int id);
}
