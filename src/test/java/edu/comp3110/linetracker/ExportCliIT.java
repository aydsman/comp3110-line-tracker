package edu.comp3110.linetracker;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks export and option behavior through the shipped executable rather than Main.run alone. */
class ExportCliIT {
  @TempDir Path temp;

  @Test
  void packagedDefaultAndAdvancedModesMatchLoadedApiForTextAndJson() throws Exception {
    Path oldFile = input("old source.txt", "int count = 1;\n");
    Path newFile = input("new source.txt", "int count = 2;\n");
    SourceFile oldSource = SourceReader.read(oldFile), newSource = SourceReader.read(newFile);

    CliResult defaultText = run("map", oldFile.toString(), newFile.toString());
    assertEquals(0, defaultText.exitCode(), defaultText.stderr());
    assertEquals("", defaultText.stdout());
    assertEquals("Unmatched old lines: [1]\nUnmatched new lines: [1]\n", defaultText.stderr());

    for (boolean advanced : List.of(false, true)) {
      var config = advanced ? new MatchConfig(100, 2, 0, 0.75) : new MatchConfig(100);
      MappingResult expected =
          new MappingPipeline(advanced ? new AdvancedMatcher() : new BaselineMatcher())
              .compare(oldSource, newSource, config);
      for (String format : List.of("text", "json")) {
        var arguments =
            new ArrayList<>(
                List.of(
                    "map",
                    oldFile.toString(),
                    newFile.toString(),
                    "--max-lcs-cells",
                    "100",
                    "--format",
                    format));
        if (advanced) {
          arguments.addAll(
              List.of(
                  "--minimum-score",
                  "0.75",
                  "--candidate-limit",
                  "2",
                  "--context-radius",
                  "0",
                  "--advanced"));
        }
        CliResult actual = run(arguments.toArray(String[]::new));
        assertEquals(0, actual.exitCode(), actual.stderr());
        assertEquals(
            format.equals("json")
                ? JsonMappingFormatter.format(expected)
                : TextMappingFormatter.format(expected),
            actual.stdout());
        if (advanced && format.equals("text")) assertEquals("1-1\n", actual.stdout());
      }
    }
  }

  @Test
  void packagedJsonExportWritesTheRequestedFileAndRefusesToOverwriteIt() throws Exception {
    Path oldFile = input("old.txt", "int count = 1;\n");
    Path newFile = input("new.txt", "int count = 2;\n");
    Path output = temp.resolve("mapping export.json");
    var expected =
        new MappingPipeline(new AdvancedMatcher())
            .compare(oldFile, newFile, new MatchConfig(100, 1, 0, 0.99));
    String[] arguments = {
      "map",
      oldFile.toString(),
      newFile.toString(),
      "--output",
      output.toString(),
      "--minimum-score",
      "0.99",
      "--format",
      "json",
      "--candidate-limit",
      "1",
      "--context-radius",
      "0",
      "--max-lcs-cells",
      "100",
      "--advanced"
    };

    CliResult created = run(arguments);
    assertEquals(0, created.exitCode(), created.stderr());
    assertEquals("", created.stdout());
    assertEquals(
        JsonMappingFormatter.format(expected), Files.readString(output, StandardCharsets.UTF_8));
    byte[] before = Files.readAllBytes(output);

    CliResult repeated = run(arguments);
    assertFailure(repeated);
    assertTrue(repeated.stderr().contains("Cannot write export"), repeated.stderr());
    assertArrayEquals(before, Files.readAllBytes(output));
  }

  @Test
  void packagedFailuresNeverEmitJsonOrCreateAnOutput() throws Exception {
    Path oldFile = input("old.txt", "old\n"), missing = temp.resolve("missing.txt");
    Path output = temp.resolve("must not exist.json");
    CliResult missingInput =
        run(
            "map",
            oldFile.toString(),
            missing.toString(),
            "--format",
            "json",
            "--output",
            output.toString());
    assertFailure(missingInput);
    assertTrue(missingInput.stderr().contains("Cannot read new file"), missingInput.stderr());
    assertFalse(Files.exists(output));

    CliResult badOptions =
        run(
            "map",
            missing.toString(),
            missing.toString(),
            "--output",
            output.toString(),
            "--candidate-limit",
            "0",
            "--advanced");
    assertEquals(2, badOptions.exitCode(), badOptions.stderr());
    assertEquals("", badOptions.stdout());
    assertTrue(badOptions.stderr().contains("Usage:"), badOptions.stderr());
    assertFalse(badOptions.stderr().contains("Cannot read"), badOptions.stderr());
    assertFalse(Files.exists(output));

    byte[] before = Files.readAllBytes(oldFile);
    assertFailure(
        run(
            "map",
            oldFile.toString(),
            oldFile.toString(),
            "--format",
            "json",
            "--output",
            oldFile.toString()));
    assertArrayEquals(before, Files.readAllBytes(oldFile));
  }

  @Test
  void packagedHelpSucceedsWithoutFilesAndAdvertisesExports() throws Exception {
    CliResult result = run("map", "--help");
    assertEquals(0, result.exitCode(), result.stderr());
    assertTrue(result.stdout().startsWith("Usage:"), result.stdout());
    assertTrue(result.stdout().contains("--format"), result.stdout());
    assertTrue(result.stdout().contains("--output"), result.stdout());
    assertEquals("", result.stderr());
  }

  private Path input(String name, String content) throws IOException {
    return Files.writeString(temp.resolve(name), content, StandardCharsets.UTF_8);
  }

  private CliResult run(String... arguments) throws Exception {
    Path jar = Path.of(System.getProperty("lineTracker.jar")).toAbsolutePath();
    assertTrue(Files.isRegularFile(jar), "Packaged JAR is missing: " + jar);
    Path javaDirectory = Path.of(System.getProperty("java.home"), "bin");
    Path java = javaDirectory.resolve("java.exe");
    if (!Files.isRegularFile(java)) java = javaDirectory.resolve("java");
    var command =
        new ArrayList<>(List.of(java.toString(), "-Dfile.encoding=UTF-8", "-jar", jar.toString()));
    command.addAll(List.of(arguments));
    Path stdout = Files.createTempFile(temp, "stdout-", ".txt");
    Path stderr = Files.createTempFile(temp, "stderr-", ".txt");
    Path workingDirectory = Files.createDirectories(temp.resolve("unrelated working directory"));
    Process process =
        new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();
    try {
      assertTrue(
          process.waitFor(20, TimeUnit.SECONDS), "Packaged CLI did not exit within 20 seconds");
      return new CliResult(
          process.exitValue(),
          Files.readString(stdout, StandardCharsets.UTF_8),
          Files.readString(stderr, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
      }
    }
  }

  private static void assertFailure(CliResult result) {
    assertEquals(1, result.exitCode(), result.stderr());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().startsWith("Comparison failed:"), result.stderr());
    assertFalse(result.stderr().contains("Exception in thread"), result.stderr());
    assertFalse(result.stderr().contains("\tat "), result.stderr());
  }

  private record CliResult(int exitCode, String stdout, String stderr) {}
}
