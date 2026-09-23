package se.yarin.morphy.api;

/**
 * Controls how much of a game a {@link Database} materialises into a {@link
 * se.yarin.morphy.model.GameDto}.
 *
 * <p>Listing many games wants the header only; showing a single game wants everything. The "detail"
 * flags decide whether nested entity DTOs (tournament, source, team) carry their full fields or just
 * an id and name.
 *
 * @param includeMoves include the moves as PGN
 * @param includeText include the guiding-text body
 * @param includeTournamentDetails full tournament fields (dates, place, category, …)
 * @param includeSourceDetails full source fields (publisher, date, …)
 * @param includeTeamDetails full team fields (number, season, year, nation)
 * @param includeRawData include the raw storage bytes of the record (only where {@link
 *     Capabilities#hasRawData()})
 */
public record GameFetchOptions(
    boolean includeMoves,
    boolean includeText,
    boolean includeTournamentDetails,
    boolean includeSourceDetails,
    boolean includeTeamDetails,
    boolean includeRawData) {

  /** Header only: no moves, text, entity details or raw bytes. Cheapest; good for listings. */
  public static GameFetchOptions headersOnly() {
    return new GameFetchOptions(false, false, false, false, false, false);
  }

  /** Everything except raw bytes: moves, text and full entity details. Good for a single game. */
  public static GameFetchOptions full() {
    return new GameFetchOptions(true, true, true, true, true, false);
  }

  public GameFetchOptions withMoves(boolean value) {
    return new GameFetchOptions(
        value,
        includeText,
        includeTournamentDetails,
        includeSourceDetails,
        includeTeamDetails,
        includeRawData);
  }

  public GameFetchOptions withText(boolean value) {
    return new GameFetchOptions(
        includeMoves,
        value,
        includeTournamentDetails,
        includeSourceDetails,
        includeTeamDetails,
        includeRawData);
  }

  public GameFetchOptions withRawData(boolean value) {
    return new GameFetchOptions(
        includeMoves,
        includeText,
        includeTournamentDetails,
        includeSourceDetails,
        includeTeamDetails,
        value);
  }
}
