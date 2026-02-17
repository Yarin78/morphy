package se.yarin.morphy.queries.filter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.MultiPlayerNameFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link Player} from a filter expression
 * string.
 *
 * <p>Supported fields: name (with pipe syntax for OR matching).
 */
public class PlayerQueryBuilder extends AbstractEntityQueryBuilder<Player> {

  private static final Map<String, Function<FilterCondition, EntityFilter<Player>>> FILTERS =
      Map.ofEntries(
          Map.entry(
              "name",
              c -> {
                String value = c.value();
                if (value.contains("|")) {
                  List<String> names =
                      Arrays.stream(value.split("\\|"))
                          .map(String::trim)
                          .collect(Collectors.toList());
                  return new MultiPlayerNameFilter(names, false, false);
                }
                return new PlayerNameFilter(value, false, false);
              }));

  public PlayerQueryBuilder() {
    super(EntityType.PLAYER, "player", "name");
  }

  @Override
  protected Map<String, Function<FilterCondition, EntityFilter<Player>>> filters() {
    return FILTERS;
  }
}
