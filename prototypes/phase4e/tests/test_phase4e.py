"""Worker contract checks with a separate identified-runner expectation."""
import builtins
import contextlib
from copy import deepcopy
from dataclasses import FrozenInstanceError
import hashlib
import io as text_io
import itertools
import os
from pathlib import Path
import random
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from prototypes.phase4e import CAPABILITIES, DEPENDENCY_SOURCES
from prototypes.phase4e import fixtures, io, model, runner
from prototypes.phase4e.model import AdvanceHalf, BeginBatter, HalfInningState, HalfSequenceState, Pitch, PitchHalfState, Resolve, ResolveSafeHit, action_from_mapping, replay, step

CountState=model.sequence_rules.pitch_half_rules.CountState
B,C,M,T,F,U,O,P=model.sequence_rules.pitch_half_rules.count_rules.EVENTS
W,X,H=model.sequence_rules.pitch_half_rules.half_rules.EVENTS
BASES=tuple(itertools.product((False,True),repeat=3))


def state(bases=(False,False,False),side="top",outs=0,runs=7,count=None,n=1,a=11,h=13,inning=2):
    if count=="fair":count=CountState(0,0,n,"fair_play_pending")
    return HalfSequenceState(inning,side,a,h,PitchHalfState(count,HalfInningState(outs,bases,runs)))


def reference(bases,hit,destinations):
    """Identify batter/each pre-play runner and compare every ordered pair."""
    original={i for i,present in enumerate(bases,1) if present}
    supplied={i for i,d in enumerate(destinations,1) if d is not None}
    if original!=supplied:raise ValueError("runner identities do not match")
    locations={0:hit}|{i:destinations[i-1] for i in original}
    if any(locations[i]<i for i in original):raise ValueError("runner finishes behind origin")
    for back,front in itertools.combinations(sorted(locations),2):
        back_end,front_end=locations[back],locations[front]
        if back_end>front_end or (back_end==front_end and front_end!=4):raise ValueError("runner order/collision")
    scored={identity for identity,destination in locations.items() if destination==4}
    occupied=tuple(any(destination==base for destination in locations.values()) for base in (1,2,3))
    return occupied,len(scored)


