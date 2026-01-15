package se.yarin.morphy.validation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityStats {
  public Map<Integer, List<Integer>> players = new HashMap<>();
  public Map<Integer, List<Integer>> tournaments = new HashMap<>();
  public Map<Integer, List<Integer>> annotators = new HashMap<>();
  public Map<Integer, List<Integer>> sources = new HashMap<>();
  public Map<Integer, List<Integer>> teams = new HashMap<>();
  public Map<Integer, List<Integer>> gameTags = new HashMap<>();
}
