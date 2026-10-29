"""Self checks from design facts, not a claim of independent manager review."""
import builtins
import contextlib
from copy import deepcopy
from dataclasses import FrozenInstanceError
import hashlib
import io as text_io
import itertools
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

from prototypes.phase4b import CAPABILITIES, DESIGN
from prototypes.phase4b import fixtures, io, runner
from prototypes.phase4b.model import HalfInningState, EVENTS, ReplayResult, TraceEntry, replay, step

W = "walk_forced_only"
X = "batter_out_runners_hold"
H = "legal_over_fence_home_run"
OCCUPANCIES = tuple(itertools.product((False, True), repeat=3))


def reference(before, event):
    """Reserve the first unoccupied forced base; no boolean update formula."""
    occupied = {base for base, present in enumerate(before.bases, 1) if present}
    outs, runs = before.outs, before.runs
    if event == W:
        for destination in (1, 2, 3):
            if destination not in occupied:
                occupied.add(destination)
                break
        else:
            runs += 1
    elif event == X:
        outs += 1
    elif event == H:
        runs += len(occupied)+1
        occupied.clear()
    else:
        raise ValueError("reference event outside design")
    return {"outs": outs, "bases": [i in occupied for i in (1, 2, 3)], "runs": runs}


class ModelTests(unittest.TestCase):
    def test_arbitrary_external_starts_and_stopped_snapshots(self):
        for outs, bases in itertools.product(range(4), OCCUPANCIES):
            with self.subTest(outs=outs, bases=bases):
                state = HalfInningState(outs, bases, 10**100)
                self.assertEqual(state.half_complete, outs == 3)
                self.assertEqual(state.bases, bases)
        self.assertEqual(HalfInningState().as_dict(), {"outs": 0, "bases": [False]*3, "runs": 0})

    def test_exact_integer_fields(self):
        for field in ("outs", "runs"):
            for value in (True, False, 1.0, "1", None, float("nan"), float("inf"), complex(0, 0)):
                with self.subTest(field=field, value=repr(value)), self.assertRaises(ValueError):
                    HalfInningState(**{field: value})
        for fields in ({"outs": -1}, {"outs": 4}, {"runs": -1}):
            with self.subTest(fields=fields), self.assertRaises(ValueError): HalfInningState(**fields)

    def test_bases_require_exact_tuple_and_bools(self):
        class TupleSubclass(tuple): pass
        for value in ([False]*3, (), (False, False), (False,)*4, (0, 0, 0), (True, 1, False),
                      (None, False, False), "000", None, TupleSubclass((False,)*3)):
            with self.subTest(value=repr(value)), self.assertRaises(ValueError): HalfInningState(bases=value)

    def test_mapping_representation_exact_keys_and_explicit_array_conversion(self):
        value = {"outs": 2, "bases": [True, False, True], "runs": 7}
        state = HalfInningState.from_mapping(value)
        value["bases"][0] = False
        self.assertEqual(state.bases, (True, False, True))
        for data in ({}, None, [], {"outs": 0, "bases": [False]*3},
                     state.as_dict() | {"status": "active"}, state.as_dict() | {"bases": (True, False, True)}):
            with self.subTest(value=repr(data)), self.assertRaises(ValueError): HalfInningState.from_mapping(data)

    def test_json_duplicate_nonfinite_and_wrong_shape_rejected(self):
        for text in ('{"outs":0,"outs":1,"bases":[],"runs":0}', '{"nested":{"x":0,"x":1}}',
                     '{"runs":NaN}', '{"runs":Infinity}', '{"runs":-Infinity}'):
            with self.subTest(text=text), self.assertRaises(ValueError): fixtures.parse_json(text)
        for raw in ('{"outs":0,"bases":[0,0,0],"runs":0}', '{"outs":0,"bases":null,"runs":0}',
                    '{"outs":true,"bases":[false,false,false],"runs":0}'):
            with self.subTest(raw=raw), self.assertRaises(ValueError): HalfInningState.from_mapping(fixtures.parse_json(raw))
        self.assertEqual(HalfInningState.from_mapping(fixtures.parse_json(json.dumps(HalfInningState().as_dict()))), HalfInningState())

    def test_exact_events_and_no_implicit_4a_adapter(self):
        class StrSubclass(str): pass
        bad = (None, 0, True, [], {}, StrSubclass(W), "out", "strikeout", "flyout", "walk", "home_run", "single",
               "double_play", "walk_awarded", "strikeout_out", "caught_foul_out", "fair_play_pending",
               "HBP", "intentional_walk", "automatic_walk", "runner_out", "sacrifice_fly", "appeal", "walk_off")
        for event in bad:
            with self.subTest(event=repr(event)), self.assertRaises(ValueError): step(HalfInningState(), event)
        self.assertEqual(EVENTS, (W, X, H))

    def test_state_and_sequence_types_no_implicit_iteration(self):
        class ListSubclass(list): pass
        class TupleSubclass(tuple): pass
        for start in (None, {}, HalfInningState().as_dict(), (0, (False,)*3, 0)):
            with self.subTest(start=repr(start)):
                with self.assertRaises(ValueError): step(start, W)
                with self.assertRaises(ValueError): replay(start, [])
        for events in (W, {W}, {"event": W}, iter([W]), None, ListSubclass([W]), TupleSubclass((W,))):
            with self.subTest(events=repr(events)), self.assertRaises(ValueError): replay(HalfInningState(), events)

    def test_all_twenty_four_stopped_rejections_retain_bases_and_runs(self):
        for bases, event in itertools.product(OCCUPANCIES, (W, X, H)):
            with self.subTest(bases=bases, event=event):
                state = HalfInningState(3, bases, 7)
                with self.assertRaises(ValueError): step(state, event)
                self.assertEqual(state, HalfInningState(3, bases, 7))

    def test_empty_replay_allowed_after_stop_and_active(self):
        for outs, bases in itertools.product(range(4), OCCUPANCIES):
            state = HalfInningState(outs, bases, 7)
            for sequence in ([], ()):
                self.assertEqual(replay(state, sequence), ReplayResult(state, ()))

    def test_invalid_tail_exposes_no_partial_result_or_mutation(self):
        for events in ([W, "walk"], [X, X, X, H], [W, None]):
            original = deepcopy(events)
            start = HalfInningState()
            with self.assertRaises(ValueError): replay(start, events)
            self.assertEqual(events, original)
            self.assertEqual(start, HalfInningState())

    def test_immutable_values_and_detached_json(self):
        start = HalfInningState(0, (True, False, True), 2)
        result = replay(start, [H])
        for obj, key, value in ((start, "outs", 1), (result, "final_state", start),
                                (result.trace[0], "runs_added", 0)):
            with self.assertRaises(FrozenInstanceError): setattr(obj, key, value)
        with self.assertRaises(TypeError): CAPABILITIES["product_adoption"] = True
        json_value = result.as_dict()
        json_value["trace"][0]["before"]["bases"][0] = False
        self.assertEqual(start.bases, (True, False, True))
        self.assertEqual(set(result.trace[0].as_dict()), {"event", "before", "after", "runs_added"})
        with self.assertRaises(ValueError): ReplayResult(start, [])
        with self.assertRaises(ValueError): TraceEntry(H, start, result.final_state, True)
        self.assertIsNot(step(start, W), start)

    def test_validation_remains_under_python_optimized_mode(self):
        code = """from prototypes.phase4b.model import HalfInningState, step, replay
cases=[lambda:HalfInningState(True),lambda:HalfInningState(bases=[False]*3),lambda:HalfInningState(runs=1.0),lambda:step(HalfInningState(3),'walk_forced_only'),lambda:replay(HalfInningState(),'walk_forced_only')]
for case in cases:
    try: case()
    except ValueError: continue
    raise RuntimeError('validation vanished')
"""
        subprocess.run([sys.executable, "-O", "-c", code], cwd=io.SOURCE_ROOT, check=True, capture_output=True)


