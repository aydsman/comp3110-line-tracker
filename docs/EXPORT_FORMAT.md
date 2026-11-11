# CLI and shared exports

Ali's Task 2 connects Ayden's advanced backend to the existing validated pipeline and adds shared text/JSON output. The default `map OLD NEW` remains baseline text for existing callers. These exports describe predictions; they are not independently reviewed annotation files.

## Commands and options

```text
java -jar target/line-tracker.jar map OLD NEW [--advanced] [--format text|json] [--output FILE] [--max-lcs-cells N] [--candidate-limit N] [--context-radius N] [--minimum-score N]
```

Options follow both paths and may appear in any order, once each. Quote paths containing spaces. `--help` prints usage and exits successfully. Unknown options, repeated options, missing values and invalid values return exit code 2. Comparison, file-read and export-write errors return 1; successful comparison/help returns 0.

| Option | Default | Accepted value and effect |
| --- | --- | --- |
| `--advanced` | Off | Select Ayden's edited/moved/exact-split matcher; otherwise use baseline LCS. |
| `--format` | `text` | `text` or `json`; both are produced by shared formatter APIs. |
| `--output` | Standard output | Create a new UTF-8 file. Existing destinations are refused. |
| `--max-lcs-cells` | `4000000` | Positive Java `long` within its representable range; LCS memory guard in both modes. |
| `--candidate-limit` | `15` | Positive Java `int`; requires `--advanced`. |
| `--context-radius` | `4` | Nonnegative Java `int`; requires `--advanced`. |
| `--minimum-score` | `0.65` | Finite number in `(0,1]`; requires `--advanced`. |

```sh
# Existing baseline command and explicit JSON output:
java -jar target/line-tracker.jar map old.txt new.txt
java -jar target/line-tracker.jar map old.txt new.txt --format json

# Advanced settings and a new export file:
java -jar target/line-tracker.jar map old.txt new.txt --advanced --candidate-limit 10 --context-radius 3 --minimum-score 0.7 --format json --output mappings.json
```

With `--output`, standard output is empty; unmatched-line diagnostics remain on standard error. The destination must not already exist, including an input file. Choose a different filename to retain a prior export. A failed comparison does not produce a successful export. Text/JSON data contains no diagnostic headers or status lines.

## Text contract

`TextMappingFormatter.format(MappingResult)` returns one `old-new` pair per line, sorted by original old then new number. Each pair ends in LF (`\n`), including on Windows. A split contributes a pair for every target line; scores and unmatched lists are absent from text. No matches produces an empty string.

```text
1-2
4-5
4-6
```

The CLI prints unmatched old/new lists separately to standard error. Mujtaba's current evaluator runs the unchanged `map OLD NEW` command and reads only strict text pairs. It does not consume JSON or select advanced matching; extending the evaluator is a separate coordinated change.

## Prediction JSON version 1

`JsonMappingFormatter.format(MappingResult)` returns pretty JSON with a final LF. All field names use snake_case. A complete example for two old lines and three new lines is:

```json
{
  "schema_version": 1,
  "matches": [
    {
      "old_lines": [1],
      "new_lines": [2, 3],
      "kind": "split",
      "score": 1.0
    }
  ],
  "deleted": [2],
  "added": [1],
  "config": {
    "max_lcs_cells": 4000000,
    "candidate_limit": 15,
    "context_radius": 4,
    "minimum_score": 0.65
  }
}
```

This illustrates the supported SPLIT model/export shape. The integrated Task 3 backend produces exact splits of 2–4 consecutive nonblank available new lines; see [architecture](ARCHITECTURE.md#split-detection-ayden-task-3) for reconstruction and priority rules.

| Field | Meaning |
| --- | --- |
| `schema_version` | Integer `1`; consumers should reject unsupported versions. |
| `matches` | Groups sorted by old then new number. Empty results use `[]`. |
| `old_lines` | One original one-based line number. |
| `new_lines` | One original one-based number, or multiple consecutive numbers for a split. |
| `kind` | `unchanged`, `modified`, `moved`, or `split`. |
| `score` | Finite similarity in `[0,1]`; not a calibrated probability. |
| `deleted`, `added` | Sorted unmatched old/new line numbers; never omitted. |
| `config` | Actual requested `MatchConfig` fields, including defaults. |

The baseline only uses `max_lcs_cells`; the remaining config fields are retained for the shared record and advanced mode. Fixed algorithm details such as 0.6/0.4 content/context weights and minimum content similarity 0.5 are code constants, documented in [architecture](ARCHITECTURE.md). Config alone does not identify which matcher or code revision produced a result. Keep the command/mode and exact code revision or JAR hash with reproducibility evidence.

The pipeline validates original bounds, complete coverage and exclusive group ownership before formatting. Formatters do not receive source lengths and cannot establish coverage themselves; callers should pass the result returned by the pipeline. They sort copies and preserve the original records. JSON contains only model numbers and fixed field/enum names, without source paths, original text, timing or annotations.

## Shared frontend API

```java
var config = new MatchConfig(4_000_000, 15, 4, 0.65);
var pipeline = new MappingPipeline(new AdvancedMatcher());
SourceFile oldFile = SourceReader.read(oldPath);
SourceFile newFile = SourceReader.read(newPath);
MappingResult result = pipeline.compare(oldFile, newFile, config);
String text = TextMappingFormatter.format(result);
String json = JsonMappingFormatter.format(result);
```

Aadi can retain `oldFile` and `newFile` for the displayed snapshots, run reading/comparison in a JavaFX background task, and save either formatter string as UTF-8 after handling the destination. No JavaFX dependency enters the core. Shared APIs support equivalence checks, but an actual GUI comparison/export has not yet been verified.

## Prediction and gold-data distinction

Prediction groups share the line/kind shape used by `expected.json`, adding scores and config. They must not replace manually annotated answers. In particular, backend UNCHANGED means equality after lowercasing/whitespace normalization, while the current gold validator requires exact original text equality for `unchanged`. The different conventions require deliberate evaluation rules; running the annotation validator on predictions does not establish their correctness.

QA annotations remain pending teammate review. Synthetic tests and formatter checks establish software behavior, not benchmark accuracy, course approval, or reviewed dataset quality.
