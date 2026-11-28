package edu.comp3110.linetracker;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationContractTest {
  @TempDir Path temp;

  @Test
  void unicodeBoundaryWhitespaceIsTrimmedWithoutChangingOriginalInput() {
    var source =
        SourceFile.fromLines("example", List.of("\u00a0INT\u202fValue\u00a0", "\u00a0\u202f", ""));
    assertEquals(
        List.of("int value", "", ""), source.lines().stream().map(SourceLine::normalized).toList());
    assertEquals("\u00a0INT\u202fValue\u00a0", source.lines().get(0).original());
    assertEquals(List.of(1, 2, 3), source.lines().stream().map(SourceLine::number).toList());
    assertEquals(
        3,
        new MappingPipeline(new BaselineMatcher())
            .compare(
                source,
                SourceFile.fromLines("other", List.of("int value", "", "")),
                MatchConfig.DEFAULT)
            .matches()
            .size());
  }

  @Test
  void sourceSnapshotsAndConfigReachInjectedMatcherWithoutReloading() {
    var oldFile = SourceFile.fromLines("not-on-disk/old", List.of("  A + B;  ", "x"));
    var newFile = SourceFile.fromLines("not-on-disk/new", List.of("A +", "B;", "y"));
    var config = new MatchConfig(100);
    var expected =
        new MappingResult(
            List.of(
                new Match(List.of(1), List.of(1, 2), MatchKind.SPLIT, 0.9),
                new Match(List.of(2), List.of(3), MatchKind.MODIFIED, 0.7)),
            List.of(),
            List.of(),
            config);
    LineMatcher matcher =
        (oldSnapshot, newSnapshot, settings) -> {
          assertSame(oldFile, oldSnapshot);
          assertSame(newFile, newSnapshot);
          assertSame(config, settings);
          assertEquals("a + b;", oldSnapshot.lines().get(0).normalized());
          return expected;
        };
    assertSame(expected, new MappingPipeline(matcher).compare(oldFile, newFile, config));
  }

  @Test
  void pathAndSnapshotEntryPointsReturnTheSameValidatedMappings() throws IOException {
    Path oldPath = temp.resolve("old source.txt"), newPath = temp.resolve("new source.txt");
    Files.writeString(oldPath, "café\r\n\r\nFin\r\n");
    Files.writeString(newPath, "intro\ncafé\nfin\n");
    var pipeline = new MappingPipeline(new BaselineMatcher());
    assertEquals(
        pipeline.compare(
            SourceReader.read(oldPath), SourceReader.read(newPath), MatchConfig.DEFAULT),
        pipeline.compare(oldPath, newPath, MatchConfig.DEFAULT));
  }

  @Test
  void invalidMatcherOutputsCannotEscapePipelineValidation() {
    var oldFile = SourceFile.fromLines("old", List.of("a"));
    var newFile = SourceFile.fromLines("new", List.of("a"));
    List<LineMatcher> invalidMatchers =
        List.of(
            (a, b, c) -> null,
            (a, b, c) -> new MappingResult(List.of(), List.of(1), List.of(1), new MatchConfig(1)),
            (a, b, c) -> new MappingResult(List.of(), List.of(), List.of(), c),
            (a, b, c) ->
                new MappingResult(
                    List.of(new Match(List.of(1), List.of(1), MatchKind.UNCHANGED, 1)),
                    List.of(1),
                    List.of(),
                    c));
    for (LineMatcher matcher : invalidMatchers) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new MappingPipeline(matcher).compare(oldFile, newFile, MatchConfig.DEFAULT));
    }
  }

  @Test
  void invalidInputIsRejectedBeforeCallingMatcher() {
    var called = new AtomicBoolean();
    var pipeline =
        new MappingPipeline(
            (a, b, c) -> {
              called.set(true);
              return null;
            });
    var source = SourceFile.fromLines("a", List.of());
    assertThrows(NullPointerException.class, () -> pipeline.compare(source, source, null));
    assertThrows(
        NullPointerException.class,
        () -> pipeline.compare((SourceFile) null, source, MatchConfig.DEFAULT));
    assertThrows(
        NullPointerException.class,
        () -> pipeline.compare(temp.resolve("missing"), temp.resolve("also-missing"), null));
    assertFalse(called.get());
  }

  @Test
  void readFailureIdentifiesInputRoleAndRetainsCause() throws IOException {
    Path valid = temp.resolve("valid.txt"),
        missing = temp.resolve("missing.txt"),
        invalid = temp.resolve("invalid.txt");
    Files.writeString(valid, "valid\n");
    Files.write(invalid, new byte[] {(byte) 0xc3, (byte) 0x28});
    var pipeline = new MappingPipeline(new BaselineMatcher());
    var newError =
        assertThrows(
            IOException.class, () -> pipeline.compare(valid, missing, MatchConfig.DEFAULT));
    assertTrue(newError.getMessage().contains("Cannot read new file " + missing));
    assertInstanceOf(NoSuchFileException.class, newError.getCause());
    var oldError =
        assertThrows(
            IOException.class, () -> pipeline.compare(invalid, valid, MatchConfig.DEFAULT));
    assertTrue(oldError.getMessage().contains("Cannot read old file " + invalid + " as UTF-8"));
    assertInstanceOf(MalformedInputException.class, oldError.getCause());
  }
}
