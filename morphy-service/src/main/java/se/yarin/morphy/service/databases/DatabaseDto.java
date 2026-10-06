package se.yarin.morphy.service.databases;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record DatabaseDto(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull String path,
    boolean readOnly,
    @Nullable String referenceName) {}
