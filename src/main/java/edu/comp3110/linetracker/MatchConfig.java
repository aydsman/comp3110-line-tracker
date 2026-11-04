package edu.comp3110.linetracker;

public record MatchConfig(long maxLcsCells) {
  public static final MatchConfig DEFAULT = new MatchConfig(4_000_000);

  public MatchConfig {
    if (maxLcsCells < 1) throw new IllegalArgumentException("LCS cell limit must be positive");
  }
}
