package se.yarin.morphy.service.games;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.model.AnnotationDto;
import se.yarin.morphy.model.GameMovesDto;

/** The moves of a game and their annotations as JSON, the way the frontend gets and sends them. */
class GameMovesJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static final GameMovesDto MOVES =
      new GameMovesDto(
          "1. e4 c5 (1... c6 2. d4) 2. Nf3",
          null,
          List.of(
              new AnnotationDto.TextAfter(-1, "Intro", "ENG", null),
              new AnnotationDto.Symbols(0, List.of(1)),
              new AnnotationDto.Squares(
                  2, List.of(new AnnotationDto.ColoredSquare("red", "d5"))),
              new AnnotationDto.Arrows(
                  3, List.of(new AnnotationDto.ColoredArrow("blue", "e2", "e4"))),
              new AnnotationDto.WhiteClock(4, 543210),
              new AnnotationDto.Quotation(
                  4, Map.of("white", "Carlsen, Magnus"), "1. e4", null, null),
              new AnnotationDto.Raw(4, 0x1A, new byte[] {1, 2, 3}, false)));

  @Test
  void annotationsHaveTheirKindAsType() throws Exception {
    JsonNode json = mapper.readTree(mapper.writeValueAsString(MOVES));

    assertEquals("1. e4 c5 (1... c6 2. d4) 2. Nf3", json.get("pgn").asText());
    JsonNode annotations = json.get("annotations");
    assertEquals(
        mapper.readTree(
            """
            {"type":"textAfter","move":-1,"text":"Intro","language":"ENG"}
            """),
        annotations.get(0));
    assertEquals(
        mapper.readTree(
            """
            {"type":"arrows","move":3,"arrows":[{"color":"blue","from":"e2","to":"e4"}]}
            """),
        annotations.get(3));
    assertEquals(
        mapper.readTree(
            """
            {"type":"raw","move":4,"annotationType":26,"data":"AQID","invalid":false}
            """),
        annotations.get(6));
  }

  @Test
  void readsBackEqual() throws Exception {
    GameMovesDto back = mapper.readValue(mapper.writeValueAsString(MOVES), GameMovesDto.class);

    assertEquals(MOVES.pgn(), back.pgn());
    assertEquals(MOVES.annotations().subList(0, 6), back.annotations().subList(0, 6));
    assertArrayEquals(
        ((AnnotationDto.Raw) MOVES.annotations().get(6)).data(),
        ((AnnotationDto.Raw) back.annotations().get(6)).data());
  }

  @Test
  void movesWithoutAnnotationsCanLeaveThemOut() throws Exception {
    GameMovesDto moves = mapper.readValue("{\"pgn\":\"1. e4\"}", GameMovesDto.class);

    assertEquals(List.of(), moves.annotations());
  }
}
