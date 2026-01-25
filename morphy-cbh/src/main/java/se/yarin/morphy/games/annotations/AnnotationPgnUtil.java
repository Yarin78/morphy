package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for PGN annotation encoding and decoding.
 * These methods are shared by annotation codec classes.
 */
public class AnnotationPgnUtil {

    private AnnotationPgnUtil() {
        // Utility class
    }

    // ========== String Manipulation ==========

    /**
     * Appends text to a StringBuilder with a space separator if the builder is not empty.
     */
    public static void appendWithSpace(@NotNull StringBuilder sb, @Nullable String text) {
        if (text == null || text.isEmpty()) return;
        if (!sb.isEmpty()) {
            sb.append(" ");
        }
        sb.append(text);
    }

    /**
     * Escapes special characters in a string for PGN annotation encoding.
     * Backslashes are escaped first, then quotes, brackets, and curly braces.
     */
    public static String escapeString(@NotNull String s) {
        // Escape backslashes first, then other special characters
        // Replace curly braces with placeholders since PGN comments don't support escaping them
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("]", "\\]")
                .replace("{", "\\<")  // Use \< as placeholder for {
                .replace("}", "\\>");  // Use \> as placeholder for }
    }

    /**
     * Unescapes a string that was escaped with {@link #escapeString(String)}.
     */
    public static String unescapeString(@NotNull String s) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (char c : s.toCharArray()) {
            if (escaped) {
                // Map escape sequences back to original characters
                if (c == '<') {
                    sb.append('{');
                } else if (c == '>') {
                    sb.append('}');
                } else {
                    sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Parses a quoted string, handling escapes.
     * If the string starts and ends with quotes, they are removed and the content is unescaped.
     */
    public static String parseQuotedString(@NotNull String quoted) {
        if (quoted.startsWith("\"") && quoted.endsWith("\"")) {
            return unescapeString(quoted.substring(1, quoted.length() - 1));
        }
        return quoted;
    }

    // ========== Time Formatting ==========

    /**
     * Formats centiseconds as H:MM:SS.
     */
    public static String formatCentisecondsAsTime(int centiseconds) {
        int totalSeconds = centiseconds / 100;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Parses H:MM:SS or M:SS or S format to centiseconds.
     */
    public static int parseTimeToCentiseconds(@NotNull String time) {
        String[] parts = time.split(":");
        int hours = 0, minutes = 0, seconds = 0;

        if (parts.length == 3) {
            hours = Integer.parseInt(parts[0]);
            minutes = Integer.parseInt(parts[1]);
            seconds = Integer.parseInt(parts[2]);
        } else if (parts.length == 2) {
            minutes = Integer.parseInt(parts[0]);
            seconds = Integer.parseInt(parts[1]);
        } else if (parts.length == 1) {
            seconds = Integer.parseInt(parts[0]);
        }

        return ((hours * 3600) + (minutes * 60) + seconds) * 100;
    }

    /**
     * Formats a time control duration (in centiseconds) as a human-readable string.
     * Examples: "90m", "30s", "15m30s"
     */
    public static String formatTimeControlDuration(int centiseconds) {
        int seconds = centiseconds / 100;
        int minutes = seconds / 60;
        seconds %= 60;
        if (minutes > 0 && seconds > 0) {
            return minutes + "m" + seconds + "s";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Parses a time control duration string to centiseconds.
     * Supports formats like "90m", "30s", "15m30s", etc.
     * If no unit markers are present, assumes minutes for backward compatibility.
     */
    public static int parseTimeControlDuration(@NotNull String duration) {
        int total = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([hms])").matcher(duration.toLowerCase());
        while (m.find()) {
            int value = Integer.parseInt(m.group(1));
            switch (m.group(2)) {
                case "h" -> total += value * 3600 * 100;
                case "m" -> total += value * 60 * 100;
                case "s" -> total += value * 100;
            }
        }
        // If no unit markers, assume minutes for backward compatibility
        if (total == 0) {
            try {
                total = Integer.parseInt(duration) * 60 * 100;
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return total;
    }

    // ========== Header Value Serialization ==========

    /**
     * Serializes a header value to a string representation suitable for text encoding.
     */
    public static String serializeHeaderValue(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Integer i) {
            return i.toString();
        }
        if (value instanceof se.yarin.chess.Date d) {
            return d.isUnset() ? "" : d.toString();
        }
        if (value instanceof Eco e) {
            return e.isSet() ? e.toString() : "";
        }
        if (value instanceof GameResult r) {
            return r.toString();
        }
        if (value instanceof NAG n) {
            return n.toString();
        }
        // Fallback for any other type
        return value.toString();
    }

    /**
     * Deserializes a header value from string based on the field name and expected type.
     */
    @Nullable
    public static Object deserializeHeaderValue(@NotNull String fieldName, @NotNull String valueStr) {
        if (valueStr.isEmpty()) {
            return null;
        }

        // Try to determine the type based on field name and parse accordingly
        return switch (fieldName) {
            case "whiteElo", "blackElo", "round", "subRound", "eventCategory", "eventRounds" -> {
                try {
                    yield Integer.parseInt(valueStr);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case "date", "eventDate", "eventEndDate", "sourceDate" -> {
                try {
                    yield parsePgnDate(valueStr);
                } catch (Exception e) {
                    yield null;
                }
            }
            case "eco" -> {
                try {
                    yield new Eco(valueStr);
                } catch (Exception e) {
                    yield null;
                }
            }
            case "result" -> parseGameResult(valueStr);
            case "lineEvaluation" -> {
                try {
                    yield NAG.valueOf(valueStr);
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> valueStr; // Default to String
        };
    }

    /**
     * Parse a PGN date string (YYYY.MM.DD format with ?? for unknown parts).
     */
    @Nullable
    public static se.yarin.chess.Date parsePgnDate(@NotNull String dateStr) {
        String[] parts = dateStr.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        int year = 0, month = 0, day = 0;

        if (!parts[0].equals("????") && !parts[0].isEmpty()) {
            year = Integer.parseInt(parts[0]);
        }
        if (!parts[1].equals("??") && !parts[1].isEmpty()) {
            month = Integer.parseInt(parts[1]);
        }
        if (!parts[2].equals("??") && !parts[2].isEmpty()) {
            day = Integer.parseInt(parts[2]);
        }

        if (year == 0) {
            return null;
        }
        return new se.yarin.chess.Date(year, month, day);
    }

    /**
     * Parse a game result string into a GameResult enum.
     */
    @NotNull
    public static GameResult parseGameResult(@Nullable String resultStr) {
        if (resultStr == null) {
            return GameResult.NOT_FINISHED;
        }
        return switch (resultStr.trim()) {
            case "1-0" -> GameResult.WHITE_WINS;
            case "0-1" -> GameResult.BLACK_WINS;
            case "1/2-1/2", "1/2" -> GameResult.DRAW;
            case "+:-" -> GameResult.WHITE_WINS_ON_FORFEIT;
            case "-:+" -> GameResult.BLACK_WINS_ON_FORFEIT;
            case "=:=" -> GameResult.DRAW_ON_FORFEIT;
            case "0-0" -> GameResult.BOTH_LOST;
            default -> GameResult.NOT_FINISHED;
        };
    }

    /**
     * Parses key="value" pairs from a string.
     * Format: key1="value1" key2="value2" ...
     */
    public static Map<String, String> parseKeyValuePairs(@NotNull String content) {
        Map<String, String> result = new HashMap<>();
        if (content.isEmpty()) {
            return result;
        }

        int i = 0;
        while (i < content.length()) {
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length()) break;

            // Read key (everything until '=')
            StringBuilder key = new StringBuilder();
            while (i < content.length() && content.charAt(i) != '=') {
                key.append(content.charAt(i));
                i++;
            }

            if (i >= content.length() || content.charAt(i) != '=') {
                // Expected '=' after key
                break;
            }
            i++; // Skip '='

            // Expect opening quote
            if (i >= content.length() || content.charAt(i) != '"') {
                // Expected '"' after '='
                break;
            }
            i++; // Skip opening quote

            // Read value (everything until unescaped closing quote)
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            while (i < content.length()) {
                char c = content.charAt(i);
                if (escaped) {
                    value.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    i++; // Skip closing quote
                    break;
                } else {
                    value.append(c);
                }
                i++;
            }

            result.put(key.toString().trim(), value.toString());
        }

        return result;
    }

    // ========== Graphical Annotation Helpers ==========

    /**
     * Converts a GraphicalAnnotationColor to its single-character representation.
     */
    public static String colorToChar(@NotNull GraphicalAnnotationColor color) {
        return switch (color) {
            case GREEN -> "G";
            case RED -> "R";
            case YELLOW -> "Y";
            default -> "G";
        };
    }

    /**
     * Converts a single character to a GraphicalAnnotationColor.
     */
    @Nullable
    public static GraphicalAnnotationColor charToColor(char ch) {
        return switch (ch) {
            case 'G', 'g' -> GraphicalAnnotationColor.GREEN;
            case 'R', 'r' -> GraphicalAnnotationColor.RED;
            case 'Y', 'y' -> GraphicalAnnotationColor.YELLOW;
            default -> null;
        };
    }
}
