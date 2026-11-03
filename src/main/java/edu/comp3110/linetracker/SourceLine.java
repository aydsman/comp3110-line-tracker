package edu.comp3110.linetracker;

import java.util.Objects;

public record SourceLine(int number, String original, String normalized) {
    public SourceLine {
        if (number < 1) throw new IllegalArgumentException("Line numbers start at 1");
        Objects.requireNonNull(original);
        Objects.requireNonNull(normalized);
    }
}
