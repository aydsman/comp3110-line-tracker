package edu.comp3110.linetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SplitDetectionTest {
  @Test
  void reconstructsWithAndWithoutSpacesAtBreaks() {
    MappingResult result =
        compare(
            List.of("before", "return buildResult(account, total);", "after"),
            List.of("before", "return buildResult(", "  account,", "  total);", "after"));
    assertEquals(
        new Match(List.of(2), List.of(2, 3, 4), MatchKind.SPLIT, 1), result.matches().get(1));
    assertEquals(List.of(), result.added());
    assertEquals(List.of(), result.deleted());
  }

  @Test
  void exactSplitWinsOverCompetingFuzzyMatchAndCannotReuseFragments() {
    var result =
        compare(
            List.of("calculateAccountTotal + tax", "calculateAccountTotal + tax"),
            List.of("calculateAccountTotal", "+ tax"));
    assertEquals(
        List.of(new Match(List.of(1), List.of(1, 2), MatchKind.SPLIT, 1)), result.matches());
    assertEquals(List.of(2), result.deleted());
  }

  @Test
  void doesNotConsumeUnchangedAnchorOrCrossBlank() {
    assertNoSplit(compare(List.of("alpha beta", "beta"), List.of("alpha", "beta")));
    assertNoSplit(compare(List.of("alpha beta"), List.of("alpha", "", "beta")));
  }

  @Test
  void preservesExactMoveBeforeConsideringSplits() {
    var result =
        compare(
            List.of("alpha", "start", "stop", "alpha beta"),
            List.of("start", "stop", "alpha", "beta"));
    assertEquals(MatchKind.MOVED, result.matches().get(0).kind());
    assertEquals(List.of(3), result.matches().get(0).newLines());
    assertNoSplit(result);
  }

  @Test
  void boundsTheNumberOfFragmentsAndRejectsPartialReconstruction() {
    assertNoSplit(
        compare(
            List.of("one two three four five"), List.of("one", "two", "three", "four", "five")));
    assertNoSplit(compare(List.of("alpha beta suffix"), List.of("alpha", "beta")));
    assertNoSplit(compare(List.of("alpha beta"), List.of("alpha", "unrelated", "beta")));
  }

  @Test
  void advancedCliPrintsEveryPairInTheSplit(@TempDir Path folder) throws Exception {
    Path oldFile = Files.writeString(folder.resolve("old.txt"), "return sum(a, b);\n");
    Path newFile = Files.writeString(folder.resolve("new.txt"), "return sum(a,\n b);\n");
    var output = new ByteArrayOutputStream();
    try (PrintStream out = new PrintStream(output);
        PrintStream err = new PrintStream(new ByteArrayOutputStream())) {
      assertEquals(
          0,
          Main.run(
              new String[] {"map", oldFile.toString(), newFile.toString(), "--advanced"},
              out,
              err));
    }
    assertEquals(List.of("1-1", "1-2"), output.toString().lines().toList());
  }

  private static void assertNoSplit(MappingResult result) {
    assertTrue(result.matches().stream().noneMatch(match -> match.kind() == MatchKind.SPLIT));
  }

  private static MappingResult compare(List<String> oldLines, List<String> newLines) {
    MappingResult result =
        new AdvancedMatcher()
            .match(
                SourceFile.fromLines("old", oldLines),
                SourceFile.fromLines("new", newLines),
                MatchConfig.DEFAULT);
    result.validate(oldLines.size(), newLines.size());
    return result;
  }
}
