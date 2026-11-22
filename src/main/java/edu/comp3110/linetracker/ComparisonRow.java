package edu.comp3110.linetracker;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** A whole mapping group, or one explicitly unmatched line, selectable in the desktop view. */
record ComparisonRow(List<Integer> oldLines, List<Integer> newLines, Kind kind) {
  enum Kind {
    UNCHANGED("Unchanged"),
    MODIFIED("Modified"),
    MOVED("Moved"),
    SPLIT("Split"),
    DELETED("Deleted (unmatched old)"),
    ADDED("Added (unmatched new)");

    private final String label;

    Kind(String label) {
      this.label = label;
    }
  }

  ComparisonRow {
    oldLines = List.copyOf(oldLines);
    newLines = List.copyOf(newLines);
    Objects.requireNonNull(kind);
  }

  String label() {
    return kind.label + " · " + numbers(oldLines) + " → " + numbers(newLines);
  }

  private static String numbers(List<Integer> numbers) {
    return numbers.isEmpty()
        ? "—"
        : numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }
}
