package se.yarin.morphy.api.query;

import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * What can be searched and sorted on for one search target (games, or one kind of entity) in a
 * particular database.
 *
 * @param defaultField the field a bare search term applies to
 * @param fields the filter fields, in display order
 * @param hiddenFields fields that work but are not meant to be offered to users
 * @param sortFields the fields a result can be sorted on
 */
public record SearchSchema(
    @NotNull String defaultField,
    @NotNull List<String> fields,
    @NotNull Set<String> hiddenFields,
    @NotNull List<SortField> sortFields) {

  /**
   * A field a result can be sorted on.
   *
   * @param name the field name, as used in a {@link Sort}
   * @param defaultDirection the direction used when a sort key gives none
   */
  public record SortField(@NotNull String name, @NotNull Sort.Direction defaultDirection) {}

  public SearchSchema {
    fields = List.copyOf(fields);
    hiddenFields = Set.copyOf(hiddenFields);
    sortFields = List.copyOf(sortFields);
  }
}
