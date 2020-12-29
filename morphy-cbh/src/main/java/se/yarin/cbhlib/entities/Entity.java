package se.yarin.cbhlib.entities;

/**
 * Base interface for an entity. Implementations should be immutable.
 */
public interface Entity {
    int getId();
    int getCount();
    int getFirstGameId();
    <T extends Entity> T withNewId(int id);
    <T extends Entity> T withNewStats(int count, int firstGameId);
}
