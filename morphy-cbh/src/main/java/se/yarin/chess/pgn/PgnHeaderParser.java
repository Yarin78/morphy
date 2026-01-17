package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameResult;

/**
 * Parser for PGN tag pairs (header section).
 * Converts PGN tags into GameHeaderModel fields.
 */
public class PgnHeaderParser {

    private final GameHeaderModel header = new GameHeaderModel();

    /**
     * Parses a single tag pair into the header model.
     *
     * @param tagName the name of the tag
     * @param tagValue the value of the tag (without quotes)
     * @throws PgnFormatException if the tag value is invalid for the tag type
     */
    public void parseTag(@NotNull String tagName, @NotNull String tagValue) throws PgnFormatException {
        // Map standard PGN tags to GameHeaderModel fields
        switch (tagName) {
            case "Event" -> header.setEvent(tagValue);
            case "Site" -> header.setEventSite(tagValue);
            case "Date" -> header.setDate(parseDate(tagValue));
            case "Round" -> parseRound(tagValue);
            case "White" -> header.setWhite(tagValue);
            case "Black" -> header.setBlack(tagValue);
            case "Result" -> header.setResult(parseResult(tagValue));
            case "WhiteElo" -> header.setWhiteElo(parseInteger(tagValue, tagName));
            case "BlackElo" -> header.setBlackElo(parseInteger(tagValue, tagName));
            case "ECO" -> header.setEco(parseEco(tagValue));
            case "Annotator" -> header.setAnnotator(tagValue);
            case "EventDate" -> header.setEventDate(parseDate(tagValue));
            case "EventCountry" -> header.setEventCountry(tagValue);
            case "TimeControl" -> header.setEventTimeControl(tagValue);
            case "WhiteTeam" -> header.setWhiteTeam(tagValue);
            case "BlackTeam" -> header.setBlackTeam(tagValue);
            case "Source" -> header.setSource(tagValue);
            case "SourceTitle" -> header.setSourceTitle(tagValue);
            case "SourceDate" -> header.setSourceDate(parseDate(tagValue));
            case "EventType" -> header.setEventType(tagValue);
            case "EventRounds" -> header.setEventRounds(parseInteger(tagValue, tagName));
            case "EventCategory" -> header.setEventCategory(parseInteger(tagValue, tagName));

            // SetUp and FEN are special - they're handled separately during game creation
            case "SetUp", "FEN" -> {
                // Store as custom field for later use
                header.setField(tagName, tagValue);
            }

            default -> {
                // Store unknown tags as custom fields
                header.setField(tagName, tagValue);
            }
        }
    }

    /**
     * @return the parsed header model
     */
    @NotNull
    public GameHeaderModel getHeader() {
        return header;
    }

    /**
     * Parses a PGN date string (YYYY.MM.DD format, with ?? for unknown parts).
     */
    private Date parseDate(String value) {
        if (value == null || value.equals("????.??.??")) {
            return Date.unset();
        }

        String[] parts = value.split("\\.");
        if (parts.length != 3) {
            // Invalid format, but be lenient
            return Date.unset();
        }

        int year = parseIntOrZero(parts[0].replace("?", "0"));
        int month = parseIntOrZero(parts[1].replace("?", "0"));
        int day = parseIntOrZero(parts[2].replace("?", "0"));

        return new Date(year, month, day);
    }

    /**
     * Parses a game result.
     */
    private GameResult parseResult(String value) throws PgnFormatException {
        return switch (value) {
            case "1-0" -> GameResult.WHITE_WINS;
            case "0-1" -> GameResult.BLACK_WINS;
            case "1/2-1/2" -> GameResult.DRAW;
            case "*" -> GameResult.NOT_FINISHED;
            case "+:-" -> GameResult.WHITE_WINS_ON_FORFEIT;
            case "-:+" -> GameResult.BLACK_WINS_ON_FORFEIT;
            case "=:=" -> GameResult.DRAW_ON_FORFEIT;
            case "0-0" -> GameResult.BOTH_LOST;
            default -> throw new PgnFormatException("Invalid result: " + value);
        };
    }

    /**
     * Parses an integer value.
     */
    private int parseInteger(String value, String tagName) throws PgnFormatException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new PgnFormatException("Invalid integer value for " + tagName + ": " + value, e);
        }
    }

    /**
     * Parses an integer or returns 0 if invalid.
     */
    private int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Parses an ECO code.
     */
    private Eco parseEco(String value) {
        try {
            return new Eco(value);
        } catch (IllegalArgumentException e) {
            // Invalid ECO code, return unset
            return Eco.unset();
        }
    }

    /**
     * Parses a round value. The round may be numeric (e.g., "5") or include
     * a sub-round (e.g., "5.2").
     */
    private void parseRound(String value) {
        String[] parts = value.split("\\.");
        if (parts.length == 0) {
            return;
        }

        try {
            int round = Integer.parseInt(parts[0]);
            header.setRound(round);

            if (parts.length >= 2) {
                int subRound = Integer.parseInt(parts[1]);
                header.setSubRound(subRound);
            }
        } catch (NumberFormatException e) {
            // Can't parse round, just ignore
        }
    }
}
