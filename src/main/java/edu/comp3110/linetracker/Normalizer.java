package edu.comp3110.linetracker;

import java.util.Locale;
import java.util.Objects;

public final class Normalizer {
  private Normalizer() {}

  public static String normalize(String text) {
    return Objects.requireNonNull(text)
        .replaceAll("(?U)\\s+", " ")
        .strip()
        .toLowerCase(Locale.ROOT);
  }
}
