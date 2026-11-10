"""Check annotation structure and line coverage without running a matcher."""

import hashlib
import json
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def source_lines(path):
    """Match Java readAllLines: CR/LF delimit lines, Unicode separators do not."""
    text = path.read_text(encoding="utf-8")
    lines = text.split("\n")
    return lines[:-1] if text.endswith("\n") or not text else lines


def validate_case(manifest_path, case, check_provenance=True):
    base = manifest_path.parent.resolve()

    def case_path(field):
        path = (base / case[field]).resolve()
        require(path.is_relative_to(base), f"{field}: path escapes dataset")
        require(path.is_file(), f"{field}: file missing")
        return path

    old_path = case_path("old")
    new_path = case_path("new")
    old_lines = source_lines(old_path)
    new_lines = source_lines(new_path)
    expected = json.loads(case_path("expected").read_text(encoding="utf-8"))
    require(expected["schema_version"] == 1, "Unsupported schema version")
    ownership = {"old": [], "new": []}
    for group in expected["matches"]:
        require(group["kind"] in {"unchanged", "modified", "moved", "split"}, "Unknown draft kind")
        for side in ("old", "new"):
            numbers = group[f"{side}_lines"]
            require(isinstance(numbers, list) and numbers, "Empty mapping group")
            require(all(type(number) is int for number in numbers), "Line numbers must be integers")
            ownership[side].extend(numbers)
        if group["kind"] == "split":
            require(len(group["old_lines"]) == 1 and len(group["new_lines"]) > 1, "Invalid split group")
            require(group["new_lines"] == list(range(group["new_lines"][0], group["new_lines"][0] + len(group["new_lines"]))), "Split lines must be consecutive")
        else:
            require(len(group["old_lines"]) == len(group["new_lines"]) == 1, "Expected one-to-one group")
        if group["kind"] == "unchanged":
            old_number = group["old_lines"][0]
            new_number = group["new_lines"][0]
            require(1 <= old_number <= len(old_lines), "Old line outside file")
            require(1 <= new_number <= len(new_lines), "New line outside file")
            require(old_lines[old_number - 1] == new_lines[new_number - 1], "Unchanged text differs")
    for side, unmatched, lines in (("old", "deleted", old_lines), ("new", "added", new_lines)):
        require(isinstance(expected[unmatched], list), "Unmatched lines must be a list")
        require(all(type(number) is int for number in expected[unmatched]), "Invalid unmatched line number")
        ownership[side].extend(expected[unmatched])
        require(sorted(ownership[side]) == list(range(1, len(lines) + 1)), f"{side}: bounds, duplicate ownership or missing coverage")
    if check_provenance and not case.get("synthetic"):
        for field in ("source_project", "source_url", "source_path", "old_revision", "new_revision", "license", "annotation_note", "annotator"):
            require(bool(case.get(field)), f"Missing provenance field: {field}")
        case_path("license_file")
        for side, path in (("old", old_path), ("new", new_path)):
            require(hashlib.sha256(path.read_bytes()).hexdigest() == case[f"{side}_sha256"], "Source checksum changed")
    return len(old_lines), len(new_lines)


def main():
    root = Path(__file__).resolve().parent
    total = 0
    for relative in ("datasets/new/manifest.json", "datasets/development/manifest.json"):
        manifest_path = root / relative
        cases = json.loads(manifest_path.read_text(encoding="utf-8"))
        ids = [case["id"] for case in cases]
        require(len(ids) == len(set(ids)), "Duplicate case IDs")
        if relative == "datasets/new/manifest.json":
            sources = {(case["source_url"], case["source_path"]) for case in cases}
            require(len(sources) == len(cases), "Source files must be distinct")
        for case in cases:
            old_count, new_count = validate_case(manifest_path, case)
            print(f"PASS {case['id']}: {old_count} old / {new_count} new lines; peer review {case['review_status']}")
            total += 1
    print(f"Validated {total} cases. These checks do not establish semantic correctness or teammate approval.")


if __name__ == "__main__":
    main()
