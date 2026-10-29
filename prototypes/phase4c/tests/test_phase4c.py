"""Approved contract checks, separate from the manager's independent oracle."""
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

from prototypes.phase4c import CAPABILITIES, DEPENDENCY_SOURCES, DESIGN
from prototypes.phase4c import fixtures, io, model, runner
from prototypes.phase4c.model import BeginBatter, CountState, HalfInningState, Pitch, PitchHalfState, Resolve, action_from_mapping, replay, step

B, C, M, T, F, U, O, P = model.count_rules.EVENTS
W, X, H = model.half_rules.EVENTS
BASES = tuple(itertools.product((False, True), repeat=3))
WAITING = (CountState(4, 0, 4, "walk_awarded"), CountState(0, 3, 3, "strikeout_out"),
           CountState(0, 0, 1, "caught_foul_out"), CountState(0, 0, 1, "fair_play_pending"))
PERMITTED = ((W,), (X,), (X,), (X, H))


def start(half=None): return PitchHalfState(None, half if half is not None else HalfInningState())


class IntegrationTests(unittest.TestCase):
    def test_control_twelve_cells_against_separate_stage_matrix(self):
        states = [start(), PitchHalfState(CountState(), HalfInningState()), PitchHalfState(WAITING[0], HalfInningState()), start(HalfInningState(3))]
        allowed = ((True, False, False), (False, True, False), (False, False, True), (False, False, False))
        cells = fixtures.control_table()
        self.assertEqual(len(cells), 12)
        for state, flags in zip(states, allowed):
            for action, accepted in zip((BeginBatter(), Pitch(B), Resolve(W)), flags):
                with self.subTest(stage=state.stage, action=action):
                    if accepted: step(state, action)
                    else:
                        with self.assertRaises(ValueError): step(state, action)
        runner.check_tables(cells, fixtures.compatibility_table())

    def test_2304_representative_pitch_pairs_delegate_count_and_hold_half(self):
        checked = 0
        for balls, strikes, event, outs, bases in itertools.product(range(4), range(3), (B, C, M, T, F, U, O, P), range(3), BASES):
            with self.subTest(balls=balls, strikes=strikes, event=event, outs=outs, bases=bases):
                count, half = CountState(balls, strikes, balls+strikes), HalfInningState(outs, bases, 7)
                expected = model.count_rules.step(count, event)
                result = step(PitchHalfState(count, half), Pitch(event))
                self.assertEqual(result.count, expected)
                self.assertIs(result.half, half)
                checked += 1
        self.assertEqual(checked, 2304)

    def test_288_compatibility_trials_120_accept_168_reject(self):
        accepted = rejected = 0
        self.assertEqual(sum(row["allowed"] for row in fixtures.compatibility_table()), 5)
        for outs, bases in itertools.product(range(3), BASES):
            half = HalfInningState(outs, bases, 7)
            for count, plays in zip(WAITING, PERMITTED):
                state = PitchHalfState(count, half)
                for play in (W, X, H):
                    with self.subTest(outs=outs, bases=bases, status=count.status, play=play):
                        if play in plays:
                            result = step(state, Resolve(play))
                            self.assertEqual(result, PitchHalfState(None, model.half_rules.step(half, play)))
                            accepted += 1
                        else:
                            with self.assertRaises(ValueError): step(state, Resolve(play))
                            self.assertEqual(state, PitchHalfState(count, half))
                            rejected += 1
        self.assertEqual((accepted, rejected), (120, 168))

    def test_begin_24_open_halves_and_closed_96_actions(self):
        for outs, bases in itertools.product(range(3), BASES):
            with self.subTest(outs=outs, bases=bases):
                half = HalfInningState(outs, bases, 7)
                self.assertEqual(step(start(half), BeginBatter()), PitchHalfState(CountState(), half))
        actions = [BeginBatter(), *[Pitch(e) for e in (B, C, M, T, F, U, O, P)], *[Resolve(e) for e in (W, X, H)]]
        self.assertEqual(len(actions), 12)
        for bases, action in itertools.product(BASES, actions):
            with self.subTest(bases=bases, action=action), self.assertRaises(ValueError): step(start(HalfInningState(3, bases, 7)), action)

    def test_delegation_exactly_one_old_step_per_applicable_action(self):
        old_count_step, old_half_step = model.count_rules.step, model.half_rules.step
        with patch.object(model.count_rules, "step", wraps=old_count_step) as count_call, patch.object(model.half_rules, "step", wraps=old_half_step) as half_call:
            begun = step(start(), BeginBatter())
            self.assertEqual((count_call.call_count, half_call.call_count), (0, 0))
            step(begun, Pitch(B))
            self.assertEqual((count_call.call_count, half_call.call_count), (1, 0))
            step(PitchHalfState(WAITING[0], HalfInningState()), Resolve(W))
            self.assertEqual((count_call.call_count, half_call.call_count), (1, 1))
            with self.assertRaises(ValueError): step(PitchHalfState(WAITING[0], HalfInningState()), Resolve(H))
            self.assertEqual((count_call.call_count, half_call.call_count), (1, 1))

    def test_terminal_pitch_does_not_apply_result_without_resolve(self):
        half = HalfInningState(2, (True,)*3, 7)
        state = replay(start(half), [BeginBatter(), Pitch(C), Pitch(C), Pitch(C)]).final_state
        self.assertEqual(state.stage, "awaiting_resolution")
        self.assertIs(state.half, half)
        self.assertEqual(state.count, WAITING[1])
        closed = step(state, Resolve(X))
        self.assertEqual(closed, start(HalfInningState(3, (True,)*3, 7)))
        self.assertEqual(closed.stage, "half_complete")
        for action in (BeginBatter(), Pitch(B), Resolve(X)):
            with self.assertRaises(ValueError): step(closed, action)

    def test_fair_pending_stops_and_only_explicit_x_or_h_updates(self):
        half = HalfInningState(1, (True,)*3, 7)
        pending = replay(start(half), [BeginBatter(), Pitch(P)]).final_state
        self.assertEqual(pending, PitchHalfState(WAITING[3], half))
        self.assertEqual(step(pending, Resolve(X)), start(HalfInningState(2, (True,)*3, 7)))
        self.assertEqual(step(pending, Resolve(H)), start(HalfInningState(1, (False,)*3, 11)))
        with self.assertRaises(ValueError): step(pending, Resolve(W))

    def test_stage_violations_double_resolve_and_explicit_next_begin(self):
        between = start()
        pitching = step(between, BeginBatter())
        waiting = PitchHalfState(WAITING[0], between.half)
        resolved = step(waiting, Resolve(W))
        for state, action in ((between, Pitch(B)), (pitching, Resolve(W)), (pitching, BeginBatter()),
                              (waiting, BeginBatter()), (waiting, Pitch(B)), (resolved, Resolve(W)), (resolved, Pitch(B))):
            with self.subTest(stage=state.stage, action=action), self.assertRaises(ValueError): step(state, action)
        next_batter = step(resolved, BeginBatter())
        self.assertEqual(next_batter.count, CountState())
        self.assertEqual(next_batter.half, resolved.half)
        # Pure-function reuse of an old snapshot is possible; no external exactly-once claim.
        self.assertEqual(step(waiting, Resolve(W)), resolved)

    def test_long_foul_loop_huge_scores_and_arbitrary_n_snapshot(self):
        half = HalfInningState(2, (True,)*3, 10**100)
        result = replay(start(half), [BeginBatter(), Pitch(C), Pitch(M)]+[Pitch(F)]*1000)
        self.assertEqual(result.final_state, PitchHalfState(CountState(0, 2, 1002), half))
        self.assertEqual(step(PitchHalfState(WAITING[0], half), Resolve(W)).half.runs, 10**100+1)
        self.assertEqual(step(PitchHalfState(WAITING[3], half), Resolve(H)).half.runs, 10**100+4)
        count = CountState(3, 2, 10**100)
        self.assertEqual(step(PitchHalfState(count, half), Pitch(F)).count.pitches_seen, 10**100+1)

    def test_eighteen_columns_82_actions_100_splits_and_order(self):
        rows = fixtures.scenarios()
        self.assertEqual(len(rows), 18)
        self.assertEqual(sum(len(row["actions"]) for row in rows), 82)
        split_count = 0
        expected = {}
        for row in rows:
            initial = PitchHalfState.from_mapping(row["initial_state"])
            actions = [action_from_mapping(action) for action in row["actions"]]
            expected[row["id"]] = replay(initial, actions)
            self.assertEqual(expected[row["id"]].final_state.as_dict(), row["expected_final"])
            self.assertEqual(expected[row["id"]].final_state.stage, row["expected_stage"])
            for split in range(len(actions)+1):
                with self.subTest(id=row["id"], split=split):
                    first = replay(initial, actions[:split]); second = replay(first.final_state, actions[split:])
                    self.assertEqual(second.final_state, expected[row["id"]].final_state)
                    self.assertEqual(first.trace+second.trace, expected[row["id"]].trace)
                    split_count += 1
        self.assertEqual(split_count, 100)
        for row in reversed(rows):
            self.assertEqual(replay(PitchHalfState.from_mapping(row["initial_state"]), tuple(action_from_mapping(a) for a in row["actions"])), expected[row["id"]])

    def test_trace_people_invariant_and_no_pitch_count_increment_on_other_actions(self):
        for row in fixtures.scenarios():
            result = replay(PitchHalfState.from_mapping(row["initial_state"]), [action_from_mapping(a) for a in row["actions"]])
            for entry in result.trace:
                with self.subTest(id=row["id"], action=entry.action):
                    before, after = entry.before.half, entry.after.half
                    delta = (after.outs+after.runs+sum(after.bases))-(before.outs+before.runs+sum(before.bases))
                    self.assertEqual(delta, 1 if type(entry.action) is Resolve else 0)
                    self.assertEqual(entry.runs_added, after.runs-before.runs)
                    self.assertEqual(set(entry.as_dict()), {"action", "before", "after", "runs_added"})
                    if type(entry.action) is Pitch: self.assertEqual(entry.after.count.pitches_seen, entry.before.count.pitches_seen+1)
                    elif type(entry.action) is BeginBatter: self.assertEqual(entry.after.count.pitches_seen, 0)
                    else: self.assertIsNone(entry.after.count)


