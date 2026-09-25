package se.yarin.morphy.service.search;

import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.service.databases.DatabaseService;

/**
 * What can be filtered and sorted on in a database: for games, and for each kind of entity. The
 * options depend on the database's format, so they are served per database.
 */
@RestController
@RequestMapping("/api/databases/{databaseId}/filters")
public class FiltersController {

  private final DatabaseService databaseService;

  public FiltersController(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  /**
   * The game filter options.
   *
   * @param depth 0 to leave out entity sub-fields such as {@code player.name}
   * @param includeHidden whether to include fields not meant to be offered to users, such as ids
   */
  @GetMapping("/games")
  public FilterOptionsResponse games(
      @PathVariable String databaseId,
      @RequestParam(defaultValue = "0") int depth,
      @RequestParam(name = "include_hidden", defaultValue = "false") boolean includeHidden) {
    SearchSchema schema = databaseService.read(databaseId, db -> db.gameSearchSchema());
    Set<String> hidden = includeHidden ? Set.of() : schema.hiddenFields();
    List<String> fields =
        schema.fields().stream()
            .filter(f -> !hidden.contains(f))
            .filter(f -> depth > 0 || !f.contains("."))
            .toList();
    return new FilterOptionsResponse(schema.defaultField(), fields, sortFields(schema));
  }

  @GetMapping("/players")
  public FilterOptionsResponse players(@PathVariable String databaseId) {
    return entityFilterOptions(databaseId, EntityKind.PLAYER);
  }

  @GetMapping("/tournaments")
  public FilterOptionsResponse tournaments(@PathVariable String databaseId) {
    return entityFilterOptions(databaseId, EntityKind.TOURNAMENT);
  }

  @GetMapping("/annotators")
  public FilterOptionsResponse annotators(@PathVariable String databaseId) {
    return entityFilterOptions(databaseId, EntityKind.ANNOTATOR);
  }

  @GetMapping("/sources")
  public FilterOptionsResponse sources(@PathVariable String databaseId) {
    return entityFilterOptions(databaseId, EntityKind.SOURCE);
  }

  @GetMapping("/teams")
  public FilterOptionsResponse teams(@PathVariable String databaseId) {
    return entityFilterOptions(databaseId, EntityKind.TEAM);
  }

  @GetMapping("/gametags")
  public FilterOptionsResponse gametags(@PathVariable String databaseId) {
    return entityFilterOptions(databaseId, EntityKind.GAME_TAG);
  }

  private FilterOptionsResponse entityFilterOptions(
      @NotNull String databaseId, @NotNull EntityKind<?> kind) {
    SearchSchema schema = databaseService.read(databaseId, db -> db.entitySearchSchema(kind));
    return new FilterOptionsResponse(schema.defaultField(), schema.fields(), sortFields(schema));
  }

  private static List<FilterOptionsResponse.SortFieldOption> sortFields(SearchSchema schema) {
    return schema.sortFields().stream()
        .map(
            f ->
                new FilterOptionsResponse.SortFieldOption(
                    f.name(), f.defaultDirection() == Sort.Direction.DESCENDING ? "desc" : "asc"))
        .toList();
  }
}
