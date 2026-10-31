"""Verify fixed-source runs, hashes, RNG draws, and a separate log ledger.

This helper imports no game rule functions and reads no old static fixtures.
Its log ledger is not an independent oracle for every baseball transition.
Old source bytes are read only to bind identity to the fixed Git commit.
"""
import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import platform
import random
import subprocess

BASELINE="409bbf6d40ee04a52b6e91546892a532d7c3d2d2"
SOURCE_COMMIT="a5fda901bc0a8cca9ae0eefc1d076fd8171c73a6"
DEPENDENCIES=("prototypes/__init__.py",*["prototypes/phase4"+p+"/"+f for p in "abcde" for f in ("__init__.py","model.py")])
FILES=("plan.json","events.json","result.json","REPORT.md","SHA256SUMS.json")
FLAGS={"source_kind":"synthetic_toy_policy","scope":"single_game_integration_prototype",**{"connected_to_phase4"+p:True for p in "abcde"},
    "probability_model":True,"probability_model_kind":"unvalidated_toy_pitch_event_weights","game_end_rules_implemented":True,
    **{key:False for key in ("upstream_pitch_generation","perception_model","batted_ball_physics","research_model_connected","mlb_calibrated","ability_calibrated","official_stats_generated","savant_metrics_generated","android_integrated","product_adoption")}}
COUNT_FIELDS=("completed_plate_appearances","safe_hit_events","walk_events","strikeout_events","home_run_events")
METHODS={"phase4f":23,"phase4e":24,"phase4d":26,"phase4c":28,"phase4a":33,"phase4b":32}


def parse(text):
    def pairs(items):
        result={}
        for key,value in items:
            if key in result:raise ValueError("duplicate JSON key")
            result[key]=value
        return result
    def reject(value):raise ValueError("non-finite JSON")
    return json.loads(text,object_pairs_hook=pairs,parse_constant=reject)


def read(path):return parse(path.read_text(encoding="utf-8"))
def canonical(value):return (json.dumps(value,sort_keys=True,indent=2,ensure_ascii=False,allow_nan=False)+"\n").encode("utf-8")
def same(a,b):return canonical(a)==canonical(b)
def sha(value):return hashlib.sha256(value).hexdigest()
def normalized(value):return value.decode("utf-8").replace("\r\n","\n").replace("\r","\n").encode("utf-8")
def git(*args):return subprocess.check_output(["git",*args])
def write(path,value):
    with path.open("xb") as stream:stream.write(canonical(value))


def score(sequence):
    result={"away":sequence["closed_away_runs"],"home":sequence["closed_home_runs"]}
    result["away" if sequence["side"]=="top" else "home"]+=sequence["current"]["half"]["runs"]
    return result