class ValidationTests(unittest.TestCase):
    def test_exact_old_types_outer_state_and_closed_count_contradiction(self):
        class CountSubclass(CountState): pass
        class HalfSubclass(HalfInningState): pass
        for count, half in (({}, HalfInningState()), (False, HalfInningState()), (None, {}), (CountSubclass(), HalfInningState()), (None, HalfSubclass())):
            with self.subTest(count=repr(count), half=repr(half)), self.assertRaises(ValueError): PitchHalfState(count, half)
        for count, bases in itertools.product((CountState(), *WAITING), BASES):
            with self.subTest(count=count, bases=bases), self.assertRaises(ValueError): PitchHalfState(count, HalfInningState(3, bases, 7))
        for invalid in (None, {}, start().as_dict()):
            with self.assertRaises(ValueError): step(invalid, BeginBatter())

    def test_payload_exact_strings_unknown_values_and_subclasses(self):
        class StringSubclass(str): pass
        for value in (None, True, 0, [], {}, StringSubclass(B), "strike", "walk_awarded", "single"):
            with self.subTest(value=repr(value)), self.assertRaises(ValueError): Pitch(value)
        for value in (None, True, 0, [], {}, StringSubclass(W), "walk", "home_run", "strikeout", "single"):
            with self.subTest(value=repr(value)), self.assertRaises(ValueError): Resolve(value)
        class BeginSubclass(BeginBatter): pass
        for action in ({"kind": "begin_batter"}, "G", None, BeginSubclass()):
            with self.assertRaises(ValueError): step(start(), action)

    def test_json_exact_keys_and_old_type_validation(self):
        value = start().as_dict()
        for invalid in ({}, None, [], value | {"stage": "between_batters"}, {"count": None}, value | {"half": None}):
            with self.subTest(invalid=repr(invalid)), self.assertRaises(ValueError): PitchHalfState.from_mapping(invalid)
        for field, wrong in (("outs", True), ("runs", 1.0), ("bases", [0, 0, 0]), ("bases", (False,)*3)):
            invalid = deepcopy(value); invalid["half"][field] = wrong
            with self.subTest(field=field), self.assertRaises(ValueError): PitchHalfState.from_mapping(invalid)
        invalid = deepcopy(value); invalid["count"] = CountState().as_dict() | {"pitches_seen": False}
        with self.assertRaises(ValueError): PitchHalfState.from_mapping(invalid)
        self.assertEqual(PitchHalfState.from_mapping(value), start())

    def test_action_json_exact_kind_keys_and_duplicate_nonfinite(self):
        for action in (BeginBatter(), Pitch(B), Resolve(W)):
            self.assertEqual(action_from_mapping(action.as_dict()), action)
            with self.assertRaises(ValueError): action_from_mapping(action.as_dict() | {"extra": 1})
        for value in ({}, None, [], {"kind": True}, {"kind": "G"}, {"kind": "pitch"}, {"kind": "resolve", "event": W}, {"kind": "begin_batter", "event": B}):
            with self.subTest(value=repr(value)), self.assertRaises(ValueError): action_from_mapping(value)
        for text in ('{"kind":"pitch","kind":"resolve"}', '{"half":{"runs":0,"runs":1}}', '{"x":NaN}', '{"x":Infinity}', '{"x":-Infinity}'):
            with self.subTest(text=text), self.assertRaises(ValueError): fixtures.parse_json(text)

    def test_immutable_all_values_and_detached_json(self):
        state, action = start(), Pitch(B)
        result = replay(state, [BeginBatter(), action])
        for obj, field, value in ((state, "count", CountState()), (action, "event", C), (Resolve(W), "completed_play", H),
                                  (result, "final_state", state), (result.trace[0], "runs_added", 1)):
            with self.assertRaises(FrozenInstanceError): setattr(obj, field, value)
        with self.assertRaises(TypeError): CAPABILITIES["connected_to_phase4a"] = False
        data = result.as_dict(); data["trace"][0]["before"]["half"]["bases"][0] = True
        self.assertEqual(state.half.bases, (False,)*3)
        self.assertEqual(set(result.final_state.as_dict()), {"count", "half"})
        with self.assertRaises(ValueError): model.ReplayResult(state, [])

    def test_replay_strict_finite_sequences_empty_all_stages_invalid_tail(self):
        class ListSubclass(list): pass
        for actions in ("G", {}, iter([BeginBatter()]), None, ListSubclass([BeginBatter()])):
            with self.subTest(actions=repr(actions)), self.assertRaises(ValueError): replay(start(), actions)
        for state in (start(), PitchHalfState(CountState(), HalfInningState()), PitchHalfState(WAITING[0], HalfInningState()), start(HalfInningState(3))):
            self.assertEqual(replay(state, []), model.ReplayResult(state, ()))
            self.assertEqual(replay(state, ()), model.ReplayResult(state, ()))
        for actions in ([BeginBatter(), Pitch(B), None], [BeginBatter(), Resolve(W)], [BeginBatter(), BeginBatter()]):
            original = list(actions)
            with self.assertRaises(ValueError): replay(start(), actions)
            self.assertEqual(actions, original)

    def test_optimized_mode_keeps_validation(self):
        code = """from prototypes.phase4c.model import *
cases=[lambda:Pitch(True),lambda:Resolve('walk'),lambda:PitchHalfState(CountState(),HalfInningState(3)),lambda:step(PitchHalfState(None,HalfInningState()),Pitch('called_ball')),lambda:replay(PitchHalfState(None,HalfInningState()),'G')]
for case in cases:
    try: case()
    except ValueError: continue
    raise RuntimeError('optimized validation vanished')
"""
        subprocess.run([sys.executable, "-O", "-c", code], cwd=io.SOURCE_ROOT, check=True, capture_output=True)


