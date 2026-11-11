package edu.comp3110.linetracker;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public record SourceFile(String path, List<SourceLine> lines) {
  public SourceFile {
    Objects.requireNonNull(path);
    lines = List.copyOf(lines);
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).number() != i + 1)
        throw new IllegalArgumentException("Lines must keep consecutive original numbers");
    }
  }

  public static SourceFile fromLines(String path, List<String> text) {
    return new SourceFile(
        path,
        IntStream.range(0, text.size())
            .mapToObj(i -> new SourceLine(i + 1, text.get(i), Normalizer.normalize(text.get(i))))
            .toList());
  }
}
