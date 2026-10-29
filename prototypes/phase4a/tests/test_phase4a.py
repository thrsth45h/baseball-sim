"""Method counts are distinct from subtests, 96 transitions and 16 fixtures."""
from collections import deque
from dataclasses import FrozenInstanceError
import builtins
import contextlib
import hashlib
import io as string_io
import json
import os
from pathlib import Path
import random
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from prototypes.phase4a import CAPABILITIES, DESIGN
from prototypes.phase4a import fixtures, io, model, runner
from prototypes.phase4a.model import CountState, EVENTS, STATUSES, replay, step

B, C, M, T, F, U, O, P = EVENTS


class ModelTests(unittest.TestCase):
    def test_start_and_frozen_inputs_results(self):
        start = CountState()
        self.assertEqual(start.as_dict(), dict(balls=0, strikes=0, pitches_seen=0, status="active"))
        result = replay(start, [B])
        with self.assertRaises(FrozenInstanceError): start.balls = 2
        with self.assertRaises(FrozenInstanceError): result.final_state = start
        with self.assertRaises(FrozenInstanceError): result.trace[0].ordinal = 0
        self.assertEqual(start, CountState())
        with self.assertRaises(TypeError): CAPABILITIES["product_adoption"] = True

    def test_exact_integer_validation_every_field(self):
        for key in ("balls", "strikes", "pitches_seen"):
            for invalid in (True, False, 1.0, "1", None, float("nan"), float("inf"), complex(0, 0)):
                with self.subTest(key=key, value=repr(invalid)):
                    data = CountState().as_dict() | {key: invalid}
                    with self.assertRaises(ValueError): CountState.from_mapping(data)

    def test_status_and_exact_mapping_validation(self):
        for status in (True, None, "ACTIVE", "hit", "out", 0):
            with self.subTest(status=status), self.assertRaises(ValueError): CountState(status=status)
        for data in ({}, CountState().as_dict() | {"outs": 0}, [], None):
            with self.subTest(data=data), self.assertRaises(ValueError): CountState.from_mapping(data)
        with self.assertRaises(ValueError): step(CountState().as_dict(), B)
        with self.assertRaises(ValueError): replay(None, [])

    def test_inconsistent_and_boundary_states(self):
        valid = [CountState(3, 2, 5), CountState(3, 2, 100), CountState(4, 0, 4, "walk_awarded"),
                 CountState(4, 2, 100, "walk_awarded"), CountState(3, 3, 6, "strikeout_out"),
                 CountState(0, 0, 1, "caught_foul_out"), CountState(3, 2, 6, "fair_play_pending")]
        self.assertEqual(len(valid), 7)
        invalid = [(0, 0, 1, "active"), (0, 1, 2, "active"), (0, 2, 1, "active"),
                   (4, 2, 6, "active"), (-1, 0, 0, "active"), (0, -1, 0, "active"),
                   (4, 1, 6, "walk_awarded"), (4, 2, 5, "walk_awarded"),
                   (3, 3, 5, "strikeout_out"), (4, 3, 7, "strikeout_out"),
                   (0, 0, 0, "caught_foul_out"), (1, 1, 4, "fair_play_pending"),
                   (0, 0, -1, "active")]
        for row in invalid:
            with self.subTest(state=row), self.assertRaises(ValueError): CountState(*row)

    def test_reachable_state_characterization_with_bounded_graph(self):
        # Enumerate successors, independently compare which constructor states exist.
        limit = 16
        reached = {CountState()}
        queue = deque([CountState()])
        while queue:
            state = queue.popleft()
            if state.status != "active" or state.pitches_seen >= limit: continue
            for event in EVENTS:
                nxt = step(state, event)
                if nxt not in reached: reached.add(nxt); queue.append(nxt)
        for b in range(-1, 6):
            for s in range(-1, 5):
                for n in range(limit+1):
                    for status in STATUSES:
                        try: value = CountState(b, s, n, status)
                        except ValueError: value = None
                        self.assertEqual(value is not None, (b, s, n, status) in {
                            (x.balls, x.strikes, x.pitches_seen, x.status) for x in reached})

    def test_ninety_six_static_transitions(self):
        rows = fixtures.transition_table()
        self.assertEqual(len(rows), 96)
        for row in rows:
            with self.subTest(before=row["before"], event=row["event"]):
                self.assertEqual(step(CountState.from_mapping(row["before"]), row["event"]).as_dict(), row["after"])

    def test_fixture_table_against_separate_design_columns(self):
        # Manual design columns; this oracle never invokes step/replay.
        rows = fixtures.transition_table()
        for row in rows:
            b, s = row["before"]["balls"], row["before"]["strikes"]
            event = row["event"]
            columns = {
                B: (b+1, s, "walk_awarded" if b == 3 else "active"),
                C: (b, s+1, "strikeout_out" if s == 2 else "active"),
                M: (b, s+1, "strikeout_out" if s == 2 else "active"),
                T: (b, s+1, "strikeout_out" if s == 2 else "active"),
                F: (b, min(s+1, 2), "active"), U: (b, s+1, "strikeout_out" if s == 2 else "active"),
                O: (b, s, "caught_foul_out"), P: (b, s, "fair_play_pending"),
            }
            expected = dict(zip(("balls", "strikes", "status"), columns[event]))
            expected["pitches_seen"] = b+s+1
            self.assertEqual(row["after"], expected)

    def test_sixteen_fixed_scenario_finals(self):
        for row in fixtures.scenarios():
            with self.subTest(id=row["id"]):
                self.assertEqual(replay(CountState(), row["events"]).final_state.as_dict(), row["expected_final"])

    def test_every_split_preserves_absolute_trace(self):
        splits = 0
        for row in fixtures.scenarios():
            events = row["events"]
            full = replay(CountState(), events)
            for split in range(len(events)+1):
                with self.subTest(id=row["id"], split=split):
                    prefix = replay(CountState(), events[:split])
                    suffix = replay(prefix.final_state, events[split:])
                    self.assertEqual(prefix.trace+suffix.trace, full.trace)
                    self.assertEqual(suffix.final_state, full.final_state)
                    self.assertEqual([x.ordinal for x in full.trace], list(range(1, len(events)+1)))
                    splits += 1
        self.assertEqual(splits, 95)

    def test_thousand_two_strike_fouls_do_not_end_subject(self):
        self.assertEqual(replay(CountState(), [C, M]+[F]*1000).final_state, CountState(0, 2, 1002))

    def test_four_foul_kinds_remain_distinct_at_two_strikes(self):
        start = CountState(0, 2, 2)
        self.assertEqual(step(start, F), CountState(0, 2, 3))
        for event in (T, U): self.assertEqual(step(start, event), CountState(0, 3, 3, "strikeout_out"))
        self.assertEqual(step(start, O), CountState(0, 2, 3, "caught_foul_out"))

    def test_fair_handoff_does_not_claim_hit_out_or_reset(self):
        state = step(CountState(3, 2, 5), P)
        self.assertEqual(state, CountState(3, 2, 6, "fair_play_pending"))
        self.assertEqual(set(state.as_dict()), {"balls", "strikes", "pitches_seen", "status"})

    def test_stopped_states_reject_all_eight_events(self):
        terminal = [CountState(4, 0, 4, "walk_awarded"), CountState(0, 3, 3, "strikeout_out"),
                    CountState(0, 0, 1, "caught_foul_out"), CountState(0, 0, 1, "fair_play_pending")]
        for state in terminal:
            for event in EVENTS:
                with self.subTest(status=state.status, event=event), self.assertRaises(ValueError): step(state, event)

    def test_unsupported_ambiguous_and_nonstring_events(self):
        for event in ("strike", "foul", "swinging_strike_uncaught", "called_strike_uncaught", "hit_by_pitch",
                      "intentional_walk", "automatic_ball", "ball", None, True, {}, [], b"called_ball"):
            with self.subTest(event=event), self.assertRaises(ValueError): step(CountState(), event)

    def test_replay_only_accepts_finite_list_or_tuple(self):
        self.assertEqual(replay(CountState(), [B]), replay(CountState(), (B,)))
        for value in (B, {"event": B}, iter([B]), {B}, None, False):
            with self.subTest(value=type(value).__name__), self.assertRaises(ValueError): replay(CountState(), value)

    def test_invalid_tail_preserves_input_without_partial_return(self):
        source = [C, M, T, B]
        before = list(source)
        state = CountState()
        with self.assertRaises(ValueError): replay(state, source)
        self.assertEqual(source, before)
        self.assertEqual(state, CountState())
        with self.assertRaises(ValueError): replay(state, [B, "strike"])

    def test_empty_replay_preserves_active_and_stopped_states(self):
        for state in (CountState(), CountState(0, 0, 1, "fair_play_pending")):
            result = replay(state, [])
            self.assertIs(result.final_state, state)
            self.assertEqual(result.trace, ())

    def test_noncanonical_pitch_count_loop_state_updates(self):
        state = CountState(3, 2, 1000)
        self.assertEqual(step(state, B), CountState(4, 2, 1001, "walk_awarded"))
        self.assertEqual(replay(state, [F]).trace[0].ordinal, 1001)

    def test_order_and_repeated_execution_do_not_change_results(self):
        rows = fixtures.scenarios()
        first = {r["id"]: replay(CountState(), r["events"]) for r in rows}
        second = {r["id"]: replay(CountState(), r["events"]) for r in reversed(rows)}
        self.assertEqual(first, second)

    def test_explicit_validation_survives_optimized_python(self):
        code = "from prototypes.phase4a.model import CountState\ntry: CountState(balls=True)\nexcept ValueError: pass\nelse: raise RuntimeError('validation disabled')"
        subprocess.run([sys.executable, "-O", "-c", code], check=True, cwd=io.SOURCE_ROOT, capture_output=True, text=True)


