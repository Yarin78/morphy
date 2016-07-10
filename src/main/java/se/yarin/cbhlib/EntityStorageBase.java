package se.yarin.cbhlib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class EntityStorageBase<T extends Entity> implements EntityStorage<T> {
    /**
     * Returns all entities. There will be no null entries in the output.
     * If there are a large number of entities, consider using {@link #getEntityStream} instead.
     * @return a list of all entities
     */
    public List<T> getAllEntities() throws EntityStorageException, IOException {
        ArrayList<T> result = new ArrayList<>(getNumEntities());
        try {
            getEntityStream().forEach(result::add);
        } catch (UncheckedEntityException e) {
            throw new EntityStorageException("An error occurred reading an entity", e);
        }

        return result;
    }
}
