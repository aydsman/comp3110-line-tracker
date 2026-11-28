package edu.comp3110.linetracker;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** Shared versioned JSON export for validated mapping results. */
public final class JsonMappingFormatter {
  private JsonMappingFormatter() {}

  /**
   * Formats prediction groups, unmatched lines and requested settings without modifying the result.
   * Only validated model numbers and fixed enum names are serialized; no source text is included.
   */
  public static String format(MappingResult result) {
    Objects.requireNonNull(result, "result");
    var matches =
        result.matches().stream()
            .sorted(
                Comparator.comparingInt((Match match) -> match.oldLines().get(0))
                    .thenComparingInt(match -> match.newLines().get(0)))
            .toList();
    var json = new StringBuilder("{\n  \"schema_version\": 1,\n  \"matches\": [");
    for (int i = 0; i < matches.size(); i++) {
      Match match = matches.get(i);
      json.append(i == 0 ? "\n" : ",\n")
          .append("    {\n      \"old_lines\": ")
          .append(numbers(match.oldLines()))
          .append(",\n      \"new_lines\": ")
          .append(numbers(match.newLines()))
          .append(",\n      \"kind\": \"")
          .append(match.kind().name().toLowerCase(Locale.ROOT))
          .append("\",\n      \"score\": ")
          .append(match.score())
          .append("\n    }");
    }
    if (!matches.isEmpty()) json.append("\n  ");
    MatchConfig config = result.config();
    return json.append("],\n  \"deleted\": ")
        .append(numbers(result.deleted()))
        .append(",\n  \"added\": ")
        .append(numbers(result.added()))
        .append(",\n  \"config\": {\n    \"max_lcs_cells\": ")
        .append(config.maxLcsCells())
        .append(",\n    \"candidate_limit\": ")
        .append(config.candidateLimit())
        .append(",\n    \"context_radius\": ")
        .append(config.contextRadius())
        .append(",\n    \"minimum_score\": ")
        .append(config.minimumScore())
        .append("\n  }\n}\n")
        .toString();
  }

  private static String numbers(List<Integer> values) {
    return values.stream()
        .sorted()
        .map(String::valueOf)
        .collect(Collectors.joining(", ", "[", "]"));
  }
}
