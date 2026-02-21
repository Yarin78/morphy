package se.yarin.morphy.service.search;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public record FilterOptionsResponse(
    @NotNull String defaultField,
    @NotNull List<String> fields,
    @NotNull List<SortFieldOption> sortFields) {

  public record SortFieldOption(@NotNull String name, @NotNull String defaultDirection) {}
}
