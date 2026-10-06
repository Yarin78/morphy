package se.yarin.morphy.api;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameMovesModel;

/**
 * A game as a {@link GameScan} reads it: its moves, without annotations, and its facts.
 *
 * @param id the game id
 * @param moves the moves; only the main line is looked at when indexing
 * @param facts the header facts
 */
public record ScannedGame(int id, @NotNull GameMovesModel moves, @NotNull GameFacts facts) {}
