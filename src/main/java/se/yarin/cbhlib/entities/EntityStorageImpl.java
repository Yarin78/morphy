package se.yarin.cbhlib.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class EntityStorageImpl<T extends Entity & Comparable<T>> implements EntityStorage<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityStorageImpl.class);

    private static final int ENTITY_DELETED = -999;
    private static final int DEFAULT_HEADER_SIZE = 32;

    private final EntityNodeStorageBase<T> nodeStorage;
    private final EntityNodeStorageMetadata metadata;

    private EntityStorageImpl(@NonNull File file, @NonNull EntitySerializer<T> serializer)
            throws IOException {
        nodeStorage = new PersistentEntityNodeStorage<>(file, serializer);
        metadata = ((PersistentEntityNodeStorage) nodeStorage).getMetadata();
    }

    private EntityStorageImpl() {
        nodeStorage = new InMemoryEntityNodeStorage<>();
        metadata = new EntityNodeStorageMetadata(0, 0);
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> open(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return new EntityStorageImpl<>(file, serializer);
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> create(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        return create(file, serializer, DEFAULT_HEADER_SIZE);
    }

    static <T extends Entity & Comparable<T>> EntityStorage<T> create(
            File file, @NonNull EntitySerializer<T> serializer, int headerSize) throws IOException {
        PersistentEntityNodeStorage.createEmptyStorage(file, serializer, headerSize);
        return open(file, serializer);
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> openInMemory(
            File file, @NonNull EntitySerializer<T> serializer) throws IOException {
        EntityStorageImpl<T> source = new EntityStorageImpl<>(file, serializer);
        EntityStorageImpl<T> target = new EntityStorageImpl<>();
        int batchSize = 1000, capacity = source.metadata.getCapacity();
        for (int i = 0; i < capacity; i+=batchSize) {
            List<EntityNode<T>> nodes = source.nodeStorage.getEntityNodes(i, Math.min(i + batchSize, capacity));
            for (EntityNode<T> node : nodes) {
                target.nodeStorage.putEntityNode(node);
            }
        }
        target.metadata.setCapacity(capacity);
        target.metadata.setRootEntityId(source.metadata.getRootEntityId());
        target.metadata.setFirstDeletedEntityId(source.metadata.getFirstDeletedEntityId());
        target.metadata.setNumEntities(source.metadata.getNumEntities());
        return target;
    }

    public static <T extends Entity & Comparable<T>> EntityStorage<T> createInMemory() {
        return new EntityStorageImpl<>();
    }

    @Override
    public int getNumEntities() {
        return metadata.getNumEntities();
    }

    @Override
    public int getCapacity() {
        return metadata.getCapacity();
    }

    @Override
    public void close() throws IOException {
        nodeStorage.close();
    }

    @Override
    public T getEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= metadata.getCapacity()) {
            return null;
        }
        return nodeStorage.getEntityNode(entityId).getEntity();
    }

    @Override
    public T getEntity(T entity) throws IOException {
        TreePath treePath = treeSearch(entity);
        if (treePath.compare == 0) {
            return treePath.node.getEntity();
        }
        return null;
    }

    @Override
    public int addEntity(@NonNull T entity) throws IOException, EntityStorageException {
        int entityId;

        TreePath result = treeSearch(entity);
        if (result != null && result.compare == 0) {
            throw new EntityStorageException("An entity with the same key already exists");
        }

        if (metadata.getFirstDeletedEntityId() >= 0) {
            // Replace a deleted entity
            entityId = metadata.getFirstDeletedEntityId();
            metadata.setFirstDeletedEntityId(nodeStorage.getEntityNode(entityId).getRightEntityId());
        } else {
            // Appended new entity to the end
            entityId = metadata.getCapacity();
            metadata.setCapacity(entityId + 1);
        }
        metadata.setNumEntities(metadata.getNumEntities() + 1);

        if (result == null) {
            metadata.setRootEntityId(entityId);
        } else {
            if (result.compare < 0) {
                result.node = result.node.update(entityId, result.node.getRightEntityId(), result.node.getHeightDif());
            } else {
                result.node = result.node.update(result.node.getLeftEntityId(), entityId, result.node.getHeightDif());
            }
            nodeStorage.putEntityNode(result.node);
        }

        EntityNode<T> z = nodeStorage.createNode(entityId, entity);
        nodeStorage.putEntityNode(z);

        for(; result != null; result = result.parent) {
            // result.node might contain an old version of the node (I think!?)
            EntityNode<T> x = nodeStorage.getEntityNode(result.node.getEntityId());
            EntityNode<T> g;
            int n;
            // BalanceFactor(X) has not yet been updated!
            if (z.getEntityId() == x.getRightEntityId()) { // The right subtree increases
                if (x.getHeightDif() > 0) { // X is right-heavy
                    // ===> the temporary BalanceFactor(X) == +2
                    // ===> rebalancing is required.
                    g = result.parent != null ? result.parent.node : null;
                    if (z.getHeightDif() < 0) {
                        n = rotateRightLeft(x.getEntityId());
                    } else {
                        n = rotateLeft(x.getEntityId());
                    }
                } else {
                    if (x.getHeightDif() < 0) {
                        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                        break;
                    }
                    EntityNode<T> newX = x.update(x.getLeftEntityId(), x.getRightEntityId(), 1);
                    nodeStorage.putEntityNode(newX);
                    z = newX;
                    continue;
                }
            } else { // Z == left_child(X): the left subtree increases
                if (x.getHeightDif() < 0) { // X is left-heavy
                    g = result.parent != null ? result.parent.node : null;
                    if (z.getHeightDif() > 0) {
                        n = rotateLeftRight(x.getEntityId());
                    } else {
                        n = rotateRight(x.getEntityId());
                    }
                } else {
                    if (x.getHeightDif() > 0) {
                        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                        break;
                    }
                    EntityNode<T> newX = x.update(x.getLeftEntityId(), x.getRightEntityId(), -1);
                    nodeStorage.putEntityNode(newX);
                    z = newX;
                    continue;
                }
            }

            if (g != null) {
                if (x.getEntityId() == g.getLeftEntityId()) {
                    nodeStorage.putEntityNode(g.update(n, g.getRightEntityId(), g.getHeightDif()));
                } else {
                    nodeStorage.putEntityNode(g.update(g.getLeftEntityId(), n, g.getHeightDif()));
                }
                break;
            } else {
                metadata.setRootEntityId(n);
                break;
            }
        }

        nodeStorage.putMetadata(metadata);

        return entityId;
    }

    /**
     * Returns all entities. There will be no null entries in the output.
     * If there are a large number of entities, consider using {@link #iterator()} instead.
     * @return a list of all entities
     * @throws IOException if there was an error getting any entity
     */
    public List<T> getAllEntities() throws IOException {
        ArrayList<T> result = new ArrayList<>(getNumEntities());
        for (T entity : this) {
            result.add(entity);
        }
        return result;
    }
    @AllArgsConstructor
    private class TreePath {
        private int compare;
        private EntityNode<T> node;
        private TreePath parent;

        private TreePath last() {
            return parent == null ? this : parent.last();
        }
    }

    /**
     * Searches the tree for a specific entity. Returns a path from the root
     * to the searched entity.
     * If the entity doesn't exist in the tree, the path ends at the node in the
     * tree where the entity can be inserted.
     * @param entity the entity to search for
     * @return the most recent node in the path
     * @throws IOException if an IO error occurred when searching in the tree
     */
    private TreePath treeSearch(@NonNull T entity) throws IOException {
        return treeSearch(metadata.getRootEntityId(), null, entity);
    }

    /**
     * Searches the tree for a specific entity. Returns a path from the root
     * to the searched entity.
     * If the entity doesn't exist in the tree, the path ends at the node in the
     * tree where the entity can be inserted.
     * @param currentId the start node to search from
     * @param path the path searched for so far
     * @param entity the entity to search for
     * @return the most recent node in the path
     * @throws IOException if an IO error occurred when searching in the tree
     */
    private TreePath treeSearch(int currentId, TreePath path, @NonNull T entity) throws IOException {
        if (currentId < 0) {
            return path;
        }

        T current = getEntity(currentId);
        EntityNode<T> node = nodeStorage.getEntityNode(currentId);
        int comp = entity.compareTo(current);

        path = new TreePath(comp, node, path);
        if (comp == 0) {
            return path;
        } else if (comp < 0) {
            return treeSearch(node.getLeftEntityId(), path, entity);
        } else {
            return treeSearch(node.getRightEntityId(), path, entity);
        }
    }

    @Override
    public void putEntityById(int entityId, @NonNull T entity) throws IOException, EntityStorageException {
        if (entityId < 0 || entityId >= metadata.getCapacity()) {
            throw new IllegalArgumentException(String.format("Can't put an entity with id %d when capacity is %d",
                    entityId, metadata.getCapacity()));
        }
        EntityNode<T> oldNode = nodeStorage.getEntityNode(entityId);
        if (oldNode.isDeleted()) {
            throw new IllegalArgumentException("Can't replace a deleted entity");
        }
        if (oldNode.getEntity().compareTo(entity) == 0) {
            // The key is the same, so we don't have to update the tree
            EntityNode<T> newNode = nodeStorage.createNode(entityId, entity);
            newNode = newNode.update(oldNode.getLeftEntityId(), oldNode.getRightEntityId(), oldNode.getHeightDif());
            nodeStorage.putEntityNode(newNode);
        } else {
            // TODO: Do this in a transaction?
            deleteEntity(entityId);
            int newEntityId = addEntity(entity);
            assert entityId == newEntityId; // Important!
        }
    }

    @Override
    public void putEntityByKey(@NonNull T entity) throws IOException, EntityStorageException {
        TreePath treePath = treeSearch(entity);
        if (treePath == null || treePath.compare != 0) {
            throw new EntityStorageException("The entity doesn't exist in the storage");
        }
        EntityNode<T> node = treePath.node;
        int entityId = node.getEntityId();
        EntityNode<T> newNode = nodeStorage.createNode(entityId, entity);
        newNode = newNode.update(node.getLeftEntityId(), node.getRightEntityId(), node.getHeightDif());
        nodeStorage.putEntityNode(newNode);
    }

    private void replaceChild(TreePath path, int newChildId) throws IOException {
        if (path == null) {
            // The root node has no parent
            metadata.setRootEntityId(newChildId);
        } else {
            EntityNode<T> node = nodeStorage.getEntityNode(path.node.getEntityId());
            if (path.compare < 0) {
                node = node.update(newChildId, node.getRightEntityId(), node.getHeightDif());
            } else {
                node = node.update(node.getLeftEntityId(), newChildId, node.getHeightDif());
            }
            nodeStorage.putEntityNode(node);
        }
    }

    @Override
    public boolean deleteEntity(T entity) throws IOException, EntityStorageException {
        TreePath nodePath = treeSearch(entity);
        if (nodePath == null || nodePath.compare != 0) {
            log.debug("Deleted entity didn't exist");
            return false;
        }
        return internalDeleteEntity(nodePath);
    }

    @Override
    public boolean deleteEntity(int entityId) throws IOException, EntityStorageException {
        EntityNode<T> node = nodeStorage.getEntityNode(entityId);
        if (node.isDeleted()) {
            log.debug("Deleted entity with id " + entityId + " that was already deleted");
            return false;
        }

        // Find the node we want to delete in the tree
        TreePath nodePath = treeSearch(node.getEntity());
        if (nodePath == null || nodePath.compare != 0) {
            throw new EntityStorageException("Broken database structure; couldn't find the node to delete.");
        }
        return internalDeleteEntity(nodePath);
    }



    private boolean internalDeleteEntity(TreePath nodePath) throws IOException {
        EntityNode<T> node = nodePath.node;
        int entityId = node.getEntityId();
        TreePath nodePathOrg = nodePath;
        nodePath = nodePath.parent;

        // Switch the node we want to delete with a successor node until it has at most one child
        // This will take at most one iteration, so we could simplify this
        while (node.getLeftEntityId() >= 0 && node.getRightEntityId() >= 0) {
            // Invariant: node is the node we want to delete, and it has two children
            // nodePath.node = the parent node
            // nodePath.compare < 0 if the deleted node is a left child, > 0 if a right child

            // Find successor node and replace it with this one
            TreePath successorPath = treeSearch(node.getRightEntityId(), null, node.getEntity());
            assert successorPath.compare < 0; // Should always be a left child
            EntityNode<T> successorNode = successorPath.node;
            // successorPath.node = the node we want to move up and replace node
            successorPath = successorPath.parent; // successorPath.node may now equal node!!

            EntityNode<T> newNode = node.update(successorNode.getLeftEntityId(), successorNode.getRightEntityId(), successorNode.getHeightDif());
            int rid = node.getRightEntityId();
            if (rid == successorNode.getEntityId()) {
                rid = node.getEntityId();
            }
            EntityNode<T> newSuccessorNode = successorNode.update(node.getLeftEntityId(), rid, node.getHeightDif());
            replaceChild(nodePath, successorNode.getEntityId());
            if (successorPath != null) {
                replaceChild(successorPath, node.getEntityId());
            }
            nodeStorage.putEntityNode(newNode);
            nodeStorage.putEntityNode(newSuccessorNode);

            node = newNode;
            if (successorPath == null) {
                successorPath = new TreePath(1, newSuccessorNode, nodePath);
            } else {
                successorPath.last().parent = new TreePath(1, newSuccessorNode, nodePath);
            }

            nodePath = successorPath; // Won't work probably if parent to successor was node
//            if (nodePath == null) {
//                nodePath = new TreePath(1, newSuccessorNode, null);
//            }
        }

        // Now node has at most one child!
        // nodePath.node = the parent node
        // nodePath.compare < 0 if the deleted node is a left child, > 0 if a right child
        int onlyChild = node.getLeftEntityId() >= 0 ? node.getLeftEntityId() : node.getRightEntityId();
        replaceChild(nodePath, onlyChild);

        // Nothing should now point to the node we want to delete
        EntityNode<T> deletedNode = nodeStorage.createNode(entityId, null)
                .update(ENTITY_DELETED, metadata.getFirstDeletedEntityId(), 0);

        nodeStorage.putEntityNode(deletedNode);
        metadata.setFirstDeletedEntityId(entityId);
        metadata.setNumEntities(metadata.getNumEntities() - 1);


        // Retrace and rebalance tree
        /*
        TreePath tp = nodePath;
        System.out.println("nodepath");
        while (tp != null) {
            System.out.println(tp.node.getEntityId() + " " + tp.compare);
            tp = tp.parent;
        }*/

        int n = -1;
        EntityNode<T> g;
        for (EntityNode<T> x = nodePath == null ? null : nodeStorage.getEntityNode(nodePath.node.getEntityId()); x != null; x = g, nodePath = nodePath.parent){
            // The stored value in the path might be old
            g = nodePath.parent == null ? null : nodeStorage.getEntityNode(nodePath.parent.node.getEntityId());
            int b;
            if (nodePath.compare < 0) {
                if (x.getHeightDif() > 0) {
                    EntityNode<T> z = nodeStorage.getEntityNode(x.getRightEntityId());
                    b = z.getHeightDif();
                    if (b < 0) {
                        n = rotateRightLeft(x.getEntityId());
                    } else {
                        n = rotateLeft(x.getEntityId());
                    }
                    // After rotation adapt parent link
                } else {
                    if (x.getHeightDif() == 0) {
                        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 1));
                        break;
                    }
                    n = x.getEntityId();
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                    continue;
                }
            } else {
                if (x.getHeightDif() < 0) {
                    EntityNode<T> z = nodeStorage.getEntityNode(x.getLeftEntityId());
                    b = z.getHeightDif();
                    if (b > 0) {
                        n = rotateLeftRight(x.getEntityId());
                    } else {
                        n = rotateRight(x.getEntityId());
                    }
                    // After rotation adapt parent link
                } else {
                    if (x.getHeightDif() == 0) {
                        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), -1));
                        break;
                    }
                    n = x.getEntityId();
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                    continue;
                }
            }

            if (g == null) {
                metadata.setRootEntityId(n);
            } else {
                if (nodePath.parent.compare < 0) {
                    g = g.update(n, g.getRightEntityId(), g.getHeightDif());
                } else {
                    g = g.update(g.getLeftEntityId(), n, g.getHeightDif());
                }
                nodeStorage.putEntityNode(g);
                if (b == 0) break;
            }
        }

        nodeStorage.putMetadata(metadata);
        return true;
    }

    // Rotates the tree rooted at nodeId to the left and returns the new root
    int rotateLeft(int nodeId) throws IOException {
        EntityNode<T> x = nodeStorage.getEntityNode(nodeId);
        EntityNode<T> z = nodeStorage.getEntityNode(x.getRightEntityId());
        int newRightChildX = z.getLeftEntityId();
        int newLeftChildZ = x.getEntityId();
        int newXHeightDif = 0, newZHeightDif = 0;
        if (z.getHeightDif() == 0) {
            newXHeightDif = 1;
            newZHeightDif = -1;
        }
        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), newRightChildX, newXHeightDif));
        nodeStorage.putEntityNode(z.update(newLeftChildZ, z.getRightEntityId(), newZHeightDif));
        return z.getEntityId();
    }

    // Rotates the tree rooted at nodeId to the right and returns the new root
    int rotateRight(int nodeId) throws IOException {
        EntityNode<T> x = nodeStorage.getEntityNode(nodeId);
        EntityNode<T> z = nodeStorage.getEntityNode(x.getLeftEntityId());
        int newLeftChildX = z.getRightEntityId();
        int newRightChildZ = x.getEntityId();
        int newXHeightDif = 0, newZHeightDif = 0;
        if (z.getHeightDif() == 0) {
            newXHeightDif = -1;
            newZHeightDif = 1;
        }
        nodeStorage.putEntityNode(x.update(newLeftChildX, x.getRightEntityId(), newXHeightDif));
        nodeStorage.putEntityNode(z.update(z.getLeftEntityId(), newRightChildZ, newZHeightDif));
        return z.getEntityId();
    }

    // Rotates the tree rooted at nodeId first to the right then to the left and returns the new root
    int rotateRightLeft(int nodeId) throws IOException {
        EntityNode<T> x = nodeStorage.getEntityNode(nodeId);
        EntityNode<T> z = nodeStorage.getEntityNode(x.getRightEntityId());
        EntityNode<T> y = nodeStorage.getEntityNode(z.getLeftEntityId());
        int newLeftChildZ = y.getRightEntityId();
        int newRightChildY = z.getEntityId();
        int newRightChildX = y.getLeftEntityId();
        int newLeftChildY = nodeId;
        int newXHeightDif = 0, newYHeightDif = 0, newZHeightDif = 0;
        if (y.getHeightDif() > 0) {
            newXHeightDif = -1;
        } else if (y.getHeightDif() < 0) {
            newZHeightDif = 1;
        }
        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), newRightChildX, newXHeightDif));
        nodeStorage.putEntityNode(y.update(newLeftChildY, newRightChildY, newYHeightDif));
        nodeStorage.putEntityNode(z.update(newLeftChildZ, z.getRightEntityId(), newZHeightDif));
        return y.getEntityId();
    }

    // Rotates the tree rooted at nodeId first to the left then to the right and returns the new root
    int rotateLeftRight(int nodeId) throws IOException {
        EntityNode<T> x = nodeStorage.getEntityNode(nodeId);
        EntityNode<T> z = nodeStorage.getEntityNode(x.getLeftEntityId());
        EntityNode<T> y = nodeStorage.getEntityNode(z.getRightEntityId());
        int newRightChildZ = y.getLeftEntityId();
        int newLeftChildY = z.getEntityId();
        int newLeftChildX = y.getRightEntityId();
        int newRightChildY = nodeId;
        int newXHeightDif = 0, newYHeightDif = 0, newZHeightDif = 0;
        if (y.getHeightDif() < 0) {
            newXHeightDif = 1;
        } else if (y.getHeightDif() > 0) {
            newZHeightDif = -1;
        }
        nodeStorage.putEntityNode(x.update(newLeftChildX, x.getRightEntityId(), newXHeightDif));
        nodeStorage.putEntityNode(y.update(newLeftChildY, newRightChildY, newYHeightDif));
        nodeStorage.putEntityNode(z.update(z.getLeftEntityId(), newRightChildZ, newZHeightDif));
        return y.getEntityId();
    }

    public void printTree() throws IOException {
        printTree(metadata.getRootEntityId());
    }

    public void printTree(int entityId) throws IOException {
        if (entityId < 0) return;
        EntityNode<T> node = nodeStorage.getEntityNode(entityId);
        System.out.println(String.format("%d -> %d,%d (%d)", entityId, node.getLeftEntityId(), node.getRightEntityId(), node.getHeightDif()));
        printTree(node.getLeftEntityId());
        printTree(node.getRightEntityId());
    }

    /**
     * Validates that the entity headers correctly reflects the order of the entities
     */
    public void validateStructure() throws EntityStorageException, IOException {
        if (metadata.getRootEntityId() == -1) {
            if (getNumEntities() == 0) {
                return;
            }
            throw new EntityStorageException(String.format(
                    "Header says there are %d entities in the storage but the root points to no entity.", getNumEntities()));
        }

        ValidationResult result = validate(metadata.getRootEntityId(), null, null);
        if (result.getCount() != getNumEntities()) {
            throw new EntityStorageException(String.format(
                    "Found %d entities when traversing the base but the header says there should be %d entities.",
                    result.getCount(), getNumEntities()));
        }
    }

    @AllArgsConstructor
    private class ValidationResult {
        @Getter private int count;
        @Getter private int height;
    }

    private ValidationResult validate(int entityId, T min, T max) throws IOException, EntityStorageException {
        EntityNode<T> node = nodeStorage.getEntityNode(entityId);
        T entity = node.getEntity();
        if (node.isDeleted() || entity == null) {
            throw new EntityStorageException(String.format(
                    "Reached deleted element %d when validating the storage structure.", entityId));
        }
        if ((min != null && min.compareTo(entity) >= 0) || (max != null && max.compareTo(entity) <= 0)) {
            throw new EntityStorageException(String.format(
                    "Entity %d out of order when validating the storage structure", entityId));
        }

        // Since the range is strictly decreasing every time, we should not have to worry
        // about ending up in an infinite recursion.
        int cnt = 1, leftHeight = 0, rightHeight = 0;
        if (node.getLeftEntityId() != -1) {
            ValidationResult result = validate(node.getLeftEntityId(), min, entity);
            cnt += result.getCount();
            leftHeight = result.getHeight();
        }
        if (node.getRightEntityId() != -1) {
            ValidationResult result = validate(node.getRightEntityId(), entity, max);
            cnt += result.getCount();
            rightHeight = result.getHeight();
        }

        if (rightHeight - leftHeight != node.getHeightDif()) {
            throw new EntityStorageException(String.format("Height difference at node %d was %d but node data says it should be %d",
                    node.getEntityId(), rightHeight - leftHeight, node.getHeightDif()));
        }

        if (Math.abs(leftHeight - rightHeight) > 1) {
            throw new EntityStorageException(String.format("Height difference at node %d was %d",
                    node.getEntityId(), leftHeight - rightHeight));
        }

        return new ValidationResult(cnt, 1 + Math.max(leftHeight, rightHeight));
    }

    public Iterator<T> iterator() {
        return iterator(0);
    }

    @Override
    public Iterator<T> iterator(int startId) {
        return new DefaultIterator(startId);
    }

    private class DefaultIterator implements Iterator<T> {
        private List<EntityNode<T>> batch = new ArrayList<>();
        private int batchPos, nextBatchStart = 0, batchSize = 1000;

        private void getNextBatch() {
            int endId = Math.min(metadata.getCapacity(), nextBatchStart + batchSize);
            if (nextBatchStart >= endId) {
                batch = null;
            } else {
                try {
                    batch = nodeStorage.getEntityNodes(nextBatchStart, endId);
                } catch (IOException e) {
                    throw new UncheckedEntityException("AN IO error when iterating entities", e);
                }
                nextBatchStart = endId;
            }
            batchPos = 0;
        }

        private void skipDeleted() {
            while (batch != null) {
                while (batchPos < batch.size()) {
                    if (!batch.get(batchPos).isDeleted()) return;
                    batchPos++;
                }
                getNextBatch();
            }
        }

        DefaultIterator(int startId) {
            nextBatchStart = startId;
            skipDeleted();
        }

        @Override
        public boolean hasNext() {
            return batch != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of entity iteration reached");
            }
            EntityNode<T> node = batch.get(batchPos++);
            skipDeleted();
            return node.getEntity();
        }
    }

    private class OrderedIterator implements Iterator<T> {

        // Invariant: treePath.node is the next entity to be returned
        // If treePath == null, there are no more entities to be returned
        private TreePath treePath;
        private final boolean ascending;

        OrderedIterator(TreePath treePath, boolean ascending) throws IOException {
            this.treePath = treePath;
            this.ascending = ascending;
        }

        @Override
        public boolean hasNext() {
            return treePath != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of entity iteration reached");
            }
            try {
                // TODO: Check if any writes have happened since iterator was created
                T entity = treePath.node.getEntity();
                if (ascending && treePath.node.getRightEntityId() >= 0) {
                    treePath.compare = 1;
                    treePath = treeSearch(treePath.node.getRightEntityId(), treePath, entity);
                } else if (!ascending && treePath.node.getLeftEntityId() >= 0) {
                    treePath.compare = -1;
                    treePath = treeSearch(treePath.node.getLeftEntityId(), treePath, entity);
                } else {
                    treePath = treePath.parent;
                    while (treePath != null && treePath.compare * (ascending ? 1 : -1) > 0) {
                        treePath = treePath.parent;
                    }
                }
                return entity;
            } catch (IOException e) {
                throw new UncheckedEntityException("AN IO error when iterating entities", e);
            }
        }
    }

    private Iterator<T> getOrderedIterator(T startEntity, boolean ascending) throws IOException {
        TreePath treePath = null;
        if (startEntity == null) {
            int currentId = metadata.getRootEntityId();
            while (currentId >= 0) {
                EntityNode<T> node = nodeStorage.getEntityNode(currentId);
                treePath = new TreePath(ascending ? -1 : 1, node, treePath);
                currentId = ascending ? node.getLeftEntityId() : node.getRightEntityId();
            }
        } else {
            treePath = treeSearch(startEntity);
            while (treePath != null && treePath.compare * (ascending ? 1 : -1) > 0) {
                treePath = treePath.parent;
            }
        }
        return new OrderedIterator(treePath, ascending);
    }

    public Iterator<T> getOrderedAscendingIterator(T startEntity) throws IOException {
        return getOrderedIterator(startEntity, true);
    }

    public Iterator<T> getOrderedDescendingIterator(T startEntity) throws IOException {
        return getOrderedIterator(startEntity, false);
    }
}
