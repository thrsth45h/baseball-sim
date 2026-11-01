"""Freeze old Python transition references before the Android implementation.

Reads only the supplied old prototypes and writes only below this task directory.
No Kotlin implementation, private expected files, research data, or clock is used.
"""
from __future__ import annotations

import argparse
from dataclasses import replace
import hashlib
import gzip
import io
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True

SOURCE_CORE = "a5fda901bc0a8cca9ae0eefc1d076fd8171c73a6"
ROOT = Path(__file__).resolve().parent
MASK64 = (1 << 64) - 1
GAMMA = 0x9E3779B97F4A7C15
MUL1 = 0xBF58476D1CE4E5B9
MUL2 = 0x94D049BB133111EB
THRESHOLD = 4_294_967_200


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def encoded(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True,
                       separators=(",", ":"), allow_nan=False) + "\n").encode("utf-8")


def raw_step(state: int) -> tuple[int, int]:
    state = (state + GAMMA) & MASK64
    z = state
    z = ((z ^ (z >> 30)) * MUL1) & MASK64
    z = ((z ^ (z >> 27)) * MUL2) & MASK64
    return state, (z ^ (z >> 31)) & MASK64


def limb_multiply(a: int, b: int) -> int:
    """A second low-64 multiplication route using 32-bit limbs."""
    low = (a & 0xFFFFFFFF) * (b & 0xFFFFFFFF)
    cross = (a >> 32) * (b & 0xFFFFFFFF) + (a & 0xFFFFFFFF) * (b >> 32)
    return (low + ((cross & 0xFFFFFFFF) << 32)) % (1 << 64)