def ledger(plan,document,result):
    """Recount PA/order/lines without calling production completion functions."""
    initial=document["initial_snapshot"];current=deepcopy(initial["sequence"]);lines=list(initial["line_score"])
    orders=list(initial["batting_indices"]);counts=deepcopy(initial["batter_counts"]);pitches=initial["pitches_accepted"];fair_calls=0
    owner=random.Random(plan["seed"]);last_delta={"away":0,"home":0};terminal=False
    for index,event in enumerate(document["events"],1):
        if event["event_index"]!=index:raise ValueError("non-contiguous log index")
        kind=event["type"]
        if kind=="action":
            if terminal or not same(event["before"],current):raise ValueError("old-action chain differs")
            action=event["action"];after=event["after"]
            for team in ("away","home"):
                if event[team+"_runs_added"]!=score(after)[team]-score(current)[team]:raise ValueError("action score delta differs")
            last_delta={t:event[t+"_runs_added"] for t in ("away","home")}
            if action["kind"]=="advance_half":
                half=(after["inning"]-1)*2+(after["side"]=="bottom")
                if lines[half] is not None or any(last_delta.values()):raise ValueError("Advance revisits a half or changes total score")
                lines[half]=0
            elif action["kind"]=="pitch":
                value=event["input"];draw=owner.randrange(100);pitches+=1
                chosen="called_ball" if draw<35 else "called_strike_caught" if draw<50 else "swinging_strike_caught" if draw<65 else "ordinary_foul_uncaught" if draw<80 else "fair_ball_handoff"
                if value["pitch_draw"]!=draw or value["event"]!=chosen or action["event"]!=chosen:raise ValueError("pitch RNG draw/threshold differs")
                if chosen=="fair_ball_handoff":
                    draw=owner.randrange(100);fair_calls+=1;fair=value["fair"]
                    expected="out_hold" if draw<65 else "safe_1" if draw<87 else "safe_2" if draw<94 else "safe_3" if draw<95 else "home_run"
                    if value["fair_draw"]!=draw:raise ValueError("fair RNG draw differs")
                    if expected.startswith("safe_"):
                        h=int(expected[-1]);dest=[min(i+h,4) if occupied else None for i,occupied in enumerate(current["current"]["half"]["bases"],1)]
                        wanted={"kind":"safe_hit","candidate_bases":h,"runner_destinations":dest}
                    else:wanted={"kind":expected,"candidate_bases":None,"runner_destinations":None}
                    if not same(fair,wanted):raise ValueError("fair threshold/provider advancement differs")
                elif value["fair"] is not None or value["fair_draw"] is not None:raise ValueError("nonfair used fair input")
            elif event["input"] is not None:raise ValueError("nonpitch contains input")
            current=after
        elif kind=="terminal_play":
            if terminal or not same(current,event["last_projected_sequence"]):raise ValueError("terminal old projection differs")
            if current["inning"]!=9 or current["side"]!="bottom":raise ValueError("terminal outside bottom nine")
            needed=score(current)["away"]-score(current)["home"]+1
            amount=event["candidate_runs"] if event["reason"]=="walkoff_home_run" else needed
            if needed<1 or event["candidate_runs"]<needed or not same(event["runs_added"],{"away":0,"home":amount}):raise ValueError("terminal clipping differs")
            if event["post_terminal_old_sequence_created"] is not False or event["official_hit_value_adjudicated"] is not False:raise ValueError("terminal semantics differ")
            last_delta=event["runs_added"];terminal=True
        elif kind=="plate_appearance_completed":
            team=0 if event["team"]=="away" else 1;slot=orders[team];name=("away","home")[team]
            if event["batter_id"]!=name+"_"+str(slot+1) or event["slot"]!=slot+1 or event["batting_index_before"]!=slot or event["batting_index_after"]!=(slot+1)%9:raise ValueError("batting order differs")
            if event["runs_added"]!=last_delta[name]:raise ValueError("PA score does not match resolved delta")
            row=counts[team*9+slot];category=event["category"];row[COUNT_FIELDS[0]]+=1
            if category in ("safe_hit","home_run"):row["safe_hit_events"]+=1
            if category=="walk":row["walk_events"]+=1
            if category=="strikeout":row["strikeout_events"]+=1
            if category=="home_run":row["home_run_events"]+=1
            if category not in ("out_hold","safe_hit","home_run","walk","strikeout"):raise ValueError("unknown PA category")
            half=(event["inning"]-1)*2+(event["side"]=="bottom")
            if lines[half] is None:raise ValueError("PA in an unreached half")
            lines[half]+=event["runs_added"];orders[team]=(slot+1)%9;last_delta={"away":0,"home":0}
        elif kind=="game_end":
            if event["reason"]!=result["reason"] or event["score"]!=result["score"]:raise ValueError("end marker differs")
        elif kind=="abort":raise ValueError("formal fixed-seed run must complete")
        else:raise ValueError("unknown event type")
        if event.get("pitches_accepted",pitches)!=pitches:raise ValueError("logged accepted pitch total differs")
    final=document["final_snapshot"]
    if not same(final["sequence"],current) or not same(final["line_score"],lines) or not same(final["batter_counts"],counts) or not same(final["batting_indices"],orders):raise ValueError("final snapshot differs from log ledger")
    if not same(result["last_projected_sequence"],current) or result["pitches_accepted"]!=pitches or not same(result["next_batting_indices"],dict(zip(("away","home"),orders))):raise ValueError("result differs from log ledger")
    for offset,team in enumerate(("away","home")):
        if not same(result["line_score"][team],lines[offset::2]) or result["score"][team]!=sum(v or 0 for v in lines[offset::2]):raise ValueError("line score sum differs")
        group=counts[offset*9:(offset+1)*9]
        if not same(result["team_events"][team],{f:sum(r[f] for r in group) for f in COUNT_FIELDS}):raise ValueError("team event totals differ")
        for slot,row in enumerate(group):
            wanted={"batter_id":team+"_"+str(slot+1),"team":team,"slot":slot+1,**row}
            if not same(result["batter_events"][offset*9+slot],wanted):raise ValueError("batter totals differ")
    if (plan["rng"]["pitch_calls"],plan["rng"]["fair_calls"],plan["rng"]["randrange_100_calls"])!=(pitches,fair_calls,pitches+fair_calls):raise ValueError("RNG call totals differ")
    if result["status"]!="completed" or result["final_score"]!=result["score"] or result["partial_score"] is not None:raise ValueError("formal result status differs")
    return {"events":len(document["events"]),"accepted_pitches":pitches,"fair_draws":fair_calls,"completed_pa":sum(r[COUNT_FIELDS[0]] for r in counts)}


