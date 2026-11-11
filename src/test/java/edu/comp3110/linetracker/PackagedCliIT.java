package edu.comp3110.linetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the packaged executable, including its manifest, process exits and file inputs. */
class PackagedCliIT {
  @TempDir Path temp;

  @Test
  void shippedFixturesRunThroughTheExecutableJar() throws Exception {
    var identical = run("map", fixture("identical", "old.txt"), fixture("identical", "new.txt"));
    assertEquals(0, identical.exitCode());
    assertEquals("1-1\n2-2\n3-3\n", identical.stdout());
    assertEquals("Unmatched old lines: []\nUnmatched new lines: []\n", identical.stderr());

    var shifted =
        run("map", fixture("insert-delete", "old.txt"), fixture("insert-delete", "new.txt"));
    assertEquals(0, shifted.exitCode());
    assertEquals("1-2\n3-3\n", shifted.stdout());
    assertEquals("Unmatched old lines: [2]\nUnmatched new lines: [1, 4]\n", shifted.stderr());
  }

  @Test
  void emptyAndOneSidedFilesSucceedWithoutPhantomMappings() throws Exception {
    Path empty = input("empty file.txt", "");
    Path content = input("new content.txt", "\nkeep\n");
    var bothEmpty = run("map", empty.toString(), empty.toString());
    assertEquals(0, bothEmpty.exitCode());
    assertEquals("", bothEmpty.stdout());
    assertEquals("Unmatched old lines: []\nUnmatched new lines: []\n", bothEmpty.stderr());

    var oneSided = run("map", empty.toString(), content.toString());
    assertEquals(0, oneSided.exitCode());
    assertEquals("", oneSided.stdout());
    assertEquals("Unmatched old lines: []\nUnmatched new lines: [1, 2]\n", oneSided.stderr());
  }

  @Test
  void utf8CrLfAndUnicodeBoundaryWhitespaceKeepOriginalLineNumbers() throws Exception {
    Path oldFile = input("old source with spaces.txt", "\u00a0CAFÉ\u202f\r\n\r\nfin\r\n");
    Path newFile = input("new source with spaces.txt", "café\nfin\n");

    var result = run("map", oldFile.toString(), newFile.toString());

    assertEquals(0, result.exitCode());
    assertEquals("1-1\n3-2\n", result.stdout());
    assertEquals("Unmatched old lines: [2]\nUnmatched new lines: []\n", result.stderr());
  }

  @Test
  void missingNewFileReportsItsRoleAndFailureExit() throws Exception {
    Path oldFile = input("valid old.txt", "line\n");
    Path missing = temp.resolve("missing new file.txt");

    var result = run("map", oldFile.toString(), missing.toString());

    assertComparisonFailure(result, "Cannot read new file " + missing + " as UTF-8:");
  }

  @Test
  void malformedOldUtf8ReportsItsRoleAndFailureExit() throws Exception {
    Path malformed = temp.resolve("malformed old file.txt");
    Files.write(malformed, new byte[] {(byte) 0xc3, 0x28});
    Path newFile = input("valid new.txt", "line\n");

    var result = run("map", malformed.toString(), newFile.toString());

    assertComparisonFailure(result, "Cannot read old file " + malformed + " as UTF-8:");
  }

  @Test
  void incorrectUsageHasItsOwnExitAndNoResult() throws Exception {
    var result = run("map");

    assertEquals(2, result.exitCode());
    assertEquals("", result.stdout());
    assertEquals("Usage: java -jar target/line-tracker.jar map OLD NEW\n", result.stderr());
  }

  private static void assertComparisonFailure(CliResult result, String detailPrefix) {
    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().startsWith("Comparison failed: " + detailPrefix), result.stderr());
    assertFalse(result.stderr().contains("Exception in thread"), result.stderr());
    assertFalse(result.stderr().contains("\tat "), result.stderr());
  }

  private Path input(String name, String contents) throws Exception {
    return Files.writeString(temp.resolve(name), contents, StandardCharsets.UTF_8);
  }

  private static String fixture(String name, String file) {
    return Path.of(System.getProperty("lineTracker.projectDirectory"))
        .resolve("datasets/development")
        .resolve(name)
        .resolve(file)
        .toString();
  }

  private CliResult run(String... arguments) throws Exception {
    Path jar = Path.of(System.getProperty("lineTracker.jar")).toAbsolutePath();
    assertTrue(Files.isRegularFile(jar), "Packaged JAR is missing: " + jar);
    Path javaDirectory = Path.of(System.getProperty("java.home"), "bin");
    Path java = javaDirectory.resolve("java.exe");
    if (!Files.isRegularFile(java)) java = javaDirectory.resolve("java");
    var command = new ArrayList<String>();
    command.add(java.toString());
    command.add("-Dfile.encoding=UTF-8");
    command.add("-jar");
    command.add(jar.toString());
    command.addAll(List.of(arguments));
    Path stdout = Files.createTempFile(temp, "stdout-", ".txt");
    Path stderr = Files.createTempFile(temp, "stderr-", ".txt");
    Path workingDirectory = Files.createDirectories(temp.resolve("working directory with spaces"));
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

  private record CliResult(int exitCode, String stdout, String stderr) {}
}
