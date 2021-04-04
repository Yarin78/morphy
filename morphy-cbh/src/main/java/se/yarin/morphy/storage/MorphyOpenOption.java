package se.yarin.morphy.storage;

import java.nio.file.OpenOption;
import java.util.HashSet;
import java.util.Set;

public enum MorphyOpenOption implements OpenOption {
    /**
     * By default any kind of invalid data encountered will cause exceptions.
     * By specifying this flag, non-critical errors will be ignored and empty data
     * (or similar) will be returned.
     * This can not be used in combination with {@link java.nio.file.StandardOpenOption#WRITE}
     */
    IGNORE_NON_CRITICAL_ERRORS,

    /**
     * If set, any extended storage will be ignored (.cbj file will be ignored when reading from .cbh,
     * .cbtt will be ignored when reading from .cbt). Typically only used in repair mode.
     */
    IGNORE_EXTENDED_STORAGE,
    ;

    /**
     * Filter out our custom OpenOptions so they can be passed to FileChannel.open
     * @param options a set of OpenOption
     * @return a new set with any MorphyOpenOption removed
     */
    public static Set<? extends OpenOption> valid(Set<OpenOption> options) {
        HashSet<OpenOption> validOptions = new HashSet<>(options);
        for (MorphyOpenOption invalidOption : MorphyOpenOption.values()) {
            validOptions.remove(invalidOption);
        }
        return validOptions;
    }
}
