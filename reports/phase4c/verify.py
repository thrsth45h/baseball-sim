"""Verify fixed runs, allowed sources and historical game-file protection.

Unlike the runner, this verification helper reads old static transition Git
blobs to check traces. It does not import rule functions or research models.
Run from this game checkout; preserve all prior output records.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

BASELINE = "06256f8a4f0f8795709f56365f1ea212e2090092"
SOURCE_COMMIT = "a1d2cbc4ae9f949babb2f350be9ed41193f477b5"
DEPENDENCIES = ("prototypes/__init__.py", "prototypes/phase4a/__init__.py", "prototypes/phase4a/model.py",
                "prototypes/phase4b/__init__.py", "prototypes/phase4b/model.py")
REFERENCE_FIXTURES = ("prototypes/phase4a/fixtures/transitions.json", "prototypes/phase4b/fixtures/transitions.json")
FILES = ("plan.json", "traces.json", "REPORT.md", "SHA256SUMS.json")
FLAGS = {"source_kind": "synthetic_fixture", "scope": "partial_pitch_half_orchestration",
         "connected_to_phase4a": True, "connected_to_phase4b": True,
         "connected_to_statcast": False, "connected_to_phase3d": False, "probability_model": False,
         "official_stats_generated": False, "ability_calibrated": False, "android_integrated": False, "product_adoption": False}


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


def same(a, b): return canonical(a) == canonical(b)


def normalized(value): return value.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n").encode("utf-8")


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


def stage(state):
    if state["half"]["outs"] == 3: return "half_complete"
    if state["count"] is None: return "between_batters"
    return "pitching" if state["count"]["status"] == "active" else "awaiting_resolution"


def verify(first, second, tests, regression_a, regression_b, output):
    root = Path.cwd().resolve()
    first, second, tests, regression_a, regression_b, output = map(lambda p: Path(p).resolve(), (first, second, tests, regression_a, regression_b, output))
    if not all(p.is_relative_to(root) for p in (first, second, tests, regression_a, regression_b, output)):
        raise ValueError("all paths must stay inside this checkout")
    if not output.is_relative_to(root/"reports/phase4c") or first == second:
        raise ValueError("use Phase4c evidence output and two distinct run directories")
    for name in ("test-results.json", "phase4a-regression.json", "phase4b-regression.json", "reproducibility.json", "verification.json", "SHA256SUMS.json"):
        if (output/name).exists(): raise FileExistsError("preserve existing verification records")
    records = {"phase4c": test_record(tests, "phase4c", 28), "phase4a": test_record(regression_a, "phase4a", 33),
               "phase4b": test_record(regression_b, "phase4b", 32)}
    if git("diff", "--name-only", SOURCE_COMMIT, "--", "prototypes", ".github/workflows/phase4a-tests.yml",
           ".github/workflows/phase4b-tests.yml", ".github/workflows/phase4c-tests.yml").strip():
        raise ValueError("current source/tests/dependencies/CI differ from fixed source")
    baseline = set(git("ls-tree", "-r", "--name-only", BASELINE).decode().splitlines())
    protected = baseline-{"README.md"}
    snapshot_changed = set(git("diff", "--name-only", BASELINE, SOURCE_COMMIT).decode().splitlines()) & baseline
    if snapshot_changed & protected: raise ValueError("protected game file changed at fixed implementation snapshot")
    current_changed = set(git("diff", "--name-only", SOURCE_COMMIT).decode().splitlines()) & baseline
    def fixed(path, key): return parse(git("show", SOURCE_COMMIT+":"+path))[key]
    rows = fixed("prototypes/phase4c/fixtures/scenarios.json", "scenarios")
    control = fixed("prototypes/phase4c/fixtures/control.json", "control")
    compatibility = fixed("prototypes/phase4c/fixtures/compatibility.json", "compatibility")
    permitted = {(row["status"], row["completed_play"]) for row in compatibility if row["allowed"]}
    effects = {(row["stage"], row["kind"]): row["effect"] for row in control}
    count_rows = fixed(REFERENCE_FIXTURES[0], "transitions")
    half_rows = fixed(REFERENCE_FIXTURES[1], "transitions")
    count_table = {(row["before"]["balls"], row["before"]["strikes"], row["event"]): row["after"] for row in count_rows}
    half_table = {(row["before"]["outs"], tuple(row["before"]["bases"]), row["event"]): row["after"] for row in half_rows}
    if (len(rows), sum(len(row["actions"]) for row in rows), len(effects), len(compatibility), len(permitted), len(count_table), len(half_table)) != (18, 82, 12, 12, 5, 96, 72):
        raise ValueError("fixed table/column dimensions differ")
    manifest_checks = trace_checks = 0
    for folder in (first, second):
        if {p.name for p in folder.iterdir()} != set(FILES): raise ValueError("unexpected run artifact set")
        manifest = read(folder/"SHA256SUMS.json")
        if set(manifest) != set(FILES)-{"SHA256SUMS.json"}: raise ValueError("run hash manifest incomplete")
        for name, wanted in manifest.items():
            path = (folder/name).resolve()
            if not path.is_relative_to(folder) or sha(path.read_bytes()) != wanted: raise ValueError("run hash/path mismatch")
            manifest_checks += 1
        plan, traces = read(folder/"plan.json"), read(folder/"traces.json")
        for document in (plan, traces):
            if document.get("design") != "phase4c-design-v0.1" or document.get("schema") != "phase4c-reference-v0.1" or any(
                    type(document.get(key)) is not type(value) or document.get(key) != value for key, value in FLAGS.items()):
                raise ValueError("run design/scope flags differ")
        if (not same(plan["fixed_scenarios"], rows) or not same(plan["control_cells"], control)
                or not same(plan["compatibility_cells"], compatibility) or len(traces["scenarios"]) != 18):
            raise ValueError("fixed tables/scenario inputs differ")
        identity = plan["implementation"]; sources = identity["sources_and_fixtures"]
        required = set(DEPENDENCIES) | {p.relative_to(root).as_posix() for p in (root/"prototypes/phase4c").glob("*.py")}
        required |= {"prototypes/phase4c/.gitattributes", *["prototypes/phase4c/fixtures/"+name for name in ("control.json", "compatibility.json", "scenarios.json")]}
        if (set(sources) != required or sha(canonical(sources)) != identity["bundle_sha256"]
                or identity["old_dependency_sources"] != list(DEPENDENCIES)):
            raise ValueError("source/dependency set or bundle incomplete")
        if any(identity["python"] != record["python"] for record in records.values()) or identity["third_party_runtime_dependencies"] != []:
            raise ValueError("runtime/test Python/dependency identity differs")
        for name, wanted in sources.items():
            path = (root/name).resolve()
            if not path.is_relative_to(root/"prototypes"): raise ValueError("unexpected source path")
            if sha(normalized(path.read_bytes())) != wanted or sha(normalized(git("show", SOURCE_COMMIT+":"+name))) != wanted:
                raise ValueError("identity does not bind current checkout and fixed Git source")
        for row, actual in zip(rows, traces["scenarios"]):
            if (actual["id"] != row["id"] or not same(actual["initial_state"], row["initial_state"])
                    or not same(actual["actions"], row["actions"]) or len(actual["trace"]) != len(row["actions"])):
                raise ValueError("fixed trace inputs/shape differ")
            current = row["initial_state"]
            for action, entry in zip(row["actions"], actual["trace"]):
                if effects[(stage(current), action["kind"])] == "reject": raise ValueError("trace contains forbidden stage/action")
                count, half = current["count"], current["half"]
                if action["kind"] == "begin_batter":
                    after = {"count": {"balls": 0, "strikes": 0, "pitches_seen": 0, "status": "active"}, "half": half}
                elif action["kind"] == "pitch":
                    projected = count_table[(count["balls"], count["strikes"], action["event"])]
                    after = {"count": projected | {"pitches_seen": count["pitches_seen"]+1}, "half": half}
                else:
                    play = action["completed_play"]
                    if (count["status"], play) not in permitted: raise ValueError("trace resolve violates compatibility")
                    projected = half_table[(half["outs"], tuple(half["bases"]), play)]
                    after = {"count": None, "half": projected | {"runs": half["runs"]+projected["runs"]}}
                wanted = {"action": action, "before": current, "after": after, "runs_added": after["half"]["runs"]-half["runs"]}
                if not same(entry, wanted): raise ValueError("trace differs from fixed control and old static references")
                current = after; trace_checks += 1
            if not same(current, row["expected_final"]) or not same(actual["final_state"], current) or actual["final_stage"] != stage(current) or stage(current) != row["expected_stage"]:
                raise ValueError("fixed scenario final differs")
    compared = {}
    for name in FILES:
        a, b = sha((first/name).read_bytes()), sha((second/name).read_bytes())
        if a != b: raise ValueError("two-run artifact bytes differ: "+name)
        compared[name] = a
    for phase, record in records.items(): write(output/("test-results.json" if phase == "phase4c" else phase+"-regression.json"), record)
    write(output/"reproducibility.json", {"first": first.relative_to(root).as_posix(), "second": second.relative_to(root).as_posix(),
          "all_four_artifacts_byte_identical": True, "compared_sha256": compared, "no_time_or_absolute_path_in_run_artifacts": True})
    write(output/"verification.json", {"baseline": BASELINE, "source_commit": SOURCE_COMMIT,
          "existing_game_file_protection": {"from_commit": BASELINE, "through_commit": SOURCE_COMMIT,
              "baseline_game_paths": len(baseline), "protected_game_paths": len(protected),
              "protected_game_paths_unchanged_at_source_commit": len(protected),
              "changed_baseline_game_paths_at_source_commit": sorted(snapshot_changed), "old_file_changes_allowed_at_source_commit": ["README.md guide only"]},
          "current_checkout_changes_since_source_commit": {"from_commit": SOURCE_COMMIT,
              "comparison": "fixed source to current working tree, including tracked uncommitted changes",
              "baseline_game_paths_changed": sorted(current_changed), "protected_baseline_game_paths_changed": sorted(current_changed & protected), "informational_only": True},
          "source_identity_binds_checkout_and_fixed_git_commit": True, "current_source_tests_dependencies_ci_match_fixed_source": True,
          "old_dependency_source_paths": list(DEPENDENCIES), "old_reference_fixture_git_blobs_read_for_trace_validation": list(REFERENCE_FIXTURES),
          "old_rule_functions_imported_by_helper": False, "research_repository_opened_by_helper": False,
          "raw_or_fitted_model_artifacts_opened": False, "existing_game_git_metadata_read_for_integrity": True,
          "control_cells": len(control), "compatibility_cells": len(compatibility), "compatible_pairs": len(permitted),
          "fixed_sequence_rows": len(rows), "trace_entries_checked_across_two_runs": trace_checks,
          "run_hash_entries_verified": manifest_checks, "public_git_blob_check": "separate check after committing", **FLAGS})
    write(output/"SHA256SUMS.json", {p.relative_to(output).as_posix(): sha(p.read_bytes()) for p in sorted(output.rglob("*")) if p.is_file() and p != output/"SHA256SUMS.json"})
    print(json.dumps({"four_files_identical": True, "trace_entries_two_runs": trace_checks, "historical_protected_paths": len(protected),
                      "phase4c_test_methods": records["phase4c"]["test_methods_run"], "phase4c_subtests_separate": records["phase4c"]["subtests_observed_separate"],
                      "phase4a_regression_methods_separate": records["phase4a"]["test_methods_run"], "phase4b_regression_methods_separate": records["phase4b"]["test_methods_run"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("first", "second", "tests", "regression-a", "regression-b", "output"): parser.add_argument("--"+name, required=True)
    args = parser.parse_args()
    verify(args.first, args.second, args.tests, args.regression_a, args.regression_b, args.output)
