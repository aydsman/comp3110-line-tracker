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

1. Is bug tracing mandatory, and is exact LHDiff reproduction required?
2. Where are the existing benchmark dataset and reference implementation/paper?
3. Does the new dataset require 25 distinct old/new file pairs, and are synthetic mutations acceptable?
4. What language/library/AI-assistance restrictions apply?
5. Are many-to-one merges required alongside one-to-many splits? Are exact normalization/threshold rules prescribed?
6. What are the official cutoff time, grading rubric, report/demo requirements and contribution evidence expectations?

## Remaining team choices

Before Task 2 integration freeze tokenization, SimHash details, context empty-vector handling, threshold, conflict tie rules and split limits. Tune only on development examples. Select JavaFX/JSON dependencies when needed. Do not make benchmark accuracy claims from synthetic fixtures or copy research-slide results as our own.
