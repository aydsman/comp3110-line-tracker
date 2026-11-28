# Development fixtures

Seven synthetic fixtures exercise identity, insertions/deletions, empty inputs and an exact split. They are separate from the 25 real source files. Their expected mappings are preserved independently of matcher output.

Two histories used the path `identical/` for different fixtures. Both remain intact:

| Manifest ID | Path | Original contents | Evidence |
| --- | --- | --- | --- |
| `identical-foundation` | `identical/` | Three lines: `int value = 1;`, blank, `return value;` | Ayden foundation commit `933763e319f6ba8b9d90defc04ab7f49d5d92cfd`; existing Java tests still use this path. No separate annotation-review claim. |
| `identical-qa` | `identical-qa/` | Two lines: `alpha`, `beta` | Original QA case ID `identical` and original reviewed paths retained in manifest metadata. The original source review remains attached to those bytes. |

Both entries record old/new/expected SHA-256 values. The relocation changes the QA fixture's location and current ID, not its source bytes or answers. `insert-delete`, `both-empty`, `empty-to-content` and `content-to-empty` retain the original QA review. `split-wrap` references the November 20 AI-assisted source review by Codex for Ayden; old line 1 maps to new lines 1–2 and old line 2 maps to new line 3. These reviews do not grant approval of the release.

`python3 validate_dataset.py` checks these fixtures together with the real cases. The [candidate QA command](../README.md) keeps synthetic accuracy separate from real development/test results and records hashes before and after execution.
