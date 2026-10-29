"""Check two fixed-fixture runs, source Git binding and existing game files.

This helper does not open the Statcast research repository or raw/model files.
Run in this checkout; existing verification records are never replaced.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

BASELINE = "1c5b738671a452305a4be874c57f77cd8ad5db10"
SOURCE_COMMIT = "922a2a9c1cb5a76e9d984fc5bd5c9fef2952b7ae"
FILES = ("plan.json", "traces.json", "REPORT.md", "SHA256SUMS.json")
FLAGS = {"source_kind": "synthetic_fixture", "scope": "partial_count_rules", "probability_model": False,
         "connected_to_statcast": False, "connected_to_phase3d": False, "official_stats_generated": False,
         "ability_calibrated": False, "android_integrated": False, "product_adoption": False}


def read(path):
    def reject(value): raise ValueError("non-finite JSON: "+value)
    return json.loads(path.read_text(encoding="utf-8"), parse_constant=reject)


def canonical(value):
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False, allow_nan=False)+"\n").encode()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def normalized(data):
    return data.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n").encode()


def git(*arguments):
    return subprocess.check_output(["git", *arguments])


def write(path, value):
    with path.open("xb") as stream: stream.write(canonical(value))


def verify(first, second, tests, output):
    root = Path.cwd().resolve()
    first, second, tests, output = map(lambda p: Path(p).resolve(), (first, second, tests, output))
    if not all(p.is_relative_to(root) for p in (first, second, tests, output)):
        raise ValueError("paths must stay inside this checkout")
    if not output.is_relative_to(root/"reports/phase4a") or first == second:
        raise ValueError("use Phase4a output and two distinct runs")
    for name in ("reproducibility.json", "verification.json", "test-results.json", "SHA256SUMS.json"):
        if (output/name).exists(): raise FileExistsError("preserve existing verification record")
    records = read(tests)
    if (records.get("source_commit") != SOURCE_COMMIT or records.get("test_methods_run") != 33
            or records.get("successful") is not True
            or any(records.get(key) != 0 for key in ("failures", "errors", "skipped"))):
        raise ValueError("test method record did not pass without skips for fixed source")
    if git("diff", "--name-only", SOURCE_COMMIT, "--", "prototypes", ".github/workflows/phase4a-tests.yml").strip():
        raise ValueError("source/tests/CI differ from the fixed source commit")
    tree = git("ls-tree", "-r", BASELINE).decode()
    baseline = {line.split("\t", 1)[1]: line.split()[2] for line in tree.splitlines()}
    changed = set(git("diff", "--name-only", BASELINE).decode().splitlines())
    if (changed & set(baseline)) - {"README.md", ".gitignore"}:
        raise ValueError("protected baseline game file changed")
    fixture_rows = json.loads(git("show", SOURCE_COMMIT+":prototypes/phase4a/fixtures/scenarios.json"))["scenarios"]
    table = json.loads(git("show", SOURCE_COMMIT+":prototypes/phase4a/fixtures/transitions.json"))["transitions"]
    reference = {(r["before"]["balls"], r["before"]["strikes"], r["event"]): r["after"] for r in table}
    if len(table) != 96 or len(reference) != 96 or len(fixture_rows) != 16:
        raise ValueError("fixed fixture dimensions differ")
    manifest_checks, trace_entries = 0, 0
    for folder in (first, second):
        if {p.name for p in folder.iterdir()} != set(FILES):
            raise ValueError("unexpected run artifact set")
        manifest = read(folder/"SHA256SUMS.json")
        if set(manifest) != set(FILES)-{"SHA256SUMS.json"}:
            raise ValueError("incomplete run hash manifest")
        for name, wanted in manifest.items():
            target = (folder/name).resolve()
            if not target.is_relative_to(folder) or sha(target.read_bytes()) != wanted:
                raise ValueError("run hash mismatch/unsafe path")
            manifest_checks += 1
        plan, traces = read(folder/"plan.json"), read(folder/"traces.json")
        for document in (plan, traces):
            if document.get("design") != "phase4a-design-v0.1" or any(
                    type(document.get(k)) is not type(v) or document.get(k) != v for k, v in FLAGS.items()):
                raise ValueError("run scope/capability flag mismatch")
        if plan["fixed_scenarios"] != fixture_rows or len(traces["scenarios"]) != 16:
            raise ValueError("run inputs differ from fixed source fixtures")
        sources = plan["implementation"]["sources_and_fixtures"]
        for name, wanted in sources.items():
            path = (root/name).resolve()
            if not path.is_relative_to(root/"prototypes"):
                raise ValueError("unexpected implementation source path")
            if sha(normalized(path.read_bytes())) != wanted or sha(normalized(git("show", SOURCE_COMMIT+":"+name))) != wanted:
                raise ValueError("source identity does not bind both checkout and fixed Git source")
        required = {p.relative_to(root).as_posix() for p in (root/"prototypes/phase4a").glob("*.py")}
        required |= {"prototypes/__init__.py", "prototypes/phase4a/.gitattributes",
                     "prototypes/phase4a/fixtures/transitions.json", "prototypes/phase4a/fixtures/scenarios.json"}
        if set(sources) != required or sha(canonical(sources)) != plan["implementation"]["bundle_sha256"]:
            raise ValueError("source set/bundle incomplete")
        for fixture, actual in zip(fixture_rows, traces["scenarios"]):
            if actual["id"] != fixture["id"] or actual["events"] != fixture["events"] or len(actual["trace"]) != len(fixture["events"]):
                raise ValueError("scenario trace shape differs")
            current = {"balls": 0, "strikes": 0, "pitches_seen": 0, "status": "active"}
            if actual["initial_state"] != current: raise ValueError("unexpected initial state")
            for event, entry in zip(fixture["events"], actual["trace"]):
                wanted = dict(reference[current["balls"], current["strikes"], event])
                wanted["pitches_seen"] = current["pitches_seen"]+1
                if (current["status"] != "active" or entry["before"] != current or entry["event"] != event
                        or entry["ordinal"] != current["pitches_seen"]+1 or entry["after"] != wanted):
                    raise ValueError("trace does not follow static transition reference")
                current = wanted
                trace_entries += 1
            if actual["final_state"] != current or current != fixture["expected_final"]:
                raise ValueError("scenario final differs")
    compared = {}
    for name in FILES:
        a, b = sha((first/name).read_bytes()), sha((second/name).read_bytes())
        if a != b: raise ValueError("repeated artifact differs: "+name)
        compared[name] = a
    write(output/"test-results.json", records)
    write(output/"reproducibility.json", {"first": first.relative_to(root).as_posix(),
          "second": second.relative_to(root).as_posix(), "all_four_artifacts_byte_identical": True,
          "compared_sha256": compared, "no_time_or_absolute_path_in_run_artifacts": True})
    write(output/"verification.json", {"baseline": BASELINE, "source_commit": SOURCE_COMMIT,
          "baseline_game_paths": len(baseline), "protected_game_paths_unchanged": len(baseline)-2,
          "old_file_changes_allowed": ["README.md guide only", ".gitignore data/prototypes/ only"],
          "run_hash_entries_verified": manifest_checks, "fixed_transition_rows": len(table),
          "fixed_sequence_rows": len(fixture_rows), "trace_entries_checked_across_two_runs": trace_entries,
          "source_identity_binds_checkout_and_fixed_git_commit": True,
          "research_repository_opened_by_helper": False, "raw_or_model_files_opened": False,
          "existing_game_git_metadata_read_for_integrity": True,
          "public_git_blob_check": "separate check after committing", **FLAGS})
    write(output/"SHA256SUMS.json", {p.relative_to(output).as_posix(): sha(p.read_bytes()) for p in sorted(output.rglob("*"))
                                   if p.is_file() and p != output/"SHA256SUMS.json"})
    print(json.dumps({"four_files_identical": True, "run_hash_entries": manifest_checks,
                      "baseline_paths": len(baseline), "protected_paths": len(baseline)-2,
                      "test_methods": records["test_methods_run"], "subtests_separate": records["subtests_observed_separate"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("first", "second", "tests", "output"): parser.add_argument("--"+name, required=True)
    args = parser.parse_args()
    verify(args.first, args.second, args.tests, args.output)
