package edu.comp3110.linetracker;

import java.util.Map;

/** Stable 64-bit weighted similarity fingerprints. */
final class SimHash {
  private SimHash() {}

  static long fingerprint(Map<String, Integer> frequencies) {
    long[] votes = new long[Long.SIZE];
    for (var feature : frequencies.entrySet()) {
      long hash = featureHash(feature.getKey());
      for (int bit = 0; bit < Long.SIZE; bit++) {
        votes[bit] += ((hash >>> bit) & 1) == 1 ? feature.getValue() : -feature.getValue();
      }
    }
    long fingerprint = 0;
    for (int bit = 0; bit < Long.SIZE; bit++) {
      if (votes[bit] > 0) fingerprint |= 1L << bit;
    }
    return fingerprint;
  }

  private static long featureHash(String feature) {
    long hash = 0xcbf29ce484222325L;
    for (int i = 0; i < feature.length(); i++) {
      hash ^= feature.charAt(i);
      hash *= 0x100000001b3L;
    }
    // Mix nearby feature hashes before using their individual bits as votes.
    hash = (hash ^ (hash >>> 33)) * 0xff51afd7ed558ccdL;
    hash = (hash ^ (hash >>> 33)) * 0xc4ceb9fe1a85ec53L;
    return hash ^ (hash >>> 33);
  }
}
