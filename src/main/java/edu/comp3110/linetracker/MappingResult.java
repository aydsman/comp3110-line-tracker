package edu.comp3110.linetracker;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MappingResult(List<Match> matches, List<Integer> deleted,
                            List<Integer> added, MatchConfig config) {
    public MappingResult {
        matches = List.copyOf(matches);
        deleted = List.copyOf(deleted);
        added = List.copyOf(added);
        Objects.requireNonNull(config);
    }

    public void validate(int oldCount, int newCount) {
        Set<Integer> oldSeen = new HashSet<>();
        Set<Integer> newSeen = new HashSet<>();
        for (Match match : matches) {
            consume(match.oldLines(), oldSeen, oldCount);
            consume(match.newLines(), newSeen, newCount);
        }
        consume(deleted, oldSeen, oldCount);
        consume(added, newSeen, newCount);
        if (oldSeen.size() != oldCount || newSeen.size() != newCount)
            throw new IllegalArgumentException("Every original line must be accounted for");
    }

    private static void consume(List<Integer> lines, Set<Integer> seen, int count) {
        for (int line : lines) {
            if (line < 1 || line > count || !seen.add(line))
                throw new IllegalArgumentException("Out-of-range or reused line: " + line);
        }
    }
}
