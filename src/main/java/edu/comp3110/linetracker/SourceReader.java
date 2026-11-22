package edu.comp3110.linetracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SourceReader {
  private SourceReader() {}

  public static SourceFile read(Path path) throws IOException {
    return SourceFile.fromLines(path.toString(), Files.readAllLines(path, StandardCharsets.UTF_8));
  }
}
