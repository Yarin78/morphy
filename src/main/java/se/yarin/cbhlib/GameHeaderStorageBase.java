package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.IOException;

abstract class GameHeaderStorageBase {
    private GameHeaderStorageMetadata metadata;

    GameHeaderStorageBase(@NonNull GameHeaderStorageMetadata metadata) {
        this.metadata = metadata;
    }

    GameHeaderStorageMetadata getMetadata() {
        return this.metadata;
    }

    void setMetadata(GameHeaderStorageMetadata metadata) throws IOException {
        this.metadata = metadata;
    }

    abstract GameHeader get(int id) throws IOException;
    abstract void put(GameHeader gameHeader) throws IOException;

    void close() throws IOException { }
}
