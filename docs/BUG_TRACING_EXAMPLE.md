# Verified project-history example

This example traces the Unicode boundary-whitespace defect repaired in Ali's Task 1. It is a reproducible local history investigation prepared for Task 3 review. Whether the course requires a different project, algorithm or historical tracing interface is still unconfirmed. It is not a general SZZ implementation.

## Reproduction and fix

Compare an old line containing non-breaking spaces around `RETURN X;` with a new line containing `return x;`. The comparison contract ignores boundary whitespace and case, so these should produce one unchanged mapping.

The initial normalizer called `strip()` before replacing Unicode whitespace. Java's stripping step left the non-breaking spaces intact; the replacement then converted them into ordinary leading/trailing spaces. The old normalized string was therefore ` return x; ` and did not equal `return x;`.

| History point | Commit | Expected observation |
| --- | --- | --- |
| Initial implementation | `933763e319f6ba8b9d90defc04ab7f49d5d92cfd` | Bug reproduces: zero matching pairs |
| Parent of the fix | `2f133fc2e52799da717a70449b4a099aedbe30fc` | Bug still reproduces: zero matching pairs |
| Fix | `a2c5f16679cc5d5fea508fbcb42e9bee52837dff` | Unicode whitespace collapses before stripping: one matching pair |

The changed code is `src/main/java/edu/comp3110/linetracker/Normalizer.java`. The fix also added `unicodeBoundaryWhitespaceIsTrimmedWithoutChangingOriginalInput` in `IntegrationContractTest`, including a check that original source text remains unchanged.

## Repeat the investigation

Use a full-history clone and JDK 17 or newer. No network requests are made by this check:

```sh
python3 scripts/verify_bug_history.py --java-home "$JAVA_HOME"
git show a2c5f16 -- src/main/java/edu/comp3110/linetracker/Normalizer.java
git log --follow -- src/main/java/edu/comp3110/linetracker/Normalizer.java
git blame -w 2f133fc -- src/main/java/edu/comp3110/linetracker/Normalizer.java
```

The Python check extracts the three exact historical Java source trees into temporary directories, compiles them independently and runs the same baseline comparison. It writes observations and blame candidates to `target/bug-history.json`; it does not edit the checkout or derive dataset answers from predictions.

## What the evidence establishes

The tested defect is present in the initial implementation and its later pre-fix revision, and absent after the specific fix. Source inspection connects the failure to the operation order. Blame and history identify the initial implementation as a candidate origin, while later formatting changes can affect blame output. There is no earlier implementation of this class in the repository to establish an older good version.

These observations concern this precise input and code path. They do not prove real-world incident causation, establish that a person caused an external bug, or demonstrate arbitrary tracing through renames, merges and semantic edits. A teammate must review the example and the instructor must confirm whether this scope satisfies any course tracing requirement.
