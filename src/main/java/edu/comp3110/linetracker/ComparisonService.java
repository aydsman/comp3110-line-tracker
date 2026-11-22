package edu.comp3110.linetracker;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Desktop operations over the same immutable source pipeline and formatters as the CLI. */
final class ComparisonService {
  enum Mode {
    BASELINE("Baseline"),
    ADVANCED("Advanced");

    private final String label;

    Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum ResourceProfile {
    DEFAULT("Default (4 million cells)", 4_000_000),
    BENCHMARK("Benchmark (8 million cells)", 8_000_000);

    private final String label;
    private final long cells;

    ResourceProfile(String label, long cells) {
      this.label = label;
      this.cells = cells;
    }

    MatchConfig config() {
      return new MatchConfig(cells);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum ExportFormat {
    TEXT("Text", "txt"),
    JSON("JSON", "json");

    private final String label;
    private final String extension;

    ExportFormat(String label, String extension) {
      this.label = label;
      this.extension = extension;
    }

    String extension() {
      return extension;
    }

    String format(MappingResult result) {
      return this == JSON
          ? JsonMappingFormatter.format(result)
          : TextMappingFormatter.format(result);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  ComparisonSession compare(Path oldPath, Path newPath, Mode mode, MatchConfig config)
      throws IOException {
    Objects.requireNonNull(mode);
    Objects.requireNonNull(config);
    SourceFile oldSource = read(oldPath, "old");
    SourceFile newSource = read(newPath, "new");
    MappingResult result =
        new MappingPipeline(mode == Mode.ADVANCED ? new AdvancedMatcher() : new BaselineMatcher())
            .compare(oldSource, newSource, config);
    return new ComparisonSession(oldSource, newSource, result);
  }

  void export(ComparisonSession session, ExportFormat format, Path output) throws IOException {
    Objects.requireNonNull(session);
    Objects.requireNonNull(format);
    Objects.requireNonNull(output);
    Path destination = output.toAbsolutePath().normalize();
    for (String source : new String[] {session.oldSource().path(), session.newSource().path()}) {
      if (destination.equals(Path.of(source).toAbsolutePath().normalize())) {
        throw new IOException("Choose a new export file; an input file cannot be used as output.");
      }
    }
    try {
      // CREATE_NEW refuses existing files and links atomically; no source file can be truncated.
      Files.writeString(
          destination,
          format.format(session.result()),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
    } catch (FileAlreadyExistsException e) {
      throw new IOException("That export file already exists. Choose a new name.", e);
    } catch (IOException e) {
      throw new IOException("Cannot write export " + output + ": " + e.getMessage(), e);
    }
  }

  private static SourceFile read(Path path, String side) throws IOException {
    Objects.requireNonNull(path);
    try {
      return SourceReader.read(path.toAbsolutePath().normalize());
    } catch (NoSuchFileException e) {
      throw new IOException("The " + side + " file is missing. Choose it again.", e);
    } catch (CharacterCodingException e) {
      throw new IOException("The " + side + " file is not valid UTF-8.", e);
    } catch (IOException e) {
      String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
      throw new IOException(
          "Cannot read the " + side + " file (" + name + "). " + e.getMessage(), e);
    }
  }
}