def verify(args):
    root=Path.cwd().resolve();first,second,output=(Path(getattr(args,k)).resolve() for k in ("first","second","output"))
    record_paths={"phase4f":Path(args.tests).resolve(),**{"phase4"+p:Path(getattr(args,"regression_"+p)).resolve() for p in "edcab"}}
    if first==second or not all(p.is_relative_to(root) for p in (first,second,output,*record_paths.values())) or not output.is_relative_to(root/"reports/phase4f"):raise ValueError("use distinct local runs and Phase4f evidence")
    names=["test-results.json",*[p+"-regression.json" for p in METHODS if p!="phase4f"],"reproducibility.json","verification.json","SHA256SUMS.json"]
    if any((output/name).exists() for name in names):raise FileExistsError("preserve prior evidence; choose a new output")
    records={p:read(path) for p,path in record_paths.items()}
    for phase,record in records.items():
        wanted={"phase":phase,"source_commit":SOURCE_COMMIT,"test_methods_run":METHODS[phase],"successful":True,"failures":0,"errors":0,"skipped":0}
        if any(not same(record.get(k),v) for k,v in wanted.items()):raise ValueError("test record not passing for fixed source: "+phase)
    if git("diff","--name-only",SOURCE_COMMIT,"--","prototypes",".github/workflows").strip() or git("ls-files","--others","--exclude-standard","--","prototypes",".github/workflows").strip():raise ValueError("current source/tests/dependencies/CI differ from fixed core")
    baseline=set(git("ls-tree","-r","--name-only",BASELINE).decode().splitlines());protected=baseline-{"README.md"}
    historical=set(git("diff","--name-only",BASELINE,SOURCE_COMMIT).decode().splitlines())&baseline
    if historical&protected:raise ValueError("protected old game path changed in implementation snapshot")
    current_changes=set(git("diff","--name-only",SOURCE_COMMIT).decode().splitlines())&baseline
    traces=[];hash_checks=0
    for folder in (first,second):
        if {p.name for p in folder.iterdir()}!=set(FILES):raise ValueError("unexpected artifact set")
        manifest=read(folder/"SHA256SUMS.json")
        if set(manifest)!=set(FILES)-{"SHA256SUMS.json"}:raise ValueError("incomplete run hash index")
        for name,wanted in manifest.items():
            if sha((folder/name).read_bytes())!=wanted:raise ValueError("artifact hash differs")
            hash_checks+=1
        plan,events,result=(read(folder/name) for name in ("plan.json","events.json","result.json"))
        for document in (plan,events,result):
            if document.get("schema")!="phase4f-game-v0.1" or document.get("design")!="phase4f-design-v0.1" or any(not same(document.get(k),v) for k,v in FLAGS.items()):raise ValueError("scope/schema differs")
        if plan["seed"]!=42 or events["seed"]!=42 or plan["max_pitches"]!=events["max_pitches"]:raise ValueError("formal seed/budget differs")
        identity=plan["implementation"];sources=identity["sources_and_fixtures"]
        required=set(DEPENDENCIES)|{p.relative_to(root).as_posix() for p in (root/"prototypes/phase4f").glob("*.py")}
        required|={p.relative_to(root).as_posix() for name in ("profiles","fixtures") for p in (root/"prototypes/phase4f"/name).glob("*.json")}|{"prototypes/phase4f/.gitattributes"}
        if set(sources)!=required or sha(canonical(sources))!=identity["bundle_sha256"] or identity["old_dependency_sources"]!=list(DEPENDENCIES):raise ValueError("identity set differs")
        if identity["python"]!=platform.python_version() or any(identity["python"]!=r["python"] for r in records.values()) or identity["third_party_runtime_dependencies"]!=[]:raise ValueError("runtime/test Python differs")
        for name,wanted in sources.items():
            if sha(normalized((root/name).read_bytes()))!=wanted or sha(normalized(git("show",SOURCE_COMMIT+":"+name)))!=wanted:raise ValueError("identity does not bind current and fixed Git source")
        profile=parse(git("show",SOURCE_COMMIT+":prototypes/phase4f/profiles/toy-v1.json"))
        if not same(plan["profile"],profile):raise ValueError("fixed toy profile differs")
        traces.append(ledger(plan,events,result))
    compared={name:sha((first/name).read_bytes()) for name in FILES}
    if any(compared[name]!=sha((second/name).read_bytes()) for name in FILES):raise ValueError("repeat bytes differ")
    output.mkdir(parents=True,exist_ok=True)
    for phase,record in records.items():write(output/("test-results.json" if phase=="phase4f" else phase+"-regression.json"),record)
    write(output/"reproducibility.json",{"first":first.relative_to(root).as_posix(),"second":second.relative_to(root).as_posix(),"all_five_artifacts_byte_identical":True,"compared_sha256":compared,"same_python_only":True})
    write(output/"verification.json",{"baseline":BASELINE,"source_commit":SOURCE_COMMIT,"verification_date_jst":"2026-10-31",
        "existing_game_file_protection":{"from_commit":BASELINE,"through_commit":SOURCE_COMMIT,"baseline_game_paths":len(baseline),"protected_game_paths":len(protected),"protected_game_paths_unchanged_at_source_commit":len(protected),"changed_baseline_game_paths_at_source_commit":sorted(historical),"old_file_changes_allowed_at_source_commit":["README.md guide only"]},
        "current_checkout_changes_since_source_commit":{"from_commit":SOURCE_COMMIT,"comparison":"fixed core to current checkout including tracked uncommitted changes","baseline_game_paths_changed":sorted(current_changes),"protected_baseline_game_paths_changed":sorted(current_changes&protected),"informational_only":True},
        "source_identity_binds_checkout_and_fixed_git_commit":True,"current_source_tests_dependencies_ci_match_fixed_source":True,"old_dependency_source_paths":list(DEPENDENCIES),
        "old_rule_functions_imported_by_helper":False,"old_static_fixtures_read_by_helper":False,"research_repository_opened_by_helper":False,"old_source_read_purpose":"normalized identity hash and fixed Git source binding only",
        "new_profile_git_blob_read":SOURCE_COMMIT+":prototypes/phase4f/profiles/toy-v1.json","ledger_and_rng_checks_per_run":traces,"run_hash_entries_verified":hash_checks,
        "ledger_is_full_independent_baseball_rule_oracle":False,"public_git_blob_check":"separate check after committing",**FLAGS})
    write(output/"SHA256SUMS.json",{p.relative_to(output).as_posix():sha(p.read_bytes()) for p in sorted(output.rglob("*")) if p.is_file() and p!=output/"SHA256SUMS.json"})
    print(json.dumps({"five_files_identical":True,"log_ledgers":traces,"run_hashes":hash_checks,"historical_protected_paths":len(protected)}))


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("first","second","tests","regression-e","regression-d","regression-c","regression-a","regression-b","output"):parser.add_argument("--"+name,required=True)
    verify(parser.parse_args())