class HitTests(unittest.TestCase):
    def test_18000_typed_trials_against_identified_runner_expectation(self):
        accepted=rejected=base_accepted=base_rejected=0;accepted_keys=set()
        for hit,destinations,bases,side,outs in itertools.product((1,2,3),itertools.product((None,1,2,3,4),repeat=3),BASES,("top","bottom"),range(3)):
            with self.subTest(hit=hit,destinations=destinations,bases=bases,side=side,outs=outs):
                before=state(bases,side,outs,count="fair");action=ResolveSafeHit(hit,destinations)
                try:expected_bases,added=reference(bases,hit,destinations)
                except ValueError:
                    with self.assertRaises(ValueError):step(before,action)
                    rejected+=1
                    if side=="top" and outs==0:base_rejected+=1
                    self.assertEqual(before,state(bases,side,outs,count="fair"))
                else:
                    after=step(before,action)
                    expected=HalfSequenceState(2,side,11,13,PitchHalfState(None,HalfInningState(outs,expected_bases,7+added)))
                    self.assertEqual(after,expected)
                    self.assertEqual(sum(after.current.half.bases)+added,sum(bases)+1)
                    team="away" if side=="top" else "home";other="home" if team=="away" else "away"
                    self.assertEqual(after.score[team]-before.score[team],added);self.assertEqual(after.score[other],before.score[other])
                    accepted_keys.add((bases,hit,destinations));accepted+=1
                    if side=="top" and outs==0:base_accepted+=1
        self.assertEqual((accepted,rejected),(288,17712));self.assertEqual((base_accepted,base_rejected),(48,2952))
        fixture_keys={(tuple(r["before"]["bases"]),r["hit_bases"],tuple(r["runner_destinations"])) for r in fixtures.pattern_table()}
        self.assertEqual(accepted_keys,fixture_keys)

    def test_static_48_patterns_and_mask_hit_counts(self):
        rows=fixtures.pattern_table();self.assertEqual(len(rows),48);counts={}
        for row in rows:
            before=HalfInningState.from_mapping(row["before"]);bases,added=reference(before.bases,row["hit_bases"],tuple(row["runner_destinations"]))
            self.assertEqual(row["after"],HalfInningState(0,bases,added).as_dict());self.assertEqual(row["runs_added"],added)
            key=(before.bases,row["hit_bases"]);counts[key]=counts.get(key,0)+1
        table={"000":(1,1,1),"001":(2,2,1),"010":(3,2,1),"011":(4,2,1),"100":(3,2,1),"101":(4,2,1),"110":(4,2,1),"111":(4,2,1)}
        for bits,expected in table.items():self.assertEqual(tuple(counts[(tuple(b=="1" for b in bits),hit)] for hit in (1,2,3)),expected)

    def test_holds_multiple_runs_and_specific_illegal_final_positions(self):
        for bases,hit,dest,expected,added in (((False,True,False),1,(None,2,None),(True,True,False),0),
            ((False,False,True),2,(None,None,3),(False,True,True),0),
            ((True,True,True),1,(2,3,4),(True,True,True),1),((True,True,True),1,(3,4,4),(True,False,True),2),((True,True,True),1,(4,4,4),(True,False,False),3)):
            after=step(state(bases,count="fair"),ResolveSafeHit(hit,dest));self.assertEqual(after.current.half.bases,expected);self.assertEqual(after.current.half.runs,7+added)
        for bases,hit,dest in (((False,False,False),1,(2,None,None)),((True,False,False),1,(None,None,None)),
            ((False,True,False),1,(None,1,None)),((True,False,False),1,(1,None,None)),((True,True,False),1,(4,3,None)),((True,False,True),1,(3,None,3))):
            with self.assertRaises(ValueError):step(state(bases,count="fair"),ResolveSafeHit(hit,dest))

    def test_fair_only_control20_cells_and_other_waiting_status_rejected(self):
        currents=(None,CountState(),CountState(4,0,4,"walk_awarded"),CountState(0,3,3,"strikeout_out"),CountState(0,0,1,"caught_foul_out"))
        for side,count in itertools.product(("top","bottom"),currents):
            with self.assertRaises(ValueError):step(state(side=side,count=count),ResolveSafeHit(1,(None,)*3))
        with self.assertRaises(ValueError):step(state(outs=3),ResolveSafeHit(1,(None,)*3))
        self.assertEqual(len(fixtures.control_table()),20);runner.check_control(fixtures.control_table())

    def test_exact_old_aliases_parser_step_delegation_once_new_hit_zero(self):
        self.assertIs(HalfSequenceState,model.sequence_rules.HalfSequenceState)
        for name in ("BeginBatter","Pitch","Resolve","AdvanceHalf"):self.assertIs(getattr(model,name),getattr(model.sequence_rules,name))
        cases=((state(),BeginBatter()),(state(count=CountState()),Pitch(B)),(state(count=CountState(4,0,4,"walk_awarded")),Resolve(W)),(state(outs=3),AdvanceHalf()))
        real=model.sequence_rules.step
        with patch.object(model.sequence_rules,"step",wraps=real) as call:
            for index,(before,action) in enumerate(cases,1):step(before,action);self.assertEqual(call.call_count,index)
            step(state(count="fair"),ResolveSafeHit(1,(None,)*3));self.assertEqual(call.call_count,4)
            with self.assertRaises(ValueError):step(state(),ResolveSafeHit(1,(None,)*3))
            self.assertEqual(call.call_count,4)
        parser=model.sequence_rules.action_from_mapping
        with patch.object(model.sequence_rules,"action_from_mapping",wraps=parser) as call:
            for _,action in cases:self.assertEqual(action_from_mapping(action.as_dict()),action)
            self.assertEqual(call.call_count,4)
            self.assertEqual(action_from_mapping(ResolveSafeHit(2,(None,)*3).as_dict()),ResolveSafeHit(2,(None,)*3));self.assertEqual(call.call_count,4)

    def test_legacy_resolve_five_compatibilities_preserved(self):
        counts=(CountState(4,0,4,"walk_awarded"),CountState(0,3,3,"strikeout_out"),CountState(0,0,1,"caught_foul_out"),CountState(0,0,1,"fair_play_pending"))
        allowed=((W,),(X,),(X,),(X,H))
        for count,plays in zip(counts,allowed):
            for play in (W,X,H):
                before=state(count=count)
                if play in plays:self.assertEqual(step(before,Resolve(play)),model.sequence_rules.step(before,Resolve(play)))
                else:
                    with self.assertRaises(ValueError):step(before,Resolve(play))

    def test_new_hit_consumption_explicit_next_begin_and_old_snapshot_purity(self):
        pending=state(count="fair");hit=ResolveSafeHit(1,(None,)*3);after=step(pending,hit)
        self.assertEqual(after.stage,"between_batters");self.assertIsNone(after.current.count)
        with self.assertRaises(ValueError):step(after,hit)
        with self.assertRaises(ValueError):step(after,Pitch(P))
        self.assertEqual(step(pending,hit),after);self.assertEqual(step(after,BeginBatter()).current.count,CountState())

    def test_huge_scores_distinct_fair_counts_and_long_mixed_sequence(self):
        huge=10**100
        for count in (CountState(0,0,1,"fair_play_pending"),CountState(3,2,huge,"fair_play_pending")):
            for side in ("top","bottom"):
                before=state((True,True,True),side,runs=huge,count=count,a=huge,h=huge)
                after=step(before,ResolveSafeHit(3,(4,4,4)));self.assertEqual(after.score[before.batting_team],2*huge+3)
        actions=[]
        for _ in range(128):
            actions += [BeginBatter(),Pitch(P),ResolveSafeHit(1,(None,)*3),BeginBatter(),Pitch(P),ResolveSafeHit(2,(4,None,None)),
                BeginBatter(),Pitch(P),ResolveSafeHit(1,(None,2,None)),BeginBatter(),*[Pitch(B)]*4,Resolve(W),BeginBatter(),Pitch(P),Resolve(H),
                *([BeginBatter(),Pitch(O),Resolve(X)]*3),AdvanceHalf()]
        result=replay(state(runs=0,a=0,h=0,inning=1),actions)
        self.assertEqual(result.final_state.score,{"away":320,"home":320});self.assertEqual((result.final_state.inning,result.final_state.side),(65,"top"))
        self.assertEqual(result.final_state.current.half,HalfInningState())

    def test_fixed18_columns60_actions78_splits_and_reverse_order(self):
        rows=fixtures.scenarios();self.assertEqual(len(rows),18);self.assertEqual(sum(len(r["actions"]) for r in rows),60);splits=0;results={}
        for row in rows:
            initial=HalfSequenceState.from_mapping(row["initial_state"]);actions=[action_from_mapping(a) for a in row["actions"]];result=replay(initial,actions);results[row["id"]]=result
            self.assertEqual(result.final_state.as_dict(),row["expected_final"]);self.assertEqual(result.final_state.score,row["expected_score"]);self.assertEqual(result.final_state.stage,row["expected_stage"])
            for split in range(len(actions)+1):
                with self.subTest(id=row["id"],split=split):
                    prefix=replay(initial,actions[:split]);suffix=replay(prefix.final_state,actions[split:]);self.assertEqual(suffix.final_state,result.final_state);self.assertEqual(prefix.trace+suffix.trace,result.trace);splits+=1
        self.assertEqual(splits,78)
        for row in reversed(rows):self.assertEqual(replay(HalfSequenceState.from_mapping(row["initial_state"]),tuple(action_from_mapping(a) for a in row["actions"])),results[row["id"]])

    def test_trace_exact5_keys_derived_score_deltas_and_zero_non_scoring_actions(self):
        for row in fixtures.scenarios():
            result=replay(HalfSequenceState.from_mapping(row["initial_state"]),[action_from_mapping(a) for a in row["actions"]])
            for entry in result.trace:
                self.assertEqual(set(entry.as_dict()),{"action","before","after","away_runs_added","home_runs_added"})
                self.assertEqual((entry.away_runs_added,entry.home_runs_added),(entry.after.score["away"]-entry.before.score["away"],entry.after.score["home"]-entry.before.score["home"]))
                self.assertGreaterEqual(entry.away_runs_added,0);self.assertGreaterEqual(entry.home_runs_added,0)
                if type(entry.action) is ResolveSafeHit:
                    self.assertLessEqual(entry.away_runs_added+entry.home_runs_added,3);self.assertEqual(entry.before.current.half.outs,entry.after.current.half.outs)
                elif type(entry.action) is not Resolve:self.assertEqual((entry.away_runs_added,entry.home_runs_added),(0,0))


