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

BASELINE = "e1af536153c36ab34be8199a3707ff0c1c9ba0fb"
SOURCE_COMMIT = "88d239ce33c8330402fe17d8a9f3ee4dab9c34e8"
DEPENDENCIES = ("prototypes/__init__.py", "prototypes/phase4a/__init__.py", "prototypes/phase4a/model.py",
                "prototypes/phase4b/__init__.py", "prototypes/phase4b/model.py",
                "prototypes/phase4c/__init__.py", "prototypes/phase4c/model.py",
                "prototypes/phase4d/__init__.py", "prototypes/phase4d/model.py")
REFERENCE_FIXTURES = ("prototypes/phase4a/fixtures/transitions.json", "prototypes/phase4b/fixtures/transitions.json",
                      "prototypes/phase4c/fixtures/compatibility.json")
FILES = ("plan.json", "traces.json", "REPORT.md", "SHA256SUMS.json")
FLAGS = {"source_kind": "synthetic_fixture", "scope": "partial_completed_safe_hit_projection",
         "connected_to_phase4a": True, "connected_to_phase4b": True, "connected_to_phase4c": True, "connected_to_phase4d": True,
         "connected_to_statcast": False, "connected_to_phase3d": False, "probability_model": False,
         "game_end_rules_implemented": False, "official_stats_generated": False, "ability_calibrated": False, "android_integrated": False, "product_adoption": False}


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
    current=state["current"]
    if current["half"]["outs"]==3:return "half_complete"
    if current["count"] is None:return "between_batters"
    return "pitching" if current["count"]["status"]=="active" else "awaiting_resolution"


def score(state):
    values={"away":state["closed_away_runs"],"home":state["closed_home_runs"]}
    values["away" if state["side"]=="top" else "home"]+=state["current"]["half"]["runs"]
    return values


