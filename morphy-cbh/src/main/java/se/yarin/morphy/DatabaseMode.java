package se.yarin.morphy;

import se.yarin.morphy.storage.MorphyOpenOption;

import java.nio.file.OpenOption;
import java.util.Set;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public enum DatabaseMode {
    READ_WRITE,  // (default) The database can be read and written do; it will get upgraded if needed when opening it
    READ_ONLY,   // The database can only be read to
    IN_MEMORY,   // The contents of the entire database is loaded to memory; the in-memory copy can be written to but it's not persisted
    READ_REPAIR,
    ; // The database can only be read to; if faulty data is found, no exception (if possible) will be thrown

    public Set<OpenOption> openOptions() {
        return switch (this) {
            case READ_WRITE -> Set.of(READ, WRITE);
            case READ_ONLY, IN_MEMORY -> Set.of(READ);
            case READ_REPAIR -> Set.of(READ, MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS);
        };
    }
}
