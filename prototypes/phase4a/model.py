"""Pure partial rule projection; event facts are inputs, never predictions."""
from dataclasses import asdict, dataclass, fields
from typing import Mapping

EVENTS = (
    "called_ball", "called_strike_caught", "swinging_strike_caught",
    "foul_tip_caught", "ordinary_foul_uncaught", "bunt_foul_uncaught",
    "foul_fly_caught", "fair_ball_handoff",
)
STATUSES = ("active", "walk_awarded", "strikeout_out", "caught_foul_out", "fair_play_pending")
STRIKE_EVENTS = frozenset(("called_strike_caught", "swinging_strike_caught",
                           "foul_tip_caught", "bunt_foul_uncaught"))


@dataclass(frozen=True, slots=True)
class CountState:
    balls: int = 0
    strikes: int = 0
    pitches_seen: int = 0
    status: str = "active"

    def __post_init__(self):
        b, s, n = self.balls, self.strikes, self.pitches_seen
        if any(type(value) is not int for value in (b, s, n)):
            raise ValueError("count fields require exact integers; no bool/float/string coercion")
        if type(self.status) is not str or self.status not in STATUSES or n < 0:
            raise ValueError("unknown status or negative accepted-event count")
        if self.status == "active":
            valid = 0 <= b <= 3 and 0 <= s <= 2 and (n == b+s if s < 2 else n >= b+2)
        elif self.status == "walk_awarded":
            valid = b == 4 and 0 <= s <= 2 and (n == 4+s if s < 2 else n >= 6)
        elif self.status == "strikeout_out":
            valid = 0 <= b <= 3 and s == 3 and n >= b+3
        else:
            valid = 0 <= b <= 3 and 0 <= s <= 2 and (n == b+s+1 if s < 2 else n >= b+3)
        if not valid:
            raise ValueError("state is inconsistent with the approved eight-event subset")

    @classmethod
    def from_mapping(cls, value):
        if not isinstance(value, Mapping) or set(value) != {f.name for f in fields(cls)}:
            raise ValueError("CountState requires exactly balls/strikes/pitches_seen/status")
        return cls(**value)

    def as_dict(self):
        return asdict(self)


@dataclass(frozen=True, slots=True)
class TraceEntry:
    ordinal: int
    event: str
    before: CountState
    after: CountState

    def as_dict(self):
        return {"ordinal": self.ordinal, "event": self.event,
                "before": self.before.as_dict(), "after": self.after.as_dict()}


@dataclass(frozen=True, slots=True)
class ReplayResult:
    final_state: CountState
    trace: tuple[TraceEntry, ...]

    def as_dict(self):
        return {"final_state": self.final_state.as_dict(),
                "trace": [entry.as_dict() for entry in self.trace]}


def _state(value):
    if type(value) is not CountState:
        raise ValueError("explicit CountState required")


def step(state, event):
    """Consume one adjudicated pitch, with no runner/scoring side effects."""
    _state(state)
    if type(event) is not str or event not in EVENTS:
        raise ValueError("unsupported or ambiguous pitch event")
    if state.status != "active":
        raise ValueError("this count projection has stopped; start a new subject explicitly")
    b, s, n, status = state.balls, state.strikes, state.pitches_seen+1, state.status
    if event == "called_ball":
        b += 1
        if b == 4:
            status = "walk_awarded"
    elif event in STRIKE_EVENTS:
        s += 1
        if s == 3:
            status = "strikeout_out"
    elif event == "ordinary_foul_uncaught":
        s = min(2, s+1)
    elif event == "foul_fly_caught":
        status = "caught_foul_out"
    else:  # The validated remaining event is fair_ball_handoff.
        status = "fair_play_pending"
    return CountState(b, s, n, status)


def replay(start_state, events):
    """Finite in-memory replay, without silently truncating an invalid tail."""
    _state(start_state)
    if type(events) not in (list, tuple):
        raise ValueError("pitch sequence must be a finite list or tuple")
    current, trace = start_state, []
    for event in tuple(events):
        following = step(current, event)
        trace.append(TraceEntry(current.pitches_seen+1, event, current, following))
        current = following
    return ReplayResult(current, tuple(trace))
