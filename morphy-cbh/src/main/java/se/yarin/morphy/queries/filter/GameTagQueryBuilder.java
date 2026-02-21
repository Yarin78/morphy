package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.GameTagLanguageCountFilter;
import se.yarin.morphy.entities.filters.GameTagLanguagesFilter;
import se.yarin.morphy.entities.filters.GameTagTitleFilter;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.QuerySortOrder;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link GameTag} from a filter
 * expression string.
 *
 * <p>Supported fields: title, languages, languagecount (default field: title).
 */
public class GameTagQueryBuilder extends AbstractEntityQueryBuilder<GameTag> {

  private static final Map<String, Function<FilterCondition, EntityFilter<GameTag>>> FILTERS =
      Map.ofEntries(
          Map.entry("title", c -> new GameTagTitleFilter(c.value(), false, false, true)),
          Map.entry("languages", c -> new GameTagLanguagesFilter(c.value())),
          Map.entry(
              "languagecount", GameTagQueryBuilder::buildLanguageCountFilter));

  public GameTagQueryBuilder() {
    super(EntityType.GAME_TAG, "game tag", "title");
  }

  private static final Map<String, QuerySortField<GameTag>> SORT_FIELDS =
      Map.ofEntries(
          Map.entry("title", QuerySortField.gameTagTitle()),
          Map.entry("languages", QuerySortField.gameTagLanguages()),
          Map.entry("languagecount", QuerySortField.gameTagLanguageCount()),
          Map.entry("englishtitle", QuerySortField.gameTagEnglishTitle()),
          Map.entry("germantitle", QuerySortField.gameTagGermanTitle()),
          Map.entry("frenchtitle", QuerySortField.gameTagFrenchTitle()),
          Map.entry("spanishtitle", QuerySortField.gameTagSpanishTitle()),
          Map.entry("italiantitle", QuerySortField.gameTagItalianTitle()),
          Map.entry("dutchtitle", QuerySortField.gameTagDutchTitle()),
          Map.entry("sloveniantitle", QuerySortField.gameTagSlovenianTitle()),
          Map.entry("count", QuerySortField.entityCount()));

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

  private static @NotNull EntityFilter<GameTag> buildLanguageCountFilter(
      @NotNull FilterCondition condition) {
    String value = condition.value();
    if (value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max =
          parts.length > 1 && !parts[1].isEmpty()
              ? Integer.parseInt(parts[1])
              : Integer.MAX_VALUE;
      return new GameTagLanguageCountFilter(min, max);
    }
    int count = Integer.parseInt(value);
    return new GameTagLanguageCountFilter(count, count);
  }
}
