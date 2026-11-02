# Decisions and open questions

## Proposed defaults

- Name: comp3110-line-tracker; private GitHub repository.
- Java command-line tool; no GUI required initially.
- Implement an LHDiff-inspired pipeline; verify whether exact reproduction is required.
- Keep original line numbers; use normalized copies only for comparison.
- JSON for machine-readable mappings and evaluation; plain text for the required line-pair output.
- No implementation or accuracy claims in the initial scaffold.

## Ask the professor

1. Are bug-introducing change detection and exact LHDiff reproduction mandatory?
2. Where are the existing dataset, reference paper/code, and expected output specification?
3. Does the new dataset require 25 distinct old/new file pairs? Are synthetic mutations acceptable?
4. Are there restrictions on language, libraries, use of existing diff/SimHash implementations, or AI assistance?
5. Are line merges required alongside splits? What matching threshold and normalization details are expected?
6. What are the deadline, grading rubric, team contribution requirements, and report/demo/submission formats?

## Team decisions before advanced matching

Freeze tokenization, empty-context behavior, thresholds, tie handling, and split limits in a short decision entry after testing development fixtures. Do not tune final settings using held-out test results. Replace the project owner's label with their preferred name when available. GitHub collaborator invitations require members' GitHub usernames.