class ValidationTests(unittest.TestCase):
    def test_constructor_shape_only_and_strict_types_subclasses_ranges(self):
        class IntSubclass(int):pass
        class TupleSubclass(tuple):pass
        for value in (True,False,1.0,"1",None,0,4,-1,IntSubclass(1),float("inf")):
            with self.subTest(hit=repr(value)),self.assertRaises(ValueError):ResolveSafeHit(value,(None,)*3)
        for dest in ([None]*3,(),(None,None),(None,)*4,TupleSubclass((None,)*3),"---",None,(0,None,None),(5,None,None),(-1,None,None),(True,None,None),(1.0,None,None),(IntSubclass(1),None,None)):
            with self.subTest(dest=repr(dest)),self.assertRaises(ValueError):ResolveSafeHit(1,dest)
        # Occupancy and order are deliberately step validation, not constructor validation.
        self.assertEqual(ResolveSafeHit(1,(1,None,None)).runner_destinations,(1,None,None))

    def test_json_exact_keys_list_conversion_duplicates_nonfinite(self):
        action=ResolveSafeHit(1,(2,None,4));self.assertEqual(action_from_mapping(action.as_dict()),action)
        value=action.as_dict();converted=action_from_mapping(value);value["runner_destinations"][0]=4;self.assertEqual(converted,action)
        for data in ({},None,[],{"kind":True},action.as_dict()|{"extra":1},action.as_dict()|{"runner_destinations":(2,None,4)},
            {"kind":"resolve_safe_hit","hit_bases":1},action.as_dict()|{"runner_destinations":[False,None,4]}):
            with self.assertRaises(ValueError):action_from_mapping(data)
        for text in ('{"kind":"resolve_safe_hit","kind":"pitch"}','{"x":NaN}','{"x":Infinity}','{"x":-Infinity}'):
            with self.assertRaises(ValueError):fixtures.parse_json(text)

    def test_immutable_new_values_detached_json_strict_state_action_sequence(self):
        class HitSubclass(ResolveSafeHit):pass
        class ListSubclass(list):pass
        before=state(count="fair");hit=ResolveSafeHit(1,(None,)*3);result=replay(before,[hit])
        for obj,field,value in ((hit,"hit_bases",2),(result,"final_state",before),(result.trace[0],"away_runs_added",1)):
            with self.assertRaises(FrozenInstanceError):setattr(obj,field,value)
        data=result.as_dict();data["trace"][0]["action"]["runner_destinations"][0]=4;self.assertEqual(hit.runner_destinations,(None,)*3)
        with self.assertRaises(TypeError):CAPABILITIES["product_adoption"]=True
        for action in (hit.as_dict(),"S1",None,HitSubclass(1,(None,)*3)):
            with self.assertRaises(ValueError):step(before,action)
        for s in (None,{},before.as_dict()):
            with self.assertRaises(ValueError):step(s,hit)
        for actions in ("S1",None,{},iter([hit]),ListSubclass([hit])):
            with self.assertRaises(ValueError):replay(before,actions)
        with self.assertRaises(ValueError):model.ReplayResult(before,[])
        with self.assertRaises(ValueError):model.TraceEntry(hit,before,result.final_state,True,0)

    def test_empty_all_stages_invalid_tail_input_unchanged_and_optimized_validation(self):
        for before in (state(),state(count=CountState()),state(count="fair"),state(outs=3)):
            self.assertEqual(replay(before,[]),model.ReplayResult(before,()));self.assertEqual(replay(before,()),model.ReplayResult(before,()))
        actions=[ResolveSafeHit(1,(None,)*3),ResolveSafeHit(1,(None,)*3)];original=list(actions)
        with self.assertRaises(ValueError):replay(state(count="fair"),actions)
        self.assertEqual(actions,original)
        code="""from prototypes.phase4e.model import *
s=HalfSequenceState(1,'top',0,0,PitchHalfState(None,HalfInningState()))
cases=[lambda:ResolveSafeHit(True,(None,)*3),lambda:ResolveSafeHit(1,[None]*3),lambda:ResolveSafeHit(1,(False,None,None)),lambda:step(s,ResolveSafeHit(1,(None,)*3)),lambda:replay(s,'S1')]
for case in cases:
    try:case()
    except ValueError:continue
    raise RuntimeError('optimized validation vanished')
"""
        subprocess.run([sys.executable,"-O","-c",code],cwd=io.SOURCE_ROOT,check=True,capture_output=True)


