# Team work plan

No course deadlines were provided. These are ordered milestones, not promised dates.

## Milestone 0 — agree before implementation

All: confirm language, goals, deadlines, and rubric; read the architecture and dataset contracts. Integration lead freezes the initial interface definitions. Mujtaba prepares two tiny annotated file pairs immediately so Ali and Aadi can share fixtures. Each member opens a branch for their first task.

## Milestone 1 — first end-to-end baseline

- Ali: file reading, normalization, unchanged diff matches, text/JSON output.
- Aadi: design candidate/scoring interfaces and implement similarity functions on the shared tiny fixtures.
- Mujtaba: dataset manifest, annotation guide, first five distinct file pairs; locate the old benchmark dataset.
- Integration lead: shared models, CLI entry point, pipeline, test setup and CI.

Exit: one command maps identical files and reports additions/deletions; shared formats are validated. No advanced accuracy claims yet.

## Milestone 2 — advanced matching

- Aadi: deterministic SimHash shortlist, exact scoring, conflict resolution, then split detection.
- Ali: integrate remaining-line handling and validate original line numbering/output.
- Mujtaba: grow to 15 reviewed file pairs, establish baseline evaluation and development subset.
- Integration lead: connect stages, help with split/conflict cases, add regression fixtures.

Exit: edited, moved, and split-line examples pass; evaluation produces precision/recall/F1 and runtime with settings recorded.

## Milestone 2b — JavaFX frontend

Project owner: build file pickers, Compare action, side-by-side original files, mapping selection/highlights, and export. Call the existing pipeline in a background task. Ali supports output integration; Aadi supplies mapping examples; Mujtaba supplies reviewed demo fixtures.

Exit: the GUI produces the same mappings as the CLI on identical inputs/settings, handles split highlights and file errors, and stays responsive. Complete this before the final demo; keep evaluation runnable independently.

## Milestone 3 — complete evaluation

- Mujtaba: finish at least 25 distinct files, each with two versions and verified mappings; run new and existing datasets separately.
- Aadi: freeze settings after development experiments; investigate failures without tuning against held-out answers.
- Ali: harden file errors and output documentation.
- Integration lead: reproduce evaluation from a clean checkout, organize final evidence, coordinate report/demo if required.

Exit: reproducible results, dataset provenance, known limitations, and no invented benchmark claims.

## Milestone 4 — bug tracing

Integration lead owns a small verified historical example; others help validate mappings. Schedule this after the core pipeline unless the professor confirms it is required earlier.

## Proposed initial GitHub issues

1. Integration lead — agree models, settings and CLI contract (blocks integration).
2. Ali — implement source loading and normalization (depends on 1).
3. Ali — unchanged baseline and output formatting (depends on 2).
4. Aadi — exact content/context similarity (depends on 1).
5. Aadi — stable SimHash candidate shortlist (depends on 1).
6. Aadi + integration lead — conflict resolution and split detection (depends on 4–5).
7. Mujtaba — manifest and first five reviewed pairs (start immediately).
8. Mujtaba — evaluation metrics and baseline comparison (depends on agreed mapping format).
9. Integration lead — pipeline, CLI and regression checks (start with 1; integrate 3–6).
10. Mujtaba — complete 25-file dataset and final evaluation (depends on 7–9).
11. Integration lead — verified bug-fix tracing prototype (after core milestones).
12. Integration lead — JavaFX frontend and GUI/CLI consistency checks (after baseline, using shared pipeline).
13. All — final reproducibility review and submission checklist.

## Definition of done

A task includes understandable code, meaningful verification, updated documentation, and a reviewed pull request. A working demonstration alone does not replace the required dataset and evaluation. Everyone can explain their own implementation and the overall pipeline.
