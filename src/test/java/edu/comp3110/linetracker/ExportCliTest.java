package edu.comp3110.linetracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportCliTest {
  @TempDir Path temp;

  @Test
  void textAndJsonMatchTheSameLoadedSourcePipelineForBothAlgorithms() throws IOException {
    Path oldFile = input("old source.txt", "move this\nanchor a\nanchor b\nint count = 1;\n");
    Path newFile = input("new source.txt", "anchor a\nanchor b\nmove this\nint count = 2;\n");
    SourceFile oldSource = SourceReader.read(oldFile), newSource = SourceReader.read(newFile);

    for (boolean advanced : List.of(false, true)) {
      MatchConfig config = advanced ? new MatchConfig(100, 3, 0, 0.8) : new MatchConfig(100);
      MappingResult expected =
          new MappingPipeline(advanced ? new AdvancedMatcher() : new BaselineMatcher())
              .compare(oldSource, newSource, config);
      for (String format : List.of("text", "json")) {
        var options = new ArrayList<>(List.of("--format", format, "--max-lcs-cells", "100"));
        if (advanced) {
          // Settings precede the flag to check order-independent option parsing.
          options.addAll(
              List.of(
                  "--minimum-score",
                  "0.8",
                  "--context-radius",
                  "0",
                  "--candidate-limit",
                  "3",
                  "--advanced"));
        }
        CliResult actual = runMap(oldFile, newFile, options.toArray(String[]::new));
        assertEquals(0, actual.exitCode(), actual.stderr());
        assertEquals(
            format.equals("json")
                ? JsonMappingFormatter.format(expected)
                : TextMappingFormatter.format(expected),
            actual.stdout());
        assertEquals(
            "Unmatched old lines: "
                + expected.deleted()
                + "\nUnmatched new lines: "
                + expected.added()
                + "\n",
            actual.stderr());
      }
    }
  }

  @Test
  void minimumScoreChangesAdvancedMappingsAndLcsLimitAppliesToBaseline() throws IOException {
    Path oldFile = input("old.txt", "int count = 1;\n");
    Path newFile = input("new.txt", "int count = 2;\n");

    CliResult accepted = runMap(oldFile, newFile, "--advanced", "--minimum-score", "0.65");
    CliResult rejected = runMap(oldFile, newFile, "--minimum-score", "0.99", "--advanced");
    assertEquals(0, accepted.exitCode(), accepted.stderr());
    assertEquals("1-1\n", accepted.stdout());
    assertEquals(0, rejected.exitCode(), rejected.stderr());
    assertEquals("", rejected.stdout());
    assertEquals("Unmatched old lines: [1]\nUnmatched new lines: [1]\n", rejected.stderr());

    CliResult limited = runMap(oldFile, newFile, "--max-lcs-cells", "1", "--format", "json");
    assertFailure(limited);
    assertTrue(limited.stderr().contains("LCS"), limited.stderr());
  }

  @Test
  void malformedOptionsFailBeforeReadingInputsOrCreatingTheExport() {
    Path absentOld = temp.resolve("absent old.txt"), absentNew = temp.resolve("absent new.txt");
    List<List<String>> invalidOptions =
        List.of(
            List.of("--unknown"),
            List.of("--format"),
            List.of("--format", "xml"),
            List.of("--format", "json", "--format", "text"),
            List.of("--advanced", "--advanced"),
            List.of("--max-lcs-cells", "1", "--max-lcs-cells", "2"),
            List.of("--max-lcs-cells", "not-a-number"),
            List.of("--max-lcs-cells", "0"),
            List.of("--max-lcs-cells", "9223372036854775808"),
            List.of("--candidate-limit", "1"),
            List.of("--context-radius", "0"),
            List.of("--minimum-score", "0.8"),
            List.of("--advanced", "--candidate-limit", "0"),
            List.of("--advanced", "--candidate-limit", "1.5"),
            List.of("--advanced", "--context-radius", "-1"),
            List.of("--advanced", "--context-radius", "2147483648"),
            List.of("--advanced", "--minimum-score", "NaN"),
            List.of("--advanced", "--minimum-score", "Infinity"),
            List.of("--advanced", "--minimum-score", "0"),
            List.of("--advanced", "--minimum-score", "1.01"),
            List.of("--advanced", "--minimum-score"));
    for (int i = 0; i < invalidOptions.size(); i++) {
      Path output = temp.resolve("invalid-" + i + ".json");
      var options = new ArrayList<>(List.of("--output", output.toString()));
      options.addAll(invalidOptions.get(i));
      CliResult result = runMap(absentOld, absentNew, options.toArray(String[]::new));
      assertEquals(2, result.exitCode(), options + "\n" + result.stderr());
      assertEquals("", result.stdout());
      assertFalse(result.stderr().isBlank());
      assertFalse(result.stderr().contains("Cannot read"), result.stderr());
      assertFalse(Files.exists(output), options.toString());
    }
    assertEquals(2, runMap(absentOld, absentNew, "--output").exitCode());
    Path first = temp.resolve("first.json"), second = temp.resolve("second.json");
    assertEquals(
        2,
        runMap(absentOld, absentNew, "--output", first.toString(), "--output", second.toString())
            .exitCode());
    assertFalse(Files.exists(first));
    assertFalse(Files.exists(second));
  }

  @Test
  void helpUsesStdoutAndSucceedsWithoutInputFiles() {
    for (String[] arguments :
        List.of(new String[] {"--help"}, new String[] {"help"}, new String[] {"map", "--help"})) {
      CliResult result = run(arguments);
      assertEquals(0, result.exitCode());
      assertTrue(result.stdout().startsWith("Usage:"), result.stdout());
      assertTrue(result.stdout().contains("--format"), result.stdout());
      assertEquals("", result.stderr());
    }
  }

  @Test
  void exportCreatesANewUtf8FileAndLeavesStdoutEmpty() throws IOException {
    Path oldFile = input("old.txt", "café\nfin\n"), newFile = input("new.txt", "CAFÉ\nfin\n");
    MappingResult expected =
        new MappingPipeline(new BaselineMatcher()).compare(oldFile, newFile, MatchConfig.DEFAULT);

    for (String format : List.of("text", "json")) {
      Path output = temp.resolve("new output." + format);
      CliResult result =
          runMap(oldFile, newFile, "--output", output.toString(), "--format", format);
      assertEquals(0, result.exitCode(), result.stderr());
      assertEquals("", result.stdout());
      assertEquals(
          format.equals("json")
              ? JsonMappingFormatter.format(expected)
              : TextMappingFormatter.format(expected),
          Files.readString(output, StandardCharsets.UTF_8));
    }
  }

  @Test
  void existingExportsAndBothInputsAreNeverTruncated() throws IOException {
    Path oldFile = input("old.txt", "original old\n"), newFile = input("new.txt", "original new\n");
    Path existing = input("existing.json", "keep this existing export\n");
    for (Path output : List.of(existing, oldFile, newFile)) {
      byte[] before = Files.readAllBytes(output);
      assertFailure(runMap(oldFile, newFile, "--format", "json", "--output", output.toString()));
      assertArrayEquals(before, Files.readAllBytes(output));
    }
    CliResult invalid =
        runMap(oldFile, newFile, "--output", existing.toString(), "--format", "invalid");
    assertEquals(2, invalid.exitCode());
    assertEquals("keep this existing export\n", Files.readString(existing));
  }

  @Test
  void symbolicLinkExportsAreRejectedWithoutTouchingTheirTargets() throws IOException {
    Path oldFile = input("old.txt", "keep old\n"), newFile = input("new.txt", "keep new\n");
    Path link = temp.resolve("linked output.txt");
    try {
      Files.createSymbolicLink(link, oldFile);
    } catch (IOException | UnsupportedOperationException | SecurityException e) {
      assumeTrue(
          false,
          "Symbolic links are unavailable in this test environment: "
              + e.getClass().getSimpleName());
    }
    assertFailure(runMap(oldFile, newFile, "--output", link.toString()));
    assertTrue(Files.isSymbolicLink(link));
    assertEquals("keep old\n", Files.readString(oldFile));

    Path absentTarget = temp.resolve("absent target.txt"), dangling = temp.resolve("dangling.txt");
    Files.createSymbolicLink(dangling, absentTarget);
    assertFailure(runMap(oldFile, newFile, "--output", dangling.toString()));
    assertTrue(Files.isSymbolicLink(dangling));
    assertFalse(Files.exists(absentTarget));
  }

  @Test
  void hardLinkExportsCannotOverwriteAnInputThroughAnotherName() throws IOException {
    Path oldFile = input("old.txt", "keep old\n"), newFile = input("new.txt", "keep new\n");
    Path link = temp.resolve("hard linked output.txt");
    try {
      Files.createLink(link, newFile);
    } catch (IOException | UnsupportedOperationException | SecurityException e) {
      assumeTrue(
          false,
          "Hard links are unavailable in this test environment: " + e.getClass().getSimpleName());
    }
    assertFailure(runMap(oldFile, newFile, "--format", "json", "--output", link.toString()));
    assertTrue(Files.isSameFile(link, newFile));
    assertEquals("keep new\n", Files.readString(newFile));
  }

  @Test
  void badInputsAndOutputFailuresProduceNoPartialJsonOrNewExport() throws IOException {
    Path valid = input("valid.txt", "valid\n");
    Path malformed = temp.resolve("malformed.txt");
    Files.write(malformed, new byte[] {(byte) 0xc3, 0x28});
    for (Path badInput : List.of(temp.resolve("missing.txt"), malformed)) {
      Path output = temp.resolve(badInput.getFileName() + ".json");
      assertFailure(runMap(badInput, valid, "--format", "json", "--output", output.toString()));
      assertFalse(Files.exists(output));
    }
    assertFailure(
        runMap(
            valid,
            valid,
            "--format",
            "json",
            "--output",
            temp.resolve("missing-parent/out.json").toString()));
    assertFailure(runMap(valid, valid, "--format", "json", "--output", temp.toString()));
  }

  private Path input(String name, String contents) throws IOException {
    return Files.writeString(temp.resolve(name), contents, StandardCharsets.UTF_8);
  }

  private static CliResult runMap(Path oldFile, Path newFile, String... options) {
    var arguments = new ArrayList<>(List.of("map", oldFile.toString(), newFile.toString()));
    arguments.addAll(List.of(options));
    return run(arguments.toArray(String[]::new));
  }

  private static CliResult run(String... arguments) {
    var output = new ByteArrayOutputStream();
    var error = new ByteArrayOutputStream();
    int exit;
    try (var out = new PrintStream(output, true, StandardCharsets.UTF_8);
        var err = new PrintStream(error, true, StandardCharsets.UTF_8)) {
      exit = Main.run(arguments, out, err);
    }
    return new CliResult(
        exit,
        output.toString(StandardCharsets.UTF_8),
        error.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
  }

  private static void assertFailure(CliResult result) {
    assertEquals(1, result.exitCode(), result.stderr());
    assertEquals("", result.stdout());
    assertFalse(result.stderr().isBlank());
    assertFalse(result.stderr().contains("Exception in thread"), result.stderr());
    assertFalse(result.stderr().contains("\tat "), result.stderr());
  }

  private record CliResult(int exitCode, String stdout, String stderr) {}
}
