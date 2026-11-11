package edu.comp3110.linetracker;

import java.util.List;
import java.util.Objects;

public record Match(List<Integer> oldLines, List<Integer> newLines, MatchKind kind, double score) {
  public Match {
    oldLines = List.copyOf(oldLines);
    newLines = List.copyOf(newLines);
    Objects.requireNonNull(kind);
    if (!Double.isFinite(score) || score < 0 || score > 1)
      throw new IllegalArgumentException("Similarity must be between 0 and 1");
    if (oldLines.size() != 1 || newLines.isEmpty())
      throw new IllegalArgumentException("Match requires one old line and at least one new line");
    if (kind != MatchKind.SPLIT && newLines.size() != 1)
      throw new IllegalArgumentException("Only splits may map to multiple new lines");
    if (kind == MatchKind.SPLIT && newLines.size() < 2)
      throw new IllegalArgumentException("A split requires multiple new lines");
    for (int number : oldLines)
      if (number < 1) throw new IllegalArgumentException("Invalid old line");
    for (int i = 0; i < newLines.size(); i++) {
      if (newLines.get(i) < 1 || (i > 0 && newLines.get(i) != newLines.get(i - 1) + 1))
        throw new IllegalArgumentException("New lines must be positive and consecutive");
    }
  }
}
