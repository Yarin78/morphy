package se.yarin.morphy.games.annotations;

import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.morphy.games.Medal;

import java.util.EnumSet;

/**
 * Represents statistics over annotations in a game.
 * This is a mutable data structure. It's updated by annotation
 * implementing the {@link StatisticalAnnotation} interface.
 */
public class AnnotationStatistics {
    EnumSet<Medal> medals = EnumSet.noneOf(Medal.class);

    EnumSet<GameHeaderFlags> flags = EnumSet.noneOf(GameHeaderFlags.class);

    int commentariesLength; // Number of characters of commentaries
    int noSymbols;
    int noGraphicalSquares;
    int noGraphicalArrows;
    int noTraining;
    int noTimeSpent;

    public EnumSet<Medal> getMedals() {
        return medals;
    }

    public EnumSet<GameHeaderFlags> getFlags() {
        return flags;
    }

    public int getCommentariesLength() {
        return commentariesLength;
    }

    // TODO: All these boundaries need to be double checked

    public int getCommentariesMagnitude() {
        if (commentariesLength > 200) return 2;
        if (commentariesLength > 0) return 1;
        return 0;
    }

    public int getSymbolsMagnitude() {
        if (noSymbols >= 10) return 2;
        if (noSymbols > 0) return 1;
        return 0;
    }

    public int getGraphicalSquaresMagnitude() {
        if (noGraphicalSquares >= 10) return 2;
        if (noGraphicalSquares > 0) return 1;
        return 0;
    }

    public int getGraphicalArrowsMagnitude() {
        if (noGraphicalArrows >= 6) return 2;
        if (noGraphicalArrows > 0) return 1;
        return 0;
    }

    public int getTrainingMagnitude() {
        if (noTraining > 10) return 2;
        if (noTraining > 0) return 1;
        return 0;
    }

    public int getTimeSpentMagnitude() {
        if (noTimeSpent > 10) return 2;
        if (noTimeSpent > 0) return 1;
        return 0;
    }
}
