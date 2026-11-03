package edu.comp3110.linetracker;

import java.util.Objects;

public final class ContentSimilarity {
    private ContentSimilarity() { }

    /** 1 - edit distance / maximum length; empty/empty is 1. Inputs are comparison strings. */
    public static double score(String left, String right) {
        Objects.requireNonNull(left);
        Objects.requireNonNull(right);
        if (left.equals(right)) return 1;
        if (left.length() < right.length()) return score(right, left);
        if (right.isEmpty()) return 0;
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return 1.0 - (double) previous[right.length()] / left.length();
    }
}
