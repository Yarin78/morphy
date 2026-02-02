package se.yarin.morphy.service.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import se.yarin.chess.Date;

/**
 * Jackson configuration for JSON serialization.
 *
 * <p>This configuration uses mix-ins to control serialization of classes from the core morphy-cbh
 * library without adding Jackson dependencies to that module.
 */
@Configuration
public class JacksonConfig {

  /** Mix-in interface for se.yarin.chess.Date to ignore the isUnset() method in JSON. */
  private interface DateMixin {
    @JsonIgnore
    boolean isUnset();
  }

  @Bean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    ObjectMapper mapper = builder.build();
    // Add mix-in to ignore isUnset() method on Date class
    mapper.addMixIn(Date.class, DateMixin.class);
    return mapper;
  }
}
