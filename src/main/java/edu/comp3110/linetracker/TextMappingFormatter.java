package edu.comp3110.linetracker;

import java.util.Comparator;
import java.util.Objects;

/** Shared text output for the CLI and future GUI exports. */
public final class TextMappingFormatter {
  private TextMappingFormatter() {}

  /**
   * Formats matched pairs in ascending old/new line order, with one LF-terminated pair per line.
   * Splits emit every pair; unmatched lines are not included. The input result is not modified.
   */
  public static String format(MappingResult result) {
    Objects.requireNonNull(result, "result");
    var ordered =
        result.matches().stream()
            .sorted(
                Comparator.comparingInt((Match match) -> match.oldLines().get(0))
                    .thenComparingInt(match -> match.newLines().get(0)))
            .toList();
    var text = new StringBuilder();
    for (Match match : ordered) {
      int oldLine = match.oldLines().get(0);
      for (int newLine : match.newLines()) {
        text.append(oldLine).append('-').append(newLine).append('\n');
      }
    }
    return text.toString();
  }
}
