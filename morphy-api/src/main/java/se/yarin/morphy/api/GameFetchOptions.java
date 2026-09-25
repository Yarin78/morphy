package se.yarin.morphy.api;

/**
 * Controls how much of a game a {@link Database} materialises into a {@link
 * se.yarin.morphy.model.GameDto}.
 *
 * <p>Listing many games wants the header only; showing a single game wants everything.
 *
 * @param includeMoves include the moves as PGN
 * @param includeText include the body of a guiding text
 * @param includeEntityDetails give the tournament, source and teams all their fields rather than
 *     just an id and a name
 */
public record GameFetchOptions(
    boolean includeMoves, boolean includeText, boolean includeEntityDetails) {

  /** Header only: no moves, text or entity details. Cheapest; good for listings. */
  public static GameFetchOptions headersOnly() {
    return new GameFetchOptions(false, false, false);
  }

  /** Everything: moves, text and full entity details. Good for a single game. */
  public static GameFetchOptions full() {
    return new GameFetchOptions(true, true, true);
  }

  public GameFetchOptions withMoves(boolean value) {
    return new GameFetchOptions(value, includeText, includeEntityDetails);
  }

  public GameFetchOptions withText(boolean value) {
    return new GameFetchOptions(includeMoves, value, includeEntityDetails);
  }

  public GameFetchOptions withEntityDetails(boolean value) {
    return new GameFetchOptions(includeMoves, includeText, value);
  }
}
