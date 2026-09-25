package se.yarin.morphy.service.debug;

import org.jetbrains.annotations.NotNull;

/**
 * One stored record behind a game or entity.
 *
 * @param file the extension of the file the record is stored in, e.g. {@code ".cbh"}
 * @param bytes the record's bytes, base64-encoded in JSON
 */
public record RawRecordDto(@NotNull String file, byte @NotNull [] bytes) {}
