package edu.comp3110.linetracker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** Finds exact one-to-many reconstructions across consecutive available lines. */
final class SplitDetector {
  static final int MAX_SPLIT_LINES = 4;

  private SplitDetector() {}

  static List<Match> find(
      SourceFile oldFile, SourceFile newFile, List<Integer> oldNumbers, List<Integer> newNumbers) {
    Set<Integer> available = new HashSet<>(newNumbers);
    List<Match> matches = new ArrayList<>();
    for (int oldNumber : oldNumbers) {
      String original = oldFile.lines().get(oldNumber - 1).normalized();
      if (original.isBlank()) continue;
      int bestStart = 0;
      int bestEnd = 0;
      int bestDistance = Integer.MAX_VALUE;
      for (int start : newNumbers) {
        int distance = Math.abs(oldNumber - start);
        if (distance >= bestDistance) continue;
        int end = matchingEnd(original, newFile.lines(), available, start);
        if (end != 0) {
          bestStart = start;
          bestEnd = end;
          bestDistance = distance;
        }
      }
      if (bestStart == 0) continue;
      List<Integer> targets = IntStream.rangeClosed(bestStart, bestEnd).boxed().toList();
      available.removeAll(targets);
      matches.add(new Match(List.of(oldNumber), targets, MatchKind.SPLIT, 1));
    }
    return matches;
  }

  private static int matchingEnd(
      String original, List<SourceLine> lines, Set<Integer> available, int start) {
    int offset = 0;
    for (int number = start; number <= lines.size() && number - start < MAX_SPLIT_LINES; number++) {
      if (!available.contains(number)) break;
      String fragment = lines.get(number - 1).normalized();
      if (fragment.isBlank()) break;

      // A line break may replace one normalized space or fall directly between characters.
      if (offset > 0 && original.charAt(offset) == ' ') offset++;
      if (!original.startsWith(fragment, offset)) break;
      offset += fragment.length();
      if (offset == original.length()) return number > start ? number : 0;
    }
    return 0;
  }
}
