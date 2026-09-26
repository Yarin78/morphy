package se.yarin.morphy.tools.testdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import se.yarin.chess.Date;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.tools.testdata.Corpus.Event;
import se.yarin.morphy.tools.testdata.Corpus.Person;

/**
 * Made-up players and events in numbers, for the versions that need entity trees with some depth.
 * The names stay within what the v1 format can hold: Latin-1, and at most 30 characters for a last
 * name and 20 for a first name.
 */
final class Bulk {

  private Bulk() {}

  private static final String[] SURNAME_START = {
    "Kar", "Mor", "Vel", "Tan", "Bra", "Sto", "Hel", "Dra", "Nor", "Pet", "Wal", "Gri", "Lin",
    "Mar", "Ros", "Fen", "Ald", "Bol", "Cas", "Dol", "Eri", "Fal", "Gan", "Hal"
  };

  private static final String[] SURNAME_END = {
    "ov", "sen", "ski", "son", "ini", "ez", "berg", "man", "ic", "ler", "dal", "mark"
  };

  private static final String[] FIRST_NAMES = {
    "Alexei", "Boris", "Carla", "Dmitri", "Elena", "Fredrik", "Greta", "Henrik", "Irina", "Jonas",
    "Katarina", "Lars", "Marta", "Nikolai", "Olga", "Pavel", "Reneé", "Søren", "Tomas",
    "Ulla", "Viktor", "Wanda", "Zoë", "Åke", "J.", "M.", "A.", "José", "André", "Björn"
  };

  /** A place and its IOC nation code. */
  private record City(String name, String nation) {}

  private static final City[] CITIES = {
    new City("Bergen", "NOR"), new City("Gothenburg", "SWE"), new City("Aarhus", "DEN"),
    new City("Tallinn", "EST"), new City("Riga", "LAT"), new City("Vilnius", "LTU"),
    new City("Prague", "CZE"), new City("Brno", "CZE"), new City("Vienna", "AUT"),
    new City("Zurich", "SUI"), new City("Reykjavik", "ISL"), new City("Lisbon", "POR"),
    new City("Sevilla", "ESP"), new City("Bilbao", "ESP"), new City("Athens", "GRE"),
    new City("Sofia", "BUL"), new City("Belgrade", "SRB"), new City("Zagreb", "CRO"),
    new City("Ljubljana", "SLO"), new City("Budapest", "HUN"), new City("Warsaw", "POL"),
    new City("Krakow", "POL"), new City("Kyiv", "UKR"), new City("Tbilisi", "GEO"),
    new City("Yerevan", "ARM"), new City("Baku", "AZE"), new City("Tehran", "IRI"),
    new City("Mumbai", "IND"), new City("Manila", "PHI"), new City("Dubai", "UAE")
  };

  /**
   * Made-up players.
   *
   * @param count how many; they are all different
   */
  static List<Person> players(Random random, int count) {
    Set<String> used = new LinkedHashSet<>();
    List<Person> players = new ArrayList<>();
    while (players.size() < count) {
      String last =
          SURNAME_START[random.nextInt(SURNAME_START.length)]
              + SURNAME_END[random.nextInt(SURNAME_END.length)];
      String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
      if (used.add(last + ", " + first)) {
        players.add(new Person(last, first, 1800 + random.nextInt(800)));
      }
    }
    return players;
  }

  /** Made-up events, each in a different place. */
  static List<Event> events(Random random, int count) {
    List<City> cities = new ArrayList<>(List.of(CITIES));
    Collections.shuffle(cities, random);
    String[] kinds = {"Open", "Masters", "Cup", "Memorial", "Festival"};
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      City city = cities.get(i % cities.size());
      if (Nation.fromIOC(city.nation()) == Nation.NONE) {
        throw new IllegalStateException("Not a nation: " + city.nation());
      }
      boolean swiss = random.nextBoolean();
      events.add(
          new Event(
              city.name() + " " + kinds[random.nextInt(kinds.length)] + " 2021",
              city.name(),
              city.nation(),
              new Date(2021, 1 + random.nextInt(12), 1 + random.nextInt(20)),
              swiss ? null : 8 + random.nextInt(8),
              5 + random.nextInt(7),
              swiss ? "swiss" : "tourn",
              null));
    }
    return events;
  }
}
