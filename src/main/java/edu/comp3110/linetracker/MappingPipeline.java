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
    SourceFile oldFile = SourceReader.read(oldPath), newFile = SourceReader.read(newPath);
    MappingResult result = matcher.match(oldFile, newFile, config);
    result.validate(oldFile.lines().size(), newFile.lines().size());
    return result;
  }
}
