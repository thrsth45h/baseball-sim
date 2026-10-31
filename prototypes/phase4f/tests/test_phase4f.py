"""Test the usable game and boundaries, without inventing a test-count target."""
import builtins
import contextlib
from copy import deepcopy
from dataclasses import FrozenInstanceError, replace
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

from prototypes.phase4f import CAPABILITIES, DEPENDENCY_SOURCES
from prototypes.phase4f import io, runner, source
from prototypes.phase4f.engine import GameSession, replay_events
from prototypes.phase4f.protocol import CountState, FAIR, FairPlay, HalfInningState, HalfSequenceState, InputError, PitchHalfState, PitchInput, RecordedAbort, preflight, rules, parse_json
from prototypes.phase4f.source import ScriptedSource, ToyAdvancePolicy, ToyPitchSource, choose
from prototypes.phase4f.state import COUNT_FIELDS, EventCounts, GameResult, GameSnapshot


def ending_cases():return parse_json((runner.FIXTURE_ROOT/"ending_cases.json").read_text(encoding="utf-8"))["cases"]


def boundary(row):
    seq=HalfSequenceState(9,row["side"],row["away"],row["home"],PitchHalfState(CountState.from_mapping(row["count"]),HalfInningState(row["outs"],tuple(row["bases"]),0)))
    return GameSnapshot(seq,(0,0),tuple(EventCounts() for _ in range(18)),0,tuple(row["line_score"]),history_origin="provided_snapshot")


def input_for(event,fair=None):return PitchInput(event,fair)


def tally(document):
    """Separate log ledger: do not call the production completion/result code."""
    initial=document["initial_snapshot"];lines=list(initial["line_score"]);orders=list(initial["batting_indices"])
    batters=deepcopy(initial["batter_counts"]);pitches=initial["pitches_accepted"]
    for event in document["events"]:
        if event["type"]=="action":
            kind=event["action"]["kind"]
            if kind=="pitch":pitches+=1
            if kind=="advance_half":
                seq=event["after"];index=(seq["inning"]-1)*2+(seq["side"]=="bottom")
                if lines[index] is not None:raise AssertionError("half reached twice")
                lines[index]=0
        elif event["type"]=="plate_appearance_completed":
            team=0 if event["team"]=="away" else 1;slot=orders[team]
            if event["batter_id"]!=event["team"]+"_"+str(slot+1) or event["slot"]!=slot+1:raise AssertionError("batting order differs")
            if event["batting_index_before"]!=slot or event["batting_index_after"]!=(slot+1)%9:raise AssertionError("PA did not consume exactly one lineup slot")
            counts=batters[team*9+slot];counts["completed_plate_appearances"]+=1
            category=event["category"]
            if category in ("safe_hit","home_run"):counts["safe_hit_events"]+=1
            if category=="walk":counts["walk_events"]+=1
            if category=="strikeout":counts["strikeout_events"]+=1
            if category=="home_run":counts["home_run_events"]+=1
            index=(event["inning"]-1)*2+(event["side"]=="bottom")
            if lines[index] is None:raise AssertionError("scored in an unvisited half")
            lines[index]+=event["runs_added"];orders[team]=(slot+1)%9
    return {"score":{"away":sum(v or 0 for v in lines[::2]),"home":sum(v or 0 for v in lines[1::2])},"line_score":lines,"orders":orders,"batter_counts":batters,"pitches":pitches}


