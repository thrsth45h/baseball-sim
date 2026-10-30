"""Worker checks of the approved ledger/control contract."""
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

from prototypes.phase4d import CAPABILITIES, DEPENDENCY_SOURCES
from prototypes.phase4d import fixtures, io, model, runner
from prototypes.phase4d.model import AdvanceHalf, BeginBatter, HalfInningState, HalfSequenceState, Pitch, PitchHalfState, Resolve, action_from_mapping, replay, step

B,C,M,T,F,U,O,P = model.pitch_half_rules.count_rules.EVENTS
W,X,H = model.pitch_half_rules.half_rules.EVENTS
CountState = model.pitch_half_rules.CountState
BASES = tuple(itertools.product((False,True),repeat=3))


def state(side="top",outs=0,bases=(False,False,False),runs=0,count=None,inning=1,a=0,h=0):
    return HalfSequenceState(inning,side,a,h,PitchHalfState(count,HalfInningState(outs,bases,runs)))


class ControllerTests(unittest.TestCase):
    def test_96_representative_advances_preserve_score_reset_new_snapshot(self):
        checked=0
        for inning,side,bases,runs in itertools.product((1,2),("top","bottom"),BASES,(0,7,11)):
            with self.subTest(inning=inning,side=side,bases=bases,runs=runs):
                before=state(side,3,bases,runs,inning=inning,a=11,h=13)
                after=step(before,AdvanceHalf())
                self.assertEqual(after.score,before.score)
                self.assertEqual(after.inning,inning+(side=="bottom"))
                self.assertEqual(after.side,"bottom" if side=="top" else "top")
                self.assertEqual(after.closed_away_runs,11+(runs if side=="top" else 0))
                self.assertEqual(after.closed_home_runs,13+(runs if side=="bottom" else 0))
                self.assertEqual(after.current,PitchHalfState(None,HalfInningState()))
                self.assertEqual(before.current.half,HalfInningState(3,bases,runs))
                with self.assertRaises(ValueError): step(after,Pitch(B))
                self.assertEqual(step(after,BeginBatter()).current.count,CountState())
                checked+=1
        self.assertEqual(checked,96)

    def test_control_16_cells_separate_diagonal_acceptance(self):
        states=[state(),state(count=CountState()),state(count=CountState(4,0,4,"walk_awarded")),state(outs=3)]
        actions=[BeginBatter(),Pitch(B),Resolve(W),AdvanceHalf()]
        self.assertEqual(len(fixtures.control_table()),16)
        for i,s in enumerate(states):
            for j,action in enumerate(actions):
                with self.subTest(stage=s.stage,action=action):
                    if i==j:step(s,action)
                    else:
                        with self.assertRaises(ValueError):step(s,action)
        runner.check_control(fixtures.control_table())

    def test_old_types_parser_aliases_and_one_delegate_each_zero_for_advance(self):
        for name in ("BeginBatter","Pitch","Resolve"):
            self.assertIs(getattr(model,name),getattr(model.pitch_half_rules,name))
        real=model.pitch_half_rules.step
        with patch.object(model.pitch_half_rules,"step",wraps=real) as call:
            s=step(state(),BeginBatter());self.assertEqual(call.call_count,1)
            step(s,Pitch(B));self.assertEqual(call.call_count,2)
            step(state(count=CountState(4,0,4,"walk_awarded")),Resolve(W));self.assertEqual(call.call_count,3)
            step(state(outs=3),AdvanceHalf());self.assertEqual(call.call_count,3)
            with self.assertRaises(ValueError):step(state(),Resolve(W))
            self.assertEqual(call.call_count,4)
        parser=model.pitch_half_rules.action_from_mapping
        with patch.object(model.pitch_half_rules,"action_from_mapping",wraps=parser) as call:
            for action in (BeginBatter(),Pitch(B),Resolve(W)):self.assertEqual(action_from_mapping(action.as_dict()),action)
            self.assertEqual(call.call_count,3)
            self.assertEqual(action_from_mapping({"kind":"advance_half"}),AdvanceHalf());self.assertEqual(call.call_count,3)

    def test_both_side_old_current_updates_and_resolve_compatibility(self):
        counts=(CountState(4,0,4,"walk_awarded"),CountState(0,3,3,"strikeout_out"),CountState(0,0,1,"caught_foul_out"),CountState(0,0,1,"fair_play_pending"))
        allowed=((W,),(X,),(X,),(X,H))
        for side in ("top","bottom"):
            for count,plays in zip(counts,allowed):
                for play in (W,X,H):
                    with self.subTest(side=side,status=count.status,play=play):
                        before=state(side,1,(True,True,True),7,count=count,inning=2,a=11,h=13)
                        if play not in plays:
                            with self.assertRaises(ValueError):step(before,Resolve(play))
                            continue
                        expected=model.pitch_half_rules.step(before.current,Resolve(play))
                        after=step(before,Resolve(play))
                        self.assertEqual(after.current,expected)
                        self.assertEqual((after.inning,after.side,after.closed_away_runs,after.closed_home_runs),(2,side,11,13))
                        team=before.batting_team;other="home" if team=="away" else "away"
                        self.assertEqual(after.score[other],before.score[other])
                        self.assertEqual(after.score[team]-before.score[team],expected.half.runs-before.current.half.runs)
            for action,count in ((BeginBatter(),None),(Pitch(B),CountState())):
                before=state(side,0,(True,False,True),7,count=count,a=11,h=13)
                after=step(before,action)
                self.assertEqual(after.current,model.pitch_half_rules.step(before.current,action))
                self.assertEqual(after.score,before.score)

    def test_advance_unfinished_pending_third_out_and_continuous_rejection(self):
        for side in ("top","bottom"):
            for count in (None,CountState(),CountState(0,3,3,"strikeout_out")):
                with self.subTest(side=side,count=count),self.assertRaises(ValueError):step(state(side,2,(True,)*3,7,count=count),AdvanceHalf())
            waiting=state(side,2,(True,)*3,7,count=CountState(0,3,3,"strikeout_out"))
            closed=step(waiting,Resolve(X));self.assertEqual(closed.stage,"half_complete")
            switched=step(closed,AdvanceHalf());self.assertEqual(switched.stage,"between_batters")
            with self.assertRaises(ValueError):step(switched,AdvanceHalf())
            self.assertEqual(step(closed,AdvanceHalf()),switched)

    def test_all_twelve_old_concrete_actions_rejected_when_closed(self):
        actions=[BeginBatter(),*[Pitch(e) for e in (B,C,M,T,F,U,O,P)],*[Resolve(e) for e in (W,X,H)]]
        self.assertEqual(len(actions),12)
        for side,action in itertools.product(("top","bottom"),actions):
            with self.subTest(side=side,action=action),self.assertRaises(ValueError):step(state(side,3,(True,)*3,7),action)

    def test_huge_inning_closed_and_current_integer_scores(self):
        huge=10**100
        for side in ("top","bottom"):
            before=state(side,3,(True,False,True),huge,inning=huge,a=huge,h=huge+1)
            after=step(before,AdvanceHalf())
            self.assertEqual(after.score,before.score)
            self.assertEqual(after.inning,huge+(side=="bottom"))
            waiting=state(side,1,(True,)*3,huge,count=CountState(0,0,1,"fair_play_pending"),a=huge,h=huge)
            after=step(waiting,Resolve(H))
            self.assertEqual(after.score[waiting.batting_team],huge*2+4)

    def test_ninth_tenth_no_game_end_no_runner_placement_no_winner(self):
        top=state("top",3,inning=9,a=0,h=100)
        bottom=step(top,AdvanceHalf())
        self.assertEqual((bottom.inning,bottom.side),(9,"bottom"))
        self.assertEqual(bottom.score,{"away":0,"home":100})
        tenth=step(state("bottom",3,inning=9,a=0,h=100),AdvanceHalf())
        self.assertEqual((tenth.inning,tenth.side),(10,"top"))
        self.assertEqual(tenth.current.half.bases,(False,)*3)
        self.assertFalse(CAPABILITIES["game_end_rules_implemented"])
        self.assertEqual(step(state("bottom",3,inning=10),AdvanceHalf()).inning,11)

    def test_long_half_sequence_matches_separate_team_ledger_no_advance_score(self):
        actions=[];ledger={"away":0,"home":0}
        for half in range(128):
            runs=half%4+1;ledger["away" if half%2==0 else "home"]+=runs
            actions += [BeginBatter(),Pitch(P),Resolve(H)]*runs+[BeginBatter(),Pitch(O),Resolve(X)]*3+[AdvanceHalf()]
        result=replay(state(),actions)
        self.assertEqual(result.final_state.score,ledger)
        self.assertEqual((result.final_state.inning,result.final_state.side),(65,"top"))
        self.assertEqual((result.final_state.closed_away_runs,result.final_state.closed_home_runs),(ledger["away"],ledger["home"]))
        for entry in result.trace:
            if type(entry.action) is AdvanceHalf:self.assertEqual((entry.away_runs_added,entry.home_runs_added),(0,0))

    def test_sixteen_columns_100_actions_116_splits_reverse_order(self):
        rows=fixtures.scenarios();self.assertEqual(len(rows),16);self.assertEqual(sum(len(r["actions"]) for r in rows),100)
        expected={};splits=0
        for row in rows:
            initial=HalfSequenceState.from_mapping(row["initial_state"]);actions=[action_from_mapping(a) for a in row["actions"]]
            result=replay(initial,actions);expected[row["id"]]=result
            self.assertEqual(result.final_state.as_dict(),row["expected_final"])
            self.assertEqual(result.final_state.score,row["expected_score"]);self.assertEqual(result.final_state.stage,row["expected_stage"])
            for split in range(len(actions)+1):
                with self.subTest(id=row["id"],split=split):
                    prefix=replay(initial,actions[:split]);suffix=replay(prefix.final_state,actions[split:])
                    self.assertEqual(suffix.final_state,result.final_state);self.assertEqual(prefix.trace+suffix.trace,result.trace);splits+=1
        self.assertEqual(splits,116)
        for row in reversed(rows):self.assertEqual(replay(HalfSequenceState.from_mapping(row["initial_state"]),tuple(action_from_mapping(a) for a in row["actions"])),expected[row["id"]])

    def test_trace_five_keys_team_attribution_nonnegative_and_zero_other_actions(self):
        for row in fixtures.scenarios():
            result=replay(HalfSequenceState.from_mapping(row["initial_state"]),[action_from_mapping(a) for a in row["actions"]])
            for entry in result.trace:
                self.assertEqual(set(entry.as_dict()),{"action","before","after","away_runs_added","home_runs_added"})
                self.assertEqual(entry.away_runs_added,entry.after.score["away"]-entry.before.score["away"])
                self.assertEqual(entry.home_runs_added,entry.after.score["home"]-entry.before.score["home"])
                self.assertGreaterEqual(entry.away_runs_added,0);self.assertGreaterEqual(entry.home_runs_added,0)
                if type(entry.action) is not Resolve:self.assertEqual((entry.away_runs_added,entry.home_runs_added),(0,0))
                elif entry.before.side=="top":self.assertEqual(entry.home_runs_added,0)
                else:self.assertEqual(entry.away_runs_added,0)


