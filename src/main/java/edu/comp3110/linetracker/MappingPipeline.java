package edu.comp3110.linetracker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class MappingPipeline {
  private final LineMatcher matcher;

  public MappingPipeline(LineMatcher matcher) {
    this.matcher = Objects.requireNonNull(matcher);
  }

  public MappingResult compare(Path oldPath, Path newPath, MatchConfig config) throws IOException {
    Objects.requireNonNull(oldPath, "oldPath");
    Objects.requireNonNull(newPath, "newPath");
    Objects.requireNonNull(config, "config");
    return compare(read(oldPath, "old"), read(newPath, "new"), config);
  }

  /** Compares the same immutable input snapshots that a caller can display in the frontend. */
  public MappingResult compare(SourceFile oldFile, SourceFile newFile, MatchConfig config) {
    Objects.requireNonNull(oldFile, "oldFile");
    Objects.requireNonNull(newFile, "newFile");
    Objects.requireNonNull(config, "config");
    MappingResult result = matcher.match(oldFile, newFile, config);
    if (result == null) throw new IllegalArgumentException("Matcher returned no mapping result");
    if (!config.equals(result.config()))
      throw new IllegalArgumentException("Matcher returned a result with different configuration");
    result.validate(oldFile.lines().size(), newFile.lines().size());
    return result;
  }

  private static SourceFile read(Path path, String side) throws IOException {
    try {
      return SourceReader.read(path);
    } catch (IOException e) {
      throw new IOException(
          "Cannot read " + side + " file " + path + " as UTF-8: " + e.getMessage(), e);
    }
  }
}
