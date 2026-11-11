package edu.comp3110.linetracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Retains the baseline anchors and recovers edited, moved, and split lines from the unmatched
 * lines.
 */
public final class AdvancedMatcher implements LineMatcher {
  private static final double CONTENT_WEIGHT = 0.6;
  private static final double CONTEXT_WEIGHT = 0.4;
  private static final double MINIMUM_CONTENT = 0.5;
  private static final Pattern TOKEN =
      Pattern.compile("[\\p{L}\\p{M}\\p{N}_]+|[^\\s]", Pattern.UNICODE_CHARACTER_CLASS);

  private record Features(
      int number,
      String content,
      long contentHash,
      Map<String, Integer> context,
      long contextHash,
      double norm) {}

  private record Candidate(Features target, boolean exact, double distance) {}

  private record Proposal(int oldNumber, int newNumber, boolean exact, double score) {}

  private static final Comparator<Candidate> CANDIDATE_ORDER =
      Comparator.comparing(Candidate::exact)
          .reversed()
          .thenComparingDouble(Candidate::distance)
          .thenComparingInt(candidate -> candidate.target().number());

  private static final Comparator<Proposal> MATCH_ORDER =
      Comparator.comparing(Proposal::exact)
          .reversed()
          .thenComparing(Comparator.comparingDouble(Proposal::score).reversed())
          .thenComparingInt(Proposal::oldNumber)
          .thenComparingInt(Proposal::newNumber);

  @Override
  public MappingResult match(SourceFile oldFile, SourceFile newFile, MatchConfig config) {
    MappingResult baseline = new BaselineMatcher().match(oldFile, newFile, config);
    if (baseline.deleted().isEmpty() || baseline.added().isEmpty()) return baseline;

    List<Features> targets =
        baseline.added().stream()
            .map(number -> features(newFile, number, config.contextRadius()))
            .filter(line -> !line.content().isBlank())
            .toList();

    List<Proposal> proposals = new ArrayList<>();
    for (int number : baseline.deleted()) {
      if (oldFile.lines().get(number - 1).normalized().isBlank()) continue;
      Features source = features(oldFile, number, config.contextRadius());
      for (Candidate candidate : shortlist(source, targets, config.candidateLimit())) {
        Features target = candidate.target();
        double content = ContentSimilarity.score(source.content(), target.content());
        double score =
            source.context().isEmpty() && target.context().isEmpty()
                ? content
                : CONTENT_WEIGHT * content + CONTEXT_WEIGHT * cosine(source, target);
        if (candidate.exact() || (content >= MINIMUM_CONTENT && score >= config.minimumScore())) {
          proposals.add(new Proposal(number, target.number(), candidate.exact(), score));
        }
      }
    }

    proposals.sort(MATCH_ORDER);
    Set<Integer> usedOld = new HashSet<>();
    Set<Integer> usedNew = new HashSet<>();
    List<Match> matches = new ArrayList<>(baseline.matches());
    int nextProposal = 0;
    while (nextProposal < proposals.size() && proposals.get(nextProposal).exact()) {
      accept(proposals.get(nextProposal++), usedOld, usedNew, matches);
    }

    List<Match> splits =
        SplitDetector.find(
            oldFile,
            newFile,
            baseline.deleted().stream().filter(number -> !usedOld.contains(number)).toList(),
            baseline.added().stream().filter(number -> !usedNew.contains(number)).toList());
    for (Match split : splits) {
      usedOld.addAll(split.oldLines());
      usedNew.addAll(split.newLines());
      matches.add(split);
    }

    while (nextProposal < proposals.size()) {
      accept(proposals.get(nextProposal++), usedOld, usedNew, matches);
    }
    matches.sort(Comparator.comparingInt(match -> match.oldLines().get(0)));

    MappingResult result =
        new MappingResult(
            matches,
            baseline.deleted().stream().filter(number -> !usedOld.contains(number)).toList(),
            baseline.added().stream().filter(number -> !usedNew.contains(number)).toList(),
            config);
    result.validate(oldFile.lines().size(), newFile.lines().size());
    return result;
  }

  private static void accept(
      Proposal proposal, Set<Integer> usedOld, Set<Integer> usedNew, List<Match> matches) {
    if (usedOld.contains(proposal.oldNumber()) || usedNew.contains(proposal.newNumber())) return;
    usedOld.add(proposal.oldNumber());
    usedNew.add(proposal.newNumber());
    matches.add(
        new Match(
            List.of(proposal.oldNumber()),
            List.of(proposal.newNumber()),
            proposal.exact() ? MatchKind.MOVED : MatchKind.MODIFIED,
            proposal.score()));
  }

  private static PriorityQueue<Candidate> shortlist(
      Features source, List<Features> targets, int limit) {
    // The worst retained candidate sits at the head so replacements cost O(log limit).
    PriorityQueue<Candidate> candidates = new PriorityQueue<>(CANDIDATE_ORDER.reversed());
    for (Features target : targets) {
      double distance = CONTENT_WEIGHT * Long.bitCount(source.contentHash() ^ target.contentHash());
      if (!source.context().isEmpty() && !target.context().isEmpty()) {
        distance += CONTEXT_WEIGHT * Long.bitCount(source.contextHash() ^ target.contextHash());
      } else if (source.context().isEmpty() != target.context().isEmpty()) {
        distance += CONTEXT_WEIGHT * Long.SIZE;
      }
      Candidate candidate =
          new Candidate(target, source.content().equals(target.content()), distance);
      if (candidates.size() < limit) {
        candidates.add(candidate);
      } else if (CANDIDATE_ORDER.compare(candidate, candidates.element()) < 0) {
        candidates.remove();
        candidates.add(candidate);
      }
    }
    return candidates;
  }

  private static Features features(SourceFile file, int number, int radius) {
    List<SourceLine> lines = file.lines();
    int index = number - 1;
    String content = lines.get(index).normalized();
    Map<String, Integer> grams = new HashMap<>();
    int width = Math.min(3, content.length());
    for (int i = 0; i <= content.length() - width; i++) {
      grams.merge(content.substring(i, i + width), 1, Integer::sum);
    }
    Map<String, Integer> context = new HashMap<>();
    int start = Math.max(0, index - radius);
    int end = (int) Math.min(lines.size() - 1L, (long) index + radius);
    for (int i = start; i <= end; i++) {
      if (i == index) continue;
      var tokens = TOKEN.matcher(lines.get(i).normalized());
      while (tokens.find()) context.merge(tokens.group(), 1, Integer::sum);
    }
    double norm =
        Math.sqrt(context.values().stream().mapToDouble(count -> (double) count * count).sum());
    return new Features(
        number, content, SimHash.fingerprint(grams), context, SimHash.fingerprint(context), norm);
  }

  private static double cosine(Features source, Features target) {
    if (source.norm() == 0 || target.norm() == 0) return 0;
    double dot = 0;
    for (var token : source.context().entrySet()) {
      dot += (double) token.getValue() * target.context().getOrDefault(token.getKey(), 0);
    }
    return Math.min(1, dot / (source.norm() * target.norm()));
  }
}
