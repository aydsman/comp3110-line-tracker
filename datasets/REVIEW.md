# Mujtaba's dataset review

Fifteen distinct real source files are prepared under `new/`. Each case contains the original downloaded bytes, expected mappings, and upstream license copies for both revisions. `new/manifest.json` records source URLs, release tags, SHA-256 checksums, annotation rationale, and review status. Ten cases are development data and five whole projects are reserved test data. No matcher settings were tuned using these results.

All cases are drafts. Codex prepared the annotations for Mujtaba. A teammate must independently inspect them and supply their own review before these are counted as reviewed pairs. No matching-program predictions were used as gold labels.

| Case | Old/new lines | Mappings to inspect |
| --- | --- | --- |
| Requests version | 14 / 14 | All lines map to the same number. Version and build lines 8 and 9 change. |
| Flask entry point | 15 / 14 | Copyright line 8 changes. Retain blank 11 and delete duplicate blank 12. Old 13–15 map to new 12–14. |
| urllib3 version | 4 / 4 | All lines map to the same number. Line 4 changes. |
| Certifi entry point | 2 / 12 | Old import 1 maps to new 3. Old print 2 maps to new 12. Other new lines are additions. |
| IDNA version | 2 / 2 | Version line 1 changes. Blank line 2 is retained. |
| is-stream functions | 28 / 29 | 24 mappings, including function renaming and condition rewrites. Four old lines are deleted and five function-closing braces are added. |
| escape-string-regexp | 13 / 11 | Old 3-13 map to new 1-11. Delete the strict directive and separator. Declaration, explanatory comment and terminator change. |
| strip-bom (test) | 14 / 15 | Old 1 stays at 1. Old 2-14 map to new 3-15. New blank 2 is added. Parameter and error construction change. |
| is-plain-obj (test) | 10 / 10 | All lines keep their numbers. Line 9 simplifies the prototype expression. |
| is-unicode-supported (test) | 11 / 12 | Old 3 changes the Linux-console condition. New 8 adds an environment check. Old 8-11 shift to 9-12. |
| is-path-inside | 12 / 12 | Delete strict directive 1, rewrite import/export, add separator 5. The return block retains lines 6-11. |
| is-fullwidth-code-point | 46 / 50 | Old 1 and 2 swap. Old 3-46 map to new 4-47. Rename x to codePoint and add explicit exports at 49-50. |
| is-docker (test) | 29 / 29 | Remove strict directive, rename cache, simplify catches, name exported function, and add TODO at new 23. |
| path-exists (test) | 23 / 19 | Old import 2 maps to 1, separator 6 to 2, and functions 7-23 to 3-19. Delete old 1,3,4,5 and replace the promisify helper with fs promises. |
| is-obj | 6 / 4 | Delete old 1-2. Old 3-6 map to new 1-4, with a named export and changed terminator. |

The draft uses `modified` for edited lines and exact text equality for `unchanged`. The baseline code defines MODIFIED, consistent with the lowercase JSON label. Confirm the exact-text annotation convention with the team before freezing the dataset. In particular, Certifi's print line gains indentation, which the baseline normalizer may ignore. Flask's duplicate blank lines permit equivalent alternatives, so the reviewer must confirm the recorded convention before freezing the labels.

The five small synthetic fixtures under `development/` cover identical input, insertion/deletion, both files empty, empty-to-content, and content-to-empty. They are separate from the real source pairs and do not count toward the required final 25 files.

Run structural validation from the project folder with `python validate_dataset.py`. It checks JSON structure, complete and exclusive line ownership, bounds, unchanged text, distinct real source identities, provenance fields, license-file presence, and source checksums. It does not run the Java matcher or measure accuracy.

The [preliminary baseline results](../docs/qa/baseline-draft.json) record the baseline code revision, Java version, configuration, source and annotation hashes, predicted pairs, and missing pairs. That run found 30 of 36 draft expected pairs, with no unexpected pairs. It used Ayden's unmerged baseline branch, whose 12 JUnit tests passed locally. The development base of this dataset PR does not yet contain that Java build. These five unreviewed development cases do not establish final accuracy.

This batch uses small Python and JavaScript files. It includes metadata changes, renamed functions/variables, line reordering, conditions, import/export rewrites, and deleted helpers. The final 25-file collection still needs larger examples and real line splits. Existing benchmark acquisition remains outstanding. Test projects are strip-bom, is-plain-obj, is-unicode-supported, is-docker and path-exists. Keep their labels out of parameter tuning. All current annotations remain unreviewed, including this reserved partition.

The current 15-case run is recorded separately for [development](../docs/qa/task2-development.json) and [reserved test](../docs/qa/task2-test.json), with [synthetic fixture results](../docs/qa/task2-fixtures.json) kept separate. The unchanged-only baseline found 74 of 132 draft development pairs and 57 of 82 draft test pairs. Four unexpected development pairs are alternative blank-line matches in is-stream under the recorded convention. All five synthetic fixtures passed. These preliminary counts await independent annotation review. The matcher binary, configuration and dataset hashes are recorded in each report. No real split groups are present yet, so real-data split accuracy is null.

The sixth case adds JavaScript function rewrites from is-stream v2.0.1 to v3.0.0. Match renamed declarations and corresponding conditions by function context. Its four unchanged separator blanks map 7->6, 13->13, 19->20 and 23->25. The leading and trailing old blanks are deleted. Repeated blank-line identities admit alternative conventions, so the reviewer must confirm these context-based choices before freezing the labels. The baseline run saved locally as `results/new-six-baseline.json` found 0 of its 24 draft expected pairs. This expected limitation of unchanged-only matching should be reassessed against the advanced matcher after labels are reviewed.

After review, enter the actual reviewer's name and update the review status in the corresponding manifest. Retain the source attribution and licenses when moving these files into the team's repository.
