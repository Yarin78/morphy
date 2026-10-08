package se.yarin.morphy.service.positions;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The position indexes: listing them, searching their games by position, and building them. */
@RestController
@RequestMapping("/api/position-indexes")
public class PositionsController {
  private final PositionsService positionsService;

  public PositionsController(PositionsService positionsService) {
    this.positionsService = positionsService;
  }

  /** The indexes, as defined, and whether each can be searched. */
  @GetMapping
  public ResponseEntity<List<PositionIndexInfo>> list() {
    return ResponseEntity.ok(positionsService.list());
  }

  /** An index, and whether it can be searched. */
  @GetMapping("/{indexId}")
  public ResponseEntity<PositionIndexInfo> info(@PathVariable String indexId) {
    return ResponseEntity.ok(positionsService.info(indexId));
  }

  /**
   * A page of the games of an index that reached a position, sorted as asked; the first page also
   * tells what was played from it.
   *
   * @param fen the position
   * @param sortBy the order: {@code +} or {@code -} and one of {@link PositionsService#SORT_FIELDS};
   *     by default the most relevant first (strong players, recent games)
   */
  @GetMapping("/{indexId}/search")
  public ResponseEntity<PositionSearchResponse> search(
      @PathVariable String indexId,
      @RequestParam String fen,
      @RequestParam(defaultValue = "-relevance") String sortBy,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "false") boolean includeMoves) {
    return ResponseEntity.ok(
        positionsService.search(indexId, fen, sortBy, offset, limit, includeMoves));
  }

  /** Starts building an index in the background; its status tells how it goes. */
  @PostMapping("/{indexId}/build")
  public ResponseEntity<PositionIndexInfo> build(@PathVariable String indexId) {
    positionsService.build(indexId);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(positionsService.info(indexId));
  }

  /**
   * Starts adding the games added to an index's database since it was built or last updated, in
   * the background; its status tells how it goes.
   */
  @PostMapping("/{indexId}/update")
  public ResponseEntity<PositionIndexInfo> update(@PathVariable String indexId) {
    positionsService.update(indexId);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(positionsService.info(indexId));
  }
}
