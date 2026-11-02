# Dataset and evaluation

## Dataset unit

Conservatively interpret the requirement as 25 distinct source files, each represented by an old/new pair, rather than 25 versions of the same file. Confirm with the professor. Prefer real project revisions where redistribution is allowed. Record project, license, original path, revision identifiers, and the edits represented. Do not redistribute course benchmarks until permission is clear.

Layout: `datasets/new/<case-id>/old.txt`, `new.txt`, `expected.json`; `datasets/new/manifest.json` lists cases. Keep the old dataset separately under `datasets/existing/`. Clearly label synthetic development fixtures and do not count them toward the final dataset without instructor acceptance.

Expected JSON schema:

```json
{
  "schema_version": 1,
  "matches": [
    {"old_lines": [1], "new_lines": [1], "kind": "unchanged"},
    {"old_lines": [4], "new_lines": [7, 8], "kind": "split"}
  ],
  "deleted": [2, 3],
  "added": [2, 3, 4, 5, 6]
}
```

This is a format illustration, not a complete annotated file. Gold data has no predicted confidence score. Manifest entries contain `id`, `old`, `new`, `expected`, `source_project`, `source_url`, `source_path`, `old_revision`, `new_revision`, `license`, `edit_categories`, `annotator`, `reviewer`, and `split` (development/test). Paths are relative to the manifest directory.

## Annotation procedure

Mujtaba records all mappings and unmatched lines manually; a second team member reviews each case. Resolve ambiguous duplicates together and record the rationale. Check line bounds, coverage, and exclusive group ownership automatically. Freeze a versioned gold dataset before final testing. Never generate gold labels from our own tool's predictions.

Include additions/deletions, renamed identifiers, reordered blocks, splits, formatting changes, repeated lines, and context changes. Seek multiple languages to support language-independent claims. Keep revisions of the same source/project together when splitting development from held-out testing where feasible; record limitations for a small dataset.

## Metrics

Expand each mapping group into old/new pairs. A split from line 4 to lines 7–8 contributes (4,7) and (4,8). Compare predicted pair set P to gold pair set G:

- True positives: intersection of P and G.
- Precision: TP / size(P).
- Recall: TP / size(G).
- F1: harmonic mean of precision and recall.
- For zero denominators: if both sets are empty, precision/recall/F1 are 1; if only one is empty, all three are 0. Report empty cases separately so they do not obscure performance.

Report aggregate micro scores, per-file scores, split-group exact accuracy, and runtime. Also report unmatched-line classification accuracy separately. Compare advanced matching against our unchanged-only baseline. Record hardware/runtime, dataset version, code revision, and settings. Present old/new dataset results separately. Research-slide percentages cannot be compared directly unless their metric and benchmark versions are reproduced.
