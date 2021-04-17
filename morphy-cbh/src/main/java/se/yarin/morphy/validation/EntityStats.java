package se.yarin.morphy.validation;

import java.util.HashMap;
import java.util.Map;

public class EntityStats {
    public static class Stats {
        private int count, firstGameId;

        public Stats(int count, int firstGameId) {
            this.count = count;
            this.firstGameId = firstGameId;
        }

        public int getCount() {
            return count;
        }

        public int getFirstGameId() {
            return firstGameId;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public void setFirstGameId(int firstGameId) {
            this.firstGameId = firstGameId;
        }
    }

    public Map<Integer, Stats> players = new HashMap<>();
    public Map<Integer, Stats> tournaments = new HashMap<>();
    public Map<Integer, Stats> annotators = new HashMap<>();
    public Map<Integer, Stats> sources = new HashMap<>();
    public Map<Integer, Stats> teams = new HashMap<>();
    public Map<Integer, Stats> gameTags = new HashMap<>();
}
