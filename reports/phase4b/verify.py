"""Verify fixed Phase4b runs and historical protection, without model imports.

Run from this game checkout. Existing evidence files are never overwritten.
Current documentation changes are information, not historical exceptions.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

BASELINE = "59ae0e2f726b88d5729d026a0e293916c33a73e6"
SOURCE_COMMIT = "7f98536c8659d6fa6bd0bbe94ab6e48e8ee4fb3b"
FILES = ("plan.json", "traces.json", "REPORT.md", "SHA256SUMS.json")
FLAGS = {"source_kind": "synthetic_fixture", "scope": "partial_base_out_rules", "probability_model": False,
         "connected_to_phase4a": False, "connected_to_statcast": False, "connected_to_phase3d": False,
         "official_stats_generated": False, "ability_calibrated": False, "android_integrated": False,
         "product_adoption": False}


def parse(text):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result: raise ValueError("duplicate JSON key: "+key)
            result[key] = value
        return result
    def reject(value): raise ValueError("non-finite JSON: "+value)
    return json.loads(text, object_pairs_hook=pairs, parse_constant=reject)


def read(path): return parse(path.read_text(encoding="utf-8"))


def canonical(value):
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False, allow_nan=False)+"\n").encode("utf-8")


def normalized(value):
    return value.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n").encode("utf-8")


def sha(data): return hashlib.sha256(data).hexdigest()


def git(*args): return subprocess.check_output(["git", *args])


def write(path, value):
    with path.open("xb") as stream: stream.write(canonical(value))


def test_record(path, phase, methods):
    record = read(path)
    if (record.get("phase") != phase or record.get("source_commit") != SOURCE_COMMIT
            or record.get("test_methods_run") != methods or record.get("successful") is not True
            or any(record.get(key) != 0 for key in ("failures", "errors", "skipped"))):
        raise ValueError("test record must pass without skips for fixed source: "+phase)
    return record


def verify(first, second, tests, regression, output):
    root = Path.cwd().resolve()
    first, second, tests, regression, output = map(lambda p: Path(p).resolve(), (first, second, tests, regression, output))
    if not all(p.is_relative_to(root) for p in (first, second, tests, regression, output)):
        raise ValueError("all paths must stay inside this checkout")
    if not output.is_relative_to(root/"reports/phase4b") or first == second:
        raise ValueError("use Phase4b evidence output and two distinct run directories")
    for name in ("test-results.json", "phase4a-regression.json", "reproducibility.json", "verification.json", "SHA256SUMS.json"):
        if (output/name).exists(): raise FileExistsError("preserve existing verification records")
    new_tests = test_record(tests, "phase4b", 32)
    old_tests = test_record(regression, "phase4a", 33)
    if git("diff", "--name-only", SOURCE_COMMIT, "--", "prototypes",
           ".github/workflows/phase4a-tests.yml", ".github/workflows/phase4b-tests.yml").strip():
        raise ValueError("current source/tests/fixtures/CI differ from fixed source")
    tree = git("ls-tree", "-r", "--name-only", BASELINE).decode().splitlines()
    baseline, allowed = set(tree), {"README.md"}
    protected = baseline-allowed
    snapshot_changed = set(git("diff", "--name-only", BASELINE, SOURCE_COMMIT).decode().splitlines()) & baseline
    if snapshot_changed & protected: raise ValueError("protected game file changed at fixed implementation snapshot")
    current_changed = set(git("diff", "--name-only", SOURCE_COMMIT).decode().splitlines()) & baseline
    rows = parse(git("show", SOURCE_COMMIT+":prototypes/phase4b/fixtures/scenarios.json"))["scenarios"]
    table = parse(git("show", SOURCE_COMMIT+":prototypes/phase4b/fixtures/transitions.json"))["transitions"]
    reference = {(row["before"]["outs"], tuple(row["before"]["bases"]), row["event"]): row for row in table}
    if len(rows) != 16 or len(table) != 72 or len(reference) != 72 or sum(len(row["events"]) for row in rows) != 30:
        raise ValueError("fixed fixture dimensions differ")
    manifest_checks, trace_checks = 0, 0
    for folder in (first, second):
        if {path.name for path in folder.iterdir()} != set(FILES): raise ValueError("unexpected run artifact set")
        manifest = read(folder/"SHA256SUMS.json")
        if set(manifest) != set(FILES)-{"SHA256SUMS.json"}: raise ValueError("run hash manifest is incomplete")
        for name, wanted in manifest.items():
            path = (folder/name).resolve()
            if not path.is_relative_to(folder) or sha(path.read_bytes()) != wanted: raise ValueError("run hash/path mismatch")
            manifest_checks += 1
        plan, traces = read(folder/"plan.json"), read(folder/"traces.json")
        for document in (plan, traces):
            if document.get("design") != "phase4b-design-v0.1" or document.get("schema") != "phase4b-reference-v0.1" or any(
                    type(document.get(key)) is not type(value) or document.get(key) != value for key, value in FLAGS.items()):
                raise ValueError("run design/scope flags differ")
        if plan["fixed_scenarios"] != rows or len(traces["scenarios"]) != 16: raise ValueError("fixed scenario inputs differ")
        identity = plan["implementation"]
        sources = identity["sources_and_fixtures"]
        required = {path.relative_to(root).as_posix() for path in (root/"prototypes/phase4b").glob("*.py")}
        required |= {"prototypes/__init__.py", "prototypes/phase4b/.gitattributes",
                     "prototypes/phase4b/fixtures/scenarios.json", "prototypes/phase4b/fixtures/transitions.json"}
        if set(sources) != required or sha(canonical(sources)) != identity["bundle_sha256"]:
            raise ValueError("source set/bundle incomplete")
        if (identity["python"] != new_tests["python"] or identity["python"] != old_tests["python"]
                or identity["third_party_runtime_dependencies"] != []): raise ValueError("runtime/test Python/dependency identity differs")
        for name, wanted in sources.items():
            path = (root/name).resolve()
            if not path.is_relative_to(root/"prototypes"): raise ValueError("unexpected source path")
            if sha(normalized(path.read_bytes())) != wanted or sha(normalized(git("show", SOURCE_COMMIT+":"+name))) != wanted:
                raise ValueError("run identity does not bind current checkout and fixed Git source")
        for row, actual in zip(rows, traces["scenarios"]):
            if (actual["id"] != row["id"] or actual["initial_state"] != row["initial_state"]
                    or actual["events"] != row["events"] or len(actual["trace"]) != len(row["events"])):
                raise ValueError("fixed scenario trace shape differs")
            current = row["initial_state"]
            for event, entry in zip(row["events"], actual["trace"]):
                ref = reference[(current["outs"], tuple(current["bases"]), event)]
                after = ref["after"] | {"runs": current["runs"]+ref["runs_added"]}
                wanted = {"event": event, "before": current, "after": after, "runs_added": ref["runs_added"]}
                if current["outs"] == 3 or entry != wanted: raise ValueError("trace differs from static transition reference")
                current = after
                trace_checks += 1
            if current != row["expected_final"] or actual["final_state"] != current: raise ValueError("scenario final differs")
    compared = {}
    for name in FILES:
        a, b = sha((first/name).read_bytes()), sha((second/name).read_bytes())
        if a != b: raise ValueError("two-run artifact bytes differ: "+name)
        compared[name] = a
    write(output/"test-results.json", new_tests)
    write(output/"phase4a-regression.json", old_tests)
    write(output/"reproducibility.json", {"first": first.relative_to(root).as_posix(), "second": second.relative_to(root).as_posix(),
          "all_four_artifacts_byte_identical": True, "compared_sha256": compared,
          "no_time_or_absolute_path_in_run_artifacts": True})
    write(output/"verification.json", {"baseline": BASELINE, "source_commit": SOURCE_COMMIT,
          "existing_game_file_protection": {"from_commit": BASELINE, "through_commit": SOURCE_COMMIT,
              "baseline_game_paths": len(baseline), "protected_game_paths": len(protected),
              "protected_game_paths_unchanged_at_source_commit": len(protected),
              "changed_baseline_game_paths_at_source_commit": sorted(snapshot_changed),
              "old_file_changes_allowed_at_source_commit": ["README.md guide only"]},
          "current_checkout_changes_since_source_commit": {"from_commit": SOURCE_COMMIT,
              "comparison": "fixed source to current working tree, including tracked uncommitted changes",
              "baseline_game_paths_changed": sorted(current_changed),
              "protected_baseline_game_paths_changed": sorted(current_changed & protected), "informational_only": True},
          "source_identity_binds_checkout_and_fixed_git_commit": True,
          "current_source_tests_fixtures_ci_match_fixed_source": True,
          "fixed_transition_rows": len(table), "fixed_sequence_rows": len(rows),
          "trace_entries_checked_across_two_runs": trace_checks, "run_hash_entries_verified": manifest_checks,
          "research_repository_opened_by_helper": False, "raw_or_model_files_opened": False,
          "existing_game_git_metadata_read_for_integrity": True,
          "public_git_blob_check": "separate check after committing", **FLAGS})
    write(output/"SHA256SUMS.json", {path.relative_to(output).as_posix(): sha(path.read_bytes()) for path in sorted(output.rglob("*"))
                                   if path.is_file() and path != output/"SHA256SUMS.json"})
    print(json.dumps({"four_files_identical": True, "fixed_transitions": len(table), "fixed_sequences": len(rows),
                      "trace_entries_two_runs": trace_checks, "historical_protected_paths": len(protected),
                      "phase4b_test_methods": new_tests["test_methods_run"], "phase4b_subtests_separate": new_tests["subtests_observed_separate"],
                      "phase4a_regression_methods_separate": old_tests["test_methods_run"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("first", "second", "tests", "regression", "output"): parser.add_argument("--"+name, required=True)
    args = parser.parse_args()
    verify(args.first, args.second, args.tests, args.regression, args.output)