def raw_step_limb(state: int) -> tuple[int, int]:
    state = (state + GAMMA) % (1 << 64)
    z = state
    z = limb_multiply(z ^ (z // (1 << 30)), MUL1)
    z = limb_multiply(z ^ (z // (1 << 27)), MUL2)
    return state, z ^ (z // (1 << 31))


class Sm64:
    """Independent spec arithmetic, with a ToyPitchSource-compatible adapter."""
    def __init__(self, seed):
        self.state = seed
        self.raw_calls = self.draw_calls = self.rejections = 0

    def raw(self):
        before = self.state
        self.state, value = raw_step(before)
        assert (self.state, value) == raw_step_limb(before)
        self.raw_calls += 1
        return value

    def randrange(self, stop):
        if type(stop) is not int or stop != 100:
            raise ValueError("this reference adapter implements randrange(100) only")
        self.draw_calls += 1
        while True:
            u = self.raw() >> 32
            if u < THRESHOLD:
                return u % 100
            self.rejections += 1

    def observation(self):
        return {"state_u64": str(self.state), "raw_output_calls": self.raw_calls,
                "draw100_calls": self.draw_calls, "rejected_raw_outputs": self.rejections}


def rng_vectors():
    rows = []
    for seed in (0, 42, MASK64):
        state = seed
        raw_rows = []
        for index in range(1, 21):
            before = state
            state, raw = raw_step(state)
            assert (state, raw) == raw_step_limb(before)
            u = raw >> 32
            raw_rows.append({"raw_index": index, "state_u64": str(state),
                             "raw64_u64": str(raw), "raw64_hex": f"{raw:016x}",
                             "upper32": u, "accepted_for_draw100": u < THRESHOLD,
                             "draw100_if_accepted": u % 100 if u < THRESHOLD else None})
        adapter = Sm64(seed)
        draws = []
        for index in range(1, 21):
            value = adapter.randrange(100)
            draws.append({"draw_index": index, "value": value, **adapter.observation()})
        rows.append({"seed": str(seed), "raw_outputs": raw_rows, "draw100": draws})
    # This independently known first value also catches a wrong initial pre-mix.
    assert rows[0]["raw_outputs"][0]["raw64_hex"] == "e220a8397b1dcdaf"
    boundary = [{"upper32": u, "accepted": u < THRESHOLD,
                 "draw100": u % 100 if u < THRESHOLD else None}
                for u in (0, THRESHOLD - 1, THRESHOLD, (1 << 32) - 1)]
    forced = []
    for values in ([THRESHOLD - 1], [THRESHOLD, THRESHOLD - 1],
                   [(1 << 32) - 1, THRESHOLD, 0]):
        accepted = next(i for i, u in enumerate(values) if u < THRESHOLD)
        forced.append({"forced_upper32_sequence": values, "draw100": values[accepted] % 100,
                       "raw_outputs_consumed": accepted + 1, "rejections": accepted})
    assert (1 << 32) - THRESHOLD == 96 and THRESHOLD % 100 == 0
    return {"schema": "android-sm64-reference-v1", "algorithm": "sm64-v1",
            "state_semantics": "add gamma to persistent state; mix a copy; never store mixed output as state",
            "gamma_hex": f"{GAMMA:016x}", "multiply_constants_hex": [f"{MUL1:016x}", f"{MUL2:016x}"],
            "arithmetic": "unsigned 64-bit modulo 2^64; all shifts logical",
            "draw100_acceptance_threshold": THRESHOLD, "rejected_upper32_values": 96,
            "vectors": rows, "upper32_boundaries": boundary, "forced_rejection_examples": forced}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    repo = args.repo.resolve(strict=True)
    output = args.output.resolve()
    if output == ROOT or not output.is_relative_to(ROOT) or output.exists():
        raise ValueError("output must be a new child directory under this reference task root")
    sys.path.insert(0, str(repo))
    from prototypes.phase4f.engine import GameSession
    from prototypes.phase4f.protocol import FairPlay, PitchInput, RecordedAbort
    from prototypes.phase4f.source import ScriptedSource, ToyPitchSource, ToyProfile
    from prototypes.phase4f.state import GameSnapshot

    aliases = {"B": "called_ball", "C": "called_strike_caught", "M": "swinging_strike_caught",
               "T": "foul_tip_caught", "F": "ordinary_foul_uncaught", "U": "bunt_foul_uncaught",
               "O": "foul_fly_caught", "X": "fair_ball_handoff", "H": "fair_ball_handoff"}

    class Tokens:
        def __init__(self, values):
            self.values = list(values)
            self.position = 0

        def next_pitch(self, context):
            if self.position == len(self.values):
                raise StopIteration
            token = self.values[self.position]
            self.position += 1
            if not isinstance(token, str):
                return token
            if token in ("1", "2", "3"):
                h = int(token)
                dest = tuple(min(i + h, 4) if occupied else None
                             for i, occupied in enumerate(context.bases, 1))
                return PitchInput("fair_ball_handoff", FairPlay("safe_hit", h, dest))
            fair = FairPlay("out_hold") if token == "X" else FairPlay("home_run") if token == "H" else None
            return PitchInput(aliases[token], fair)

    def initial_from_prefix(tokens):
        session = GameSession(source=Tokens(tokens))
        for _ in tokens:
            assert not session.snapshot.done
            session.advance_one_pitch()
        assert not session.snapshot.done
        return replace(session.snapshot, history_origin="provided_snapshot")

    def ninth(away=0, home=0, bottom=True, setup=()):
        prefix = ["H"] * away + ["X"] * 3 + ["H"] * home + ["X"] * 3 + ["X"] * 42
        if bottom:
            prefix += ["X"] * 3
        prefix += list(setup)
        return initial_from_prefix(prefix)

    def capture(session):
        after, observations = [], []
        for _ in range(20000):
            if session.snapshot.done:
                break
            session.advance_one_pitch()
            after.append(session.snapshot.as_dict())
            if isinstance(session.source, ToyPitchSource):
                observation = {"pitch_calls": session.source.pitch_calls, "fair_calls": session.source.fair_calls,
                               "logical_draws": session.source.draw_count}
                if isinstance(session.source.rng, Sm64):
                    observation.update(session.source.rng.observation())
                observations.append(observation)
        else:
            raise AssertionError("reference case failed to stop")
        inputs = []
        for event in session.events:
            if event["type"] == "action" and event["action"]["kind"] == "pitch":
                inputs.append(event["input"])
            if event["type"] == "abort" and event["reason"] != "pitch_limit":
                inputs.append({"abort_reason": event["reason"]})
        return inputs, after, session.result().as_dict(), observations

    cases = []

    def add(identifier, description, *, seed=42, source=None, tokens=None, initial=None,
            max_pitches=10000, rng_kind="recorded-input-only", expected_reason=None):
        if source is None:
            source = Tokens(tokens or [])
        session = GameSession(seed, max_pitches, source, initial)
        inputs, after, result, observations = capture(session)
        if expected_reason is not None:
            assert result["reason"] == expected_reason, (identifier, result["reason"], expected_reason)
        values = [RecordedAbort(v["abort_reason"]) if "abort_reason" in v else PitchInput.from_mapping(v)
                  for v in inputs]
        replay = GameSession(seed, max_pitches, ScriptedSource(values), session.initial_snapshot)
        replay_inputs, replay_after, replay_result, _ = capture(replay)
        assert encoded((inputs, after, result)) == encoded((replay_inputs, replay_after, replay_result)), identifier
        for snapshot in [session.initial_snapshot.as_dict(), *after]:
            assert GameSnapshot.from_mapping(snapshot).as_dict() == snapshot
        case = {"id": identifier, "description": description, "seed": str(seed), "rng_kind": rng_kind,
                "max_pitches": max_pitches, "initial_snapshot": session.initial_snapshot.as_dict(),
                "inputs": inputs, "expected_after_each_advance": after, "expected_result": result}
        if observations:
            case["expected_rng_after_each_advance"] = observations
            case["expected_rng_final"] = observations[-1]
        cases.append(case)
        return case

    for seed in (0, 1, 2, 42, 2026):
        case = add(f"python_random_seed_{seed}", "Old Python toy source full game, then recorded-input replay.",
                   seed=seed, source=ToyPitchSource(seed, ToyProfile()), rng_kind="python-random-old-toy-v1")
        assert case["expected_result"]["status"] == "completed"
    for seed in (0, 42, 2026, MASK64):
        source = ToyPitchSource(seed, ToyProfile())
        source.rng = Sm64(seed)
        case = add(f"sm64_seed_{seed}", "Independent sm64-v1 adapter in the old toy source; not Python same-seed equivalence.",
                   seed=seed, source=source, rng_kind="sm64-v1")
        assert case["expected_result"]["status"] == "completed"
        assert case["expected_rng_final"]["draw100_calls"] == case["expected_rng_final"]["logical_draws"]

    add("all_out_54_draw", "54 first-pitch fair outs; full nine-inning draw.", tokens=["X"] * 54,
        expected_reason="tie_after_nine")
    add("all_out_54_cap_completed", "Completion on the budget pitch takes priority over pitch_limit.",
        tokens=["X"] * 54, max_pitches=54, expected_reason="tie_after_nine")
    add("batting_order_carry", "Four early first-pitch outs cross the half boundary; subsequent PA order is retained.",
        tokens=["X"] * 7, expected_reason="source_exhausted")
    add("counts_all_eight_events", "Two-strike ordinary fouls, tip K, walk, bunt K, swinging K and caught foul.",
        tokens=list("BCFFTBBBBUUUMMMO"), expected_reason="source_exhausted")
    add("long_two_strike_fouls", "A hundred ordinary fouls after two strikes do not complete the PA.",
        tokens=["C", "C"] + ["F"] * 100 + ["T"], expected_reason="source_exhausted")

    masks_to_prefix = {0: [], 1: ["1"], 2: ["2"], 3: ["1", "1"],
                       4: ["3"], 5: ["2", "1"], 6: ["1", "2"], 7: ["1", "1", "1"]}
    for mask, prefix in masks_to_prefix.items():
        start = initial_from_prefix(prefix + ["B"] * 3)
        assert start.sequence.current.half.bases == tuple(bool(mask & (1 << i)) for i in range(3))
        case = add(f"ordinary_walk_mask_{mask}", "Only forced runners advance; only loaded bases score.",
                   initial=start, tokens=["B"], max_pitches=start.pitches_accepted + 1,
                   expected_reason="pitch_limit")
        a, b, c = (bool(mask & (1 << i)) for i in range(3))
        end_half = case["expected_after_each_advance"][0]["sequence"]["current"]["half"]
        assert end_half["bases"] == [True, a or b, c or (a and b)]
        assert end_half["runs"] - start.sequence.current.half.runs == int(a and b and c)

    for name, mask, h, dest in (
        ("safe_single_r2_r3_hold", 6, 1, (None, 2, 3)),
        ("safe_double_r3_hold", 4, 2, (None, None, 3)),
        ("safe_single_two_runs", 7, 1, (2, 4, 4)),
        ("safe_double_two_runs", 7, 2, (3, 4, 4)),
        ("safe_triple_three_runs", 7, 3, (4, 4, 4)),
    ):
        start = initial_from_prefix(masks_to_prefix[mask])
        add(name, "Externally adjudicated all-safe final destinations; no automatic universal advancement.",
            initial=start, tokens=[PitchInput("fair_ball_handoff", FairPlay("safe_hit", h, dest))],
            max_pitches=start.pitches_accepted + 1, expected_reason="pitch_limit")

    start = ninth(setup=["1", "1", "1", "B", "B", "B"])
    add("walkoff_walk", "Loaded walk wins by one; old unresolved projection plus terminal delta.",
        initial=start, tokens=["B"], expected_reason="walkoff_walk")
    add("walkoff_walk_cap_completed", "Walk-off on the cap pitch completes rather than aborts.",
        initial=start, tokens=["B"], max_pitches=start.pitches_accepted + 1, expected_reason="walkoff_walk")
    for h in (1, 2, 3):
        add(f"walkoff_safe_{h}_minimum_one", "Candidate may score multiple runs, but only the one needed is added.",
            initial=ninth(setup=["1"] * 3), tokens=[str(h)], expected_reason="walkoff_safe_hit")
    for h in (2, 3):
        add(f"walkoff_safe_{h}_needed_{h}", "Winning safe hit needs two or three runs in lead-runner order.",
            initial=ninth(away=h - 1, setup=["1"] * 3), tokens=[str(h)], expected_reason="walkoff_safe_hit")
    add("walkoff_hr_empty", "Walk-off HR includes the batter.", initial=ninth(), tokens=["H"],
        expected_reason="walkoff_home_run")
    add("walkoff_hr_loaded_all_four", "All four HR runs count even though fewer are needed to win.",
        initial=ninth(away=2, setup=["1"] * 3), tokens=["H"], expected_reason="walkoff_home_run")
    add("bottom9_safe_hit_not_yet_winning", "Nonwinning ninth-inning hit uses the ordinary old resolution.",
        initial=ninth(away=3, setup=["1"] * 3), tokens=["1", "X", "X", "X"],
        expected_reason="away_lead_after_bottom9")
    add("top9_home_lead_skip_bottom", "Final top-nine out skips the unreached bottom half.",
        initial=ninth(home=1, bottom=False, setup=["X"] * 2), tokens=["X"],
        expected_reason="home_lead_after_top9")
    add("bottom9_away_win", "Final bottom-nine out ends the game with an away lead.",
        initial=ninth(away=1, setup=["X"] * 2), tokens=["X"], expected_reason="away_lead_after_bottom9")

    add("cap_after_one_ball", "Budget exhaustion after a nonterminal pitch, with active count preserved.",
        tokens=["B", "C"], max_pitches=1, expected_reason="pitch_limit")
    start = initial_from_prefix(["X", "X"])
    case = add("cap_third_out_before_advance", "Budget-pitch third out retains the closed half; no Advance follows.",
               initial=start, tokens=["X"], max_pitches=3, expected_reason="pitch_limit")
    end = case["expected_after_each_advance"][0]["sequence"]
    assert end["inning"] == 1 and end["side"] == "top" and end["current"]["half"]["outs"] == 3
    start = initial_from_prefix(["B"])
    add("cap_already_used_no_source", "An already-used cap stops before Begin/source calls.",
        initial=start, tokens=["C"], max_pitches=1, expected_reason="pitch_limit")
    add("source_empty_begin_retained", "No pitch input: Begin is accepted and retained before source exhaustion.",
        tokens=[], expected_reason="source_exhausted")
    add("source_short_after_ball", "A valid prefix is retained; the next source shortage accepts no pitch.",
        tokens=["B"], expected_reason="source_exhausted")
    add("missing_fair_unaccepted", "Missing fair payload is sanitized, and Pitch is not applied.",
        tokens=[{"event": "fair_ball_handoff", "fair": None, "pitch_draw": None, "fair_draw": None}],
        expected_reason="invalid_fair_result")
    start = initial_from_prefix(["1"])
    add("fair_occupancy_invalid_unaccepted", "Typed fair payload contradicts an occupied base; no Pitch or PA is accepted.",
        initial=start, tokens=[PitchInput("fair_ball_handoff", FairPlay("safe_hit", 2, (None, None, None)))],
        expected_reason="invalid_fair_result")
    add("fair_collision_invalid_unaccepted", "Batter and runner both at first is rejected before Pitch.",
        initial=start, tokens=[PitchInput("fair_ball_handoff", FairPlay("safe_hit", 1, (1, None, None)))],
        expected_reason="invalid_fair_result")
    add("unknown_pitch_unaccepted", "Unknown pitch is represented only by a sanitized abort marker.",
        tokens=[{"event": "unknown", "fair": None, "pitch_draw": None, "fair_draw": None}],
        expected_reason="invalid_pitch_input")
    add("source_error_marker", "Recorded source failure has no arbitrary object serialization.",
        tokens=[RecordedAbort("source_error")], expected_reason="source_error")

    fixtures = {"schema": "android-transition-reference-v1",
                "reference_kind": "old-Python information-contract oracle, independent of future Kotlin implementation; not a separate baseball-rules proof",
                "advance_semantics": "one actual GameSession.advance_one_pitch call, including its automatic half advance and sanitized abort",
                "input_semantics": "PitchInput.as_dict or exactly {abort_reason: source reason}; no pitch_limit input marker; all declared inputs are consumed",
                "snapshot_semantics": "GameSnapshot.as_dict; provided starts are derived from legal synthetic old-controller prefixes; no old capability flags required",
                "same_seed_warning": "python-random-old-toy-v1 and sm64-v1 are different RNG contracts; do not compare their same-seed scores",
                "cases": cases}
    summaries = [{"id": c["id"], "rng_kind": c["rng_kind"], "seed": c["seed"],
                  "advances": len(c["expected_after_each_advance"]), "inputs": len(c["inputs"]),
                  "status": c["expected_result"]["status"], "reason": c["expected_result"]["reason"],
                  "score": c["expected_result"]["score"], "pitches_accepted": c["expected_result"]["pitches_accepted"],
                  **({"rng_final": c["expected_rng_final"]} if "expected_rng_final" in c else {})}
                 for c in cases]
    source_records = []
    for module in sorted(sys.modules.values(), key=lambda m: getattr(m, "__name__", "")):
        filename = getattr(module, "__file__", None)
        if not filename:
            continue
        path = Path(filename).resolve()
        if not path.is_relative_to(repo / "prototypes"):
            continue
        relative = path.relative_to(repo).as_posix()
        if relative != "prototypes/__init__.py" and not relative.startswith(tuple(f"prototypes/phase4{x}/" for x in "abcdef")):
            raise AssertionError("unexpected old prototype import: " + relative)
        blob = subprocess.run(["git", "-C", str(repo), "show", f"{SOURCE_CORE}:{relative}"],
                              check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
        assert path.read_bytes().replace(b"\r\n", b"\n") == blob.replace(b"\r\n", b"\n"), relative
        source_records.append({"path": relative, "sha256_lf": digest(blob.replace(b"\r\n", b"\n"))})
    provenance = {"schema": "android-reference-provenance-v1", "old_source_core": SOURCE_CORE,
                  "generator_sha256": digest(Path(__file__).read_bytes()),
                  "python_implementation": sys.implementation.name, "python_version": sys.version.split()[0],
                  "old_imported_source_files": source_records,
                  "future_kotlin_or_worker_read": False, "manager_private_reference_read": False,
                  "research_raw_or_model_read": False, "recorded_replays_all_match": True,
                  "sm64_mask_and_limb_routes_agree": True, "case_count": len(cases),
                  "advance_count": sum(len(c["expected_after_each_advance"]) for c in cases),
                  "sm64_full_game_cases": 4, "python_random_full_game_cases": 5}
    output.mkdir(parents=True, exist_ok=False)
    for name, value in (("transition_cases.json", fixtures), ("sm64_vectors.json", rng_vectors()),
                        ("case_summary.json", summaries), ("provenance.json", provenance)):
        (output / name).write_bytes(encoded(value))
    compressed = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", compresslevel=9, fileobj=compressed, mtime=0) as stream:
        stream.write((output / "transition_cases.json").read_bytes())
    assert gzip.decompress(compressed.getvalue()) == (output / "transition_cases.json").read_bytes()
    (output / "transition_cases.json.gz").write_bytes(compressed.getvalue())
    manifest = {path.name: {"bytes": path.stat().st_size, "sha256": digest(path.read_bytes())}
                for path in sorted(output.iterdir())}
    (output / "SHA256_MANIFEST.json").write_bytes(encoded(manifest))
    print(json.dumps({"output": str(output), "cases": len(cases), "advances": provenance["advance_count"],
                      "files": manifest}, ensure_ascii=False))


if __name__ == "__main__":
    main()
