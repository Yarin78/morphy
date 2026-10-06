package se.yarin.morphy.service.positions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The games of a reference database by position. */
@RestController
@RequestMapping("/api/databases/{databaseId}/positions")
public class PositionsController {
  private final PositionsService positionsService;

  public PositionsController(PositionsService positionsService) {
    this.positionsService = positionsService;
  }

  /**
   * A page of the games that reached a position, sorted as asked; the first page also tells what
   * was played from it.
   *
   * @param fen the position
   * @param sortBy the order: {@code +} or {@code -} and one of {@link PositionsService#SORT_FIELDS}
   */
  @GetMapping("/search")
  public ResponseEntity<PositionSearchResponse> search(
      @PathVariable String databaseId,
      @RequestParam String fen,
      @RequestParam(defaultValue = "+id") String sortBy,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "false") boolean includeMoves) {
    return ResponseEntity.ok(
        positionsService.search(databaseId, fen, sortBy, offset, limit, includeMoves));
  }
}