class ReferenceTests(unittest.TestCase):
    def test_static_table_all_pairs_zero_seven_and_huge_run_offsets(self):
        table = fixtures.transition_table()
        self.assertEqual(len(table), 72)
        keys = set()
        for row in table:
            base = HalfInningState.from_mapping(row["before"])
            keys.add((base.outs, base.bases, row["event"]))
            self.assertEqual(reference(base, row["event"]), row["after"])
            for offset in (0, 7, 10**100):
                with self.subTest(outs=base.outs, bases=base.bases, event=row["event"], offset=offset):
                    state = HalfInningState(base.outs, base.bases, offset)
                    after = step(state, row["event"])
                    expected = row["after"] | {"runs": row["after"]["runs"]+offset}
                    self.assertEqual(after.as_dict(), expected)
                    self.assertEqual(after.runs-state.runs, row["runs_added"])
        self.assertEqual(keys, set(itertools.product(range(3), OCCUPANCIES, (W, X, H))))

    def test_person_conservation_monotonicity_and_no_extra_outs_or_runs(self):
        for outs, bases, event in itertools.product(range(3), OCCUPANCIES, (W, X, H)):
            with self.subTest(outs=outs, bases=bases, event=event):
                before = HalfInningState(outs, bases, 7)
                after = step(before, event)
                self.assertEqual(sum(bases)+1, sum(after.bases)+(after.runs-before.runs)+(after.outs-before.outs))
                self.assertGreaterEqual(after.runs, before.runs)
                self.assertGreaterEqual(after.outs, before.outs)
                if event in (W, H): self.assertEqual(after.outs, before.outs)
                else: self.assertEqual(after.runs, before.runs)

    def test_walk_eight_base_positions_not_uniform_shift(self):
        rows = {"000": ("100", 0), "001": ("101", 0), "010": ("110", 0), "011": ("111", 0),
                "100": ("110", 0), "101": ("111", 0), "110": ("111", 0), "111": ("111", 1)}
        for bits, (after_bits, increment) in rows.items():
            with self.subTest(bits=bits):
                result = step(HalfInningState(1, tuple(c == "1" for c in bits), 7), W)
                self.assertEqual(result, HalfInningState(1, tuple(c == "1" for c in after_bits), 7+increment))

    def test_sixteen_fixed_scenarios_and_thirty_events(self):
        rows = fixtures.scenarios()
        self.assertEqual(len(rows), 16)
        self.assertEqual(sum(len(row["events"]) for row in rows), 30)
        for row in rows:
            with self.subTest(id=row["id"]):
                state = HalfInningState.from_mapping(row["initial_state"])
                result = replay(state, row["events"])
                self.assertEqual(result.final_state.as_dict(), row["expected_final"])
                for entry in result.trace:
                    self.assertEqual(entry.after.as_dict(), reference(entry.before, entry.event))
                    self.assertEqual(entry.runs_added, entry.after.runs-entry.before.runs)

    def test_all_forty_six_splits_and_case_order_invariance(self):
        rows = fixtures.scenarios()
        expected = {row["id"]: replay(HalfInningState.from_mapping(row["initial_state"]), row["events"]) for row in rows}
        split_count = 0
        for row in reversed(rows):
            start = HalfInningState.from_mapping(row["initial_state"])
            self.assertEqual(replay(start, tuple(row["events"])), expected[row["id"]])
            for split in range(len(row["events"])+1):
                with self.subTest(id=row["id"], split=split):
                    prefix = replay(start, row["events"][:split])
                    rest = replay(prefix.final_state, row["events"][split:])
                    self.assertEqual(rest.final_state, expected[row["id"]].final_state)
                    self.assertEqual(prefix.trace+rest.trace, expected[row["id"]].trace)
                    split_count += 1
        self.assertEqual(split_count, 46)

    def test_thousand_walks_and_exact_huge_increments_no_cap(self):
        loaded = HalfInningState(2, (True,)*3, 7)
        self.assertEqual(replay(loaded, [W]*1000).final_state, HalfInningState(2, (True,)*3, 1007))
        self.assertEqual(replay(HalfInningState(), [W]*1000).final_state, HalfInningState(0, (True,)*3, 997))
        huge = HalfInningState(2, (True,)*3, 10**100)
        self.assertEqual(step(huge, W).runs, 10**100+1)
        self.assertEqual(step(huge, H).runs, 10**100+4)

    def test_fixed_columns_pin_id_start_events_final_and_order(self):
        for change in ("id", "initial_state", "events", "expected_final", "order"):
            rows = fixtures.scenarios()
            if change == "id": rows[0]["id"] = "other"
            elif change == "initial_state": rows[0]["initial_state"]["outs"] = 1
            elif change == "expected_final": rows[0]["expected_final"]["runs"] = 1
            elif change == "events":
                row = rows[-1]
                altered = [W, W, H, X, W, X, X]
                self.assertEqual(replay(HalfInningState(), altered).final_state.as_dict(), row["expected_final"])
                row["events"] = altered
            else: rows.reverse()
            with self.subTest(change=change), patch.object(fixtures, "load", return_value=rows), self.assertRaises(ValueError):
                fixtures.scenarios()


