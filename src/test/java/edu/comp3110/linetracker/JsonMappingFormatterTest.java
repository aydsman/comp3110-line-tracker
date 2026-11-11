package edu.comp3110.linetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class JsonMappingFormatterTest {
  @Test
  void preservesGroupsAndScoresWhileSortingCopiesOfAllLineCollections() {
    var groups =
        List.of(
            new Match(List.of(4), List.of(6, 7), MatchKind.SPLIT, 0.8),
            new Match(List.of(2), List.of(1), MatchKind.MOVED, 0.9),
            new Match(List.of(3), List.of(5), MatchKind.MODIFIED, 0.7),
            new Match(List.of(1), List.of(3), MatchKind.UNCHANGED, 1));
    var result = new MappingResult(groups, List.of(6, 5), List.of(8, 4, 2), MatchConfig.DEFAULT);
    result.validate(6, 8);

    assertEquals(
        """
        {
          "schema_version": 1,
          "matches": [
            {
              "old_lines": [1],
              "new_lines": [3],
              "kind": "unchanged",
              "score": 1.0
            },
            {
              "old_lines": [2],
              "new_lines": [1],
              "kind": "moved",
              "score": 0.9
            },
            {
              "old_lines": [3],
              "new_lines": [5],
              "kind": "modified",
              "score": 0.7
            },
            {
              "old_lines": [4],
              "new_lines": [6, 7],
              "kind": "split",
              "score": 0.8
            }
          ],
          "deleted": [5, 6],
          "added": [2, 4, 8],
          "config": {
            "max_lcs_cells": 4000000,
            "candidate_limit": 15,
            "context_radius": 4,
            "minimum_score": 0.65
          }
        }
        """,
        JsonMappingFormatter.format(result));
    assertEquals(groups, result.matches());
    assertEquals(List.of(6, 5), result.deleted());
    assertEquals(List.of(8, 4, 2), result.added());
    assertEquals("1-3\n2-1\n3-5\n4-6\n4-7\n", TextMappingFormatter.format(result));
  }

  @Test
  void emptyFilesStillExportTheActualRequestedConfiguration() {
    var config = new MatchConfig(50000, 7, 0, 0.875);
    var result = new MappingResult(List.of(), List.of(), List.of(), config);
    result.validate(0, 0);

    assertEquals(
        """
        {
          "schema_version": 1,
          "matches": [],
          "deleted": [],
          "added": [],
          "config": {
            "max_lcs_cells": 50000,
            "candidate_limit": 7,
            "context_radius": 0,
            "minimum_score": 0.875
          }
        }
        """,
        JsonMappingFormatter.format(result));
  }

  @Test
  void unmatchedOnlyOutputRetainsCompleteCoverage() {
    var result = new MappingResult(List.of(), List.of(2, 1), List.of(3, 1, 2), MatchConfig.DEFAULT);
    result.validate(2, 3);
    String json = JsonMappingFormatter.format(result);

    assertEquals(
        """
        {
          "schema_version": 1,
          "matches": [],
          "deleted": [1, 2],
          "added": [1, 2, 3],
          "config": {
            "max_lcs_cells": 4000000,
            "candidate_limit": 15,
            "context_radius": 4,
            "minimum_score": 0.65
          }
        }
        """,
        json);
    assertEquals("", TextMappingFormatter.format(result));
  }

  @Test
  void localeDoesNotChangeEnumNamesDecimalNumbersOrLineEndings() {
    var result =
        new MappingResult(
            List.of(new Match(List.of(1), List.of(1, 2), MatchKind.SPLIT, 0.8125)),
            List.of(),
            List.of(),
            new MatchConfig(100, 5, 2, 0.625));
    result.validate(1, 2);
    Locale prior = Locale.getDefault();
    try {
      Locale.setDefault(Locale.ROOT);
      String expected = JsonMappingFormatter.format(result);
      for (Locale locale : List.of(Locale.GERMANY, Locale.forLanguageTag("tr-TR"))) {
        Locale.setDefault(locale);
        assertEquals(expected, JsonMappingFormatter.format(result));
      }
      assertFalse(expected.contains("\r"));
      assertEquals('\n', expected.charAt(expected.length() - 1));
    } finally {
      Locale.setDefault(prior);
    }
  }

  @Test
  void rejectsMissingResult() {
    assertThrows(NullPointerException.class, () -> JsonMappingFormatter.format(null));
  }
}
