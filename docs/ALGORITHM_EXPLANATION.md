# Explaining Line Tracker

Prepared for Ayden's backend Task 4 on November 20, 2026.

## What the program does

Line Tracker reads two UTF-8 versions of a file and links their original line numbers. Output `4-8` means old line 4 corresponds to new line 8. A split can produce both `4-8` and `4-9`. Lines left unmatched are recorded separately.

The Java implementation compares text without requiring a parser for the input language. The core accepts immutable source snapshots. The CLI and JavaFX callers can use the same mapping pipeline.

## How matching works

1. Keep the original text and one-based line numbers. Make comparison copies by lowercasing and collapsing whitespace.
2. Find an ordered longest common subsequence (LCS) of normalized equal lines. These become baseline anchors.
3. For unmatched nonblank lines, create SimHash fingerprints of content and nearby tokens. Shortlist up to 15 candidate new lines per old line.
4. Score candidates with normalized Levenshtein content similarity and cosine similarity of surrounding token counts. Use 60% content and 40% context, or content alone when both contexts are empty. Fuzzy matches need content similarity of at least 0.5 and combined similarity of at least 0.65.
5. Accept exact moved lines first. Look for splits among the remaining lines by reconstructing an old line from two to four consecutive available nonblank new fragments.
6. Accept the remaining eligible edited-line proposals in deterministic score order. Every old and new line belongs to one group or an unmatched list.

Baseline mode ends after ordered equality matching. Advanced mode also performs the later stages. Split reconstruction currently requires exact normalized text, allowing a line break to replace a space or occur between characters.

## Short answers for the demo

**Why use context?** A short line such as `return value;` may occur several times. Nearby code can help distinguish its location.

**Why use SimHash?** It reduces the number of expensive text-similarity calculations. The implementation still scans unmatched pairs to shortlist candidates, so it has scaling limits.

**How do you prevent competing matches?** Accepted proposals reserve their old and new line numbers. Later proposals cannot reuse them.

**What happens to a line the tool cannot match?** It stays unmatched. This can represent a real addition/deletion or a missed correspondence.

**What does the split demo show?** Old `return alpha + beta;` becomes new `return alpha +` followed by `beta;`. Advanced text output includes both target line numbers.

**How is accuracy measured?** Expand expected and predicted groups into old/new pairs. Precision measures how many predicted pairs are correct. Recall measures how many expected pairs were found. F1 combines precision and recall. Exact split accuracy requires the complete target group.

## Current evidence and limitations

QA PR #11 reports 25 distinct real source-file pairs and six separate synthetic fixtures. Its five reserved test projects score 94.87% advanced pair F1. On the new real split groups, exact recovery is 5/15. Edited splits, groups longer than four fragments, and comment reordering can exceed current reconstruction support.

The 20-case development score is dominated by large mostly unchanged files. Report development and reserved-test results separately. Dataset source review and prediction accuracy are different checks.

Comparison normalization can hide case and whitespace changes. Ordered anchors and greedy proposal assignment can also miss valid alternatives. Many-to-one merges have no dedicated model.

LCS uses O(n*m) time and table space. The default 4,000,000-cell guard deliberately rejects oversized comparisons. Ali's integrated CLI supports an explicit benchmark profile:

```powershell
java -jar target/line-tracker.jar map OLD NEW --advanced --max-lcs-cells 8000000
```

Use the same declared resource profile for baseline and advanced benchmark runs. Eight million integer cells require about 32 MB for cell contents, plus row objects and other program memory. QA reports both default failures and the separate complete 8-million-cell benchmark results. Similarity thresholds remain frozen.

Historical bug tracing requires additional repository-history logic. Line mapping supplies a building block for tracing a fixed line backward through revisions.
