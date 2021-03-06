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

    public EntityNode(int id, int leftChildId, int rightChildId, int balance, int gameCount, int firstGameId, byte[] serializedEntity) {
        this.id = id;
        this.leftChildId = leftChildId;
        this.rightChildId = rightChildId;
        this.balance = balance;
        this.gameCount = gameCount;
        this.firstGameId = firstGameId;
        this.serializedEntity = serializedEntity;
    }

    private byte[] serializedEntity;
    private Entity entity;

    public byte[] getSerializedEntity() {
        return serializedEntity;
    }
}