class GameTests(unittest.TestCase):
    def test_source_cannot_claim_controller_pitch_limit_and_abort_replays(self):
        class ReturnsLimit:
            def next_pitch(self,context):return RecordedAbort("pitch_limit")
        class RaisesLimit:
            def next_pitch(self,context):raise InputError("pitch_limit")
        class MutatesError:
            def next_pitch(self,context):
                error=InputError("source_error");error.code="pitch_limit";raise error
        for constructor in (RecordedAbort,InputError):
            with self.assertRaises(ValueError):constructor("pitch_limit")
        for provider in (ReturnsLimit(),RaisesLimit(),MutatesError()):
            with self.subTest(provider=type(provider).__name__):
                game=GameSession(max_pitches=10,source=provider);result=game.run_to_end()
                self.assertEqual(result.as_dict()["reason"],"source_error");self.assertEqual(game.snapshot.pitches_accepted,0)
                self.assertEqual(game.snapshot.sequence.current.count,CountState());self.assertEqual(sum(c.completed_plate_appearances for c in game.snapshot.batter_counts),0)
                self.assertEqual(replay_events(game.events_document()).as_dict(),result.as_dict())

    def test_scripted_endings_terminal_old_resolution_zero_and_exact_limit_priority(self):
        for row in ending_cases():
            with self.subTest(case=row["id"]):
                scripted=ScriptedSource([row["input"],object()]);game=GameSession(source=scripted,initial_snapshot=boundary(row),max_pitches=1)
                real=rules.step
                with patch.object(rules,"step",wraps=real) as old:
                    result=game.run_to_end();kinds=[call.args[1].as_dict()["kind"] for call in old.call_args_list]
                data=result.as_dict();self.assertEqual(data["status"],"completed");self.assertEqual(data["reason"],row["expected_reason"]);self.assertEqual(data["score"],row["expected_score"])
                self.assertEqual(data["pitches_accepted"],1);self.assertEqual(scripted.calls,1);self.assertEqual(scripted.position,1)
                terminal=[e for e in game.events if e["type"]=="terminal_play"]
                if row["terminal_slots"] is not None:
                    self.assertEqual(len(terminal),1);self.assertEqual(terminal[0]["scored_runner_slots"],row["terminal_slots"])
                    self.assertFalse(terminal[0]["post_terminal_old_sequence_created"]);self.assertEqual(kinds,["pitch"])
                    self.assertEqual(game.snapshot.sequence.stage,"awaiting_resolution")
                    self.assertEqual(data["score"]["home"],game.snapshot.sequence.score["home"]+data["terminal_score_delta"]["home"])
                else:self.assertEqual(terminal,[]);self.assertIn("resolve",kinds);self.assertNotIn("advance_half",kinds)
                if row["id"]=="skip_bottom9":self.assertIsNone(data["line_score"]["home"][8])
                if row["id"]=="walkoff_hit_cut":self.assertEqual(terminal[0]["candidate_runs"],3);self.assertEqual(terminal[0]["runs_added"]["home"],1)
                if row["id"]=="walkoff_hr_all":self.assertEqual(data["terminal_score_delta"]["home"],4)
                before=game.events
                with self.assertRaises(ValueError):game.advance_one_pitch()
                self.assertEqual(game.events,before);self.assertEqual(scripted.calls,1)
                self.assertEqual(replay_events(game.events_document()).as_dict(),data)

    def test_all_outs_full_draw_lineup_cycles_and_carries_across_halves(self):
        fixture=runner.fixture_contract()["cases"][0];script=ScriptedSource([PitchInput(FAIR,FairPlay("out_hold"))]*54)
        game=GameSession(source=script);data=game.run_to_end().as_dict()
        self.assertEqual(data["score"],fixture["expected_score"]);self.assertEqual(data["reason"],fixture["expected_reason"])
        self.assertEqual(data["pitches_accepted"],54);self.assertEqual(script.calls,54)
        self.assertEqual(data["line_score"]["away"],[0]*9);self.assertEqual(data["line_score"]["home"],[0]*9)
        away=[e for e in game.events if e["type"]=="plate_appearance_completed" and e["team"]=="away"]
        self.assertEqual([e["batter_id"] for e in away],['away_'+str(i%9+1) for i in range(27)])
        self.assertEqual(away[3]["batter_id"],"away_4");self.assertEqual(away[9]["batter_id"],"away_1")
        self.assertEqual(data["next_batting_indices"],{"away":0,"home":0})

    def test_walk_strikeout_and_two_strike_fouls_follow_old_counts(self):
        values=[PitchInput("called_ball")]*4+[PitchInput("swinging_strike_caught")]*2+[PitchInput("ordinary_foul_uncaught")]*3+[PitchInput("swinging_strike_caught")]
        game=GameSession(source=ScriptedSource(values));data=game.run_to_end().as_dict()
        self.assertEqual(data["status"],"aborted");self.assertEqual(data["reason"],"source_exhausted")
        self.assertEqual(data["team_events"]["away"]["completed_plate_appearances"],2)
        self.assertEqual((data["team_events"]["away"]["walk_events"],data["team_events"]["away"]["strikeout_events"]),(1,1))
        foul=[e for e in game.events if e["type"]=="action" and e["action"]=={"kind":"pitch","event":"ordinary_foul_uncaught"}]
        self.assertTrue(all(e["after"]["current"]["count"]["strikes"]==2 for e in foul));self.assertEqual(game.snapshot.batting_indices,(2,0))

    def test_default_seeds_complete_and_reverse_game_order_does_not_interfere(self):
        expected={seed:GameSession(seed).run_to_end().as_dict() for seed in (42,0,1,2,2026)}
        for seed in reversed((42,0,1,2,2026)):
            actual=GameSession(seed).run_to_end().as_dict();self.assertEqual(actual,expected[seed]);self.assertEqual(actual["status"],"completed")
        self.assertEqual(expected[42]["score"],{"away":4,"home":2});self.assertEqual(expected[42]["pitches_accepted"],259)

    def test_owned_rng_draws_match_independent_integer_thresholds_and_global_rng_unused(self):
        def forbidden(*args,**kwargs):raise AssertionError("global RNG used")
        with patch.object(random,"seed",forbidden),patch.object(random,"random",forbidden),patch.object(random,"randrange",forbidden):
            game=GameSession(42);game.run_to_end()
        expected=random.Random(42);fair_count=0
        for event in game.events:
            if event["type"]!="action" or event["action"]["kind"]!="pitch":continue
            value=event["input"];draw=expected.randrange(100);self.assertEqual(value["pitch_draw"],draw)
            name="called_ball" if draw<35 else "called_strike_caught" if draw<50 else "swinging_strike_caught" if draw<65 else "ordinary_foul_uncaught" if draw<80 else FAIR
            self.assertEqual(value["event"],name)
            if name==FAIR:
                fair_count+=1;draw=expected.randrange(100);self.assertEqual(value["fair_draw"],draw)
                kind="out_hold" if draw<65 else "safe_hit" if draw<95 else "home_run"
                self.assertEqual(value["fair"]["kind"],kind)
                if kind=="safe_hit":self.assertEqual(value["fair"]["candidate_bases"],1 if draw<87 else 2 if draw<94 else 3)
            else:self.assertIsNone(value["fair_draw"])
        self.assertEqual(game.source.draw_count,game.snapshot.pitches_accepted+fair_count)
        self.assertIsNot(game.source.rng,GameSession(42).source.rng)

    def test_recorded_replay_one_pitch_iteration_and_memory_snapshot_continuation(self):
        game=GameSession(42);game.run_to_end();document=game.events_document()
        def no_rng(*args,**kwargs):raise AssertionError("recorded replay used RNG")
        with patch.object(random,"Random",no_rng):self.assertEqual(replay_events(document).as_dict(),game.result().as_dict())
        one=GameSession(42)
        for _ in range(40):one.advance_one_pitch()
        restored=GameSnapshot.from_mapping(parse_json(io.canonical(one.snapshot.as_dict())))
        inputs=[PitchInput.from_mapping(e["input"]) for e in game.events if e["type"]=="action" and e["action"]["kind"]=="pitch"]
        continuation=GameSession(42,source=ScriptedSource(inputs[40:]),initial_snapshot=restored)
        self.assertEqual(continuation.run_to_end().as_dict(),game.result().as_dict())
        while not one.snapshot.done:one.advance_one_pitch()
        self.assertEqual(one.result().as_dict(),game.result().as_dict())

    def test_independent_log_reaggregation_for_full_game_and_terminal_branches(self):
        games=[GameSession(42)]
        for row in ending_cases():games.append(GameSession(source=ScriptedSource([row["input"]]),initial_snapshot=boundary(row),max_pitches=1))
        for game in games:
            result=game.run_to_end().as_dict();ledger=tally(game.events_document())
            self.assertEqual(ledger["score"],result["score"]);self.assertEqual(ledger["pitches"],result["pitches_accepted"])
            self.assertEqual(ledger["line_score"][::2],result["line_score"]["away"]);self.assertEqual(ledger["line_score"][1::2],result["line_score"]["home"])
            self.assertEqual(ledger["orders"],list(result["next_batting_indices"].values()))
            for actual,counts in zip(result["batter_events"],ledger["batter_counts"]):self.assertEqual({key:actual[key] for key in COUNT_FIELDS},counts)

    def test_source_failure_missing_fair_and_arbitrary_invalid_objects_abort_before_pitch(self):
        class NoRepr:
            def __repr__(self):raise AssertionError("invalid object was exposed")
        values=[NoRepr(),{"event":FAIR,"fair":None,"pitch_draw":None,"fair_draw":None},
            {"event":FAIR,"fair":{"kind":"unknown","candidate_bases":None,"runner_destinations":None},"pitch_draw":None,"fair_draw":None}]
        for value in values:
            game=GameSession(source=ScriptedSource([value]));result=game.run_to_end().as_dict()
            self.assertEqual(result["status"],"aborted");self.assertIsNone(result["outcome"]);self.assertIsNone(result["final_score"])
            self.assertEqual(result["pitches_accepted"],0);self.assertEqual(game.snapshot.sequence.current.count,CountState())
            self.assertEqual(result["team_events"]["away"]["completed_plate_appearances"],0)
            self.assertEqual([e["action"]["kind"] for e in game.events if e["type"]=="action"],["begin_batter"])
            self.assertEqual(replay_events(game.events_document()).as_dict(),result);io.canonical(game.events_document())
        empty=GameSession(source=ScriptedSource([]));self.assertEqual(empty.run_to_end().snapshot.reason,"source_exhausted")
        class BrokenSource:
            def next_pitch(self,context):raise RuntimeError("private arbitrary details")
        broken=GameSession(source=BrokenSource());broken.run_to_end();self.assertNotIn(b"private arbitrary",io.canonical(broken.events_document()))
        self.assertEqual(replay_events(broken.events_document()).snapshot.reason,"source_error")

    def test_invalid_safe_destinations_never_accept_pitch_or_terminal_scoring(self):
        row=next(r for r in ending_cases() if r["id"]=="walkoff_hit_cut")
        invalid=PitchInput(FAIR,FairPlay("safe_hit",3,(4,3,4)));game=GameSession(source=ScriptedSource([invalid]),initial_snapshot=boundary(row))
        with patch.object(rules,"step",wraps=rules.step) as old:result=game.run_to_end()
        self.assertEqual(old.call_count,0);self.assertEqual(result.snapshot.reason,"invalid_fair_result");self.assertEqual(result.snapshot.pitches_accepted,0)
        self.assertFalse(any(e["type"]=="terminal_play" for e in game.events));self.assertEqual(replay_events(game.events_document()).as_dict(),result.as_dict())

    def test_pitch_limit_does_not_force_out_draw_next_begin_or_advance(self):
        scripted=ScriptedSource([PitchInput("called_ball")]*10);game=GameSession(max_pitches=4,source=scripted);data=game.run_to_end().as_dict()
        self.assertEqual((data["status"],data["reason"],data["outcome"]),("aborted","pitch_limit",None));self.assertEqual(scripted.calls,4)
        self.assertEqual(data["pitches_accepted"],4);self.assertEqual(data["team_events"]["away"]["completed_plate_appearances"],1)
        self.assertEqual(sum(e["type"]=="action" and e["action"]["kind"]=="begin_batter" for e in game.events),1)
        self.assertEqual(replay_events(game.events_document()).as_dict(),data)
        seq=HalfSequenceState(8,"bottom",0,0,PitchHalfState(CountState(0,2,2),HalfInningState(2)))
        initial=GameSnapshot(seq,(0,0),tuple(EventCounts() for _ in range(18)),0,(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,None,None),history_origin="provided_snapshot")
        capped=GameSession(max_pitches=1,source=ScriptedSource([PitchInput("called_strike_caught")]),initial_snapshot=initial);capped.run_to_end()
        self.assertEqual(capped.snapshot.sequence.stage,"half_complete");self.assertEqual(capped.snapshot.sequence.inning,8)
        self.assertIsNone(capped.snapshot.line_score[16]);self.assertFalse(any(e["type"]=="action" and e["action"]["kind"]=="advance_half" for e in capped.events))


