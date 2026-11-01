#!/usr/bin/env python3
"""Require real, owner-class-matched JUnit results for every core instrumentation class."""
import argparse
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ElementTree


def room_columns(block):
    columns, current = {}, None
    for line in block.splitlines():
        line = line.strip()
        if line.startswith("Column {"):
            current = {}
        elif current is not None and line.startswith("}"):
            name = current.pop("name", None)
            if name:
                columns[name] = current
            current = None
        elif current is not None:
            match = re.match(r"(\w+)\s*=\s*'(.*)',?$", line)
            if match:
                current[match.group(1)] = match.group(2)
    return columns


def room_diff(text):
    """Reduces Room's Expected/Found TableInfo dump to the columns and indices that differ."""
    if "Expected:" not in text or "Found:" not in text:
        return None
    expected, found = text.split("Expected:", 1)[1].split("Found:", 1)
    want, have = room_columns(expected), room_columns(found)
    lines = []
    for name in sorted(set(want) | set(have)):
        if name not in have:
            lines.append(f"  missing column {name}: {want[name]}")
        elif name not in want:
            lines.append(f"  unexpected column {name}: {have[name]}")
        elif want[name] != have[name]:
            changes = [f"{key} expected {want[name].get(key)} found {have[name].get(key)}"
                       for key in sorted(set(want[name]) | set(have[name]))
                       if want[name].get(key) != have[name].get(key)]
            lines.append(f"  {name}: " + "; ".join(changes))
    index = r"Index\s*\{\s*name\s*=\s*'([^']+)'"
    want_index, have_index = set(re.findall(index, expected)), set(re.findall(index, found))
    lines += [f"  missing index {name}" for name in sorted(want_index - have_index)]
    lines += [f"  unexpected index {name}" for name in sorted(have_index - want_index)]
    return lines or ["  (no column or index difference; compare foreign keys in the XML report)"]


def verify_results(root, classes, allowed_skips):
    valid = True
    rows = []
    for class_name in classes:
        reports = sorted((root / class_name).rglob("TEST-*.xml"))
        tests = failures = skipped = 0
        problems = []
        seen = set()
        if not reports:
            problems.append("No JUnit XML report was produced")
        for report in reports:
            try:
                suite = ElementTree.parse(report).getroot()
            except (OSError, ElementTree.ParseError) as error:
                problems.append(f"Cannot read {report.name}: {error}")
                continue
            for case in suite.iter("testcase"):
                if case.get("classname") != class_name:
                    problems.append(f"Unexpected testcase class: {case.get('classname')!r}")
                    continue
                key = f"{class_name}#{case.get('name', '')}"
                if key in seen:
                    problems.append(f"Duplicate testcase: {key}")
                seen.add(key)
                tests += 1
                if case.find("skipped") is not None:
                    skipped += 1
                    reason = allowed_skips.get(key)
                    if not reason:
                        problems.append(f"Unapproved skipped test: {key}")
                    else:
                        print(f"Allowed skip {key}: {reason}")
                for problem in list(case.findall("failure")) + list(case.findall("error")):
                    failures += 1
                    full = (problem.text or problem.get("message") or "").strip()
                    lines = full.splitlines()
                    diff = room_diff(full)
                    if diff is not None:
                        stack = [line for line in lines if line.strip().startswith("at ")][:8]
                        lines = lines[:1] + ["Room schema difference:"] + diff + stack
                    print(f"\n--- FAILED {key}")
                    print("\n".join(lines[:40]))
        if tests - skipped == 0:
            problems.append("No non-skipped test executed for this class")
        passed = not problems and failures == 0
        valid = valid and passed
        rows.append((class_name, tests, failures, skipped, passed))
        for problem in problems:
            print(f"\n--- INVALID {class_name}: {problem}")
    print("\nCore instrumentation summary (tests / failed / skipped):")
    for name, tests, failures, skipped, passed in rows:
        print(f"{'ok' if passed else 'FAIL':4} {tests:3} {failures:3} {skipped:3}  {name}")
    print(f"Total: {sum(row[1] for row in rows)} tests, "
          f"{sum(row[2] for row in rows)} failed, {sum(row[3] for row in rows)} skipped")
    return valid and bool(classes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=pathlib.Path)
    parser.add_argument("classes", nargs="+")
    args = parser.parse_args()
    skip_file = pathlib.Path(__file__).with_name("android-core-instrumented-skips.json")
    allowed_skips = json.loads(skip_file.read_text(encoding="utf-8"))
    if not isinstance(allowed_skips, dict) or any(
        not isinstance(reason, str) or not reason.strip() for reason in allowed_skips.values()
    ):
        raise ValueError("Every allowed skip must have a documented reason")
    return 0 if verify_results(args.results, args.classes, allowed_skips) else 1


if __name__ == "__main__":
    sys.exit(main())
