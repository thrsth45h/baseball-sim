"""Pure partial base/out projection. Preconditions are supplied facts."""
from dataclasses import dataclass
from collections.abc import Mapping

EVENTS = ("walk_forced_only", "batter_out_runners_hold", "legal_over_fence_home_run")


@dataclass(frozen=True, slots=True)
class HalfInningState:
    outs: int = 0
    bases: tuple[bool, bool, bool] = (False, False, False)
    runs: int = 0

    def __post_init__(self):
        if type(self.outs) is not int or not 0 <= self.outs <= 3:
            raise ValueError("outs must be an exact integer from 0 through 3")
        if type(self.bases) is not tuple or len(self.bases) != 3 or any(type(b) is not bool for b in self.bases):
            raise ValueError("bases must be an exact three-boolean tuple, in first/second/third order")
        if type(self.runs) is not int or self.runs < 0:
            raise ValueError("runs must be a nonnegative exact integer")

    @property
    def half_complete(self):
        return self.outs == 3

    @classmethod
    def from_mapping(cls, value):
        if not isinstance(value, Mapping) or set(value) != {"outs", "bases", "runs"}:
            raise ValueError("state JSON requires exactly outs/bases/runs")
        if type(value["bases"]) is not list:
            raise ValueError("state JSON bases must be an array; constructor bases must be a tuple")
        return cls(value["outs"], tuple(value["bases"]), value["runs"])

    def as_dict(self):
        return {"outs": self.outs, "bases": list(self.bases), "runs": self.runs}


def _state(value):
    if type(value) is not HalfInningState:
        raise ValueError("explicit HalfInningState required; no Phase4a conversion")


@dataclass(frozen=True, slots=True)
class TraceEntry:
    event: str
    before: HalfInningState
    after: HalfInningState
    runs_added: int

    def __post_init__(self):
        _state(self.before)
        _state(self.after)
        if type(self.event) is not str or self.event not in EVENTS:
            raise ValueError("unsupported completed play")
        if type(self.runs_added) is not int or self.runs_added != self.after.runs-self.before.runs or self.runs_added < 0:
            raise ValueError("trace runs_added must equal the exact nonnegative run difference")

    def as_dict(self):
        return {"event": self.event, "before": self.before.as_dict(),
                "after": self.after.as_dict(), "runs_added": self.runs_added}


@dataclass(frozen=True, slots=True)
class ReplayResult:
    final_state: HalfInningState
    trace: tuple[TraceEntry, ...]

    def __post_init__(self):
        _state(self.final_state)
        if type(self.trace) is not tuple or any(type(entry) is not TraceEntry for entry in self.trace):
            raise ValueError("trace must be an immutable tuple of TraceEntry values")

    def as_dict(self):
        return {"final_state": self.final_state.as_dict(), "trace": [entry.as_dict() for entry in self.trace]}


def step(state, event):
    """Apply one completed play, without predicting or inferring its facts."""
    _state(state)
    if type(event) is not str or event not in EVENTS:
        raise ValueError("unsupported or ambiguous completed play")
    if state.half_complete:
        raise ValueError("half inning has stopped; construct the next start explicitly")
    a, b, c = state.bases
    if event == "walk_forced_only":
        return HalfInningState(state.outs, (True, a or b, c or (a and b)), state.runs+int(a and b and c))
    if event == "batter_out_runners_hold":
        return HalfInningState(state.outs+1, state.bases, state.runs)
    return HalfInningState(state.outs, (False, False, False), state.runs+1+sum(state.bases))


def replay(start_state, events):
    """Replay a finite explicit sequence; expose no partial result on failure."""
    _state(start_state)
    if type(events) not in (list, tuple):
        raise ValueError("events require an exact finite list or tuple")
    current, trace = start_state, []
    for event in tuple(events):
        following = step(current, event)
        trace.append(TraceEntry(event, current, following, following.runs-current.runs))
        current = following
    return ReplayResult(current, tuple(trace))
