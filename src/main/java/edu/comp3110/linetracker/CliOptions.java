package edu.comp3110.linetracker;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Command-line settings, parsed completely before reading or writing files. */
record CliOptions(
    Path oldPath, Path newPath, boolean advanced, boolean json, Path output, MatchConfig config) {
  static CliOptions parse(String[] args) {
    if (args.length < 3 || !args[0].equals("map"))
      throw new IllegalArgumentException("Expected map OLD NEW followed by options");
    boolean advanced = false;
    boolean json = false;
    Path output = null;
    long cells = MatchConfig.DEFAULT.maxLcsCells();
    int candidates = MatchConfig.DEFAULT.candidateLimit();
    int radius = MatchConfig.DEFAULT.contextRadius();
    double score = MatchConfig.DEFAULT.minimumScore();
    Set<String> seen = new HashSet<>();
    for (int i = 3; i < args.length; i++) {
      String option = args[i];
      if (!seen.add(option)) throw new IllegalArgumentException("Duplicate option: " + option);
      if (option.equals("--advanced")) {
        advanced = true;
        continue;
      }
      if (!Set.of(
              "--format",
              "--output",
              "--max-lcs-cells",
              "--candidate-limit",
              "--context-radius",
              "--minimum-score")
          .contains(option)) throw new IllegalArgumentException("Unknown option: " + option);
      if (++i == args.length || args[i].startsWith("--"))
        throw new IllegalArgumentException("Missing value for " + option);
      String value = args[i];
      try {
        switch (option) {
          case "--format" -> {
            if (!value.equals("text") && !value.equals("json"))
              throw new IllegalArgumentException("Format must be text or json");
            json = value.equals("json");
          }
          case "--output" -> output = Path.of(value);
          case "--max-lcs-cells" -> cells = Long.parseLong(value);
          case "--candidate-limit" -> candidates = Integer.parseInt(value);
          case "--context-radius" -> radius = Integer.parseInt(value);
          case "--minimum-score" -> score = Double.parseDouble(value);
          default -> throw new IllegalArgumentException("Unknown option: " + option);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid numeric value for " + option + ": " + value, e);
      }
    }
    if (!advanced
        && (seen.contains("--candidate-limit")
            || seen.contains("--context-radius")
            || seen.contains("--minimum-score")))
      throw new IllegalArgumentException("Advanced settings require --advanced");
    return new CliOptions(
        Path.of(args[1]),
        Path.of(args[2]),
        advanced,
        json,
        output,
        new MatchConfig(cells, candidates, radius, score));
  }
}
