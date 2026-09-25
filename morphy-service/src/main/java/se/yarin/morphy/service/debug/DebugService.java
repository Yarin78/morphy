package se.yarin.morphy.service.debug;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import se.yarin.morphy.CbhDiagnostics;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.model.EntityDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.entities.EntitiesService;
import se.yarin.morphy.service.games.GamesService;
import se.yarin.morphy.service.games.dto.GameSearchRequest;
import se.yarin.morphy.service.games.dto.GameSearchResponse;
import se.yarin.morphy.service.games.search.GameSearchRequestConverter;
import se.yarin.morphy.service.queryplans.QueryPlanDtoConverter;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

/**
 * Searches that also return how they were executed and the raw records behind the result. Only
 * databases offering {@link CbhDiagnostics} (the v1 format) can be debugged.
 *
 * <p>The search, the query plans and the raw records are all taken in one read of the database,
 * and the raw records are those of exactly the items in the returned page.
 */
@Service
public class DebugService {

  private final DatabaseService databaseService;
  private final GamesService gamesService;
  private final EntitiesService entitiesService;
  private final GameSearchRequestConverter gameSearchRequestConverter;
  private final QueryPlanDtoConverter queryPlanDtoConverter;

  public DebugService(
      DatabaseService databaseService,
      GamesService gamesService,
      EntitiesService entitiesService,
      GameSearchRequestConverter gameSearchRequestConverter,
      QueryPlanDtoConverter queryPlanDtoConverter) {
    this.databaseService = databaseService;
    this.gamesService = gamesService;
    this.entitiesService = entitiesService;
    this.gameSearchRequestConverter = gameSearchRequestConverter;
    this.queryPlanDtoConverter = queryPlanDtoConverter;
  }

  /**
   * Searches for games, with the query plans and the raw records of the returned games.
   *
   * @param executeAllPlans also execute the other candidate plans and compare their results
   * @throws UnsupportedOperationException if the database offers no diagnostics
   */
  public DebugSearchResponse<GameSearchResponse> searchGames(
      @NotNull String databaseId, @NotNull GameSearchRequest request, boolean executeAllPlans) {
    return databaseService.read(
        databaseId,
        db -> {
          CbhDiagnostics diagnostics = diagnostics(db);
          GameSearchResponse result = gamesService.search(db, request);
          var plans =
              diagnostics.explainGames(
                  gameSearchRequestConverter.toQuery(request), executeAllPlans);
          Map<Long, List<RawRecordDto>> raw = new LinkedHashMap<>();
          for (GameDto game : result.games()) {
            raw.put(game.id(), toDtos(diagnostics.rawGame(game.id())));
          }
          return new DebugSearchResponse<>(result, queryPlanDtoConverter.toDebugInfo(plans), raw);
        });
  }

  /**
   * Searches for entities of a kind, with the query plans and the raw records of the returned
   * entities.
   *
   * @param executeAllPlans also execute the other candidate plans and compare their results
   * @throws UnsupportedOperationException if the database offers no diagnostics
   */
  public <T> DebugSearchResponse<EntitySearchResponse<T>> searchEntities(
      @NotNull String databaseId,
      @NotNull EntityKind<T> kind,
      @NotNull EntitySearchRequest request,
      boolean executeAllPlans) {
    return databaseService.read(
        databaseId,
        db -> {
          CbhDiagnostics diagnostics = diagnostics(db);
          EntitySearchResponse<T> result = entitiesService.search(db, kind, request);
          var plans =
              diagnostics.explainEntities(
                  kind, EntitiesService.toQuery(request), executeAllPlans);
          Map<Long, List<RawRecordDto>> raw = new LinkedHashMap<>();
          for (T item : result.items()) {
            long id = ((EntityDto) item).id();
            raw.put(id, toDtos(diagnostics.rawEntity(kind, id)));
          }
          return new DebugSearchResponse<>(result, queryPlanDtoConverter.toDebugInfo(plans), raw);
        });
  }

  private static CbhDiagnostics diagnostics(Database db) {
    return db.extension(CbhDiagnostics.class)
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "Database " + db.name() + " (" + db.format() + ") offers no diagnostics"));
  }

  private static List<RawRecordDto> toDtos(List<CbhDiagnostics.RawRecord> records) {
    return records.stream().map(r -> new RawRecordDto(r.file(), r.bytes())).toList();
  }
}