class RunnerTests(unittest.TestCase):
    def setUp(self):
        parent = io.SOURCE_ROOT/"data/prototypes/phase4c/unittest"
        parent.mkdir(parents=True, exist_ok=True)
        self.work = Path(tempfile.mkdtemp(prefix="case-", dir=parent))
        self.root_patch = patch.object(io, "ROOT", self.work); self.root_patch.start(); self.addCleanup(self.root_patch.stop)

    def test_run_four_artifacts_byte_repeat_hash_metadata(self):
        first = runner.run(self.work/"data/prototypes/phase4c/one")
        second = runner.run(self.work/"reports/phase4c/two")
        self.assertEqual({p.name for p in first.iterdir()}, {"plan.json", "traces.json", "REPORT.md", "SHA256SUMS.json"})
        for path in first.iterdir(): self.assertEqual(path.read_bytes(), (second/path.name).read_bytes())
        manifest = fixtures.parse_json((first/"SHA256SUMS.json").read_text())
        self.assertEqual(set(manifest), {"plan.json", "traces.json", "REPORT.md"})
        for name, digest in manifest.items(): self.assertEqual(io.digest(first/name), digest)
        for name in ("plan.json", "traces.json"):
            data = fixtures.parse_json((first/name).read_text())
            for key, value in CAPABILITIES.items(): self.assertEqual(data[key], value)
            self.assertNotIn(str(self.work), (first/name).read_text())

    def test_output_roots_old_namespaces_existing_and_input_overlap(self):
        for path in (self.work/"data/prototypes/phase4a/new", self.work/"reports/phase4b/new", self.work/"data/raw/new",
                     self.work/"reports/phase4c", self.work.parent/"outside"):
            with self.subTest(path=path), self.assertRaises(ValueError): io.reserve_output(path)
        output = self.work/"data/prototypes/phase4c/one"
        for protected in (output, output/"child", output.parent):
            with self.assertRaises(ValueError): io.reserve_output(output, [protected])
        self.assertFalse(output.exists())
        io.reserve_output(output)
        with self.assertRaises(FileExistsError): io.reserve_output(output)

    def test_real_junction_or_symlink_namespace_child_to_old_or_outside(self):
        for location, kind in itertools.product(("namespace", "child"), ("old", "outside")):
            with self.subTest(location=location, kind=kind):
                root = self.work/(location+"-"+kind)
                target = root/"reports/phase4b" if kind == "old" else self.work/(root.name+"-outside")
                target.mkdir(parents=True)
                namespace = root/"data/prototypes/phase4c"
                alias = namespace if location == "namespace" else namespace/"shortcut"
                alias.parent.mkdir(parents=True, exist_ok=True)
                if os.name == "nt": subprocess.run(["cmd", "/c", "mklink", "/J", str(alias), str(target)], check=True, capture_output=True, text=True)
                else: alias.symlink_to(target, target_is_directory=True)
                with patch.object(io, "ROOT", root), self.assertRaises(ValueError): io.reserve_output(alias/"forbidden")
                self.assertFalse((target/"forbidden").exists())

    def test_source_identity_exact_dependency_five_and_new_runtime_fixture_set(self):
        identity = io.implementation_identity()
        self.assertEqual(identity["old_dependency_sources"], list(DEPENDENCY_SOURCES))
        self.assertEqual(len(DEPENDENCY_SOURCES), 5)
        required = set(DEPENDENCY_SOURCES) | {"prototypes/phase4c/.gitattributes"}
        required |= {p.relative_to(io.SOURCE_ROOT).as_posix() for p in (io.SOURCE_ROOT/"prototypes/phase4c").glob("*.py")}
        required |= {"prototypes/phase4c/fixtures/"+name for name in ("control.json", "compatibility.json", "scenarios.json")}
        self.assertEqual(set(identity["sources_and_fixtures"]), required)
        self.assertEqual(hashlib.sha256(io.canonical(identity["sources_and_fixtures"])).hexdigest(), identity["bundle_sha256"])
        for name, digest in identity["sources_and_fixtures"].items():
            text = (io.SOURCE_ROOT/name).read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
            self.assertEqual(hashlib.sha256(text.encode()).hexdigest(), digest)

    def test_identity_lf_crlf_cr_copies(self):
        identity = io.implementation_identity(); root = self.work/"identity-copy"
        originals = {name: (io.SOURCE_ROOT/name).read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n") for name in identity["sources_and_fixtures"]}
        for newline in ("\n", "\r\n", "\r"):
            for name, text in originals.items():
                path = root/name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(text.replace("\n", newline).encode())
            with patch.object(io, "SOURCE_ROOT", root), patch.object(io, "__file__", str(root/"prototypes/phase4c/io.py")):
                self.assertEqual(io.implementation_identity(), identity)

    def test_fixed_scenario_id_start_actions_payload_final_stage_pins(self):
        for field in ("id", "initial_state", "actions", "expected_final", "expected_stage", "order"):
            rows = fixtures.scenarios()
            if field == "id": rows[0]["id"] = "changed"
            elif field == "initial_state": rows[0]["initial_state"]["half"]["runs"] = 1
            elif field == "expected_final": rows[0]["expected_final"]["half"]["runs"] = 1
            elif field == "expected_stage": rows[0]["expected_stage"] = "pitching"
            elif field == "order": rows.reverse()
            else:
                row = next(row for row in rows if row["id"] == "bunt_strikeout")
                row["actions"][1:4] = [Pitch(C).as_dict()]*3
                self.assertEqual(replay(PitchHalfState.from_mapping(row["initial_state"]), [action_from_mapping(a) for a in row["actions"]]).final_state.as_dict(), row["expected_final"])
            with self.subTest(field=field), patch.object(fixtures, "load", return_value=rows), self.assertRaises(ValueError): fixtures.scenarios()

    def test_actual_fixture_equal_final_payload_substitution_rejected_before_output(self):
        folder = self.work/"mutant-fixtures"; folder.mkdir()
        for path in fixtures.FIXTURE_ROOT.glob("*.json"): (folder/path.name).write_bytes(path.read_bytes())
        data = fixtures.parse_json((folder/"scenarios.json").read_text())
        row = next(row for row in data["scenarios"] if row["id"] == "bunt_strikeout")
        row["actions"][1:4] = [Pitch(C).as_dict()]*3
        (folder/"scenarios.json").write_bytes(io.canonical(data))
        output = self.work/"data/prototypes/phase4c/mutant"
        with patch.object(fixtures, "FIXTURE_ROOT", folder), self.assertRaises(ValueError): runner.run(output)
        self.assertFalse(output.exists())

    def test_control_compatibility_fixture_drift_and_source_change_before_output(self):
        for function, rows in ((fixtures.control_table, fixtures.control_table()[:-1]), (fixtures.compatibility_table, fixtures.compatibility_table()[:-1])):
            with patch.object(fixtures, "load", return_value=rows), self.assertRaises(ValueError): function()
        rows = fixtures.compatibility_table(); rows[0]["allowed"] = 1
        with patch.object(fixtures, "load", return_value=rows), self.assertRaises(ValueError): fixtures.compatibility_table()
        output = self.work/"data/prototypes/phase4c/changed"
        identity = io.implementation_identity()
        with patch.object(runner, "implementation_identity", side_effect=[identity, identity | {"python": "changed"}]), self.assertRaises(ValueError): runner.run(output)
        rows = fixtures.scenarios(); rows[0]["expected_final"]["half"]["runs"] = 1
        with patch.object(runner, "scenarios", return_value=rows), self.assertRaises(ValueError): runner.run(output)
        self.assertFalse(output.exists())

    def test_run_allowed_old_source_only_no_old_data_research_rng_clock(self):
        allowed = {(io.SOURCE_ROOT/name).resolve() for name in io.implementation_identity()["sources_and_fixtures"]}
        opened, original_open, original_path_open, original_import = [], builtins.open, Path.open, builtins.__import__
        allowed_old_imports = {"prototypes.phase4a", "prototypes.phase4a.model", "prototypes.phase4b", "prototypes.phase4b.model"}
        def guard(path, mode):
            if not isinstance(path, (str, bytes, Path)) or not ('r' in mode or '+' in mode): return
            resolved = Path(path).resolve()
            if resolved not in allowed and not resolved.is_relative_to(self.work): raise AssertionError("unapproved source/data read")
            opened.append(resolved)
        def file_open(path, mode='r', *args, **kwargs): guard(path, mode); return original_open(path, mode, *args, **kwargs)
        def path_open(path, mode='r', *args, **kwargs): guard(path, mode); return original_path_open(path, mode, *args, **kwargs)
        def imported(name, *args, **kwargs):
            if (any(word in name for word in ("phase4a", "phase4b")) and name not in allowed_old_imports) or any(word in name for word in ("statcast", "phase3")):
                raise AssertionError("unapproved old/research import")
            return original_import(name, *args, **kwargs)
        def forbidden(*args, **kwargs): raise AssertionError("RNG/clock prohibited")
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(builtins, "open", file_open)); stack.enter_context(patch.object(Path, "open", path_open)); stack.enter_context(patch.object(builtins, "__import__", imported))
            for name in ("random", "uniform", "randint", "randrange", "choice", "gauss", "getrandbits", "Random", "SystemRandom"): stack.enter_context(patch.object(random, name, forbidden))
            for name in ("time", "time_ns", "monotonic", "perf_counter"): stack.enter_context(patch.object(time, name, forbidden))
            runner.run(self.work/"data/prototypes/phase4c/audited")
        for name in DEPENDENCY_SOURCES: self.assertIn((io.SOURCE_ROOT/name).resolve(), opened)

    def test_cli_normal_and_reject_extra_inputs(self):
        from prototypes.phase4c.__main__ import main
        for option in ("--input", "--raw-root", "--model", "--seed", "--resume"):
            with patch.object(sys, "argv", ["phase4c", "run", "--output", "x", option, "x"]), contextlib.redirect_stderr(text_io.StringIO()), self.assertRaises(SystemExit): main()
        output = self.work/"reports/phase4c/cli"
        with patch.object(sys, "argv", ["phase4c", "run", "--output", str(output)]): main()
        self.assertTrue((output/"traces.json").is_file())


if __name__ == "__main__": unittest.main()