class BoundaryTests(unittest.TestCase):
    def test_profile_draw_intervals_and_all_provider_advances(self):
        profile=source.load_profile();policy=ToyAdvancePolicy()
        self.assertEqual([sum(choose(d,profile.pitch_weights)==name for d in range(100)) for name,_ in profile.pitch_weights],[35,15,15,15,20])
        self.assertEqual([sum(choose(d,profile.fair_weights)==name for d in range(100)) for name,_ in profile.fair_weights],[65,22,7,1,5])
        for bases,hit in itertools.product(itertools.product((False,True),repeat=3),(1,2,3)):
            destinations=policy.destinations(bases,hit);value=PitchInput(FAIR,FairPlay("safe_hit",hit,destinations))
            self.assertEqual(preflight(value,HalfInningState(0,bases)),value)

    def test_strict_seed_max_protocol_json_and_frozen_values(self):
        for seed in (True,False,-1,2**64,42.0,"42",None):
            with self.assertRaises(ValueError):GameSession(seed)
        for maximum in (True,0,-1,1.0,"1",None):
            with self.assertRaises(ValueError):GameSession(max_pitches=maximum)
        for value in ({},None,[],{"event":"called_ball","fair":None,"pitch_draw":True,"fair_draw":None}):
            with self.assertRaises(ValueError):PitchInput.from_mapping(value)
        for text in ('{"seed":1,"seed":2}','{"x":NaN}','{"x":Infinity}'):
            with self.assertRaises(ValueError):parse_json(text)
        snapshot=GameSnapshot.initial()
        with self.assertRaises(FrozenInstanceError):snapshot.pitches_accepted=1
        with self.assertRaises(ValueError):GameSnapshot.from_mapping(snapshot.as_dict()|{"extra":0})
        with self.assertRaises(ValueError):GameResult(snapshot)
        with self.assertRaises(TypeError):CAPABILITIES["mlb_calibrated"]=True

    def test_snapshot_history_must_be_supplied_and_reconcile_no_invented_past_zeros(self):
        initial=GameSnapshot.initial();data=initial.as_dict();data["line_score"][0]=1
        with self.assertRaises(ValueError):GameSnapshot.from_mapping(data)
        row=ending_cases()[0];good=boundary(row);data=good.as_dict();data["line_score"][1]=0
        with self.assertRaises(ValueError):GameSnapshot.from_mapping(data)
        data=good.as_dict();data["line_score"][3]=None
        with self.assertRaises(ValueError):GameSnapshot.from_mapping(data)
        self.assertEqual(good.history_origin,"provided_snapshot")

    def test_events_detached_and_tampered_record_replay_rejected(self):
        game=GameSession(source=ScriptedSource([PitchInput(FAIR,FairPlay("out_hold"))]*54));game.run_to_end()
        document=game.events_document();document["events"][0]["after"]["current"]["count"]["balls"]=True
        with self.assertRaises(ValueError):replay_events(document)
        self.assertEqual(game.events[0]["after"]["current"]["count"]["balls"],0)
        result=game.result().as_dict();result["line_score"]["away"][0]=99;self.assertEqual(game.result().as_dict()["line_score"]["away"][0],0)