class RunnerTests(unittest.TestCase):
    def setUp(self):
        parent=io.SOURCE_ROOT/"data/prototypes/phase4e/unittest";parent.mkdir(parents=True,exist_ok=True)
        self.work=Path(tempfile.mkdtemp(prefix="case-",dir=parent));self.root_patch=patch.object(io,"ROOT",self.work);self.root_patch.start();self.addCleanup(self.root_patch.stop)

    def test_two_runs_four_files_hashes_flags_and_no_absolute_paths(self):
        first=runner.run(self.work/"data/prototypes/phase4e/one");second=runner.run(self.work/"reports/phase4e/two")
        self.assertEqual({p.name for p in first.iterdir()},{"plan.json","traces.json","REPORT.md","SHA256SUMS.json"})
        for p in first.iterdir():self.assertEqual(p.read_bytes(),(second/p.name).read_bytes());self.assertNotIn(str(self.work),p.read_text(encoding="utf-8"))
        manifest=fixtures.parse_json((first/"SHA256SUMS.json").read_text());self.assertEqual(set(manifest),{"plan.json","traces.json","REPORT.md"})
        for name,wanted in manifest.items():self.assertEqual(io.digest(first/name),wanted)
        for name in ("plan.json","traces.json"):
            data=fixtures.parse_json((first/name).read_text())
            for key,value in CAPABILITIES.items():self.assertEqual(data[key],value)

    def test_output_old_namespaces_bounds_existing_and_input_overlap(self):
        for path in (self.work/"data/prototypes/phase4c/new",self.work/"reports/phase4b/new",self.work/"data/raw/new",self.work/"reports/phase4e",self.work.parent/"outside"):
            with self.assertRaises(ValueError):io.reserve_output(path)
        output=self.work/"data/prototypes/phase4e/one"
        for p in (output,output/"child",output.parent):
            with self.assertRaises(ValueError):io.reserve_output(output,[p])
        self.assertFalse(output.exists());io.reserve_output(output)
        with self.assertRaises(FileExistsError):io.reserve_output(output)

    def test_real_junction_symlink_whole_child_to_old_outside(self):
        for location,kind in itertools.product(("namespace","child"),("old","outside")):
            with self.subTest(location=location,kind=kind):
                root=self.work/(location+"-"+kind);target=root/"reports/phase4c" if kind=="old" else self.work/(root.name+"-outside");target.mkdir(parents=True)
                namespace=root/"data/prototypes/phase4e";alias=namespace if location=="namespace" else namespace/"shortcut";alias.parent.mkdir(parents=True,exist_ok=True)
                if os.name=="nt":subprocess.run(["cmd","/c","mklink","/J",str(alias),str(target)],check=True,capture_output=True,text=True)
                else:alias.symlink_to(target,target_is_directory=True)
                with patch.object(io,"ROOT",root),self.assertRaises(ValueError):io.reserve_output(alias/"forbidden")
                self.assertFalse((target/"forbidden").exists())

    def test_identity_complete_nine_old_sources_and_new_runtime(self):
        identity=io.implementation_identity();self.assertEqual(identity["old_dependency_sources"],list(DEPENDENCY_SOURCES));self.assertEqual(len(DEPENDENCY_SOURCES),9)
        required=set(DEPENDENCY_SOURCES)|{"prototypes/phase4e/.gitattributes","prototypes/phase4e/fixtures/control.json","prototypes/phase4e/fixtures/scenarios.json","prototypes/phase4e/fixtures/patterns.json"}
        required|={p.relative_to(io.SOURCE_ROOT).as_posix() for p in (io.SOURCE_ROOT/"prototypes/phase4e").glob("*.py")}
        self.assertEqual(set(identity["sources_and_fixtures"]),required);self.assertEqual(hashlib.sha256(io.canonical(identity["sources_and_fixtures"])).hexdigest(),identity["bundle_sha256"])

    def test_identity_lf_crlf_cr_copy(self):
        identity=io.implementation_identity();root=self.work/"identity-copy"
        originals={name:(io.SOURCE_ROOT/name).read_text(encoding="utf-8").replace("\r\n","\n").replace("\r","\n") for name in identity["sources_and_fixtures"]}
        for newline in ("\n","\r\n","\r"):
            for name,text in originals.items():
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(text.replace("\n",newline).encode())
            with patch.object(io,"SOURCE_ROOT",root),patch.object(io,"__file__",str(root/"prototypes/phase4e/io.py")):self.assertEqual(io.implementation_identity(),identity)

    def test_run_allowed_old_nine_only_rng_clock_and_input_guards(self):
        allowed={(io.SOURCE_ROOT/name).resolve() for name in io.implementation_identity()["sources_and_fixtures"]};opened=[]
        original_open,original_path_open,original_import=builtins.open,Path.open,builtins.__import__
        imports={"prototypes.phase4"+phase+suffix for phase in ("a","b","c","d") for suffix in ("",".model")}
        def guard(path,mode):
            if not isinstance(path,(str,bytes,Path)) or not ('r' in mode or '+' in mode):return
            path=Path(path).resolve()
            if path not in allowed and not path.is_relative_to(self.work):raise AssertionError("unapproved old/data/source read")
            opened.append(path)
        def file_open(path,mode='r',*args,**kwargs):guard(path,mode);return original_open(path,mode,*args,**kwargs)
        def path_open(path,mode='r',*args,**kwargs):guard(path,mode);return original_path_open(path,mode,*args,**kwargs)
        def imported(name,*args,**kwargs):
            if (any(word in name for word in ("phase4a","phase4b","phase4c","phase4d")) and name not in imports) or any(word in name for word in ("statcast","phase3")):raise AssertionError("unapproved old/research import")
            return original_import(name,*args,**kwargs)
        def forbidden(*a,**kw):raise AssertionError("RNG/clock prohibited")
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(builtins,"open",file_open));stack.enter_context(patch.object(Path,"open",path_open));stack.enter_context(patch.object(builtins,"__import__",imported))
            for name in ("random","uniform","randint","randrange","choice","gauss","getrandbits","Random","SystemRandom"):stack.enter_context(patch.object(random,name,forbidden))
            for name in ("time","time_ns","monotonic","perf_counter"):stack.enter_context(patch.object(time,name,forbidden))
            runner.run(self.work/"data/prototypes/phase4e/audited")
        for name in DEPENDENCY_SOURCES:self.assertIn((io.SOURCE_ROOT/name).resolve(),opened)

    def test_cli_fixed_run_and_extra_option_rejection(self):
        from prototypes.phase4e.__main__ import main
        for option in ("--input","--raw-root","--model","--seed","--resume"):
            with patch.object(sys,"argv",["phase4e","run","--output","x",option,"x"]),contextlib.redirect_stderr(text_io.StringIO()),self.assertRaises(SystemExit):main()
        output=self.work/"reports/phase4e/cli"
        with patch.object(sys,"argv",["phase4e","run","--output",str(output)]):main()
        self.assertTrue((output/"traces.json").is_file())

    def test_fixed_columns_all_pins_and_equal_final_old_pitch_substitution(self):
        for change in ("id","initial_state","actions","expected_final","expected_score","expected_stage","order"):
            rows=fixtures.scenarios()
            if change=="id":rows[0]["id"]="other"
            elif change=="initial_state":rows[0]["initial_state"]["side"]="bottom"
            elif change=="expected_final":rows[0]["expected_final"]["closed_away_runs"]=1
            elif change=="expected_score":rows[0]["expected_score"]["away"]=False
            elif change=="expected_stage":rows[0]["expected_stage"]="pitching"
            elif change=="order":rows.reverse()
            else:
                row=next(r for r in rows if r["id"]=="hit_retire_switch");row["actions"][4]=Pitch(P).as_dict()
                self.assertEqual(replay(HalfSequenceState.from_mapping(row["initial_state"]),[action_from_mapping(a) for a in row["actions"]]).final_state.as_dict(),row["expected_final"])
            with self.subTest(change=change),patch.object(fixtures,"load",return_value=rows),self.assertRaises(ValueError):fixtures.scenarios()
        rows=fixtures.scenarios();rows[0]["actions"][2]["hit_bases"]=2
        with patch.object(fixtures,"load",return_value=rows),self.assertRaises(ValueError):fixtures.scenarios()

    def test_actual_json_equivalent_final_drift_and_pattern_mismatch_before_output(self):
        folder=self.work/"mutant-fixtures";folder.mkdir()
        for p in fixtures.FIXTURE_ROOT.glob("*.json"):(folder/p.name).write_bytes(p.read_bytes())
        data=fixtures.parse_json((folder/"scenarios.json").read_text());row=next(r for r in data["scenarios"] if r["id"]=="hit_retire_switch");row["actions"][4]=Pitch(P).as_dict()
        (folder/"scenarios.json").write_bytes(io.canonical(data));output=self.work/"data/prototypes/phase4e/mutant"
        with patch.object(fixtures,"FIXTURE_ROOT",folder),self.assertRaises(ValueError):runner.run(output)
        self.assertFalse(output.exists())
        rows=fixtures.pattern_table();rows[0]["after"]["runs"]=1
        with patch.object(runner,"pattern_table",return_value=rows),self.assertRaises(ValueError):runner.run(output)
        self.assertFalse(output.exists())

    def test_pattern_control_cardinality_and_identity_drift_before_output(self):
        for function,rows in ((fixtures.pattern_table,fixtures.pattern_table()[:-1]),(fixtures.pattern_table,fixtures.pattern_table()+[fixtures.pattern_table()[0]]),(fixtures.control_table,fixtures.control_table()[:-1])):
            with patch.object(fixtures,"load",return_value=rows),self.assertRaises(ValueError):function()
        output=self.work/"data/prototypes/phase4e/changed";identity=io.implementation_identity()
        with patch.object(runner,"implementation_identity",side_effect=[identity,identity|{"python":"changed"}]),self.assertRaises(ValueError):runner.run(output)
        self.assertFalse(output.exists())


if __name__=="__main__":unittest.main()