def verify(first,second,tests,regression_d,regression_c,regression_a,regression_b,output):
    root=Path.cwd().resolve()
    first,second,tests,regression_d,regression_c,regression_a,regression_b,output=map(lambda p:Path(p).resolve(),(first,second,tests,regression_d,regression_c,regression_a,regression_b,output))
    if not all(p.is_relative_to(root) for p in (first,second,tests,regression_d,regression_c,regression_a,regression_b,output)):
        raise ValueError("all paths must stay inside this checkout")
    if not output.is_relative_to(root/"reports/phase4e") or first==second:raise ValueError("use Phase4e evidence and two distinct runs")
    for name in ("test-results.json","phase4d-regression.json","phase4c-regression.json","phase4a-regression.json","phase4b-regression.json","reproducibility.json","verification.json","SHA256SUMS.json"):
        if (output/name).exists():raise FileExistsError("preserve previous verification records")
    records={"phase4e":test_record(tests,"phase4e",24),"phase4d":test_record(regression_d,"phase4d",26),"phase4c":test_record(regression_c,"phase4c",28),
             "phase4a":test_record(regression_a,"phase4a",33),"phase4b":test_record(regression_b,"phase4b",32)}
    if git("diff","--name-only",SOURCE_COMMIT,"--","prototypes",*[".github/workflows/phase4"+p+"-tests.yml" for p in "abcde"]).strip():
        raise ValueError("current source/tests/dependencies/CI differ from fixed source")
    baseline=set(git("ls-tree","-r","--name-only",BASELINE).decode().splitlines());protected=baseline-{"README.md"}
    historical=set(git("diff","--name-only",BASELINE,SOURCE_COMMIT).decode().splitlines())&baseline
    if historical&protected:raise ValueError("protected game file changed at fixed implementation snapshot")
    current_changes=set(git("diff","--name-only",SOURCE_COMMIT).decode().splitlines())&baseline
    def fixed(path,key):return parse(git("show",SOURCE_COMMIT+":"+path))[key]
    rows=fixed("prototypes/phase4e/fixtures/scenarios.json","scenarios");control=fixed("prototypes/phase4e/fixtures/control.json","control")
    effects={(r["stage"],r["kind"]):r["effect"] for r in control}
    patterns=fixed("prototypes/phase4e/fixtures/patterns.json","patterns")
    hit_table={(tuple(r["before"]["bases"]),r["hit_bases"],tuple(r["runner_destinations"])):r["after"] for r in patterns}
    count_rows=fixed(REFERENCE_FIXTURES[0],"transitions");half_rows=fixed(REFERENCE_FIXTURES[1],"transitions");compat=fixed(REFERENCE_FIXTURES[2],"compatibility")
    count_table={(r["before"]["balls"],r["before"]["strikes"],r["event"]):r["after"] for r in count_rows}
    half_table={(r["before"]["outs"],tuple(r["before"]["bases"]),r["event"]):r["after"] for r in half_rows}
    permitted={(r["status"],r["completed_play"]) for r in compat if r["allowed"]}
    if (len(rows),sum(len(r["actions"]) for r in rows),len(effects),len(hit_table),len(count_table),len(half_table),len(permitted))!=(18,60,20,48,96,72,5):
        raise ValueError("fixed fixture dimensions differ")
    trace_checks=manifest_checks=0
    for folder in (first,second):
        if {p.name for p in folder.iterdir()}!=set(FILES):raise ValueError("unexpected artifact set")
        manifest=read(folder/"SHA256SUMS.json")
        if set(manifest)!=set(FILES)-{"SHA256SUMS.json"}:raise ValueError("run hash manifest incomplete")
        for name,wanted in manifest.items():
            path=(folder/name).resolve()
            if not path.is_relative_to(folder) or sha(path.read_bytes())!=wanted:raise ValueError("run hash/path mismatch")
            manifest_checks+=1
        plan,traces=read(folder/"plan.json"),read(folder/"traces.json")
        for document in (plan,traces):
            if document.get("design")!="phase4e-design-v0.1" or document.get("schema")!="phase4e-reference-v0.1" or any(type(document.get(k)) is not type(v) or document.get(k)!=v for k,v in FLAGS.items()):
                raise ValueError("run design/scope flags differ")
        if not same(plan["fixed_scenarios"],rows) or not same(plan["control_cells"],control) or not same(plan["accepted_patterns"],patterns) or len(traces["scenarios"])!=18:
            raise ValueError("fixed tables/inputs differ")
        identity=plan["implementation"];sources=identity["sources_and_fixtures"]
        required=set(DEPENDENCIES)|{p.relative_to(root).as_posix() for p in (root/"prototypes/phase4e").glob("*.py")}
        required|={"prototypes/phase4e/.gitattributes","prototypes/phase4e/fixtures/control.json","prototypes/phase4e/fixtures/scenarios.json","prototypes/phase4e/fixtures/patterns.json"}
        if set(sources)!=required or sha(canonical(sources))!=identity["bundle_sha256"] or identity["old_dependency_sources"]!=list(DEPENDENCIES):raise ValueError("source/dependency set or bundle incomplete")
        if any(identity["python"]!=r["python"] for r in records.values()) or identity["third_party_runtime_dependencies"]!=[]:raise ValueError("runtime/test Python identity differs")
        for name,wanted in sources.items():
            path=(root/name).resolve()
            if not path.is_relative_to(root/"prototypes") or sha(normalized(path.read_bytes()))!=wanted or sha(normalized(git("show",SOURCE_COMMIT+":"+name)))!=wanted:
                raise ValueError("identity does not bind current and fixed Git source")
        for row,actual in zip(rows,traces["scenarios"]):
            if actual["id"]!=row["id"] or not same(actual["initial_state"],row["initial_state"]) or not same(actual["actions"],row["actions"]) or len(actual["trace"])!=len(row["actions"]):raise ValueError("trace shape differs")
            current=row["initial_state"]
            for action,entry in zip(row["actions"],actual["trace"]):
                if effects[(stage(current),action["kind"])]=="reject":raise ValueError("trace contains forbidden stage/action")
                nested=current["current"];count,half=nested["count"],nested["half"]
                if action["kind"]=="advance_half":
                    team="closed_away_runs" if current["side"]=="top" else "closed_home_runs"
                    after=current|{team:current[team]+half["runs"],"inning":current["inning"]+(1 if current["side"]=="bottom" else 0),
                        "side":"bottom" if current["side"]=="top" else "top","current":{"count":None,"half":{"outs":0,"bases":[False]*3,"runs":0}}}
                elif action["kind"]=="resolve_safe_hit":
                    if count["status"]!="fair_play_pending":raise ValueError("safe hit is not fair pending")
                    projected=hit_table[(tuple(half["bases"]),action["hit_bases"],tuple(action["runner_destinations"]))]
                    after=current|{"current":{"count":None,"half":projected|{"outs":half["outs"],"runs":half["runs"]+projected["runs"]}}}
                else:
                    if action["kind"]=="begin_batter":next_current={"count":{"balls":0,"strikes":0,"pitches_seen":0,"status":"active"},"half":half}
                    elif action["kind"]=="pitch":
                        projected=count_table[(count["balls"],count["strikes"],action["event"])];next_current={"count":projected|{"pitches_seen":count["pitches_seen"]+1},"half":half}
                    else:
                        play=action["completed_play"]
                        if (count["status"],play) not in permitted:raise ValueError("resolve violates old compatibility")
                        projected=half_table[(half["outs"],tuple(half["bases"]),play)];next_current={"count":None,"half":projected|{"runs":half["runs"]+projected["runs"]}}
                    after=current|{"current":next_current}
                wanted={"action":action,"before":current,"after":after,"away_runs_added":score(after)["away"]-score(current)["away"],"home_runs_added":score(after)["home"]-score(current)["home"]}
                if not same(entry,wanted):raise ValueError("trace differs from fixed control/old static references")
                current=after;trace_checks+=1
            if (not same(current,row["expected_final"]) or not same(actual["final_state"],current) or not same(score(current),row["expected_score"])
                    or not same(actual["final_score"],score(current)) or actual["final_stage"]!=stage(current) or stage(current)!=row["expected_stage"]):raise ValueError("fixed column final differs")
    compared={}
    for name in FILES:
        a,b=sha((first/name).read_bytes()),sha((second/name).read_bytes())
        if a!=b:raise ValueError("two-run bytes differ: "+name)
        compared[name]=a
    for phase,record in records.items():write(output/("test-results.json" if phase=="phase4e" else phase+"-regression.json"),record)
    write(output/"reproducibility.json",{"first":first.relative_to(root).as_posix(),"second":second.relative_to(root).as_posix(),"all_four_artifacts_byte_identical":True,"compared_sha256":compared,"no_time_or_absolute_path_in_run_artifacts":True})
    write(output/"verification.json",{"baseline":BASELINE,"source_commit":SOURCE_COMMIT,"verification_date_jst":"2026-10-31",
        "existing_game_file_protection":{"from_commit":BASELINE,"through_commit":SOURCE_COMMIT,"baseline_game_paths":len(baseline),"protected_game_paths":len(protected),
            "protected_game_paths_unchanged_at_source_commit":len(protected),"changed_baseline_game_paths_at_source_commit":sorted(historical),"old_file_changes_allowed_at_source_commit":["README.md guide only"]},
        "current_checkout_changes_since_source_commit":{"from_commit":SOURCE_COMMIT,"comparison":"fixed source to current working tree, including tracked uncommitted changes",
            "baseline_game_paths_changed":sorted(current_changes),"protected_baseline_game_paths_changed":sorted(current_changes&protected),"informational_only":True},
        "source_identity_binds_checkout_and_fixed_git_commit":True,"current_source_tests_dependencies_ci_match_fixed_source":True,
        "old_dependency_source_paths":list(DEPENDENCIES),"old_reference_fixture_git_blobs_read_for_trace_validation":list(REFERENCE_FIXTURES),
        "old_rule_functions_imported_by_helper":False,"research_repository_opened_by_helper":False,"raw_or_fitted_model_artifacts_opened":False,
        "existing_game_git_metadata_read_for_integrity":True,"control_cells":len(control),"fixed_pattern_rows":len(patterns),"new_pattern_fixture_git_blob":"prototypes/phase4e/fixtures/patterns.json","fixed_sequence_rows":len(rows),"trace_entries_checked_across_two_runs":trace_checks,
        "run_hash_entries_verified":manifest_checks,"public_git_blob_check":"separate check after committing",**FLAGS})
    write(output/"SHA256SUMS.json",{p.relative_to(output).as_posix():sha(p.read_bytes()) for p in sorted(output.rglob("*")) if p.is_file() and p!=output/"SHA256SUMS.json"})
    print(json.dumps({"four_files_identical":True,"trace_entries_two_runs":trace_checks,"historical_protected_paths":len(protected),
        "phase4e_test_methods":records["phase4e"]["test_methods_run"],"phase4e_subtests_separate":records["phase4e"]["subtests_observed_separate"],
        "old_suite_methods_separate":{p:r["test_methods_run"] for p,r in records.items() if p!="phase4e"}}))


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("first","second","tests","regression-d","regression-c","regression-a","regression-b","output"):parser.add_argument("--"+name,required=True)
    args=parser.parse_args();verify(args.first,args.second,args.tests,args.regression_d,args.regression_c,args.regression_a,args.regression_b,args.output)
