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
    routine = current["routines"][0]
    routine_exercise = routine["exercises"][0]["id"]
    sample = current["history"][0]
    sessions = []
    for index in range(3):
        session = json.loads(json.dumps(sample))
        session["id"] = str(uuid.UUID(int=index + 100))
        session["exercises"][0]["id"] = routine_exercise
        session["exercises"][0].pop("routineExerciseID", None)
        session["exercises"][0]["sets"][0]["id"] = str(uuid.UUID(int=index + 200))
        sessions.append(session)
    sessions[2].pop("completedAt", None)
    legacy = dict(current, schemaVersion=1, history=sessions[:2], inProgress=sessions[2])
    try:
        stop()
        records.write_text(json.dumps(legacy))
        launch()
        until = time.monotonic() + 30
        while time.monotonic() < until:
            migrated = json.loads(records.read_bytes())
            if migrated["schemaVersion"] == 2:
                break
            time.sleep(0.25)
        assert migrated["schemaVersion"] == 2 and migrated["routines"] == legacy["routines"]
        seen = set()
        migrated_sessions = [*migrated["history"], migrated["inProgress"]]
        assert len(sessions) == len(migrated_sessions)
        for before, after in zip(sessions, migrated_sessions):
            exercise = after["exercises"][0]
            expected = uuid.uuid5(uuid.UUID(before["id"]), routine_exercise.lower())
            assert uuid.UUID(exercise["id"]) == expected and expected not in seen
            seen.add(expected)
            assert exercise["routineExerciseID"].lower() == routine_exercise.lower()
            assert [s["id"].lower() for s in exercise["sets"]] == [s["id"].lower() for s in before["exercises"][0]["sets"]]
            assert exercise["sets"][0]["inputWeight"] == before["exercises"][0]["sets"][0]["inputWeight"]
            assert uuid.UUID(after["id"]) == uuid.UUID(before["id"])
        first = records.read_bytes()
        stop()
        launch()
        assert records.read_bytes() == first
    finally:
        stop()
        records.write_bytes(original)


if __name__ == "__main__":
    check(sys.argv[1], sys.argv[2], Path(sys.argv[3]))
