package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.GameTagTitleFilter;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.QuerySortOrder;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link GameTag} from a filter
 * expression string.
 *
 * <p>Supported fields: name/title (default field: name).
 */
public class GameTagQueryBuilder extends AbstractEntityQueryBuilder<GameTag> {

  private static final Map<String, Function<FilterCondition, EntityFilter<GameTag>>> FILTERS =
      Map.ofEntries(
          Map.entry("name", c -> new GameTagTitleFilter(c.value(), false, false)),
          Map.entry("title", c -> new GameTagTitleFilter(c.value(), false, false)));

  public GameTagQueryBuilder() {
    super(EntityType.GAME_TAG, "game tag", "name");
  }

  private static final Map<String, QuerySortField<GameTag>> SORT_FIELDS =
      Map.ofEntries(
          Map.entry("name", QuerySortField.gameTagEnglishTitle()),
          Map.entry("englishtitle", QuerySortField.gameTagEnglishTitle()),
          Map.entry("germantitle", QuerySortField.gameTagGermanTitle()),
          Map.entry("frenchtitle", QuerySortField.gameTagFrenchTitle()),
          Map.entry("spanishtitle", QuerySortField.gameTagSpanishTitle()),
          Map.entry("italiantitle", QuerySortField.gameTagItalianTitle()),
          Map.entry("dutchtitle", QuerySortField.gameTagDutchTitle()),
          Map.entry("sloveniantitle", QuerySortField.gameTagSlovenianTitle()));

  @Override
  protected Map<String, Function<FilterCondition, EntityFilter<GameTag>>> filters() {
    return FILTERS;
  }

  @Override
  protected Map<String, QuerySortField<GameTag>> sortFields() {
    return SORT_FIELDS;
  }

  @Override
  protected QuerySortOrder<GameTag> defaultSortOrder() {
    return QuerySortOrder.byGameTagDefaultIndex();
  }
}
