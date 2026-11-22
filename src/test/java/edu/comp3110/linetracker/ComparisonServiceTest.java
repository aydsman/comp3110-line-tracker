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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComparisonServiceTest {
  private final ComparisonService service = new ComparisonService();
  @TempDir Path temp;

  @Test
  void desktopAndCliExportsAreIdenticalForBothModesFormatsAndResourceProfiles() throws IOException {
    Path oldPath = input("old source.txt", "anchor\nint count = 1;\nreturn sum(a, b);\nend\n");
    Path newPath = input("new source.txt", "anchor\nint count = 2;\nreturn sum(a,\nb);\nend\n");
    for (ComparisonService.Mode mode : ComparisonService.Mode.values()) {
      for (ComparisonService.ResourceProfile profile : ComparisonService.ResourceProfile.values()) {
        ComparisonSession session = service.compare(oldPath, newPath, mode, profile.config());
        for (ComparisonService.ExportFormat format : ComparisonService.ExportFormat.values()) {
          Path export = temp.resolve(mode.name() + profile.name() + "." + format.extension());
          service.export(session, format, export);
          var args =
              new ArrayList<>(
                  List.of(
                      "map",
                      oldPath.toString(),
                      newPath.toString(),
                      "--format",
                      format == ComparisonService.ExportFormat.JSON ? "json" : "text",
                      "--max-lcs-cells",
                      Long.toString(profile.config().maxLcsCells())));
          if (mode == ComparisonService.Mode.ADVANCED) args.add("--advanced");
          var output = new ByteArrayOutputStream();
          var error = new ByteArrayOutputStream();
          try (var out = new PrintStream(output, true, StandardCharsets.UTF_8);
              var err = new PrintStream(error, true, StandardCharsets.UTF_8)) {
            assertEquals(
                0,
                Main.run(args.toArray(String[]::new), out, err),
                error.toString(StandardCharsets.UTF_8));
          }
          assertEquals(output.toString(StandardCharsets.UTF_8), Files.readString(export));
        }
      }
    }
  }

  @Test
  void displayRowsPreserveSplitGroupsAndLabelEveryUnmatchedLine() throws IOException {
    Path oldPath = input("old.txt", "return sum(a, b);\nzzzzqqqq\n");
    Path newPath = input("new.txt", "return sum(a,\nb);\nXXXXXXXX\n");
    ComparisonSession session =
        service.compare(oldPath, newPath, ComparisonService.Mode.ADVANCED, MatchConfig.DEFAULT);
    assertEquals(
        List.of(
            new ComparisonRow(List.of(1), List.of(1, 2), ComparisonRow.Kind.SPLIT),
            new ComparisonRow(List.of(2), List.of(), ComparisonRow.Kind.DELETED),
            new ComparisonRow(List.of(), List.of(3), ComparisonRow.Kind.ADDED)),
        session.rows());
    assertEquals("Split · 1 → 1, 2", session.rows().get(0).label());
    assertTrue(session.rows().get(1).label().startsWith("Deleted (unmatched old)"));
    assertTrue(session.rows().get(2).label().startsWith("Added (unmatched new)"));
    assertEquals(
        Set.of(1, 2),
        session.rows().stream()
            .flatMap(row -> row.oldLines().stream())
            .collect(Collectors.toSet()));
    assertEquals(
        Set.of(1, 2, 3),
        session.rows().stream()
            .flatMap(row -> row.newLines().stream())
            .collect(Collectors.toSet()));
    assertThrows(UnsupportedOperationException.class, () -> session.rows().clear());
  }

  @Test
  void exportUsesTheDisplayedSnapshotsEvenIfFilesChangeAfterComparison() throws IOException {
    Path oldPath = input("old.txt", "café\nreturn sum(a, b);\n");
    Path newPath = input("new.txt", "café\nreturn sum(a,\nb);\n");
    ComparisonSession session =
        service.compare(oldPath, newPath, ComparisonService.Mode.ADVANCED, MatchConfig.DEFAULT);
    Files.writeString(oldPath, "changed later\n");
    Files.writeString(newPath, "different now\n");
    assertEquals("café", session.oldSource().lines().get(0).original());
    assertEquals("return sum(a,", session.newSource().lines().get(1).original());
    Path text = temp.resolve("snapshot.txt");
    Path json = temp.resolve("snapshot.json");
    service.export(session, ComparisonService.ExportFormat.TEXT, text);
    service.export(session, ComparisonService.ExportFormat.JSON, json);
    assertEquals("1-1\n2-2\n2-3\n", Files.readString(text));
    assertEquals(JsonMappingFormatter.format(session.result()), Files.readString(json));
  }

  @Test
  void emptyAndNoMatchComparisonsHaveCompleteDisplayRows() throws IOException {
    Path empty = input("empty.txt", "");
    Path content = input("content.txt", "first\nsecond\n");
    for (ComparisonService.Mode mode : ComparisonService.Mode.values()) {
      ComparisonSession bothEmpty = service.compare(empty, empty, mode, MatchConfig.DEFAULT);
      assertTrue(bothEmpty.rows().isEmpty());
      ComparisonSession allAdded = service.compare(empty, content, mode, MatchConfig.DEFAULT);
      assertEquals(2, allAdded.rows().size());
      assertTrue(allAdded.rows().stream().allMatch(row -> row.kind() == ComparisonRow.Kind.ADDED));
      ComparisonSession allDeleted = service.compare(content, empty, mode, MatchConfig.DEFAULT);
      assertEquals(2, allDeleted.rows().size());
      assertTrue(
          allDeleted.rows().stream().allMatch(row -> row.kind() == ComparisonRow.Kind.DELETED));
    }
  }

  @Test
  void defaultGuardStaysFourMillionAndLargerFilesRequireExplicitBenchmarkProfile()
      throws IOException {
    assertEquals(MatchConfig.DEFAULT, ComparisonService.ResourceProfile.DEFAULT.config());
    assertEquals(8_000_000, ComparisonService.ResourceProfile.BENCHMARK.config().maxLcsCells());
    String content =
        IntStream.range(0, 2200).mapToObj(i -> "line " + i).collect(Collectors.joining("\n"));
    Path large = input("large.txt", content);
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.compare(
                    large,
                    large,
                    ComparisonService.Mode.BASELINE,
                    ComparisonService.ResourceProfile.DEFAULT.config()));
    assertTrue(failure.getMessage().contains("LCS"));
    ComparisonSession accepted =
        service.compare(
            large,
            large,
            ComparisonService.Mode.BASELINE,
            ComparisonService.ResourceProfile.BENCHMARK.config());
    assertEquals(2200, accepted.result().matches().size());
    assertEquals(8_000_000, accepted.result().config().maxLcsCells());
  }

  @Test
  void missingMalformedAndUnreadableInputsHaveReadableSideSpecificErrors() throws IOException {
    Path valid = input("valid.txt", "valid\n");
    Path invalid = Files.write(temp.resolve("invalid.txt"), new byte[] {(byte) 0xc3, 0x28});
    for (boolean badOld : List.of(true, false)) {
      String side = badOld ? "old" : "new";
      for (Path bad : List.of(temp.resolve("missing.txt"), invalid, temp, temp.getRoot())) {
        IOException failure =
            assertThrows(
                IOException.class,
                () ->
                    service.compare(
                        badOld ? bad : valid,
                        badOld ? valid : bad,
                        ComparisonService.Mode.BASELINE,
                        MatchConfig.DEFAULT));
        assertTrue(failure.getMessage().contains(side + " file"), failure.getMessage());
        if (bad.equals(invalid)) assertTrue(failure.getMessage().contains("not valid UTF-8"));
        if (bad.equals(temp.resolve("missing.txt")))
          assertTrue(failure.getMessage().contains("missing"));
        if (bad.equals(temp) || bad.equals(temp.getRoot())) {
          assertTrue(
              failure.getMessage().startsWith("Cannot read the " + side + " file"),
              failure.getMessage());
          assertTrue(failure.getMessage().contains("directory"), failure.getMessage());
          assertFalse(failure.getMessage().contains("missing"), failure.getMessage());
        }
      }
    }
  }

  @Test
  void existingExportsInputsAndRemovedInputPathsCannotBeWritten() throws IOException {
    Path oldPath = input("old.txt", "original old\n");
    Path newPath = input("new.txt", "original new\n");
    Path existing = input("existing.txt", "keep existing\n");
    ComparisonSession session =
        service.compare(oldPath, newPath, ComparisonService.Mode.BASELINE, MatchConfig.DEFAULT);
    for (Path destination : List.of(oldPath, newPath, existing, temp)) {
      byte[] before = Files.isDirectory(destination) ? null : Files.readAllBytes(destination);
      assertThrows(
          IOException.class,
          () -> service.export(session, ComparisonService.ExportFormat.JSON, destination));
      if (before != null) assertArrayEquals(before, Files.readAllBytes(destination));
    }
    Files.delete(oldPath);
    IOException removedInput =
        assertThrows(
            IOException.class,
            () -> service.export(session, ComparisonService.ExportFormat.TEXT, oldPath));
    assertTrue(removedInput.getMessage().contains("input file"));
    assertFalse(Files.exists(oldPath));
    Path missingParent = temp.resolve("missing-parent/export.json");
    assertThrows(
        IOException.class,
        () -> service.export(session, ComparisonService.ExportFormat.JSON, missingParent));
    assertFalse(Files.exists(missingParent));
  }

  @Test
  void symbolicAndDanglingLinksAreRefusedWithoutTouchingTargets() throws IOException {
    Path oldPath = input("old.txt", "keep old\n");
    Path newPath = input("new.txt", "keep new\n");
    ComparisonSession session =
        service.compare(oldPath, newPath, ComparisonService.Mode.BASELINE, MatchConfig.DEFAULT);
    Path link = temp.resolve("linked export.txt");
    try {
      Files.createSymbolicLink(link, oldPath);
    } catch (IOException | UnsupportedOperationException | SecurityException failure) {
      assumeTrue(false, "Symbolic links are unavailable: " + failure.getClass().getSimpleName());
    }
    assertThrows(
        IOException.class,
        () -> service.export(session, ComparisonService.ExportFormat.TEXT, link));
    assertTrue(Files.isSymbolicLink(link));
    assertEquals("keep old\n", Files.readString(oldPath));
    Path absent = temp.resolve("absent.txt");
    Path dangling = Files.createSymbolicLink(temp.resolve("dangling.txt"), absent);
    assertThrows(
        IOException.class,
        () -> service.export(session, ComparisonService.ExportFormat.JSON, dangling));
    assertFalse(Files.exists(absent));
    assertTrue(Files.isSymbolicLink(dangling));
  }

  @Test
  void hardLinkExportsCannotOverwriteAnInputThroughAnotherName() throws IOException {
    Path oldPath = input("old.txt", "keep old\n");
    Path newPath = input("new.txt", "keep new\n");
    ComparisonSession session =
        service.compare(oldPath, newPath, ComparisonService.Mode.BASELINE, MatchConfig.DEFAULT);
    Path link = temp.resolve("linked export.txt");
    try {
      Files.createLink(link, newPath);
    } catch (IOException | UnsupportedOperationException | SecurityException failure) {
      assumeTrue(false, "Hard links are unavailable: " + failure.getClass().getSimpleName());
    }
    assertThrows(
        IOException.class,
        () -> service.export(session, ComparisonService.ExportFormat.JSON, link));
    assertTrue(Files.isSameFile(link, newPath));
    assertEquals("keep new\n", Files.readString(newPath));
  }

  private Path input(String name, String contents) throws IOException {
    return Files.writeString(temp.resolve(name), contents, StandardCharsets.UTF_8);
  }
}
