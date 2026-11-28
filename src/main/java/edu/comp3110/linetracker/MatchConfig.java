package edu.comp3110.linetracker;

public record MatchConfig(
    long maxLcsCells, int candidateLimit, int contextRadius, double minimumScore) {
  public static final MatchConfig DEFAULT = new MatchConfig(4_000_000);

  public MatchConfig(long maxLcsCells) {
    this(maxLcsCells, 15, 4, 0.65);
  }

  public MatchConfig {
    if (maxLcsCells < 1) throw new IllegalArgumentException("LCS cell limit must be positive");
    if (candidateLimit < 1) throw new IllegalArgumentException("Candidate limit must be positive");
    if (contextRadius < 0) throw new IllegalArgumentException("Context radius must be nonnegative");
    if (!Double.isFinite(minimumScore) || minimumScore <= 0 || minimumScore > 1)
      throw new IllegalArgumentException("Minimum score must be in (0, 1]");
  }
}
