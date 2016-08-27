package se.yarin.chess;

import lombok.Getter;
import lombok.NonNull;

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
    // Can't unfortunately use Lombok @Setter since we need to notify listeners upon change
    // TODO: Create manual setters for type safety please!!

    @HeaderData @Getter private String white;
    @HeaderData @Getter private String black;
    @HeaderData @Getter private Integer whiteElo;
    @HeaderData @Getter private Integer blackElo;
    @HeaderData @Getter private String whiteTeam;
    @HeaderData @Getter private String blackTeam;
    @HeaderData @Getter private GameResult result;
    @HeaderData @Getter private LineEvaluation lineEvaluation;
    @HeaderData @Getter private Date date;
    @HeaderData @Getter private Eco eco;
    @HeaderData @Getter private Integer round;
    @HeaderData @Getter private Integer subRound;

    @HeaderData @Getter private String event;
    @HeaderData @Getter private Date eventDate;
    @HeaderData @Getter private String eventSite;
    @HeaderData @Getter private String eventCountry;

    @HeaderData @Getter private String source;
    @HeaderData @Getter private String annotator;

    // This map contains all the fields, including the standard ones
    private Map<String, Object> fields = new ConcurrentHashMap<>();

    private static Map<String, Field> predefinedHeaderFields = new HashMap<>();

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
    public void unsetField(@NonNull String fieldName) {
        setField(fieldName, null);
    }

    /**
     * Sets the header value for the specified field.
     * @param fieldName the name of the field
     * @param value the value of the field. If null, the field will be unset.
     * @exception IllegalArgumentException thrown if the field is a predefined field
     * and the type of the value doesn't match
     */
    public void setField(@NonNull String fieldName, Object value) {
        internalSetField(fieldName, value, false);
        notifyHeaderChanged();
    }

    /**
     * Sets the header values for the specified fields.
     * If there are any type mismatches, those fields will be silently ignored.
     *
     * @param headerData a mapping of header fields to header values
     */
    public void setFields(@NonNull Map<String, Object> headerData) {
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
    public void addChangeListener(@NonNull GameHeaderModelChangeListener listener) {
        this.changeListeners.add(listener);
    }

    /**
     * Removes a listener of header model changes
     * @param listener the listener
     * @return true if the listener was removed
     */
    public boolean removeChangeListener(@NonNull GameHeaderModelChangeListener listener) {
        return this.changeListeners.remove(listener);
    }

    protected void notifyHeaderChanged() {
        for (GameHeaderModelChangeListener changeListener : changeListeners) {
            changeListener.headerModelChanged(this);
        }
    }
}
