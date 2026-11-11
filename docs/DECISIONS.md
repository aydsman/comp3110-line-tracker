# Decisions and open questions

## Adopted foundation

- Java 17, Maven 3.9.9 wrapper and JUnit 5; plain Java core with a future JavaFX frontend.
- Concrete shared records and LineMatcher interface in ARCHITECTURE.md; Ali coordinates subsequent contract changes.
- Original one-based positions retained; comparison copies lowercase and collapse whitespace.
- Deterministic LCS baseline with explicit unmatched lines and a 4-million-cell memory guard.
- Normalized Levenshtein utility exposed for Task 2; no edited-line mappings claimed yet.
- Ayden prepared initial infrastructure to unblock the team; ongoing ownership remains Ayden backend, Aadi frontend, Ali integration, Mujtaba QA.
- Four per-person task sections due Nov 7, 12, 17 and final preparation Nov 19/submission Nov 20, 2026.
- Two synthetic fixtures support development only and await independent review.

## Confirm with the professor

As of Nov 11, 2026, the rubric, existing benchmark source/access and whether bug tracing is mandatory remain unconfirmed. The repository and Trello plan do not supply those answers. Keep the following questions open; implementation progress does not resolve them.

1. Is bug tracing mandatory, and is exact LHDiff reproduction required?
2. Where are the existing benchmark dataset and reference implementation/paper?
3. Does the new dataset require 25 distinct old/new file pairs, and are synthetic mutations acceptable?
4. What language/library/AI-assistance restrictions apply?
5. Are many-to-one merges required alongside one-to-many splits? Are exact normalization/threshold rules prescribed?
6. What are the official cutoff time, grading rubric, report/demo requirements and contribution evidence expectations?

## Remaining team choices

The Task 2 matcher uses the starting settings documented in ARCHITECTURE.md. Freeze the final threshold and split settings before held-out evaluation. Tune only on development examples. Select JavaFX/JSON dependencies when needed. Do not make benchmark accuracy claims from synthetic fixtures or copy research-slide results as our own.

## Ali Task 1 integration decisions

- Reuse Ayden's foundation from unmerged [pull request #1](https://github.com/aydsman/comp3110-line-tracker/pull/1), preserving authorship and existing public records/interface.
- Keep the path pipeline API and add `compare(SourceFile, SourceFile, MatchConfig)` so GUI panels can display the exact source snapshots used by the matcher.
- Reject null inputs/results, mismatched result config and incomplete/reused/out-of-range line coverage at the pipeline boundary. Include old/new input role and UTF-8 path context when a path read fails, retaining its original cause.
- Use `TextMappingFormatter.format(MappingResult)` for sorted plain `old-new` pairs with `\n` separators. Preserve CLI stdout/stderr and exit-code conventions for QA; defer JSON to Task 2.
- Collapse Unicode whitespace before trimming the comparison text, removing boundary non-breaking spaces while retaining original source text.
- Run packaged-JAR integration checks in Maven `verify`. A successful unit test or `package` command alone does not prove the runnable CLI contract.
- Keep foundation fixtures intact until QA integration deliberately resolves the different two-line/three-line `identical` cases. Draft annotation metadata remains pending teammate review.

These decisions describe this implementation for review. They do not assert unanimous team agreement, instructor approval, reviewed gold labels or successful JavaFX integration.

## Ali Task 2 integration decisions

- Preserve `map OLD NEW` as baseline text for existing QA consumers; advanced matching and JSON are explicit options.
- Serialize prediction groups using versioned snake_case JSON with finite numeric scores and all requested config fields. Retain groups in JSON and expand groups in text; use the same formatters for future GUI exports.
- Parse all arguments before I/O. Exports create a new UTF-8 file; existing destinations, including aliases of inputs, are refused.
- Keep prediction semantics separate from independently reviewed gold annotations. The current evaluator only consumes baseline text; advanced/JSON evaluation requires a separate QA update.
- Keep actual JavaFX equivalence and optional bug-tracing acceptance open until the frontend and course requirements are available.

## Branch policy

`development` is the shared working/integration branch. Task pull requests target development. Reviewed and tested releases move from development to main through a separate pull request; direct main pushes are outside the team workflow.
