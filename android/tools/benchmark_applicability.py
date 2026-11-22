"""Report the historical writer comparison boundary under D061 and D063.

The original generator and frozen hashes remain unchanged. Missing/corrupt historical
objects and changes outside the accepted season, codec and Room contracts are hard failures.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "7ebb4a46a319ffde5e390a86b8f0a3a0352e4866"
OLD_SOURCE = "android/app/src/main/java/com/example/baseballsim/data/RoomSeasonStore.kt"
BASELINE_BLOB = "4cebd3e9bd283d0b0e70e70c81070edf17fae704"
ALLOWED = {
    "android/app/src/main/java/com/example/baseballsim/data/SeasonStore.kt",
    "android/app/src/main/java/com/example/baseballsim/data/PennantDatabase.kt",
    *[f"android/core/src/main/kotlin/com/example/baseballsim/season/{name}.kt" for name in
      ("Schedule", "Season", "Records", "Checkpoint", "CheckpointCodec")],
}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assert git("rev-parse", f"{BASELINE}:{OLD_SOURCE}").decode().strip() == BASELINE_BLOB
    git("show", f"{BASELINE}:{OLD_SOURCE}")  # Missing source must never become not_applicable.
    generator_path = "android/tools/generate_store_benchmark.py"
    approved_generator = git("show", f"bfb26e557ba56987fbdb468a10d62e5affc59caf:{generator_path}")
    current_generator = (ROOT / generator_path).read_text(encoding="utf-8").encode("utf-8")
    assert current_generator == approved_generator, "Historical generator changed; cannot assert frozen provenance"
    paths = ["android/app/src/main/java/com/example/baseballsim/data/SeasonStore.kt",
             "android/app/src/main/java/com/example/baseballsim/data/PennantDatabase.kt",
             *git("ls-tree", "-r", "--name-only", BASELINE, "android/core/src/main").decode().splitlines(),
             "android/app/schemas/com.example.baseballsim.data.PennantDatabase/1.json"]
    changed = []
    for path in paths:
        old = git("show", f"{BASELINE}:{path}")
        new = (ROOT / path).read_text(encoding="utf-8").encode("utf-8")
        if old != new:
            assert path in ALLOWED, f"Unexpected benchmark dependency change: {path}"
            changed.append({"path": path, "historical_sha256": digest(old), "current_sha256": digest(new)})
    supported = not changed
    report = {"schema": "historical-benchmark-applicability-v1", "source_head": git("rev-parse", "HEAD").decode().strip(),
              "baseline_commit": BASELINE, "baseline_blob_verified": BASELINE_BLOB,
              "status": "applicable" if supported else "not_applicable",
              "comparison_executed": False, "performance_gate_passed": None,
              "reason": "Shared dependencies unchanged; run the original strict generator" if supported else
              "D061 changes the season/codec contract and D063 adds Room annual history; the historical shared-dependency premise no longer holds",
              "changed_dependencies": changed,
              "historical_generator_and_hashes_unchanged": True,
              "historical_generator_sha256": digest(approved_generator),
              "baseline_helper_scope": "Historical writer compiled with current v1-compatible helpers; not an old APK or a fair new ABBA comparison",
              "correctness_tests_required_separately": True}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
            out.write(f"applicable={'true' if supported else 'false'}\n")
    print(json.dumps({"status": report["status"], "changed_dependency_count": len(changed), "performance_gate_passed": None}))


if __name__ == "__main__":
    main()
