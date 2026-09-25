package se.yarin.morphy.service.debug;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.service.games.dto.GameSearchRequest;
import se.yarin.morphy.service.games.dto.GameSearchResponse;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

/**
 * Debug versions of the game and entity searches. They take the same parameters as the normal
 * searches, plus {@code executeAllPlans}, and return the normal result together with the query
 * plans and the raw records behind every returned item. Databases without diagnostics answer 501.
 */
@RestController
@RequestMapping("/api/databases/{databaseId}/debug")
public class DebugController {

  /** Entity kinds by the path segment their endpoints use. */
  private static final Map<String, EntityKind<?>> ENTITY_PATHS =
      Map.of(
          "players", EntityKind.PLAYER,
          "tournaments", EntityKind.TOURNAMENT,
          "annotators", EntityKind.ANNOTATOR,
          "sources", EntityKind.SOURCE,
          "teams", EntityKind.TEAM,
          "gametags", EntityKind.GAME_TAG);

  private final DebugService debugService;

  public DebugController(DebugService debugService) {
    this.debugService = debugService;
  }

  /** A game search with its query plans and raw records; parameters as for a normal search. */
  @GetMapping("/games/search")
  public ResponseEntity<DebugSearchResponse<GameSearchResponse>> searchGames(
      @PathVariable String databaseId,
      @ModelAttribute GameSearchRequest request,
      @RequestParam(defaultValue = "false") boolean executeAllPlans) {
    return ResponseEntity.ok(debugService.searchGames(databaseId, request, executeAllPlans));
  }

  /**
   * An entity search with its query plans and raw records.
   *
   * @param kind the entity path segment: players, tournaments, annotators, sources, teams or
   *     gametags
   */
  @GetMapping("/{kind}/search")
  public ResponseEntity<? extends DebugSearchResponse<? extends EntitySearchResponse<?>>>
      searchEntities(
          @PathVariable String databaseId,
          @PathVariable String kind,
          @RequestParam(required = false) String filter,
          @RequestParam(required = false) Integer offset,
          @RequestParam(required = false) Integer limit,
          @RequestParam(required = false) String sortBy,
          @RequestParam(defaultValue = "false") boolean executeAllPlans) {
    EntityKind<?> entityKind = ENTITY_PATHS.get(kind);
    if (entityKind == null) {
      throw new IllegalArgumentException(
          "Unknown entity kind '" + kind + "'; one of " + ENTITY_PATHS.keySet());
    }
    EntitySearchRequest request = new EntitySearchRequest(filter, offset, limit, sortBy);
    return ResponseEntity.ok(
        debugService.searchEntities(databaseId, entityKind, request, executeAllPlans));
  }
}
