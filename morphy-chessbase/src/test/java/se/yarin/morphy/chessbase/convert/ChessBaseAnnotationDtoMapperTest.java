package se.yarin.morphy.chessbase.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.yarin.chess.Chess;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.morphy.chessbase.Medal;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.annotations.CriticalPositionAnnotation;
import se.yarin.morphy.chessbase.annotations.GameQuotationAnnotation;
import se.yarin.morphy.chessbase.annotations.GraphicalAnnotationColor;
import se.yarin.morphy.chessbase.annotations.ImmutableArrow;
import se.yarin.morphy.chessbase.annotations.ImmutableBlackClockAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableComputerEvaluationAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableCorrespondenceMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableCriticalPositionAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableGraphicalArrowsAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableGraphicalSquaresAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableInvalidAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableMedalAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutablePawnStructureAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutablePiecePathAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableSquare;
import se.yarin.morphy.chessbase.annotations.ImmutableSymbolAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTextBeforeMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTimeSpentAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableTrainingAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableUnknownAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableVariationColorAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableVideoStreamTimeAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableWebLinkAnnotation;
import se.yarin.morphy.chessbase.annotations.ImmutableWhiteClockAnnotation;
import se.yarin.morphy.model.AnnotationDto;
import se.yarin.morphy.model.GameMovesDto;
import se.yarin.morphy.pgn.PgnMoves;

/** Every ChessBase annotation becomes a DTO and back without losing anything. */
class ChessBaseAnnotationDtoMapperTest {

  private static final ChessBaseAnnotationDtoMapper MAPPER = ChessBaseAnnotationDtoMapper.INSTANCE;

  private static List<Annotation> everyKind() {
    GameHeaderModel quotedHeader = new GameHeaderModel();
    quotedHeader.setWhite("Carlsen, Magnus");
    quotedHeader.setBlack("Caruana, Fabiano");
    GameMovesModel quotedMoves = PgnMoves.PLAIN.fromPgn("1. e4 e5 2. Nf3", null, false);

    return List.of(
        ImmutableTextBeforeMoveAnnotation.builder()
            .text("Before \\ [%csl] { }")
            .language(Nation.GERMANY)
            .build(),
        ImmutableTextAfterMoveAnnotation.builder().text("After").unknown(64).build(),
        ImmutableTextAfterMoveAnnotation.builder().text("Efter").language(Nation.SWEDEN).build(),
        ImmutableSymbolAnnotation.of(NAG.DUBIOUS_MOVE, NAG.NONE, NAG.WHITE_SLIGHT_ADVANTAGE),
        ImmutableGraphicalSquaresAnnotation.of(
            List.of(
                ImmutableSquare.of(GraphicalAnnotationColor.BLUE, Chess.strToSqi("d5")),
                ImmutableSquare.of(GraphicalAnnotationColor.UNKNOWN_5, Chess.strToSqi("h8")))),
        ImmutableGraphicalArrowsAnnotation.of(
            List.of(
                ImmutableArrow.of(
                    GraphicalAnnotationColor.ORANGE, Chess.strToSqi("e2"), Chess.strToSqi("e4")))),
        ImmutableWhiteClockAnnotation.of(543217),
        ImmutableBlackClockAnnotation.of(12),
        ImmutableTimeSpentAnnotation.of(1, 2, 3, 4),
        ImmutableComputerEvaluationAnnotation.of(-35, 3, 22),
        ImmutableCriticalPositionAnnotation.of(
            CriticalPositionAnnotation.CriticalPositionType.MIDDLEGAME),
        ImmutableMedalAnnotation.of(EnumSet.of(Medal.NOVELTY, Medal.TACTICS)),
        ImmutablePawnStructureAnnotation.of(3),
        ImmutablePiecePathAnnotation.of(3, Chess.strToSqi("g1")),
        ImmutableVariationColorAnnotation.of(255, 128, 0, true, false),
        ImmutableVideoStreamTimeAnnotation.of(1234),
        ImmutableWebLinkAnnotation.of("https://example.com/?a=1", "A link"),
        new GameQuotationAnnotation(new GameModel(quotedHeader, quotedMoves), 7),
        new GameQuotationAnnotation(quotedHeader),
        ImmutableTrainingAnnotation.of(new byte[] {1, 2, 3}),
        ImmutableCorrespondenceMoveAnnotation.of(new byte[] {4}),
        ImmutableUnknownAnnotation.of(0x1A, new byte[] {8, 9}),
        ImmutableInvalidAnnotation.of(0x02, new byte[] {10}));
  }

