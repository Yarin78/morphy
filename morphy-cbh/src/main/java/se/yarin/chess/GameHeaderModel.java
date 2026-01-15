package se.yarin.chess;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contains metadata about a chess game, e.g. the name of the players,
 * their ranking, where the game was played, the result etc
 *
 * Some fields are "standard" (and typed), but it's also possible to set custom metadata fields.
 * The standard fields have typed getter methods, but can be set using {@link #setField(String, Object)}
 *
 * All fields are nullable (unset) and mutable.
 */
public class GameHeaderModel {

    private List<GameHeaderModelChangeListener> changeListeners = new ArrayList<>();

    // Define the standard fields and their types
    // The types must be an immutable type, to avoid the data from being changed outside of the model

    private static final String FIELD_WHITE = "white";
    private static final String FIELD_BLACK = "black";
    private static final String FIELD_WHITE_ELO = "whiteElo";
    private static final String FIELD_BLACK_ELO = "blackElo";
    private static final String FIELD_WHITE_TEAM = "whiteTeam";
    private static final String FIELD_BLACK_TEAM = "blackTeam";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_LINE_EVALUATION = "lineEvaluation";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_ECO = "eco";
    private static final String FIELD_ROUND = "round";
    private static final String FIELD_SUB_ROUND = "subRound";

    private static final String FIELD_EVENT = "event";
    private static final String FIELD_EVENT_DATE = "eventDate";
    private static final String FIELD_EVENT_END_DATE = "eventEndDate";
    private static final String FIELD_EVENT_SITE = "eventSite";
    private static final String FIELD_EVENT_COUNTRY = "eventCountry";
    private static final String FIELD_EVENT_CATEGORY = "eventCategory";
    private static final String FIELD_EVENT_ROUNDS = "eventRounds";
    private static final String FIELD_EVENT_TYPE = "eventType";
    private static final String FIELD_EVENT_TIME_CONTROL = "eventTimeControl";

    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_SOURCE_TITLE = "sourceTitle";
    private static final String FIELD_SOURCE_DATE= "sourceDate";
    private static final String FIELD_ANNOTATOR = "annotator";
    private static final String FIELD_GAME_TAG = "gameTag";

    @HeaderData private String white;
    @HeaderData private String black;
    @HeaderData private Integer whiteElo;
    @HeaderData private Integer blackElo;
    @HeaderData private String whiteTeam;
    @HeaderData private String blackTeam;
    @HeaderData private GameResult result;
    @HeaderData private NAG lineEvaluation;
    @HeaderData private Date date;
    @HeaderData private Eco eco;
    @HeaderData private Integer round;
    @HeaderData private Integer subRound;

    @HeaderData private String event;
    @HeaderData private Date eventDate;
    @HeaderData private Date eventEndDate;
    @HeaderData private String eventSite;
    @HeaderData private String eventCountry;
    @HeaderData private Integer eventCategory;
    @HeaderData private Integer eventRounds;
    @HeaderData private String eventType;
    @HeaderData private String eventTimeControl;

    @HeaderData private String sourceTitle;
    @HeaderData private String source;
    @HeaderData private Date sourceDate;
    @HeaderData private String annotator;
    @HeaderData private String gameTag;

    public String getWhite() { return white; }
    public String getBlack() { return black; }
    public Integer getWhiteElo() { return whiteElo; }
    public Integer getBlackElo() { return blackElo; }
    public String getWhiteTeam() { return whiteTeam; }
    public String getBlackTeam() { return blackTeam; }
    public GameResult getResult() { return result; }
    public NAG getLineEvaluation() { return lineEvaluation; }
    public Date getDate() { return date; }
    public Eco getEco() { return eco; }
    public Integer getRound() { return round; }
    public Integer getSubRound() { return subRound; }
    public String getEvent() { return event; }
    public Date getEventDate() { return eventDate; }
    public Date getEventEndDate() { return eventEndDate; }
    public String getEventSite() { return eventSite; }
    public String getEventCountry() { return eventCountry; }
    public Integer getEventCategory() { return eventCategory; }
    public Integer getEventRounds() { return eventRounds; }
    public String getEventType() { return eventType; }
    public String getEventTimeControl() { return eventTimeControl; }
    public String getSourceTitle() { return sourceTitle; }
    public String getSource() { return source; }
    public Date getSourceDate() { return sourceDate; }
    public String getAnnotator() { return annotator; }
    public String getGameTag() { return gameTag; }

    public void setWhite(String name) { setField(FIELD_WHITE, name); }
    public void setBlack(String name) { setField(FIELD_BLACK, name); }
    public void setWhiteElo(int elo) { setField(FIELD_WHITE_ELO, elo); }
    public void setBlackElo(int elo) { setField(FIELD_BLACK_ELO, elo); }
    public void setWhiteTeam(String name) { setField(FIELD_WHITE_TEAM, name); }
    public void setBlackTeam(String name) { setField(FIELD_BLACK_TEAM, name); }
    public void setResult(GameResult result) { setField(FIELD_RESULT, result); }
    public void setLineEvaluation(NAG evaluation) { setField(FIELD_LINE_EVALUATION, evaluation); }
    public void setDate(Date date) { setField(FIELD_DATE, date); }
    public void setEco(Eco eco) { setField(FIELD_ECO, eco); }
    public void setRound(int round) { setField(FIELD_ROUND, round); }
    public void setSubRound(int subRound) { setField(FIELD_SUB_ROUND, subRound); }
    public void setEvent(String name) { setField(FIELD_EVENT, name); }
    public void setEventDate(Date date) { setField(FIELD_EVENT_DATE, date); }
    public void setEventEndDate(Date date) { setField(FIELD_EVENT_END_DATE, date); }
    public void setEventSite(String site) { setField(FIELD_EVENT_SITE, site); }
    public void setEventCountry(String country) { setField(FIELD_EVENT_COUNTRY, country); }
    public void setEventRounds(int rounds) { setField(FIELD_EVENT_ROUNDS, rounds); }
    public void setEventCategory(int category) { setField(FIELD_EVENT_CATEGORY, category); }
    public void setEventType(String type) { setField(FIELD_EVENT_TYPE, type); }
    public void setEventTimeControl(String timeControl) { setField(FIELD_EVENT_TIME_CONTROL, timeControl); }
    public void setSourceTitle(String title) { setField(FIELD_SOURCE_TITLE, title); }
    public void setSource(String name) { setField(FIELD_SOURCE, name); }
    public void setSourceDate(Date date) { setField(FIELD_SOURCE_DATE, date); }
    public void setAnnotator(String annotator) { setField(FIELD_ANNOTATOR, annotator); }
    public void setGameTag(String gameTag) { setField(FIELD_GAME_TAG, gameTag); }

    // This map contains all the fields, including the standard ones
    private final Map<String, Object> fields = new ConcurrentHashMap<>();

    private static final Map<String, Field> predefinedHeaderFields = new HashMap<>();

    static {
        for (Field field : GameHeaderModel.class.getDeclaredFields()) {
            HeaderData headerData = field.getAnnotation(HeaderData.class);
            if (headerData != null) {
                String fieldName = headerData.fieldName();
                if (fieldName.length() == 0) {
                    fieldName = field.getName();
                }
                predefinedHeaderFields.put(fieldName, field);
            }
        }
    }

    private boolean internalSetField(String fieldName, Object value, boolean ignoreTypeErrors) {
        Field field = predefinedHeaderFields.get(fieldName);
        if (field != null) {
            try {
                field.set(this, value);
                // Only update the generic map if the set above succeeded
                if (value == null) {
                    fields.remove(fieldName);
                } else {
                    fields.put(fieldName, value);
                }
                return true;
            } catch (IllegalAccessException | IllegalArgumentException e) {
                if (!ignoreTypeErrors) {
                    throw new IllegalArgumentException("Header field " + fieldName + " must be of type " + field.getType(), e);
                }
                return false;
            }
        } else {
            if (value == null) {
                fields.remove(fieldName);
            } else {
                fields.put(fieldName, value);
            }
            return true;
        }
    }

    private boolean internalUnsetField(String fieldName) {
        return internalSetField(fieldName, null, true);
    }

    /**
     * Unsets the header value for the specified field.
     * @param fieldName the name of the field to unset
     */
    public void unsetField(@NotNull String fieldName) {
        setField(fieldName, null);
    }

    /**
     * Sets the header value for the specified field.
     * @param fieldName the name of the field
     * @param value the value of the field. If null, the field will be unset.
     * @exception IllegalArgumentException thrown if the field is a predefined field
     * and the type of the value doesn't match
     */
    public void setField(@NotNull String fieldName, Object value) {
        internalSetField(fieldName, value, false);
        notifyHeaderChanged();
    }

    /**
     * Sets the header values for the specified fields.
     * If there are any type mismatches, those fields will be silently ignored.
     *
     * @param headerData a mapping of header fields to header values
     */
    public void setFields(@NotNull Map<String, Object> headerData) {
        for (Map.Entry<String, Object> entry : headerData.entrySet()) {
            internalSetField(entry.getKey(), entry.getValue(), true);
        }
        notifyHeaderChanged();
    }

    /**
     * Clears all the set header values
     */
    public void clear() {
        fields.keySet().forEach(this::internalUnsetField);
        notifyHeaderChanged();
    }

    /**
     * Replaces all the data in this header with the data from another header model.
     * @param header the header model containing the new data
     */
    public void replaceAll(GameHeaderModel header) {
        fields.keySet().forEach(this::internalUnsetField);
        setFields(header.getAllFields());
    }

    /**
     * Gets the header value for the specified field.
     * @param fieldName the name of the field
     * @return the value of the field, or null if not set
     */
    public Object getField(String fieldName) {
        return fields.get(fieldName);
    }

    /**
     * Gets the header value for the specified field.
     * @param fieldName the name of the field
     * @param defaultValue the value to return if the field isn't set
     * @return the value of the field, or null if not set
     */
    public Object getField(String fieldName, Object defaultValue) {
        return fields.getOrDefault(fieldName, defaultValue);
    }

    /**
     * Gets a map of all header fields that have been set.
     * Changing the contents of this map will not affect the header data.
     * @return a map mapping field names to field values
     */
    public Map<String, Object> getAllFields() {
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Adds a listener of header model changes
     * @param listener the listener
     */
    public void addChangeListener(@NotNull GameHeaderModelChangeListener listener) {
        this.changeListeners.add(listener);
    }

    /**
     * Removes a listener of header model changes
     * @param listener the listener
     * @return true if the listener was removed
     */
    public boolean removeChangeListener(@NotNull GameHeaderModelChangeListener listener) {
        return this.changeListeners.remove(listener);
    }

    protected void notifyHeaderChanged() {
        for (GameHeaderModelChangeListener changeListener : changeListeners) {
            changeListener.headerModelChanged(this);
        }
    }




    @Override
    public int hashCode() {
        int result = 0;
        // We xor all the result since the order of the hash set is non-deterministic
        for (String field : fields.keySet()) {
            Object value = fields.get(field);
            int hc1 = field.hashCode();
            int hc2 = value == null ? 0 : value.hashCode();
            result ^= (hc1 * 37 + hc2);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameHeaderModel that = (GameHeaderModel) o;
        if (getAllFields().size() != that.getAllFields().size()) return false;
        for (String field : getAllFields().keySet()) {
            Object v1 = getField(field);
            Object v2 = that.getField(field);
            if (v1 == null && v2 != null) return false;
            if (v1 != null && !v1.equals(v2)) return false;
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{ ");
        for (String field : getAllFields().keySet()) {
            if (sb.length() > 2) {
                sb.append(", ");
            }
            sb.append(field).append(" = ").append(getField(field));
        }
        sb.append(" }");
        return sb.toString();
    }
}
