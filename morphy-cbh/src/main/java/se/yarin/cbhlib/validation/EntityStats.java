package se.yarin.cbhlib.validation;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

public class EntityStats {
    @AllArgsConstructor
    @Data
    public static class Stats {
        private int count, firstGameId;
    }

    public Map<Integer, Stats> players = new HashMap<>();
    public Map<Integer, Stats> tournaments = new HashMap<>();
    public Map<Integer, Stats> annotators = new HashMap<>();
    public Map<Integer, Stats> sources = new HashMap<>();
    public Map<Integer, Stats> teams = new HashMap<>();
    public Map<Integer, Stats> gameTags = new HashMap<>();

}
