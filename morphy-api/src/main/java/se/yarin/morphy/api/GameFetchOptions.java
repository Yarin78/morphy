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
 * @param includeRawData include the raw storage bytes of the record (only where {@link
 *     Capabilities#hasRawData()})
 */
public record GameFetchOptions(
    boolean includeMoves,
    boolean includeText,
    boolean includeEntityDetails,
    boolean includeRawData) {

  /** Header only: no moves, text, entity details or raw bytes. Cheapest; good for listings. */
  public static GameFetchOptions headersOnly() {
    return new GameFetchOptions(false, false, false, false);
  }

  /** Everything except raw bytes: moves, text and full entity details. Good for a single game. */
  public static GameFetchOptions full() {
    return new GameFetchOptions(true, true, true, false);
  }

  public GameFetchOptions withMoves(boolean value) {
    return new GameFetchOptions(value, includeText, includeEntityDetails, includeRawData);
  }

  public GameFetchOptions withText(boolean value) {
    return new GameFetchOptions(includeMoves, value, includeEntityDetails, includeRawData);
  }

  public GameFetchOptions withEntityDetails(boolean value) {
    return new GameFetchOptions(includeMoves, includeText, value, includeRawData);
  }

  public GameFetchOptions withRawData(boolean value) {
    return new GameFetchOptions(includeMoves, includeText, includeEntityDetails, value);
  }
}
