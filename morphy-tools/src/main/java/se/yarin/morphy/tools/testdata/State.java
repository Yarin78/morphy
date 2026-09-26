package se.yarin.morphy.tools.testdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import se.yarin.morphy.model.GameDto;

/**
 * What a format's database is expected to contain, kept from version to version since each version
 * continues where the one before stopped.
 */
final class State {
  /** The games and texts, in id order. */
  final List<GameDto> expected = new ArrayList<>();

  /** The id of the games that later versions want to change, by name. */
  final Map<String, Integer> named = new HashMap<>();

  /** Names of players and events that have existed, to check that the ones that don't are gone. */
  final Set<String> seenPlayers = new LinkedHashSet<>();

  final Set<String> seenEvents = new LinkedHashSet<>();

  final Set<String> seenGameTags = new LinkedHashSet<>();
}
