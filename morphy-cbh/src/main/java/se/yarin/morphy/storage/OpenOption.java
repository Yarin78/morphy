package se.yarin.morphy.storage;

import java.util.Set;

public enum OpenOption {
    /**
     * Open for read access.
     */
    READ,

    /**
     * Open for write access.
     */
    WRITE,

    /**
     * If invalid and/or inconsistent data is found while reading from the database,
     * {@link se.yarin.morphy.exceptions.MorphyInvalidDataException} is thrown.
     * Must be set if opening for WRITE access to decrease risk of further breaking an already broken database.
     */
    STRICT,
    ;

    public static void validate(OpenOption[] options) {
        Set<OpenOption> optionSet = Set.of(options);
        if (optionSet.contains(WRITE) && !optionSet.contains(STRICT)) {
            throw new IllegalArgumentException("A database open in WRITE mode must also be in STRICT mode");
        }
    }
}
