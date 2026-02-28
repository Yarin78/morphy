package se.yarin.morphy.service.search;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.Game;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.filter.*;

@RestController
@RequestMapping("/api/filters")
public class FiltersController {

  private static final GameQueryBuilder GAME_QUERY_BUILDER = new GameQueryBuilder();

  private static final List<QuerySortField<Game>> GAME_SORT_FIELDS =
      List.of(
          QuerySortField.id(),
          QuerySortField.playedDate(),
          QuerySortField.gameWhitePlayerName(),
          QuerySortField.gameBlackPlayerName(),
          QuerySortField.gameResult(),
          QuerySortField.gameEco(),
          QuerySortField.gameRound(),
          QuerySortField.gameTournamentTitle(),
          QuerySortField.gameSourceTitle(),
          QuerySortField.gameAnnotatorName(),
          QuerySortField.gameGameTagTitle(),
          QuerySortField.gameWhiteElo(),
          QuerySortField.gameBlackElo(),
          QuerySortField.gameNoMoves(),
          QuerySortField.gameWhiteTeamTitle(),
          QuerySortField.gameBlackTeamTitle(),
          QuerySortField.gameVcs(),
          QuerySortField.gameFinalMaterial(),
          QuerySortField.gameVersion(),
          QuerySortField.gameCreationTimestamp(),
          QuerySortField.gameLastChanged(),
          QuerySortField.gamePlayedYear(),
          QuerySortField.gameEloAvg(),
          QuerySortField.gameEloMax());

  private static final Map<String, AbstractEntityQueryBuilder<?>> ENTITY_BUILDERS =
      Map.of(
          "players", new PlayerQueryBuilder(),
          "tournaments", new TournamentQueryBuilder(),
          "annotators", new AnnotatorQueryBuilder(),
          "sources", new SourceQueryBuilder(),
          "teams", new TeamQueryBuilder(),
          "gametags", new GameTagQueryBuilder());

  @GetMapping("/games")
  public FilterOptionsResponse games(
      @RequestParam(defaultValue = "0") int depth,
      @RequestParam(name = "include_hidden", defaultValue = "false") boolean includeHidden) {
    List<String> fields = GAME_QUERY_BUILDER.availableFields();
    Set<String> hidden = includeHidden ? Set.of() : GAME_QUERY_BUILDER.hiddenFields();
    fields =
        fields.stream()
            .filter(f -> !hidden.contains(f))
            .filter(f -> depth > 0 || !f.contains("."))
            .toList();
    List<FilterOptionsResponse.SortFieldOption> sortFields =
        GAME_SORT_FIELDS.stream()
            .map(sf -> new FilterOptionsResponse.SortFieldOption(sf.name(), sf.defaultDirection().shortName()))
            .toList();
    return new FilterOptionsResponse(GAME_QUERY_BUILDER.defaultField(), fields, sortFields);
  }

  @GetMapping("/players")
  public FilterOptionsResponse players(@RequestParam(defaultValue = "0") int depth) {
    return entityFilterOptions("players");
  }

  @GetMapping("/tournaments")
  public FilterOptionsResponse tournaments(@RequestParam(defaultValue = "0") int depth) {
    return entityFilterOptions("tournaments");
  }

  @GetMapping("/annotators")
  public FilterOptionsResponse annotators(@RequestParam(defaultValue = "0") int depth) {
    return entityFilterOptions("annotators");
  }

  @GetMapping("/sources")
  public FilterOptionsResponse sources(@RequestParam(defaultValue = "0") int depth) {
    return entityFilterOptions("sources");
  }

  @GetMapping("/teams")
  public FilterOptionsResponse teams(@RequestParam(defaultValue = "0") int depth) {
    return entityFilterOptions("teams");
  }

  @GetMapping("/gametags")
  public FilterOptionsResponse gametags(@RequestParam(defaultValue = "0") int depth) {
    return entityFilterOptions("gametags");
  }

  private FilterOptionsResponse entityFilterOptions(String entityType) {
    AbstractEntityQueryBuilder<?> builder = ENTITY_BUILDERS.get(entityType);
    List<String> fields = builder.availableFields();
    List<FilterOptionsResponse.SortFieldOption> sortFields =
        builder.availableSortFields().stream()
            .map(
                sf ->
                    new FilterOptionsResponse.SortFieldOption(
                        sf.name(), sf.defaultDirection().shortName()))
            .toList();
    return new FilterOptionsResponse(builder.defaultField(), fields, sortFields);
  }
}
