"""Fixed design tables/columns. Pins are written independently of step/replay."""
import json
from pathlib import Path
from types import MappingProxyType
from . import DESIGN
from .model import PitchHalfState, action_from_mapping

FIXTURE_ROOT = Path(__file__).resolve().parent/"fixtures"
_PITCHES = MappingProxyType(dict(zip("BCMTFUOP", (
    "called_ball", "called_strike_caught", "swinging_strike_caught", "foul_tip_caught",
    "ordinary_foul_uncaught", "bunt_foul_uncaught", "foul_fly_caught", "fair_ball_handoff"))))
_PLAYS = MappingProxyType({"RW": "walk_forced_only", "RX": "batter_out_runners_hold", "RH": "legal_over_fence_home_run"})
_COUNTS = MappingProxyType({"A0": (0, 0, 0, "active"), "A1": (1, 0, 1, "active"),
    "AW": (4, 0, 4, "walk_awarded"), "AK": (0, 3, 3, "strikeout_out"),
    "AP": (0, 0, 1, "fair_play_pending"), "AF": (0, 2, 5, "active")})
# Each row is independently transcribed from approved design section 6.
_COLUMNS = (
    ("empty_between", 0, "000", 0, "", None, 0, "000", 0, "between_batters"),
    ("begin_only", 0, "000", 0, "G", "A0", 0, "000", 0, "pitching"),
    ("one_ball", 0, "000", 0, "G B", "A1", 0, "000", 0, "pitching"),
    ("walk_await", 0, "000", 0, "G B B B B", "AW", 0, "000", 0, "awaiting_resolution"),
    ("walk_applied", 0, "000", 0, "G B B B B RW", None, 0, "100", 0, "between_batters"),
    ("second_base_walk", 0, "010", 0, "G B B B B RW", None, 0, "110", 0, "between_batters"),
    ("loaded_walk", 2, "111", 7, "G B B B B RW", None, 2, "111", 8, "between_batters"),
    ("strikeout_await", 0, "000", 0, "G C C C", "AK", 0, "000", 0, "awaiting_resolution"),
    ("strikeout_hold", 0, "101", 2, "G M M M RX", None, 1, "101", 2, "between_batters"),
    ("third_out_strikeout", 2, "111", 7, "G C C C RX", None, 3, "111", 7, "half_complete"),
    ("caught_foul_out", 1, "001", 0, "G O RX", None, 2, "001", 0, "between_batters"),
    ("fair_await", 0, "010", 7, "G P", "AP", 0, "010", 7, "awaiting_resolution"),
    ("fair_out", 0, "101", 0, "G P RX", None, 1, "101", 0, "between_batters"),
    ("grand_slam", 1, "111", 7, "G P RH", None, 1, "000", 11, "between_batters"),
    ("next_batter", 0, "000", 0, "G B B B B RW G", "A0", 0, "100", 0, "pitching"),
    ("three_retired", 0, "000", 0, "G C C C RX G M M M RX G O RX", None, 3, "000", 0, "half_complete"),
    ("ordinary_fouls", 0, "000", 0, "G C M F F F", "AF", 0, "000", 0, "pitching"),
    ("bunt_strikeout", 0, "000", 0, "G U U U RX", None, 1, "000", 0, "between_batters"),
)


def parse_json(text):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result: raise ValueError("duplicate JSON key: "+key)
            result[key] = value
        return result
    def reject(value): raise ValueError("non-finite JSON constant: "+value)
    return json.loads(text, object_pairs_hook=pairs, parse_constant=reject)


def load(name, key):
    value = parse_json((FIXTURE_ROOT/name).read_text(encoding="utf-8"))
    if type(value) is not dict or set(value) != {"design", key} or value["design"] != DESIGN or type(value[key]) is not list:
        raise ValueError("invalid fixed fixture document")
    return value[key]


def _state(count, outs, bits, runs):
    value = None if count is None else dict(zip(("balls", "strikes", "pitches_seen", "status"), _COUNTS[count]))
    return {"count": value, "half": {"outs": outs, "bases": [b == "1" for b in bits], "runs": runs}}


def _action(token):
    if token == "G": return {"kind": "begin_batter"}
    if token in _PITCHES: return {"kind": "pitch", "event": _PITCHES[token]}
    return {"kind": "resolve", "completed_play": _PLAYS[token]}


def scenarios():
    rows = load("scenarios.json", "scenarios")
    if len(rows) != 18: raise ValueError("fixed scenario count changed")
    for row, (name, o, b, r, actions, fc, fo, fb, fr, stage) in zip(rows, _COLUMNS):
        if type(row) is not dict or set(row) != {"id", "initial_state", "actions", "expected_final", "expected_stage"}:
            raise ValueError("fixed scenario fields changed")
        before = PitchHalfState.from_mapping(row["initial_state"]).as_dict()
        after = PitchHalfState.from_mapping(row["expected_final"]).as_dict()
        if type(row["actions"]) is not list: raise ValueError("fixed actions require an array")
        actual_actions = [action_from_mapping(action).as_dict() for action in row["actions"]]
        if (row["id"] != name or type(row["id"]) is not str or before != _state(None, o, b, r)
                or actual_actions != [_action(a) for a in actions.split()] or after != _state(fc, fo, fb, fr)
                or row["expected_stage"] != stage or type(row["expected_stage"]) is not str):
            raise ValueError("fixed scenario differs from approved design columns")
    return rows


def control_table():
    rows = load("control.json", "control")
    expected = []
    for stage in ("between_batters", "pitching", "awaiting_resolution", "half_complete"):
        for kind in ("begin_batter", "pitch", "resolve"):
            effect = {("between_batters", "begin_batter"): "start_count", ("pitching", "pitch"): "delegate_count",
                      ("awaiting_resolution", "resolve"): "check_compatibility_then_delegate_half"}.get((stage, kind), "reject")
            expected.append({"stage": stage, "kind": kind, "effect": effect})
    if rows != expected: raise ValueError("fixed control table differs from approved 12 cells")
    return rows


def compatibility_table():
    rows = load("compatibility.json", "compatibility")
    permitted = {("walk_awarded", "walk_forced_only"), ("strikeout_out", "batter_out_runners_hold"),
                 ("caught_foul_out", "batter_out_runners_hold"), ("fair_play_pending", "batter_out_runners_hold"),
                 ("fair_play_pending", "legal_over_fence_home_run")}
    expected = [{"status": status, "completed_play": play, "allowed": (status, play) in permitted}
                for status in ("walk_awarded", "strikeout_out", "caught_foul_out", "fair_play_pending")
                for play in ("walk_forced_only", "batter_out_runners_hold", "legal_over_fence_home_run")]
    if rows != expected or any(type(row.get("allowed")) is not bool for row in rows):
        raise ValueError("fixed compatibility table differs from approved 12 cells")
    return rows
