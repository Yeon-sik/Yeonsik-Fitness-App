"""Capture the four product tabs and the body editor on a disposable emulator."""

import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("emulator")
    parser.add_argument("output", type=Path)
    parser.add_argument("--seed-weight", action="store_true", help="Add 72.5 kg to today's empty test fixture")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.emulator):
        parser.error("Use a disposable emulator containing only synthetic records.")
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*command):
        return subprocess.check_output(["adb", "-s", args.emulator, *command], timeout=40)

    def hierarchy():
        filename = "/data/local/tmp/fitness-parity.xml"
        adb("shell", "uiautomator", "dump", filename)
        return ET.fromstring(adb("exec-out", "cat", filename))

    def bounds(node):
        return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))

    def tap_node(node):
        left, top, right, bottom = bounds(node)
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def tap(label):
        nodes = [n for n in hierarchy().iter("node") if n.get("text") == label]
        if not nodes:
            raise RuntimeError(f"Missing {label}")
        # The tab bar and Home's quick action are below matching titles in the content.
        tap_node(max(nodes, key=lambda node: bounds(node)[1]))

    def capture(name, label, tab=False):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            root = hierarchy()
            nodes = root.iter("node")
            if tab:
                nodes = (child for node in nodes if node.get("selected") == "true" for child in node.iter("node"))
            if any(node.get("text") == label for node in nodes):
                (args.output / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
                return
        raise RuntimeError(f"{name} never displayed {label}")

    adb("shell", "am", "start", "-W", "-n", "com.yeonsik.fitnessapp/.MainActivity")
    if args.seed_weight:
        tap("메인")
        tap("체중")
        nodes = list(hierarchy().iter("node"))
        field = next(n for n in nodes if n.get("class") == "android.widget.EditText")
        if field.get("text") or not any(n.get("text") == "체중 kg" for n in nodes):
            raise RuntimeError("Use an empty synthetic day and the kg unit before seeding")
        tap_node(field)
        adb("shell", "input", "text", "72.5")
        tap("저장")
    for name, label in [("home", "메인"), ("fitness", "피트니스"), ("records", "기록"), ("settings", "설정")]:
        tap(label)
        capture(name, label, tab=True)
        if name == "home":
            tap("체중")
            capture("weight-editor", "저장")
            tap("취소")
    environment = {
        key: adb("shell", *command).decode().strip()
        for key, command in {
            "size": ["wm", "size"], "density": ["wm", "density"],
            "font_scale": ["settings", "get", "system", "font_scale"],
            "system_night": ["cmd", "uimode", "night"],
        }.items()
    }
    (args.output / "environment.json").write_text(json.dumps(environment, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
