package edu.comp3110.linetracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered normalized equality baseline. Unmatched lines are not proof of a real deletion/addition. */
public final class BaselineMatcher implements LineMatcher {
    @Override
    public MappingResult match(SourceFile oldFile, SourceFile newFile, MatchConfig config) {
        Objects.requireNonNull(config);
        var oldLines = oldFile.lines();
        var newLines = newFile.lines();
        int n = oldLines.size(), m = newLines.size();
        List<Match> matches = new ArrayList<>();
        List<Integer> deleted = new ArrayList<>(), added = new ArrayList<>();
        if (n == 0 || m == 0) {
            for (int i = 1; i <= n; i++) deleted.add(i);
            for (int j = 1; j <= m; j++) added.add(j);
        } else {
            long cells = ((long) n + 1) * ((long) m + 1);
            if (cells > config.maxLcsCells())
                throw new IllegalArgumentException("Files exceed baseline LCS memory limit (" + cells + " cells)");
            int[][] lengths = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    lengths[i][j] = equal(oldLines, newLines, i, j)
                            ? 1 + lengths[i + 1][j + 1]
                            : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
                }
            }
            int i = 0, j = 0;
            while (i < n && j < m) {
                if (equal(oldLines, newLines, i, j)) {
                    matches.add(new Match(List.of(++i), List.of(++j), MatchKind.UNCHANGED, 1));
                } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
                    deleted.add(++i); // Stable tie: advance the old file first.
                } else {
                    added.add(++j);
                }
            }
            while (i < n) deleted.add(++i);
            while (j < m) added.add(++j);
        }
        var result = new MappingResult(matches, deleted, added, config);
        result.validate(n, m);
        return result;
    }

    private static boolean equal(List<SourceLine> a, List<SourceLine> b, int i, int j) {
        return a.get(i).normalized().equals(b.get(j).normalized());
    }
}
