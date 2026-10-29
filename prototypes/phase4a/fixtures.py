"""Strict readers for the fixed language-independent synthetic fixtures."""
import json
from pathlib import Path
from types import MappingProxyType

from . import DESIGN, SCHEMA
from .model import CountState, EVENTS

FIXTURE_ROOT = Path(__file__).resolve().parent / "fixtures"
SCENARIO_IDS = (
    "empty", "one_ball", "one_called_strike", "four_balls", "mixed_walk",
    "called_strikeout", "swinging_strikeout", "foul_tip_strikeout", "fouls_to_two",
    "two_strike_fouls", "three_bunt_fouls", "bunt_foul_after_two", "caught_foul_first",
    "caught_foul_after_two", "fair_full_count", "long_foul_run",
)
# Independently transcribed from approved design section 6, never from replay.
# Event names are explicit here so a model event-order change cannot relabel it.
_DESIGN_ALIASES = MappingProxyType({
    "B": "called_ball", "C": "called_strike_caught", "M": "swinging_strike_caught",
    "T": "foul_tip_caught", "F": "ordinary_foul_uncaught", "U": "bunt_foul_uncaught",
    "O": "foul_fly_caught", "P": "fair_ball_handoff",
})
_DESIGN_SEQUENCES = ("", "B", "C", "BBBB", "CBMBFBB", "CCC", "MMM", "CMT", "FF",
                     "CMFFF", "UUU", "CMU", "O", "UUO", "BBBCMP", "CM"+"F"*32)
APPROVED_SCENARIO_EVENTS = tuple(tuple(_DESIGN_ALIASES[code] for code in sequence)
                                 for sequence in _DESIGN_SEQUENCES)


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate fixture object field")
        result[key] = value
    return result


def read_json(path):
    def reject(value):
        raise ValueError("non-finite JSON constant: " + value)
    return json.loads(Path(path).read_text(encoding="utf-8"),
                      parse_constant=reject, object_pairs_hook=_unique)


def _document(name, collection):
    document = read_json(FIXTURE_ROOT/name)
    if (type(document) is not dict or set(document) != {"design", "schema", "source_kind", collection}
            or document["design"] != DESIGN or document["schema"] != SCHEMA
            or document["source_kind"] != "synthetic_fixture" or type(document[collection]) is not list):
        raise ValueError("unexpected fixed fixture document")
    return document[collection]


def transition_table():
    rows = _document("transitions.json", "transitions")
    expected = [(b, s, event) for b in range(4) for s in range(3) for event in EVENTS]
    observed = []
    for row in rows:
        if type(row) is not dict or set(row) != {"before", "event", "after"}:
            raise ValueError("unexpected transition row fields")
        before, after = CountState.from_mapping(row["before"]), CountState.from_mapping(row["after"])
        if (before.status != "active" or before.pitches_seen != before.balls+before.strikes
                or type(row["event"]) is not str or row["event"] not in EVENTS
                or after.pitches_seen != before.pitches_seen+1):
            raise ValueError("transition is outside the canonical active-count table")
        observed.append((before.balls, before.strikes, row["event"]))
    if observed != expected:
        raise ValueError("fixture must contain exactly the ordered 12 x 8 transitions")
    return rows


def scenarios():
    rows = _document("scenarios.json", "scenarios")
    for row in rows:
        if type(row) is not dict or set(row) != {"id", "events", "expected_final"}:
            raise ValueError("unexpected scenario fields")
        if (type(row["events"]) is not list or any(type(event) is not str or event not in EVENTS for event in row["events"])
                or type(row["id"]) is not str):
            raise ValueError("scenario requires fixed explicit event strings")
        CountState.from_mapping(row["expected_final"])
    if [row["id"] for row in rows] != list(SCENARIO_IDS):
        raise ValueError("fixture must contain exactly the approved sixteen scenarios")
    if tuple(tuple(row["events"]) for row in rows) != APPROVED_SCENARIO_EVENTS:
        raise ValueError("fixed scenario events differ from the approved sixteen sequences")
    return rows
