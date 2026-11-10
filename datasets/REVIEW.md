# Mujtaba's dataset review

Six distinct real source files are prepared under `new/`. Each case contains the original downloaded bytes, expected mappings, and upstream license copies for both revisions. `new/manifest.json` records source URLs, release tags, SHA-256 checksums, annotation rationale, and review status.

All cases are drafts. Codex prepared the annotations for Mujtaba. A teammate must independently inspect them and supply their own review before these are counted as reviewed pairs. No matching-program predictions were used as gold labels.

| Case | Old/new lines | Mappings to inspect |
| --- | --- | --- |
| Requests version | 14 / 14 | All lines map to the same number. Version and build lines 8 and 9 change. |
| Flask entry point | 15 / 14 | Copyright line 8 changes. Retain blank 11 and delete duplicate blank 12. Old 13–15 map to new 12–14. |
| urllib3 version | 4 / 4 | All lines map to the same number. Line 4 changes. |
| Certifi entry point | 2 / 12 | Old import 1 maps to new 3. Old print 2 maps to new 12. Other new lines are additions. |
| IDNA version | 2 / 2 | Version line 1 changes. Blank line 2 is retained. |
| is-stream functions | 28 / 29 | 24 mappings, including function renaming and condition rewrites. Four old lines are deleted and five function-closing braces are added. |

The draft uses `modified` for edited lines and exact text equality for `unchanged`. The baseline code defines MODIFIED, consistent with the lowercase JSON label. Confirm the exact-text annotation convention with the team before freezing the dataset. In particular, Certifi's print line gains indentation, which the baseline normalizer may ignore. Flask's duplicate blank lines permit equivalent alternatives, so the reviewer must confirm the recorded convention before freezing the labels.

The five small synthetic fixtures under `development/` cover identical input, insertion/deletion, both files empty, empty-to-content, and content-to-empty. They are separate from the real source pairs and do not count toward the required final 25 files.

Run structural validation from the project folder with `python validate_dataset.py`. It checks JSON structure, complete and exclusive line ownership, bounds, unchanged text, distinct real source identities, provenance fields, license-file presence, and source checksums. It does not run the Java matcher or measure accuracy.

The [preliminary baseline results](../docs/qa/baseline-draft.json) record the baseline code revision, Java version, configuration, source and annotation hashes, predicted pairs, and missing pairs. That run found 30 of 36 draft expected pairs, with no unexpected pairs. It used Ayden's unmerged baseline branch, whose 12 JUnit tests passed locally. The development base of this dataset PR does not yet contain that Java build. These five unreviewed development cases do not establish final accuracy.

This first batch is deliberately small and mostly Python metadata. Later batches need more varied edits, larger files, reordering, line splits, and other languages. Existing benchmark acquisition remains outstanding. All current cases are development data. Reserve separate held-out cases before tuning the matcher.

The sixth case adds JavaScript function rewrites from is-stream v2.0.1 to v3.0.0. Match renamed declarations and corresponding conditions by function context. Its four unchanged separator blanks map 7->6, 13->13, 19->20 and 23->25. The leading and trailing old blanks are deleted. Repeated blank-line identities admit alternative conventions, so the reviewer must confirm these context-based choices before freezing the labels. The baseline run saved locally as `results/new-six-baseline.json` found 0 of its 24 draft expected pairs. This expected limitation of unchanged-only matching should be reassessed against the advanced matcher after labels are reviewed.

After review, enter the actual reviewer's name and update the review status in the corresponding manifest. Retain the source attribution and licenses when moving these files into the team's repository.
