"""Language-neutral fixed inputs, with independent design-column pins."""
import json
from pathlib import Path
from types import MappingProxyType

from . import DESIGN
from .model import HalfInningState

FIXTURE_ROOT = Path(__file__).resolve().parent/"fixtures"
# Written from design sections 4/6, not step/replay output or model.EVENTS.
ALIASES = MappingProxyType({"W": "walk_forced_only", "X": "batter_out_runners_hold",
                            "H": "legal_over_fence_home_run"})
_COLUMNS = (
    ("empty", 0, "000", 0, "", 0, "000", 0),
    ("walk_empty", 0, "000", 0, "W", 0, "100", 0),
    ("walk_only_second", 0, "010", 0, "W", 0, "110", 0),
    ("walk_only_third", 0, "001", 0, "W", 0, "101", 0),
    ("walk_first_third", 0, "101", 0, "W", 0, "111", 0),
    ("walk_second_third", 1, "011", 7, "W", 1, "111", 7),
    ("walk_loaded", 2, "111", 7, "W", 2, "111", 8),
    ("out_holds_runners", 0, "101", 2, "X", 1, "101", 2),
    ("third_out_strands", 2, "111", 7, "X", 3, "111", 7),
    ("solo_home_run", 2, "000", 7, "H", 2, "000", 8),
    ("two_run_home_run", 0, "010", 0, "H", 0, "000", 2),
    ("grand_slam", 1, "111", 0, "H", 1, "000", 4),
    ("four_walks", 0, "000", 0, "WWWW", 0, "111", 1),
    ("walks_then_home_run", 0, "000", 0, "WWWWH", 0, "000", 5),
    ("three_outs", 0, "000", 0, "XXX", 3, "000", 0),
    ("mixed_half", 0, "000", 0, "WWXHWXX", 3, "100", 3),
)


def parse_json(text):
    """Reject duplicate object keys and non-finite JSON constants at any depth."""
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("duplicate JSON key: "+key)
            result[key] = value
        return result
    def reject(value):
        raise ValueError("non-finite JSON constant: "+value)
    return json.loads(text, object_pairs_hook=pairs, parse_constant=reject)


def load(name, key):
    value = parse_json((FIXTURE_ROOT/name).read_text(encoding="utf-8"))
    if type(value) is not dict or set(value) != {"design", key} or value["design"] != DESIGN or type(value[key]) is not list:
        raise ValueError("invalid fixed fixture document")
    return value[key]


def _json_state(outs, bits, runs):
    return {"outs": outs, "bases": [b == "1" for b in bits], "runs": runs}


def scenarios():
    rows = load("scenarios.json", "scenarios")
    if len(rows) != len(_COLUMNS):
        raise ValueError("fixed scenario count changed")
    for row, (name, o, b, r, events, fo, fb, fr) in zip(rows, _COLUMNS):
        if type(row) is not dict or set(row) != {"id", "initial_state", "events", "expected_final"}:
            raise ValueError("invalid fixed scenario keys")
        before = HalfInningState.from_mapping(row["initial_state"]).as_dict()
        after = HalfInningState.from_mapping(row["expected_final"]).as_dict()
        if (type(row["id"]) is not str or row["id"] != name or before != _json_state(o, b, r)
                or after != _json_state(fo, fb, fr) or type(row["events"]) is not list
                or any(type(e) is not str for e in row["events"])
                or row["events"] != [ALIASES[e] for e in events]):
            raise ValueError("fixed scenario differs from approved design columns")
    return rows


def transition_table():
    rows = load("transitions.json", "transitions")
    keys = set()
    for row in rows:
        if type(row) is not dict or set(row) != {"before", "event", "after", "runs_added"}:
            raise ValueError("invalid transition keys")
        before, after = (HalfInningState.from_mapping(row[k]) for k in ("before", "after"))
        if (before.outs == 3 or before.runs != 0 or type(row["event"]) is not str
                or row["event"] not in ALIASES.values() or type(row["runs_added"]) is not int
                or row["runs_added"] < 0 or row["runs_added"] != after.runs):
            raise ValueError("invalid transition state/event/run increment")
        key = (before.outs, before.bases, row["event"])
        if key in keys:
            raise ValueError("duplicate transition key")
        keys.add(key)
    if len(keys) != 72:
        raise ValueError("fixed transition table requires all 72 distinct active-state/event pairs")
    return rows
