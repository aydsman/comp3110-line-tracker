package edu.comp3110.linetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvancedMatcherTest {
  private final AdvancedMatcher matcher = new AdvancedMatcher();

  @Test
  void editedLineUsesContextFromMatchedNeighbors() {
    MappingResult result =
        compare(
            List.of("before alpha", "int count = 1;", "after beta"),
            List.of("before alpha", "int count = 2;", "after beta"),
            MatchConfig.DEFAULT);
    Match edited = result.matches().get(1);
    assertEquals(MatchKind.MODIFIED, edited.kind());
    assertEquals(List.of(2), edited.newLines());
    assertEquals(
        0.6 * ContentSimilarity.score("int count = 1;", "int count = 2;") + 0.4,
        edited.score(),
        1e-12);
    assertEquals(MatchKind.UNCHANGED, result.matches().get(0).kind());
    assertEquals(List.of(), result.deleted());
    assertEquals(List.of(), result.added());
  }

  @Test
  void recoversAnExactMoveOutsideTheAnchors() {
    MappingResult result =
        compare(
            List.of("move this", "anchor a", "anchor b"),
            List.of("anchor a", "anchor b", "move this"),
            new MatchConfig(100, 1, 4, 1));
    assertEquals(MatchKind.MOVED, result.matches().get(0).kind());
    assertEquals(List.of(3), result.matches().get(0).newLines());
    assertEquals(3, result.matches().size());
  }

  @Test
  void candidateLimitRanksNearContentAheadOfEarlierNoise() {
    MappingResult result =
        compare(
            List.of("calculate total from account 001"),
            List.of("delete", "?", "void", "calculate total from account 002"),
            new MatchConfig(100, 1, 0, 0.65));
    assertEquals(List.of(4), result.matches().get(0).newLines());
    assertEquals(List.of(1, 2, 3), result.added());
  }

  @Test
  void tiesUseOriginalPositionsAndNeverReuseTargets() {
    var oldLines = List.of("int count = 1;", "int count = 2;");
    var newLines = List.of("int count = 3;");
    var config = new MatchConfig(100, 15, 0, 0.65);
    MappingResult result = compare(oldLines, newLines, config);
    assertEquals(result, compare(oldLines, newLines, config));
    assertEquals(List.of(1), result.matches().get(0).oldLines());
    assertEquals(1, result.matches().size());
    assertEquals(List.of(2), result.deleted());
    assertEquals(List.of(), result.added());
  }

  @Test
  void unrelatedContentStaysUnmatchedDespiteIdenticalContext() {
    MappingResult result =
        compare(
            List.of("start", "abc", "end"), List.of("start", "xyz", "end"), MatchConfig.DEFAULT);
    assertEquals(2, result.matches().size());
    assertEquals(List.of(2), result.deleted());
    assertEquals(List.of(2), result.added());
  }

  @Test
  void emptyFilesKeepBaselineCoverageAndInvalidConfigIsRejected() {
    MappingResult result = compare(List.of(), List.of("new"), MatchConfig.DEFAULT);
    assertEquals(List.of(1), result.added());
    assertEquals(List.of(), result.matches());
    assertThrows(IllegalArgumentException.class, () -> new MatchConfig(100, 0, 4, 0.65));
    assertThrows(IllegalArgumentException.class, () -> new MatchConfig(100, 15, 4, Double.NaN));
  }

  @Test
  void cliKeepsBaselineDefaultAndSupportsAdvancedOptIn(@TempDir Path folder) throws Exception {
    Path oldFile = Files.writeString(folder.resolve("old.txt"), "int count = 1;\n");
    Path newFile = Files.writeString(folder.resolve("new.txt"), "int count = 2;\n");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PrintStream out = new PrintStream(output);
        PrintStream err = new PrintStream(new ByteArrayOutputStream())) {
      assertEquals(
          0, Main.run(new String[] {"map", oldFile.toString(), newFile.toString()}, out, err));
      assertEquals("", output.toString());
      assertEquals(
          0,
          Main.run(
              new String[] {"map", oldFile.toString(), newFile.toString(), "--advanced"},
              out,
              err));
      assertEquals("1-1\n", output.toString());
    }
  }

  private MappingResult compare(List<String> oldLines, List<String> newLines, MatchConfig config) {
    MappingResult result =
        matcher.match(
            SourceFile.fromLines("old", oldLines), SourceFile.fromLines("new", newLines), config);
    result.validate(oldLines.size(), newLines.size());
    return result;
  }
}
