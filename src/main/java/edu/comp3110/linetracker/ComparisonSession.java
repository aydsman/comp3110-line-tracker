package edu.comp3110.linetracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The immutable source snapshots, mappings, and display rows for one successful comparison. */
record ComparisonSession(SourceFile oldSource, SourceFile newSource, MappingResult result) {
  ComparisonSession {
    Objects.requireNonNull(oldSource);
    Objects.requireNonNull(newSource);
    Objects.requireNonNull(result);
    result.validate(oldSource.lines().size(), newSource.lines().size());
  }

  List<ComparisonRow> rows() {
    List<ComparisonRow> rows = new ArrayList<>();
    for (Match match : result.matches()) {
      rows.add(
          new ComparisonRow(
              match.oldLines(), match.newLines(), ComparisonRow.Kind.valueOf(match.kind().name())));
    }
    for (int number : result.deleted()) {
      rows.add(new ComparisonRow(List.of(number), List.of(), ComparisonRow.Kind.DELETED));
    }
    for (int number : result.added()) {
      rows.add(new ComparisonRow(List.of(), List.of(number), ComparisonRow.Kind.ADDED));
    }
    rows.sort(
        Comparator.comparingInt(
                (ComparisonRow row) ->
                    row.oldLines().isEmpty() ? Integer.MAX_VALUE : row.oldLines().get(0))
            .thenComparingInt(row -> row.newLines().isEmpty() ? 0 : row.newLines().get(0)));
    return List.copyOf(rows);
  }
}
