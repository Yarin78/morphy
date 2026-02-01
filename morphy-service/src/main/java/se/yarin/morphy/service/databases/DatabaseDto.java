package se.yarin.morphy.service.databases;

import org.jetbrains.annotations.NotNull;

public record DatabaseDto(@NotNull String id, @NotNull String displayName, @NotNull String path) {

}
