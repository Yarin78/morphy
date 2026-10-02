package se.yarin.morphy.pgn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.morphy.model.AnnotationDto;
import se.yarin.morphy.model.GameMovesDto;

public class GameMovesDtoCodecTest {

  private static final String MOVES = "1. e4 c5 (1... c6 2. d4) 2. Nf3";

  @Test
  public void movesAreNumberedInTheOrderOfTheMovetext() {
    GameMovesModel moves = PgnMoves.PLAIN.fromPgn(MOVES, null, false);
    List<String> sans =
        moves.getAllNodesPgnOrder().stream().map(n -> n.lastMove().toSAN()).toList();
    assertEquals(List.of("e4", "c5", "c6", "d4", "Nf3"), sans);
  }

  @Test
  public void annotationsArePassedOnNextToPlainMovetext() {
    GameMovesModel moves = PgnMoves.PLAIN.fromPgn(MOVES, null, false);
    moves.root().addAnnotation(new CommentaryAfterMoveAnnotation("intro"));
    List<GameMovesModel.Node> nodes = moves.getAllNodesPgnOrder();
    nodes.get(0).addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
    nodes.get(0).addAnnotation(new NAGAnnotation(NAG.WHITE_SLIGHT_ADVANTAGE));
    nodes.get(2).addAnnotation(new CommentaryBeforeMoveAnnotation("or"));
    nodes.get(3).addAnnotation(new CommentaryAfterMoveAnnotation("solid"));

    GameMovesDto dto = GameMovesDtoCodec.PLAIN.toDto(moves);

    assertEquals(MOVES, dto.pgn());
    assertEquals(
        List.of(
            new AnnotationDto.TextAfter(-1, "intro"),
            new AnnotationDto.Symbols(0, List.of(1, 14)),
            new AnnotationDto.TextBefore(2, "or"),
            new AnnotationDto.TextAfter(3, "solid")),
        dto.annotations());

    GameMovesModel back = GameMovesDtoCodec.PLAIN.fromDto(dto, false);
    assertEquals(moves.toString(), back.toString());
  }

  @Test
  public void anAnnotationOfAMoveThatIsntThereIsRejected() {
    GameMovesDto dto =
        new GameMovesDto("1. e4", null, List.of(new AnnotationDto.TextAfter(1, "where?")));
    assertThrows(
        IllegalArgumentException.class, () -> GameMovesDtoCodec.PLAIN.fromDto(dto, false));
  }
}
