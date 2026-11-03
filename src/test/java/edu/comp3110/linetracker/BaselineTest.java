package edu.comp3110.linetracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.junit.jupiter.api.Assertions.*;

class BaselineTest {
    @TempDir Path temp;
    private MappingResult compare(List<String> old, List<String> newer) {
        return new BaselineMatcher().match(SourceFile.fromLines("old", old),
                SourceFile.fromLines("new", newer), MatchConfig.DEFAULT);
    }
    @Test void identicalIncludingBlankLines() {
        var r = compare(List.of("a", "", "b"), List.of("a", "", "b"));
        assertEquals(List.of(1, 2, 3), r.matches().stream().map(m -> m.oldLines().get(0)).toList());
        assertTrue(r.deleted().isEmpty()); assertTrue(r.added().isEmpty());
    }
    @Test void shiftedLinesKeepOriginalNumbers() {
        var r = compare(List.of("a", "b", "c"), List.of("intro", "a", "c", "tail"));
        assertEquals(List.of(new Match(List.of(1), List.of(2), MatchKind.UNCHANGED, 1),
                new Match(List.of(3), List.of(3), MatchKind.UNCHANGED, 1)), r.matches());
        assertEquals(List.of(2), r.deleted()); assertEquals(List.of(1, 4), r.added());
    }
    @Test void emptyFilesAndOneSidedFiles() {
        assertTrue(compare(List.of(), List.of()).matches().isEmpty());
        assertEquals(List.of(1, 2), compare(List.of(), List.of("a", "")).added());
        assertEquals(List.of(1), compare(List.of("a"), List.of()).deleted());
    }
    @Test void entirelyEditedLineRemainsUnmatchedAtThisStage() {
        var r = compare(List.of("old"), List.of("new"));
        assertTrue(r.matches().isEmpty()); assertEquals(List.of(1), r.deleted());
        assertEquals(List.of(1), r.added());
    }
    @Test void repeatedLinesHaveStableExclusiveMappings() {
        var a = List.of("a", "b", "a"); var b = List.of("a", "a", "b");
        var r = compare(a, b);
        assertEquals(r, compare(a, b));
        assertEquals(List.of(new Match(List.of(1), List.of(1), MatchKind.UNCHANGED, 1),
                new Match(List.of(3), List.of(2), MatchKind.UNCHANGED, 1)), r.matches());
        r.validate(3, 3);
    }
    @Test void normalizationPreservesOriginalTextAndIsLocaleIndependent() {
        var prior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var f = SourceFile.fromLines("file", List.of("  INT\tValue = 0;  ", ""));
            assertEquals("int value = 0;", f.lines().get(0).normalized());
            assertEquals("  INT\tValue = 0;  ", f.lines().get(0).original());
            assertEquals(2, f.lines().get(1).number());
            assertEquals(1, compare(List.of(" A  B "), List.of("a b")).matches().size());
        } finally { Locale.setDefault(prior); }
    }
    @Test void similarityHasKnownDistancesAndSymmetry() {
        assertEquals(1, ContentSimilarity.score("", ""));
        assertEquals(0, ContentSimilarity.score("a", ""));
        assertEquals(4.0 / 7, ContentSimilarity.score("kitten", "sitting"), 1e-12);
        assertEquals(ContentSimilarity.score("ab", "abc"), ContentSimilarity.score("abc", "ab"));
        assertEquals(0, ContentSimilarity.score("abc", "xyz"));
    }
    @Test void validationRejectsMissingReusedAndOutOfRangeLines() {
        var m = new Match(List.of(1), List.of(1), MatchKind.UNCHANGED, 1);
        assertThrows(IllegalArgumentException.class, () -> new MappingResult(List.of(m), List.of(1), List.of(), MatchConfig.DEFAULT).validate(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MappingResult(List.of(), List.of(), List.of(), MatchConfig.DEFAULT).validate(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MappingResult(List.of(m), List.of(2), List.of(), MatchConfig.DEFAULT).validate(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Match(List.of(1), List.of(2, 4), MatchKind.SPLIT, 0.7));
    }
    @Test void splitContractSupportsFutureBackendAndIsImmutable() {
        var m = new Match(List.of(1), List.of(2, 3), MatchKind.SPLIT, 0.8);
        var r = new MappingResult(List.of(m), List.of(), List.of(1), MatchConfig.DEFAULT);
        r.validate(1, 3);
        assertThrows(UnsupportedOperationException.class, () -> r.matches().clear());
    }
    @Test void largeInputFailsBeforeAllocatingLcsTable() {
        assertThrows(IllegalArgumentException.class, () -> new BaselineMatcher().match(
                SourceFile.fromLines("a", List.of("a")), SourceFile.fromLines("b", List.of("b")), new MatchConfig(1)));
    }
    @Test void readerAndCliHandleUtf8CrLfAndErrors() throws Exception {
        Path a = temp.resolve("old.txt"), b = temp.resolve("new.txt");
        Files.writeString(a, "café\r\n\r\nfin\r\n"); Files.writeString(b, "café\nfin\n");
        assertEquals(3, SourceReader.read(a).lines().size());
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        assertEquals(0, Main.run(new String[]{"map", a.toString(), b.toString()}, new PrintStream(out), new PrintStream(err)));
        assertEquals("1-1\n3-2\n", out.toString().replace("\r\n", "\n"));
        assertEquals(1, Main.run(new String[]{"map", "missing.txt", b.toString()}, new PrintStream(out), new PrintStream(err)));
        assertEquals(2, Main.run(new String[]{}, new PrintStream(out), new PrintStream(err)));
    }
    @Test void sharedFixturesMatchDevelopmentExpectations() throws Exception {
        var pipeline = new MappingPipeline(new BaselineMatcher());
        var first = pipeline.compare(Path.of("datasets/development/identical/old.txt"), Path.of("datasets/development/identical/new.txt"), MatchConfig.DEFAULT);
        assertEquals(3, first.matches().size());
        var second = pipeline.compare(Path.of("datasets/development/insert-delete/old.txt"), Path.of("datasets/development/insert-delete/new.txt"), MatchConfig.DEFAULT);
        assertEquals(List.of(2), second.deleted()); assertEquals(List.of(1, 4), second.added());
        assertEquals(List.of(2, 3), second.matches().stream().map(m -> m.newLines().get(0)).toList());
    }
}