class RunnerTests(unittest.TestCase):
    def setUp(self):
        parent = io.SOURCE_ROOT/"data/prototypes/phase4b/unittest"
        parent.mkdir(parents=True, exist_ok=True)
        self.work = Path(tempfile.mkdtemp(prefix="case-", dir=parent))
        self.root_patch = patch.object(io, "ROOT", self.work)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)
        # Preserve every case directory, including evidence of real aliases.

    def test_run_four_artifacts_repeat_hashes_and_metadata(self):
        first = runner.run(self.work/"data/prototypes/phase4b/first")
        second = runner.run(self.work/"reports/phase4b/second")
        self.assertEqual({p.name for p in first.iterdir()}, {"plan.json", "traces.json", "REPORT.md", "SHA256SUMS.json"})
        for path in first.iterdir(): self.assertEqual(path.read_bytes(), (second/path.name).read_bytes())
        manifest = fixtures.parse_json((first/"SHA256SUMS.json").read_text())
        self.assertEqual(set(manifest), {"plan.json", "traces.json", "REPORT.md"})
        for name, wanted in manifest.items(): self.assertEqual(io.digest(first/name), wanted)
        for name in ("plan.json", "traces.json"):
            document = fixtures.parse_json((first/name).read_text())
            self.assertEqual(document["design"], DESIGN)
            for key, value in CAPABILITIES.items(): self.assertEqual(document[key], value)
        for path in first.iterdir():
            self.assertNotIn(str(self.work).encode(), path.read_bytes())
        plan = fixtures.parse_json((first/"plan.json").read_text())
        self.assertEqual(plan["fixed_scenarios"], fixtures.scenarios())
        self.assertEqual(plan["transition_count"], 72)

    def test_output_namespace_roots_old_bounds_and_existing_rejected(self):
        for path in (self.work/"reports/phase4a/new", self.work/"data/prototypes/phase4a/new",
                     self.work/"data/raw/new", self.work/"reports/phase4b", self.work.parent/"outside"):
            with self.subTest(path=path), self.assertRaises(ValueError): io.reserve_output(path)
        output = io.reserve_output(self.work/"data/prototypes/phase4b/new")
        with self.assertRaises(FileExistsError): io.reserve_output(output)

    def test_input_overlap_rejected_before_mkdir(self):
        output = self.work/"data/prototypes/phase4b/new"
        for source in (output, output/"child", output.parent):
            with self.subTest(source=source), self.assertRaises(ValueError): io.reserve_output(output, [source])
        self.assertFalse(output.exists())

    def test_actual_junction_or_symlink_namespace_and_child_to_old_or_outside(self):
        for location, kind in itertools.product(("namespace", "child"), ("old", "outside")):
            with self.subTest(location=location, kind=kind):
                root = self.work/(location+"-"+kind)
                target = root/"data/prototypes/phase4a" if kind == "old" else self.work/(root.name+"-outside")
                target.mkdir(parents=True)
                namespace = root/"data/prototypes/phase4b"
                alias = namespace if location == "namespace" else namespace/"shortcut"
                alias.parent.mkdir(parents=True, exist_ok=True)
                if os.name == "nt":
                    subprocess.run(["cmd", "/c", "mklink", "/J", str(alias), str(target)], check=True, capture_output=True, text=True)
                else:
                    alias.symlink_to(target, target_is_directory=True)
                with patch.object(io, "ROOT", root), self.assertRaises(ValueError): io.reserve_output(alias/"must-not-create")
                self.assertFalse((target/"must-not-create").exists())

    def test_identity_complete_lf_normalized_and_python_metadata(self):
        identity = io.implementation_identity()
        wanted = {"prototypes/__init__.py", "prototypes/phase4b/.gitattributes",
                  "prototypes/phase4b/fixtures/transitions.json", "prototypes/phase4b/fixtures/scenarios.json"}
        wanted |= {p.relative_to(io.SOURCE_ROOT).as_posix() for p in (io.SOURCE_ROOT/"prototypes/phase4b").glob("*.py")}
        self.assertEqual(set(identity["sources_and_fixtures"]), wanted)
        for name, digest in identity["sources_and_fixtures"].items():
            text = (io.SOURCE_ROOT/name).read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
            self.assertEqual(hashlib.sha256(text.encode()).hexdigest(), digest)
        self.assertEqual(hashlib.sha256(io.canonical(identity["sources_and_fixtures"])).hexdigest(), identity["bundle_sha256"])
        self.assertEqual(identity["third_party_runtime_dependencies"], [])
        self.assertEqual(identity["python"], ".".join(map(str, sys.version_info[:3])))

    def test_fixture_json_shapes_duplicate_transition_and_cardinality(self):
        for rows in ([], fixtures.transition_table()[:-1], fixtures.transition_table()+[fixtures.transition_table()[0]]):
            with patch.object(fixtures, "load", return_value=rows), self.assertRaises(ValueError): fixtures.transition_table()
        table = fixtures.transition_table()
        table[0]["runs_added"] = False
        with patch.object(fixtures, "load", return_value=table), self.assertRaises(ValueError): fixtures.transition_table()
        with patch.object(fixtures, "load", return_value=[]), self.assertRaises(ValueError): fixtures.scenarios()
        with self.assertRaises(ValueError): io.canonical({"runs": float("inf")})

    def test_source_identity_same_for_lf_crlf_and_cr_text(self):
        root = self.work/"identity-copy"
        identity = io.implementation_identity()
        originals = {}
        for name in identity["sources_and_fixtures"]:
            value = (io.SOURCE_ROOT/name).read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
            destination = root/name
            destination.parent.mkdir(parents=True, exist_ok=True)
            originals[name] = value
        for newline in ("\n", "\r\n", "\r"):
            for name, text in originals.items(): (root/name).write_bytes(text.replace("\n", newline).encode("utf-8"))
            with patch.object(io, "SOURCE_ROOT", root), patch.object(io, "__file__", str(root/"prototypes/phase4b/io.py")):
                self.assertEqual(io.implementation_identity(), identity)

    def test_semantic_reference_mismatch_before_output(self):
        table = fixtures.transition_table()
        table[0]["after"]["runs"] = 1
        output = self.work/"data/prototypes/phase4b/bad-reference"
        with patch.object(runner, "transition_table", return_value=table), self.assertRaises(ValueError): runner.run(output)
        rows = fixtures.scenarios()
        rows[0]["expected_final"]["runs"] = 1
        with patch.object(runner, "scenarios", return_value=rows), self.assertRaises(ValueError): runner.run(output)
        self.assertFalse(output.exists())

    def test_modified_fixed_json_same_final_order_swap_rejected_before_output(self):
        folder = self.work/"mutant-fixtures"
        folder.mkdir()
        for path in fixtures.FIXTURE_ROOT.glob("*.json"): (folder/path.name).write_bytes(path.read_bytes())
        data = fixtures.parse_json((folder/"scenarios.json").read_text())
        data["scenarios"][-1]["events"] = [W, W, H, X, W, X, X]
        self.assertEqual(replay(HalfInningState(), data["scenarios"][-1]["events"]).final_state.as_dict(), data["scenarios"][-1]["expected_final"])
        (folder/"scenarios.json").write_bytes(io.canonical(data))
        output = self.work/"data/prototypes/phase4b/mutant-run"
        with patch.object(fixtures, "FIXTURE_ROOT", folder), self.assertRaises(ValueError): runner.run(output)
        self.assertFalse(output.exists())

    def test_source_identity_change_rejected_before_output(self):
        identity = io.implementation_identity()
        output = self.work/"data/prototypes/phase4b/changed-source"
        with patch.object(runner, "implementation_identity", side_effect=[identity, identity | {"python": "changed"}]), self.assertRaises(ValueError):
            runner.run(output)
        self.assertFalse(output.exists())

    def test_run_read_rng_clock_and_old_import_guards(self):
        allowed = {(io.SOURCE_ROOT/name).resolve() for name in io.implementation_identity()["sources_and_fixtures"]}
        original_open, original_path_open, original_import = builtins.open, Path.open, builtins.__import__
        violations = []
        def guard(path, mode):
            if not isinstance(path, (str, bytes, Path)) or not ('r' in mode or '+' in mode): return
            resolved = Path(path).resolve()
            if resolved not in allowed and not resolved.is_relative_to(self.work):
                violations.append(str(resolved))
                raise AssertionError("unapproved input read")
        def opened(path, mode='r', *args, **kwargs):
            guard(path, mode)
            return original_open(path, mode, *args, **kwargs)
        def path_opened(path, mode='r', *args, **kwargs):
            guard(path, mode)
            return original_path_open(path, mode, *args, **kwargs)
        def imported(name, *args, **kwargs):
            if any(word in name for word in ("phase4a", "phase3", "statcast")):
                raise AssertionError("old implementation import")
            return original_import(name, *args, **kwargs)
        def forbidden(*args, **kwargs): raise AssertionError("RNG/clock prohibited")
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(builtins, "open", opened))
            stack.enter_context(patch.object(Path, "open", path_opened))
            stack.enter_context(patch.object(builtins, "__import__", imported))
            for name in ("random", "uniform", "randint", "randrange", "choice", "gauss", "getrandbits", "Random", "SystemRandom"):
                stack.enter_context(patch.object(random, name, forbidden))
            for name in ("time", "time_ns", "monotonic", "perf_counter"):
                stack.enter_context(patch.object(time, name, forbidden))
            output = runner.run(self.work/"data/prototypes/phase4b/audited")
        self.assertEqual(violations, [])
        self.assertTrue((output/"plan.json").is_file())

    def test_cli_rejects_external_inputs_and_other_commands(self):
        from prototypes.phase4b.__main__ import main
        for option in ("--input", "--raw-root", "--model", "--seed", "--resume", "--phase4a"):
            with self.subTest(option=option), patch.object(sys, "argv", ["phase4b", "run", "--output", "x", option, "x"]), contextlib.redirect_stderr(text_io.StringIO()):
                with self.assertRaises(SystemExit): main()
        with patch.object(sys, "argv", ["phase4b", "fit"]), contextlib.redirect_stderr(text_io.StringIO()), self.assertRaises(SystemExit): main()

    def test_cli_fixed_run(self):
        from prototypes.phase4b.__main__ import main
        output = self.work/"data/prototypes/phase4b/cli"
        with patch.object(sys, "argv", ["phase4b", "run", "--output", str(output)]): main()
        self.assertTrue((output/"traces.json").is_file())


if __name__ == "__main__":
    unittest.main()
