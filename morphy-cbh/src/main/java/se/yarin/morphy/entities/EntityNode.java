package se.yarin.morphy.entities;

public class EntityNode {

    private int id;

    private int leftChildId;

    private int rightChildId;

    private int balance;

    private int gameCount;

    private int firstGameId;

    public int getId() {
        return id;
    }

    public int getLeftChildId() {
        return leftChildId;
    }

    public int getRightChildId() {
        return rightChildId;
    }

    public int getBalance() {
        return balance;
    }

    public int getGameCount() {
        return gameCount;
    }

    public int getFirstGameId() {
        return firstGameId;
    }

    public boolean isDeleted() {
        return this.leftChildId == -999;
    }

    public EntityNode(int id, int gameCount, int firstGameId, byte[] serializedEntity) {
        this(id, -1, -1, 0, gameCount, firstGameId, serializedEntity);
    }

    public EntityNode(int id, int leftChildId, int rightChildId, int balance, int gameCount, int firstGameId, byte[] serializedEntity) {
        this.id = id;
        this.leftChildId = leftChildId;
        this.rightChildId = rightChildId;
        this.balance = balance;
        this.gameCount = gameCount;
        this.firstGameId = firstGameId;
        this.serializedEntity = serializedEntity;
    }

    public EntityNode update(int newLeftChildId, int newRightChildId, int newBalance) {
        return new EntityNode(getId(), newLeftChildId, newRightChildId, newBalance, getGameCount(), getFirstGameId(), serializedEntity);
    }

    private final byte[] serializedEntity;

    public byte[] getSerializedEntity() {
        return serializedEntity;
    }
}
