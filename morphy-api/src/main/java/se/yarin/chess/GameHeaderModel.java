package se.yarin.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The header of a chess game, independent of any database format: who played, when and where, and
 * with what result. Together with a {@link GameMovesModel} it makes up a {@link GameModel}.
 *
 * <p>Every field is optional and mutable. Players, the tournament, the annotator, the source, the
 * teams and the game tag are held by name, as in PGN. Each of them may also carry the id of an
 * existing entity in a particular database, for example when a game has been read from a
 * database, or when a user picked an existing player rather than typing a name. A database that
 * writes the game uses such an id as it is and finds or creates the entity from the name
 * otherwise. An id is only meaningful in the database it came from; see {@link
 * #clearEntityIds()}.
 *
 * <p>PGN tags that have no field of their own are kept as extra tags.
 *
 * <p>The fields can also be accessed by name, see {@link #getField(String)}, {@link
 * #setField(String, Object)} and {@link #getAllFields()}. That is for code that serializes a
 * header generically; the entity ids are not part of it.
 */
public class GameHeaderModel {

  /** The names of the standard fields, as used by {@link #getField(String)}, in export order. */
  public static final List<String> STANDARD_FIELDS =
      List.of(
          "white",
          "black",
          "whiteElo",
          "blackElo",
          "whiteTeam",
          "blackTeam",
          "result",
          "lineEvaluation",
          "date",
          "eco",
          "round",
          "subRound",
          "event",
          "eventDate",
          "eventEndDate",
          "eventSite",
          "eventCountry",
          "eventCategory",
          "eventRounds",
          "eventType",
          "eventTimeControl",
          "sourceTitle",
          "source",
          "sourceDate",
          "annotator",
          "gameTag");

  private @Nullable String white;
  private @Nullable String black;
  private @Nullable Integer whiteElo;
  private @Nullable Integer blackElo;
  private @Nullable String whiteTeam;
  private @Nullable String blackTeam;
  private @Nullable GameResult result;
  private @Nullable NAG lineEvaluation;
  private @Nullable Date date;
  private @Nullable Eco eco;
  private @Nullable Integer round;
  private @Nullable Integer subRound;

  private @Nullable String event;
  private @Nullable Date eventDate;
  private @Nullable Date eventEndDate;
  private @Nullable String eventSite;
  private @Nullable String eventCountry;
  private @Nullable Integer eventCategory;
  private @Nullable Integer eventRounds;
  private @Nullable String eventType;
  private @Nullable String eventTimeControl;

  private @Nullable String sourceTitle;
  private @Nullable String source;
  private @Nullable Date sourceDate;
  private @Nullable String annotator;
  private @Nullable String gameTag;

  // Ids of existing entities in the database the header belongs to; null if not bound
  private @Nullable Long whiteId;
  private @Nullable Long blackId;
  private @Nullable Long eventId;
  private @Nullable Long annotatorId;
  private @Nullable Long sourceId;
  private @Nullable Long whiteTeamId;
  private @Nullable Long blackTeamId;
  private @Nullable Long gameTagId;

  private final LinkedHashMap<String, String> extraTags = new LinkedHashMap<>();

  public GameHeaderModel() {}

  /** Creates a copy of another header, including its entity ids and extra tags. */
  public GameHeaderModel(@NotNull GameHeaderModel other) {
    replaceAll(other);
  }

  // ── Game ────────────────────────────────────────────────────────────────

  public @Nullable String getWhite() {
    return white;
  }

  public void setWhite(@Nullable String white) {
    this.white = white;
  }

  public @Nullable String getBlack() {
    return black;
  }

  public void setBlack(@Nullable String black) {
    this.black = black;
  }

  public @Nullable Integer getWhiteElo() {
    return whiteElo;
  }

  public void setWhiteElo(@Nullable Integer whiteElo) {
    this.whiteElo = whiteElo;
  }

  public @Nullable Integer getBlackElo() {
    return blackElo;
  }

  public void setBlackElo(@Nullable Integer blackElo) {
    this.blackElo = blackElo;
  }

  public @Nullable String getWhiteTeam() {
    return whiteTeam;
  }

  public void setWhiteTeam(@Nullable String whiteTeam) {
    this.whiteTeam = whiteTeam;
  }

  public @Nullable String getBlackTeam() {
    return blackTeam;
  }

  public void setBlackTeam(@Nullable String blackTeam) {
    this.blackTeam = blackTeam;
  }

  public @Nullable GameResult getResult() {
    return result;
  }

  public void setResult(@Nullable GameResult result) {
    this.result = result;
  }

  /** The evaluation of an unfinished game; {@link NAG#NONE} if not set. */
  public @NotNull NAG getLineEvaluation() {
    return lineEvaluation == null ? NAG.NONE : lineEvaluation;
  }

  public void setLineEvaluation(@Nullable NAG lineEvaluation) {
    this.lineEvaluation = lineEvaluation;
  }

  public @Nullable Date getDate() {
    return date;
  }

  public void setDate(@Nullable Date date) {
    this.date = date;
  }

  public @Nullable Eco getEco() {
    return eco;
  }

  public void setEco(@Nullable Eco eco) {
    this.eco = eco;
  }

  public @Nullable Integer getRound() {
    return round;
  }

  public void setRound(@Nullable Integer round) {
    this.round = round;
  }

  public @Nullable Integer getSubRound() {
    return subRound;
  }

  public void setSubRound(@Nullable Integer subRound) {
    this.subRound = subRound;
  }

  // ── Tournament ──────────────────────────────────────────────────────────

  public @Nullable String getEvent() {
    return event;
  }

  public void setEvent(@Nullable String event) {
    this.event = event;
  }

  public @Nullable Date getEventDate() {
    return eventDate;
  }

  public void setEventDate(@Nullable Date eventDate) {
    this.eventDate = eventDate;
  }

  public @Nullable Date getEventEndDate() {
    return eventEndDate;
  }

  public void setEventEndDate(@Nullable Date eventEndDate) {
    this.eventEndDate = eventEndDate;
  }

  public @Nullable String getEventSite() {
    return eventSite;
  }

  public void setEventSite(@Nullable String eventSite) {
    this.eventSite = eventSite;
  }

  public @Nullable String getEventCountry() {
    return eventCountry;
  }

  public void setEventCountry(@Nullable String eventCountry) {
    this.eventCountry = eventCountry;
  }

  public @Nullable Integer getEventCategory() {
    return eventCategory;
  }

  public void setEventCategory(@Nullable Integer eventCategory) {
    this.eventCategory = eventCategory;
  }

  public @Nullable Integer getEventRounds() {
    return eventRounds;
  }

  public void setEventRounds(@Nullable Integer eventRounds) {
    this.eventRounds = eventRounds;
  }

  public @Nullable String getEventType() {
    return eventType;
  }

  public void setEventType(@Nullable String eventType) {
    this.eventType = eventType;
  }

  public @Nullable String getEventTimeControl() {
    return eventTimeControl;
  }

  public void setEventTimeControl(@Nullable String eventTimeControl) {
    this.eventTimeControl = eventTimeControl;
  }

  // ── Source, annotator, tag ──────────────────────────────────────────────

  public @Nullable String getSourceTitle() {
    return sourceTitle;
  }

  public void setSourceTitle(@Nullable String sourceTitle) {
    this.sourceTitle = sourceTitle;
  }

  /** The publisher of the source. */
  public @Nullable String getSource() {
    return source;
  }

  public void setSource(@Nullable String source) {
    this.source = source;
  }

  public @Nullable Date getSourceDate() {
    return sourceDate;
  }

  public void setSourceDate(@Nullable Date sourceDate) {
    this.sourceDate = sourceDate;
  }

  public @Nullable String getAnnotator() {
    return annotator;
  }

  public void setAnnotator(@Nullable String annotator) {
    this.annotator = annotator;
  }

  public @Nullable String getGameTag() {
    return gameTag;
  }

  public void setGameTag(@Nullable String gameTag) {
    this.gameTag = gameTag;
  }

  // ── Entity ids ──────────────────────────────────────────────────────────

  public @Nullable Long getWhiteId() {
    return whiteId;
  }

  public void setWhiteId(@Nullable Long whiteId) {
    this.whiteId = whiteId;
  }

  public @Nullable Long getBlackId() {
    return blackId;
  }

  public void setBlackId(@Nullable Long blackId) {
    this.blackId = blackId;
  }

  public @Nullable Long getEventId() {
    return eventId;
  }

  public void setEventId(@Nullable Long eventId) {
    this.eventId = eventId;
  }

  public @Nullable Long getAnnotatorId() {
    return annotatorId;
  }

  public void setAnnotatorId(@Nullable Long annotatorId) {
    this.annotatorId = annotatorId;
  }

  public @Nullable Long getSourceId() {
    return sourceId;
  }

  public void setSourceId(@Nullable Long sourceId) {
    this.sourceId = sourceId;
  }

  public @Nullable Long getWhiteTeamId() {
    return whiteTeamId;
  }

  public void setWhiteTeamId(@Nullable Long whiteTeamId) {
    this.whiteTeamId = whiteTeamId;
  }

  public @Nullable Long getBlackTeamId() {
    return blackTeamId;
  }

  public void setBlackTeamId(@Nullable Long blackTeamId) {
    this.blackTeamId = blackTeamId;
  }

  public @Nullable Long getGameTagId() {
    return gameTagId;
  }

  public void setGameTagId(@Nullable Long gameTagId) {
    this.gameTagId = gameTagId;
  }

  /**
   * Removes all entity ids, so that a database writing the header finds or creates every entity by
   * name. Needed before a header read from one database is written to another.
   */
  public void clearEntityIds() {
    whiteId = null;
    blackId = null;
    eventId = null;
    annotatorId = null;
    sourceId = null;
    whiteTeamId = null;
    blackTeamId = null;
    gameTagId = null;
  }

  // ── Extra tags ──────────────────────────────────────────────────────────

  /** PGN tags that have no field of their own, in the order they were added. */
  public @NotNull Map<String, String> getExtraTags() {
    return Collections.unmodifiableMap(extraTags);
  }

  public @Nullable String getExtraTag(@NotNull String name) {
    return extraTags.get(name);
  }

  /**
   * Sets an extra tag.
   *
   * @param name the tag name; must not be the name of a standard field
   * @param value the value, or null to remove the tag
   */
  public void setExtraTag(@NotNull String name, @Nullable String value) {
    if (STANDARD_FIELDS.contains(name)) {
      throw new IllegalArgumentException(name + " is a standard header field, not an extra tag");
    }
    if (value == null) {
      extraTags.remove(name);
    } else {
      extraTags.put(name, value);
    }
  }

  // ── Access by name ──────────────────────────────────────────────────────

  /**
   * Gets a field by name: one of {@link #STANDARD_FIELDS}, or else an extra tag.
   *
   * @return the value, or null if not set
   */
  public @Nullable Object getField(@NotNull String name) {
    return switch (name) {
      case "white" -> white;
      case "black" -> black;
      case "whiteElo" -> whiteElo;
      case "blackElo" -> blackElo;
      case "whiteTeam" -> whiteTeam;
      case "blackTeam" -> blackTeam;
      case "result" -> result;
      case "lineEvaluation" -> lineEvaluation;
      case "date" -> date;
      case "eco" -> eco;
      case "round" -> round;
      case "subRound" -> subRound;
      case "event" -> event;
      case "eventDate" -> eventDate;
      case "eventEndDate" -> eventEndDate;
      case "eventSite" -> eventSite;
      case "eventCountry" -> eventCountry;
      case "eventCategory" -> eventCategory;
      case "eventRounds" -> eventRounds;
      case "eventType" -> eventType;
      case "eventTimeControl" -> eventTimeControl;
      case "sourceTitle" -> sourceTitle;
      case "source" -> source;
      case "sourceDate" -> sourceDate;
      case "annotator" -> annotator;
      case "gameTag" -> gameTag;
      default -> extraTags.get(name);
    };
  }

  /**
   * Sets a field by name: one of {@link #STANDARD_FIELDS}, or else an extra tag.
   *
   * @param value the value, or null to unset the field
   * @throws IllegalArgumentException if the value has the wrong type for the field; extra tags
   *     only hold strings
   */
  public void setField(@NotNull String name, @Nullable Object value) {
    switch (name) {
      case "white" -> white = cast(name, value, String.class);
      case "black" -> black = cast(name, value, String.class);
      case "whiteElo" -> whiteElo = cast(name, value, Integer.class);
      case "blackElo" -> blackElo = cast(name, value, Integer.class);
      case "whiteTeam" -> whiteTeam = cast(name, value, String.class);
      case "blackTeam" -> blackTeam = cast(name, value, String.class);
      case "result" -> result = cast(name, value, GameResult.class);
      case "lineEvaluation" -> lineEvaluation = cast(name, value, NAG.class);
      case "date" -> date = cast(name, value, Date.class);
      case "eco" -> eco = cast(name, value, Eco.class);
      case "round" -> round = cast(name, value, Integer.class);
      case "subRound" -> subRound = cast(name, value, Integer.class);
      case "event" -> event = cast(name, value, String.class);
      case "eventDate" -> eventDate = cast(name, value, Date.class);
      case "eventEndDate" -> eventEndDate = cast(name, value, Date.class);
      case "eventSite" -> eventSite = cast(name, value, String.class);
      case "eventCountry" -> eventCountry = cast(name, value, String.class);
      case "eventCategory" -> eventCategory = cast(name, value, Integer.class);
      case "eventRounds" -> eventRounds = cast(name, value, Integer.class);
      case "eventType" -> eventType = cast(name, value, String.class);
      case "eventTimeControl" -> eventTimeControl = cast(name, value, String.class);
      case "sourceTitle" -> sourceTitle = cast(name, value, String.class);
      case "source" -> source = cast(name, value, String.class);
      case "sourceDate" -> sourceDate = cast(name, value, Date.class);
      case "annotator" -> annotator = cast(name, value, String.class);
      case "gameTag" -> gameTag = cast(name, value, String.class);
      default -> setExtraTag(name, cast(name, value, String.class));
    }
  }

  public void unsetField(@NotNull String name) {
    setField(name, null);
  }

  /**
   * Gets every field that is set, standard fields first in {@link #STANDARD_FIELDS} order, then
   * the extra tags. Entity ids are not included. Changing the map does not change the header.
   */
  public @NotNull Map<String, Object> getAllFields() {
    LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
    for (String name : STANDARD_FIELDS) {
      Object value = getField(name);
      if (value != null) {
        fields.put(name, value);
      }
    }
    fields.putAll(extraTags);
    return fields;
  }

  private static <T> T cast(@NotNull String name, @Nullable Object value, @NotNull Class<T> type) {
    if (value != null && !type.isInstance(value)) {
      throw new IllegalArgumentException(
          "Header field " + name + " must be of type " + type.getSimpleName());
    }
    return type.cast(value);
  }

  // ── Whole header ────────────────────────────────────────────────────────

  /** Unsets every field, entity id and extra tag. */
  public void clear() {
    for (String name : STANDARD_FIELDS) {
      setField(name, null);
    }
    clearEntityIds();
    extraTags.clear();
  }

  /** Replaces the whole contents of this header with a copy of another one. */
  public void replaceAll(@NotNull GameHeaderModel other) {
    clear();
    for (String name : STANDARD_FIELDS) {
      setField(name, other.getField(name));
    }
    whiteId = other.whiteId;
    blackId = other.blackId;
    eventId = other.eventId;
    annotatorId = other.annotatorId;
    sourceId = other.sourceId;
    whiteTeamId = other.whiteTeamId;
    blackTeamId = other.blackTeamId;
    gameTagId = other.gameTagId;
    extraTags.putAll(other.extraTags);
  }

  private List<Long> entityIds() {
    return Arrays.asList(
        whiteId, blackId, eventId, annotatorId, sourceId, whiteTeamId, blackTeamId, gameTagId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GameHeaderModel that)) return false;
    return getAllFields().equals(that.getAllFields()) && entityIds().equals(that.entityIds());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getAllFields(), entityIds());
  }

  @Override
  public String toString() {
    List<String> parts = new ArrayList<>();
    getAllFields().forEach((name, value) -> parts.add(name + " = " + value));
    List<String> idNames =
        List.of(
            "whiteId",
            "blackId",
            "eventId",
            "annotatorId",
            "sourceId",
            "whiteTeamId",
            "blackTeamId",
            "gameTagId");
    List<Long> ids = entityIds();
    for (int i = 0; i < ids.size(); i++) {
      if (ids.get(i) != null) {
        parts.add(idNames.get(i) + " = " + ids.get(i));
      }
    }
    return "{ " + String.join(", ", parts) + " }";
  }
}
