package edu.comp3110.linetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextMappingFormatterTest {
  @Test
  void sortsMixedGroupsAndEmitsEverySplitPairWithoutChangingTheResult() {
    var groups =
        List.of(
            new Match(List.of(4), List.of(6, 7), MatchKind.SPLIT, 0.8),
            new Match(List.of(2), List.of(1), MatchKind.MOVED, 0.9),
            new Match(List.of(3), List.of(5), MatchKind.MODIFIED, 0.7),
            new Match(List.of(1), List.of(3), MatchKind.UNCHANGED, 1));
    var result = new MappingResult(groups, List.of(), List.of(2, 4), MatchConfig.DEFAULT);
    result.validate(4, 7);

    assertEquals("1-3\n2-1\n3-5\n4-6\n4-7\n", TextMappingFormatter.format(result));
    assertEquals(groups, result.matches());
  }

  @Test
  void noMappingsProduceEmptyTextEvenWhenThereAreUnmatchedLines() {
    var result = new MappingResult(List.of(), List.of(1), List.of(1, 2), MatchConfig.DEFAULT);
    result.validate(1, 2);

    assertEquals("", TextMappingFormatter.format(result));
    assertEquals(
        "",
        TextMappingFormatter.format(
            new MappingResult(List.of(), List.of(), List.of(), MatchConfig.DEFAULT)));
  }

  @Test
  void unchangedMappingsKeepTheExistingPairFormatAndTrailingNewline() {
    var result =
        new MappingResult(
            List.of(
                new Match(List.of(1), List.of(1), MatchKind.UNCHANGED, 1),
                new Match(List.of(2), List.of(2), MatchKind.UNCHANGED, 1)),
            List.of(),
            List.of(),
            MatchConfig.DEFAULT);
    result.validate(2, 2);

    assertEquals("1-1\n2-2\n", TextMappingFormatter.format(result));
  }
}
