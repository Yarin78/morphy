package se.yarin.morphy.service.databases;

/** A configured database; a read-only one can be searched but not changed. */
public record DatabaseResponse(String id, String displayName, String path, boolean readOnly) {}
