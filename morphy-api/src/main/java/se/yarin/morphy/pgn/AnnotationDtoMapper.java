package se.yarin.morphy.pgn;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.morphy.model.AnnotationDto;

/**
 * Turns the annotations of a move into {@link AnnotationDto}s and back.
 *
 * <p>This one knows the annotations plain PGN has: comments and NAGs. A format with annotations of
 * its own extends it, mapping those in {@link #toDto} and {@link #fromDto} and leaving the rest to
 * this class.
 */
public class AnnotationDtoMapper {

  private static final Logger log = LoggerFactory.getLogger(AnnotationDtoMapper.class);

  /** Maps the annotations plain PGN has. */
  public static final @NotNull AnnotationDtoMapper PLAIN = new AnnotationDtoMapper();

  /**
   * The annotations of a move as DTOs, in the order they're in. All the NAGs of the move become one
   * {@link AnnotationDto.Symbols}, where the first of them is.
   *
   * @param move the index of the move, see {@link AnnotationDto#move()}
   * @param annotations the annotations of the move
   */
  public final @NotNull List<AnnotationDto> toDtos(int move, @NotNull Annotations annotations) {
    List<AnnotationDto> dtos = new ArrayList<>();
    List<Integer> nags = new ArrayList<>();
    int symbolsAt = -1;
    for (Annotation annotation : annotations) {
      if (annotation instanceof NAGAnnotation nag) {
        if (symbolsAt < 0) {
          symbolsAt = dtos.size();
        }
        nags.add(nag.getNag().ordinal());
        continue;
      }
      AnnotationDto dto = toDto(move, annotation);
      if (dto == null) {
        log.warn("Annotation {} can't be passed on and is dropped", annotation);
      } else {
        dtos.add(dto);
      }
    }
    if (symbolsAt >= 0) {
      dtos.add(symbolsAt, new AnnotationDto.Symbols(move, nags));
    }
    return dtos;
  }

  /**
   * The annotations of a move from DTOs, in the order they're in.
   *
   * @param dtos the DTOs of the annotations of one move
   * @throws IllegalArgumentException if one of them can't be read
   */
  public final @NotNull Annotations fromDtos(@NotNull List<AnnotationDto> dtos) {
    Annotations annotations = new Annotations();
    for (AnnotationDto dto : dtos) {
      annotations.addAll(fromDto(dto));
    }
    return annotations;
  }

  /**
   * One annotation as a DTO.
   *
   * @return the DTO, or null if the annotation has none
   */
  protected AnnotationDto toDto(int move, @NotNull Annotation annotation) {
    return switch (annotation) {
      case CommentaryAfterMoveAnnotation a -> new AnnotationDto.TextAfter(move, a.getCommentary());
      case CommentaryBeforeMoveAnnotation a ->
          new AnnotationDto.TextBefore(move, a.getCommentary());
      default -> null;
    };
  }

  /**
   * One DTO as annotations; the most kinds of DTO are one annotation, but symbols are a NAG each.
   *
   * @throws IllegalArgumentException if the DTO can't be read
   */
  protected @NotNull List<Annotation> fromDto(@NotNull AnnotationDto dto) {
    return switch (dto) {
      case AnnotationDto.TextAfter t -> List.of(new CommentaryAfterMoveAnnotation(t.text()));
      case AnnotationDto.TextBefore t -> List.of(new CommentaryBeforeMoveAnnotation(t.text()));
      case AnnotationDto.Symbols s -> s.nags().stream().map(n -> (Annotation) nagAnnotation(n)).toList();
      default -> {
        log.warn("Annotation {} can't be kept in PGN and is dropped", dto);
        yield List.of();
      }
    };
  }

  /** The NAG with a number. */
  protected static @NotNull NAG nag(int number) {
    if (number <= 0 || number >= NAG.values().length) {
      throw new IllegalArgumentException("Invalid NAG: " + number);
    }
    return NAG.values()[number];
  }

  private static NAGAnnotation nagAnnotation(int number) {
    return new NAGAnnotation(nag(number));
  }
}