class FilesystemTests(unittest.TestCase):
    def setUp(self):
        # Keep uniquely named evidence; never recursively remove prior test data.
        base = io.SOURCE_ROOT/"data/prototypes/phase4a/unittest"
        base.mkdir(parents=True, exist_ok=True)
        self.work = Path(tempfile.mkdtemp(prefix="case-", dir=base))
        self.root_patch = patch.object(io, "ROOT", self.work)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def test_output_four_files_and_replay_bytes(self):
        first = runner.run(self.work/"data/prototypes/phase4a/one")
        second = runner.run(self.work/"data/prototypes/phase4a/two")
        names = {"plan.json", "traces.json", "REPORT.md", "SHA256SUMS.json"}
        self.assertEqual({p.name for p in first.iterdir()}, names)
        for name in names: self.assertEqual((first/name).read_bytes(), (second/name).read_bytes())
        for name, value in fixtures.read_json(first/"SHA256SUMS.json").items():
            self.assertEqual(io.digest(first/name), value)
        document = fixtures.read_json(first/"traces.json")
        self.assertEqual(len(document["scenarios"]), 16)
        for key, value in CAPABILITIES.items(): self.assertEqual(document[key], value)

    def test_new_namespace_existing_root_and_checkout_bounds(self):
        for path in (self.work/"reports/phase3d/new", self.work/"data/raw/new",
                     self.work/"reports/phase4a", self.work.parent/"outside-not-allowed"):
            with self.subTest(path=path), self.assertRaises(ValueError): io.reserve_output(path)
        path = io.reserve_output(self.work/"data/prototypes/phase4a/new")
        with self.assertRaises(FileExistsError): io.reserve_output(path)

    def test_input_overlap_rejected(self):
        out = self.work/"data/prototypes/phase4a/new"
        for protected in (out, out/"child", out.parent):
            with self.subTest(protected=protected), self.assertRaises(ValueError):
                io.reserve_output(out, input_roots=[protected])
        self.assertFalse(out.exists())

    def test_actual_aliases_cannot_whitelist_old_or_outside_outputs(self):
        for location in ("namespace", "child"):
            for target_kind in ("old", "outside"):
                with self.subTest(location=location, target=target_kind):
                    root = self.work/(location+"-"+target_kind)
                    root.mkdir()
                    target = root/"reports/phase3d" if target_kind == "old" else self.work/(root.name+"-outside")
                    target.mkdir(parents=True)
                    namespace = root/"data/prototypes/phase4a"
                    alias = namespace if location == "namespace" else namespace/"shortcut"
                    alias.parent.mkdir(parents=True, exist_ok=True)
                    if os.name == "nt":
                        subprocess.run(["cmd", "/c", "mklink", "/J", str(alias), str(target)],
                                       check=True, capture_output=True, text=True)
                    else: alias.symlink_to(target, target_is_directory=True)
                    with patch.object(io, "ROOT", root), self.assertRaises(ValueError):
                        io.reserve_output(alias/"must-not-create")
                    self.assertFalse((target/"must-not-create").exists())

    def test_source_identity_complete_and_lf_normalized(self):
        identity = io.implementation_identity()
        sources = identity["sources_and_fixtures"]
        expected = {"prototypes/__init__.py", "prototypes/phase4a/.gitattributes"}
        expected |= {p.relative_to(io.SOURCE_ROOT).as_posix() for p in (io.SOURCE_ROOT/"prototypes/phase4a").glob("*.py")}
        expected |= {"prototypes/phase4a/fixtures/transitions.json", "prototypes/phase4a/fixtures/scenarios.json"}
        self.assertEqual(set(sources), expected)
        for name, wanted in sources.items():
            text = (io.SOURCE_ROOT/name).read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
            self.assertEqual(hashlib.sha256(text.encode()).hexdigest(), wanted)
        self.assertEqual(hashlib.sha256(io.canonical(sources)).hexdigest(), identity["bundle_sha256"])
        self.assertEqual(identity["third_party_runtime_dependencies"], [])

    def test_json_rejects_nonfinite_duplicate_and_wrong_fixture_fields(self):
        for i, text in enumerate(('{"x":NaN}', '{"x":1,"x":2}')):
            path = self.work/f"bad-{i}.json"
            path.write_text(text, encoding="utf-8")
            with self.assertRaises(ValueError): fixtures.read_json(path)
        with self.assertRaises(ValueError): io.canonical({"x": float("nan")})
        with patch.object(fixtures, "_document", return_value=[{"before": {}, "event": B, "after": {}, "unknown": 1}]):
            with self.assertRaises(ValueError): fixtures.transition_table()
        with patch.object(fixtures, "_document", return_value=[]):
            with self.assertRaises(ValueError): fixtures.scenarios()
            with self.assertRaises(ValueError): fixtures.transition_table()

    def test_scenario_table_semantic_mismatch_fails_before_output(self):
        rows = fixtures.scenarios()
        rows[0]["expected_final"] = CountState(1, 0, 1).as_dict()
        output = self.work/"data/prototypes/phase4a/invalid"
        with patch.object(runner, "scenarios", return_value=rows), self.assertRaises(ValueError): runner.run(output)
        self.assertFalse(output.exists())
        table = fixtures.transition_table()
        table[0]["after"] = CountState(0, 1, 1).as_dict()
        with patch.object(runner, "transition_table", return_value=table), self.assertRaises(ValueError): runner.run(output)
        self.assertFalse(output.exists())

    def test_implementation_change_fails_before_output(self):
        identity = io.implementation_identity()
        output = self.work/"data/prototypes/phase4a/changed"
        with patch.object(runner, "implementation_identity", side_effect=[identity, identity | {"python": "changed"}]), self.assertRaises(ValueError):
            runner.run(output)
        self.assertFalse(output.exists())

    def test_run_prohibits_old_data_models_and_rng(self):
        allowed = {(io.SOURCE_ROOT/name).resolve() for name in io.implementation_identity()["sources_and_fixtures"]}
        original_open, original_path_open = builtins.open, Path.open
        attempts = []
        def guard(path, mode):
            if not isinstance(path, (str, bytes, Path)) or not ('r' in mode or '+' in mode): return
            p = Path(path).resolve()
            if p in allowed or p.is_relative_to(self.work): return
            attempts.append(str(p))
            raise AssertionError("unapproved input read")
        def guarded_open(path, mode='r', *args, **kwargs):
            guard(path, mode)
            return original_open(path, mode, *args, **kwargs)
        def guarded_path_open(path, mode='r', *args, **kwargs):
            guard(path, mode)
            return original_path_open(path, mode, *args, **kwargs)
        def forbidden(*args, **kwargs): raise AssertionError("RNG/time prohibited in fixed runner")
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(builtins, "open", guarded_open))
            stack.enter_context(patch.object(Path, "open", guarded_path_open))
            for name in ("random", "uniform", "randint", "randrange", "choice", "gauss", "getrandbits", "Random", "SystemRandom"):
                stack.enter_context(patch.object(random, name, forbidden))
            stack.enter_context(patch.object(time, "time", forbidden))
            output = runner.run(self.work/"data/prototypes/phase4a/audited")
        self.assertFalse(attempts)
        self.assertEqual(fixtures.read_json(output/"plan.json")["design"], DESIGN)

    def test_cli_rejects_external_input_and_non_run_commands(self):
        from prototypes.phase4a.__main__ import main
        for option in ("--input", "--raw-root", "--model", "--seed", "--resume"):
            with patch.object(sys, "argv", ["phase4a", "run", "--output", "x", option, "x"]), contextlib.redirect_stderr(string_io.StringIO()):
                with self.assertRaises(SystemExit): main()
        with patch.object(sys, "argv", ["phase4a", "fit"]), contextlib.redirect_stderr(string_io.StringIO()):
            with self.assertRaises(SystemExit): main()

    def test_cli_normal_fixed_run(self):
        out = self.work/"data/prototypes/phase4a/cli"
        from prototypes.phase4a.__main__ import main
        with patch.object(sys, "argv", ["phase4a", "run", "--output", str(out)]), contextlib.redirect_stdout(string_io.StringIO()): main()
        self.assertTrue((out/"traces.json").is_file())


if __name__ == "__main__":
    unittest.main()