class ValidationTests(unittest.TestCase):
    def test_strict_five_fields_bounds_and_subclasses(self):
        class IntSubclass(int):pass
        class StringSubclass(str):pass
        class CurrentSubclass(PitchHalfState):pass
        valid=state().as_dict()
        for field in ("inning","closed_away_runs","closed_home_runs"):
            for value in (True,False,1.0,"1",None,float("nan"),float("inf"),IntSubclass(1),-1):
                data=deepcopy(valid);data[field]=value
                with self.subTest(field=field,value=repr(value)),self.assertRaises(ValueError):HalfSequenceState.from_mapping(data)
        with self.assertRaises(ValueError):HalfSequenceState(0,"top",0,0,state().current)
        for side in (True,None,"away","TOP",StringSubclass("top")):
            with self.assertRaises(ValueError):HalfSequenceState(1,side,0,0,state().current)
        for current in ({},None,CurrentSubclass(None,HalfInningState())):
            with self.assertRaises(ValueError):HalfSequenceState(1,"top",0,0,current)
        # External structure is accepted without claiming historical reachability.
        self.assertEqual(state(a=11,h=13).score,{"away":11,"home":13})

    def test_state_action_json_exact_keys_duplicates_nonfinite_and_old_validation(self):
        original=state().as_dict()
        for value in ({},None,[],original|{"score":{"away":0,"home":0}},original|{"stage":"between_batters"}):
            with self.assertRaises(ValueError):HalfSequenceState.from_mapping(value)
        invalid=deepcopy(original);invalid["current"]["half"]["outs"]=True
        with self.assertRaises(ValueError):HalfSequenceState.from_mapping(invalid)
        invalid=deepcopy(original);invalid["current"]["half"]["outs"]=3;invalid["current"]["count"]=CountState().as_dict()
        with self.assertRaises(ValueError):HalfSequenceState.from_mapping(invalid)
        for value in ({},{"kind":"advance_half","extra":0},{"kind":True},{"kind":"AdvanceHalf"},{"kind":"pitch"},{"kind":"resolve","completed_play":"single"}):
            with self.assertRaises(ValueError):action_from_mapping(value)
        for text in ('{"inning":1,"inning":2}','{"current":{"count":null,"count":null}}','{"x":NaN}','{"x":Infinity}','{"x":-Infinity}'):
            with self.assertRaises(ValueError):fixtures.parse_json(text)
        self.assertEqual(HalfSequenceState.from_mapping(original),state())

    def test_immutable_state_advance_result_trace_and_detached_score_json(self):
        before=state(outs=3,bases=(True,)*3,runs=7);result=replay(before,[AdvanceHalf()])
        for obj,field,value in ((before,"side","bottom"),(result,"final_state",before),(result.trace[0],"away_runs_added",1)):
            with self.assertRaises(FrozenInstanceError):setattr(obj,field,value)
        with self.assertRaises((FrozenInstanceError,TypeError,AttributeError)):setattr(AdvanceHalf(),"payload",1)
        score=before.score;score["away"]=-1;self.assertEqual(before.score["away"],7)
        data=before.as_dict();data["current"]["half"]["bases"][0]=False;self.assertTrue(before.current.half.bases[0])
        with self.assertRaises(TypeError):CAPABILITIES["game_end_rules_implemented"]=True
        with self.assertRaises(ValueError):model.ReplayResult(before,[])
        with self.assertRaises(ValueError):model.TraceEntry(AdvanceHalf(),before,result.final_state,False,0)

    def test_exact_action_state_types_and_empty_replay_all_stages_invalid_tail(self):
        class AdvanceSubclass(AdvanceHalf):pass
        class StateSubclass(HalfSequenceState):pass
        class TupleSubclass(tuple):pass
        for action in ("A",{},None,AdvanceSubclass()):
            with self.assertRaises(ValueError):step(state(outs=3),action)
        with self.assertRaises(ValueError):step(StateSubclass(1,"top",0,0,state().current),BeginBatter())
        for actions in ("G",{},None,iter([BeginBatter()]),TupleSubclass(())):
            with self.assertRaises(ValueError):replay(state(),actions)
        for s in (state(),state(count=CountState()),state(count=CountState(4,0,4,"walk_awarded")),state(outs=3)):
            self.assertEqual(replay(s,[]),model.ReplayResult(s,()));self.assertEqual(replay(s,()),model.ReplayResult(s,()))
        for actions in ([BeginBatter(),AdvanceHalf()],[BeginBatter(),None],[BeginBatter(),BeginBatter()]):
            original=list(actions)
            with self.assertRaises(ValueError):replay(state(),actions)
            self.assertEqual(actions,original)

    def test_optimized_mode_validation_survives(self):
        code="""from prototypes.phase4d.model import *
s=HalfSequenceState(1,'top',0,0,PitchHalfState(None,HalfInningState()))
cases=[lambda:HalfSequenceState(True,'top',0,0,s.current),lambda:HalfSequenceState(1,'top',False,0,s.current),lambda:step(s,AdvanceHalf()),lambda:replay(s,'G'),lambda:action_from_mapping({'kind':'advance_half','payload':0})]
for case in cases:
    try:case()
    except ValueError:continue
    raise RuntimeError('optimized validation vanished')
"""
        subprocess.run([sys.executable,"-O","-c",code],cwd=io.SOURCE_ROOT,check=True,capture_output=True)


