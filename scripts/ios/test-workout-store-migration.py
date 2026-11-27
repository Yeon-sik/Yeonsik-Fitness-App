"""Exercise the installed simulator app against its previous workout file format."""
import json
import subprocess
import sys
import time
import uuid
from pathlib import Path


def check(simulator, bundle, records):
    def launch():
        subprocess.run(["xcrun", "simctl", "launch", simulator, bundle], check=True)

    def stop():
        subprocess.run(["xcrun", "simctl", "terminate", simulator, bundle], capture_output=True)

    original = records.read_bytes()
    current = json.loads(original)
    # Versions 1 and 2 predate catalog metadata even when the typed name matches a preset.
    for item in [*current["routines"], *current["history"]]:
        for exercise in item["exercises"]:
            exercise.pop("catalog", None)
    routine = current["routines"][0]
    routine_exercise = routine["exercises"][0]["id"]
    sample = current["history"][0]
    sessions = []
    for index in range(3):
        session = json.loads(json.dumps(sample))
        session["id"] = str(uuid.UUID(int=index + 100))
        session["exercises"][0]["id"] = routine_exercise
        session["exercises"][0].pop("routineExerciseID", None)
        old_set = session["exercises"][0]["sets"][0]
        old_set["id"] = str(uuid.UUID(int=index + 200))
        old_set.pop("isCompleted")
        sessions.append(session)
    sessions[2].pop("completedAt", None)
    legacy = dict(current, schemaVersion=1, history=sessions[:2], inProgress=sessions[2])
    def migrate(fixture):
        stop()
        records.write_text(json.dumps(fixture))
        launch()
        until = time.monotonic() + 30
        while time.monotonic() < until:
            migrated = json.loads(records.read_bytes())
            if migrated["schemaVersion"] == 3:
                return migrated
            time.sleep(0.25)
        raise AssertionError("App did not persist the migrated workout file")

    try:
        migrated = migrate(legacy)
        assert migrated["routines"] == legacy["routines"]
        seen = set()
        migrated_sessions = [*migrated["history"], migrated["inProgress"]]
        assert len(sessions) == len(migrated_sessions)
        for before, after in zip(sessions, migrated_sessions):
            exercise = after["exercises"][0]
            assert "catalog" not in exercise, "A historical free-text exercise was reassigned to a catalog preset"
            expected = uuid.uuid5(uuid.UUID(before["id"]), routine_exercise.lower())
            assert uuid.UUID(exercise["id"]) == expected and expected not in seen
            seen.add(expected)
            assert exercise["routineExerciseID"].lower() == routine_exercise.lower()
            assert [s["id"].lower() for s in exercise["sets"]] == [s["id"].lower() for s in before["exercises"][0]["sets"]]
            assert exercise["sets"][0] == dict(before["exercises"][0]["sets"][0], isCompleted=True)
            assert uuid.UUID(after["id"]) == uuid.UUID(before["id"])
        # Version 2 already has occurrence IDs and may contain two workouts using the same routine.
        version_two = json.loads(json.dumps(migrated))
        version_two["schemaVersion"] = 2
        for session in [*version_two["history"], version_two["inProgress"]]:
            for exercise in session["exercises"]:
                for logged_set in exercise["sets"]:
                    logged_set.pop("isCompleted")
        assert migrate(version_two) == migrated
        first = records.read_bytes()
        stop()
        launch()
        time.sleep(2)
        assert records.read_bytes() == first
        incomplete = json.loads(json.dumps(version_two))
        del incomplete["history"][0]["exercises"][0]["sets"][0]["weightKg"]
        for invalid in (json.dumps(incomplete), '{"schemaVersion":99}', '{"schemaVersion":2,'):
            stop()
            raw = invalid.encode()
            records.write_bytes(raw)
            launch()
            time.sleep(2)
            assert records.read_bytes() == raw, "Unreadable or unsupported file was overwritten"
    finally:
        stop()
        records.write_bytes(original)


if __name__ == "__main__":
    check(sys.argv[1], sys.argv[2], Path(sys.argv[3]))