  @Test
  void everyKindOfAnnotationReadsBackEqual() {
    Annotations annotations = new Annotations(everyKind());

    List<AnnotationDto> dtos = MAPPER.toDtos(3, annotations);

    assertEquals(annotations.size(), dtos.size());
    dtos.forEach(dto -> assertEquals(3, dto.move()));
    assertEquals(annotations, MAPPER.fromDtos(dtos));
  }

  @Test
  void valuesAreNamed() {
    List<AnnotationDto> dtos = MAPPER.toDtos(0, new Annotations(everyKind()));

    assertEquals(new AnnotationDto.TextBefore(0, "Before \\ [%csl] { }", "GER", null), dtos.get(0));
    assertEquals(new AnnotationDto.TextAfter(0, "After", null, 64), dtos.get(1));
    assertEquals(new AnnotationDto.Symbols(0, List.of(6, 14)), dtos.get(3));
    assertEquals(
        new AnnotationDto.Squares(
            0,
            List.of(
                new AnnotationDto.ColoredSquare("blue", "d5"),
                new AnnotationDto.ColoredSquare("unknown_5", "h8"))),
        dtos.get(4));
    assertEquals(
        new AnnotationDto.Arrows(0, List.of(new AnnotationDto.ColoredArrow("orange", "e2", "e4"))),
        dtos.get(5));
    assertEquals(new AnnotationDto.CriticalPosition(0, "middlegame"), dtos.get(10));
    assertEquals(new AnnotationDto.Medals(0, List.of("NOVELTY", "TACTICS")), dtos.get(11));
    assertEquals(new AnnotationDto.VariationColor(0, "#ff8000", true, false), dtos.get(14));
    AnnotationDto.Quotation quotation = (AnnotationDto.Quotation) dtos.get(17);
    assertEquals("Carlsen, Magnus", quotation.header().get("white"));
    assertEquals("1. e4 e5 2. Nf3", quotation.moves());
  }

  @Test
  void movesAreSentWithTheirAnnotationsNextToThem() {
    GameMovesModel moves = PgnMoves.PLAIN.fromPgn("1. e4 c5 (1... c6 2. d4) 2. Nf3", null, false);
    List<GameMovesModel.Node> nodes = moves.getAllNodesPgnOrder();
    nodes.get(3).addAnnotation(ImmutableWhiteClockAnnotation.of(100));
    moves.root().addAnnotation(ImmutableTextAfterMoveAnnotation.builder().text("Intro").build());

    GameMovesDto dto = GameMovesDtos.toDto(moves);

    assertEquals("1. e4 c5 (1... c6 2. d4) 2. Nf3", dto.pgn());
    assertEquals(
        List.of(
            new AnnotationDto.TextAfter(AnnotationDto.GAME, "Intro"),
            new AnnotationDto.WhiteClock(3, 100)),
        dto.annotations());
    assertEquals(moves.toString(), GameMovesDtos.fromDto(dto, false).toString());
  }

  @Test
  void pgnCommentsBecomeAnnotationsAndBack() {
    String movetext = "1. e4 { [%csl Gd4,Re5] Good } 1... e5 $2";
    GameMovesModel moves = PgnMoves.PLAIN.fromPgn(movetext, null, false);

    GameMovesDto dto = GameMovesDtos.PGN.toDto(moves);

    assertEquals("1. e4 e5", dto.pgn());
    assertEquals(
        List.of(
            new AnnotationDto.Squares(
                0,
                List.of(
                    new AnnotationDto.ColoredSquare("green", "d4"),
                    new AnnotationDto.ColoredSquare("red", "e5"))),
            new AnnotationDto.TextAfter(0, "Good"),
            new AnnotationDto.Symbols(1, List.of(2))),
        dto.annotations());
    assertEquals(
        PgnMoves.PLAIN.toPgn(moves),
        PgnMoves.PLAIN.toPgn(GameMovesDtos.PGN.fromDto(dto, false)));
  }

  @Test
  void badValuesAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MAPPER.fromDtos(
                List.of(
                    new AnnotationDto.Squares(
                        0, List.of(new AnnotationDto.ColoredSquare("pink", "d4"))))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MAPPER.fromDtos(
                List.of(
                    new AnnotationDto.Squares(
                        0, List.of(new AnnotationDto.ColoredSquare("red", "z9"))))));
    assertThrows(
        IllegalArgumentException.class,
        () -> MAPPER.fromDtos(List.of(new AnnotationDto.TextAfter(0, "x", "XYZ", null))));
  }
}