class RunnerTests(unittest.TestCase):
    def setUp(self):
        parent=io.SOURCE_ROOT/"data/prototypes/phase4d/unittest";parent.mkdir(parents=True,exist_ok=True)
        self.work=Path(tempfile.mkdtemp(prefix="case-",dir=parent));self.root_patch=patch.object(io,"ROOT",self.work);self.root_patch.start();self.addCleanup(self.root_patch.stop)

    def test_two_runs_four_files_hashes_flags_and_no_absolute_paths(self):
        first=runner.run(self.work/"data/prototypes/phase4d/one");second=runner.run(self.work/"reports/phase4d/two")
        self.assertEqual({p.name for p in first.iterdir()},{"plan.json","traces.json","REPORT.md","SHA256SUMS.json"})
        for p in first.iterdir():self.assertEqual(p.read_bytes(),(second/p.name).read_bytes());self.assertNotIn(str(self.work),p.read_text(encoding="utf-8"))
        manifest=fixtures.parse_json((first/"SHA256SUMS.json").read_text());self.assertEqual(set(manifest),{"plan.json","traces.json","REPORT.md"})
        for name,wanted in manifest.items():self.assertEqual(io.digest(first/name),wanted)
        for name in ("plan.json","traces.json"):
            data=fixtures.parse_json((first/name).read_text())
            for key,value in CAPABILITIES.items():self.assertEqual(data[key],value)

    def test_output_old_namespaces_bounds_existing_and_input_overlap(self):
        for path in (self.work/"data/prototypes/phase4c/new",self.work/"reports/phase4b/new",self.work/"data/raw/new",self.work/"reports/phase4d",self.work.parent/"outside"):
            with self.assertRaises(ValueError):io.reserve_output(path)
        output=self.work/"data/prototypes/phase4d/one"
        for p in (output,output/"child",output.parent):
            with self.assertRaises(ValueError):io.reserve_output(output,[p])
        self.assertFalse(output.exists());io.reserve_output(output)
        with self.assertRaises(FileExistsError):io.reserve_output(output)

    def test_real_junction_symlink_whole_child_to_old_outside(self):
        for location,kind in itertools.product(("namespace","child"),("old","outside")):
            with self.subTest(location=location,kind=kind):
                root=self.work/(location+"-"+kind);target=root/"reports/phase4c" if kind=="old" else self.work/(root.name+"-outside");target.mkdir(parents=True)
                namespace=root/"data/prototypes/phase4d";alias=namespace if location=="namespace" else namespace/"shortcut";alias.parent.mkdir(parents=True,exist_ok=True)
                if os.name=="nt":subprocess.run(["cmd","/c","mklink","/J",str(alias),str(target)],check=True,capture_output=True,text=True)
                else:alias.symlink_to(target,target_is_directory=True)
                with patch.object(io,"ROOT",root),self.assertRaises(ValueError):io.reserve_output(alias/"forbidden")
                self.assertFalse((target/"forbidden").exists())

    def test_identity_complete_seven_old_sources_and_new_runtime(self):
        identity=io.implementation_identity();self.assertEqual(identity["old_dependency_sources"],list(DEPENDENCY_SOURCES));self.assertEqual(len(DEPENDENCY_SOURCES),7)
        required=set(DEPENDENCY_SOURCES)|{"prototypes/phase4d/.gitattributes","prototypes/phase4d/fixtures/control.json","prototypes/phase4d/fixtures/scenarios.json"}
        required|={p.relative_to(io.SOURCE_ROOT).as_posix() for p in (io.SOURCE_ROOT/"prototypes/phase4d").glob("*.py")}
        self.assertEqual(set(identity["sources_and_fixtures"]),required);self.assertEqual(hashlib.sha256(io.canonical(identity["sources_and_fixtures"])).hexdigest(),identity["bundle_sha256"])

    def test_identity_lf_crlf_cr_copy(self):
        identity=io.implementation_identity();root=self.work/"identity-copy"
        originals={name:(io.SOURCE_ROOT/name).read_text(encoding="utf-8").replace("\r\n","\n").replace("\r","\n") for name in identity["sources_and_fixtures"]}
        for newline in ("\n","\r\n","\r"):
            for name,text in originals.items():
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(text.replace("\n",newline).encode())
            with patch.object(io,"SOURCE_ROOT",root),patch.object(io,"__file__",str(root/"prototypes/phase4d/io.py")):self.assertEqual(io.implementation_identity(),identity)

    def test_fixture_pin_id_start_side_scores_payload_final_stage(self):
        for change in ("id","initial_state","actions","expected_final","expected_score","expected_stage","order"):
            rows=fixtures.scenarios()
            if change=="id":rows[0]["id"]="other"
            elif change=="initial_state":rows[0]["initial_state"]["side"]="bottom"
            elif change=="expected_final":rows[0]["expected_final"]["closed_away_runs"]=1
            elif change=="expected_score":rows[0]["expected_score"]["away"]=False
            elif change=="expected_stage":rows[0]["expected_stage"]="pitching"
            elif change=="order":rows.reverse()
            else:
                row=next(r for r in rows if r["id"]=="top_switch");row["actions"][1:4]=[Pitch(M).as_dict()]*3
                self.assertEqual(replay(HalfSequenceState.from_mapping(row["initial_state"]),[action_from_mapping(a) for a in row["actions"]]).final_state.as_dict(),row["expected_final"])
            with self.subTest(change=change),patch.object(fixtures,"load",return_value=rows),self.assertRaises(ValueError):fixtures.scenarios()

    def test_actual_equal_final_payload_json_drift_before_output(self):
        folder=self.work/"mutant-fixtures";folder.mkdir()
        for p in fixtures.FIXTURE_ROOT.glob("*.json"):(folder/p.name).write_bytes(p.read_bytes())
        data=fixtures.parse_json((folder/"scenarios.json").read_text());row=next(r for r in data["scenarios"] if r["id"]=="top_switch");row["actions"][1:4]=[Pitch(M).as_dict()]*3
        (folder/"scenarios.json").write_bytes(io.canonical(data));output=self.work/"data/prototypes/phase4d/mutant"
        with patch.object(fixtures,"FIXTURE_ROOT",folder),self.assertRaises(ValueError):runner.run(output)
        self.assertFalse(output.exists())

    def test_control_expected_reference_and_identity_drift_before_output(self):
        with patch.object(fixtures,"load",return_value=fixtures.control_table()[:-1]),self.assertRaises(ValueError):fixtures.control_table()
        output=self.work/"data/prototypes/phase4d/changed";identity=io.implementation_identity()
        with patch.object(runner,"implementation_identity",side_effect=[identity,identity|{"python":"changed"}]),self.assertRaises(ValueError):runner.run(output)
        rows=fixtures.scenarios();rows[0]["expected_score"]["away"]=1
        with patch.object(runner,"scenarios",return_value=rows),self.assertRaises(ValueError):runner.run(output)
        self.assertFalse(output.exists())

    def test_run_allowed_old_seven_only_rng_clock_and_input_guards(self):
        allowed={(io.SOURCE_ROOT/name).resolve() for name in io.implementation_identity()["sources_and_fixtures"]};opened=[]
        original_open,original_path_open,original_import=builtins.open,Path.open,builtins.__import__
        imports={"prototypes.phase4"+phase+suffix for phase in ("a","b","c") for suffix in ("",".model")}
        def guard(path,mode):
            if not isinstance(path,(str,bytes,Path)) or not ('r' in mode or '+' in mode):return
            path=Path(path).resolve()
            if path not in allowed and not path.is_relative_to(self.work):raise AssertionError("unapproved old/data/source read")
            opened.append(path)
        def file_open(path,mode='r',*args,**kwargs):guard(path,mode);return original_open(path,mode,*args,**kwargs)
        def path_open(path,mode='r',*args,**kwargs):guard(path,mode);return original_path_open(path,mode,*args,**kwargs)
        def imported(name,*args,**kwargs):
            if (any(word in name for word in ("phase4a","phase4b","phase4c")) and name not in imports) or any(word in name for word in ("statcast","phase3")):raise AssertionError("unapproved old/research import")
            return original_import(name,*args,**kwargs)
        def forbidden(*a,**kw):raise AssertionError("RNG/clock prohibited")
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(builtins,"open",file_open));stack.enter_context(patch.object(Path,"open",path_open));stack.enter_context(patch.object(builtins,"__import__",imported))
            for name in ("random","uniform","randint","randrange","choice","gauss","getrandbits","Random","SystemRandom"):stack.enter_context(patch.object(random,name,forbidden))
            for name in ("time","time_ns","monotonic","perf_counter"):stack.enter_context(patch.object(time,name,forbidden))
            runner.run(self.work/"data/prototypes/phase4d/audited")
        for name in DEPENDENCY_SOURCES:self.assertIn((io.SOURCE_ROOT/name).resolve(),opened)

    def test_cli_fixed_run_and_extra_option_rejection(self):
        from prototypes.phase4d.__main__ import main
        for option in ("--input","--raw-root","--model","--seed","--resume"):
            with patch.object(sys,"argv",["phase4d","run","--output","x",option,"x"]),contextlib.redirect_stderr(text_io.StringIO()),self.assertRaises(SystemExit):main()
        output=self.work/"reports/phase4d/cli"
        with patch.object(sys,"argv",["phase4d","run","--output",str(output)]):main()
        self.assertTrue((output/"traces.json").is_file())


if __name__=="__main__":unittest.main()
