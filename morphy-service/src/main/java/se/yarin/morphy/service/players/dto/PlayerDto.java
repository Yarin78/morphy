package se.yarin.morphy.service.players.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;

/** Detailed information about a chess player. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "lastName", "firstName", "gameCount"})
public record PlayerDto(
    Long id, @Nullable String lastName, @Nullable String firstName, @Nullable Integer gameCount) {}
