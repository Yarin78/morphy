package se.yarin.morphy.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.yarin.morphy.convert.AnnotatorDtoConverter;
import se.yarin.morphy.convert.GameDtoConverter;
import se.yarin.morphy.convert.GameDtoImporter;
import se.yarin.morphy.convert.GameTagDtoConverter;
import se.yarin.morphy.convert.PlayerDtoConverter;
import se.yarin.morphy.convert.SourceDtoConverter;
import se.yarin.morphy.convert.TeamDtoConverter;
import se.yarin.morphy.convert.TournamentDtoConverter;

/**
 * Exposes the vendor-neutral DTO converters — which now live in {@code morphy-cbh} — as Spring
 * beans. They stopped being {@code @Component}s when they moved out of the service module, so they
 * are wired up here instead.
 */
@Configuration
public class ConvertersConfig {

  @Bean
  public PlayerDtoConverter playerDtoConverter() {
    return new PlayerDtoConverter();
  }

  @Bean
  public AnnotatorDtoConverter annotatorDtoConverter() {
    return new AnnotatorDtoConverter();
  }

  @Bean
  public SourceDtoConverter sourceDtoConverter() {
    return new SourceDtoConverter();
  }

  @Bean
  public TeamDtoConverter teamDtoConverter() {
    return new TeamDtoConverter();
  }

  @Bean
  public TournamentDtoConverter tournamentDtoConverter() {
    return new TournamentDtoConverter();
  }

  @Bean
  public GameTagDtoConverter gameTagDtoConverter() {
    return new GameTagDtoConverter();
  }

  @Bean
  public GameDtoConverter gameDtoConverter(
      PlayerDtoConverter playerDtoConverter,
      TournamentDtoConverter tournamentDtoConverter,
      AnnotatorDtoConverter annotatorDtoConverter,
      SourceDtoConverter sourceDtoConverter,
      TeamDtoConverter teamDtoConverter,
      GameTagDtoConverter gameTagDtoConverter) {
    return new GameDtoConverter(
        playerDtoConverter,
        tournamentDtoConverter,
        annotatorDtoConverter,
        sourceDtoConverter,
        teamDtoConverter,
        gameTagDtoConverter);
  }

  @Bean
  public GameDtoImporter gameDtoImporter() {
    return new GameDtoImporter();
  }
}
