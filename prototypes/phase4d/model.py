"""Half-boundary bookkeeping only; all old actions delegate to frozen Phase4c."""
from dataclasses import dataclass
from prototypes.phase4c import model as pitch_half_rules

PitchHalfState = pitch_half_rules.PitchHalfState
HalfInningState = pitch_half_rules.HalfInningState
BeginBatter, Pitch, Resolve = pitch_half_rules.BeginBatter, pitch_half_rules.Pitch, pitch_half_rules.Resolve


@dataclass(frozen=True, slots=True)
class HalfSequenceState:
    inning: int
    side: str
    closed_away_runs: int
    closed_home_runs: int
    current: PitchHalfState

    def __post_init__(self):
        if type(self.inning) is not int or self.inning < 1: raise ValueError("inning requires an exact positive integer")
        if type(self.side) is not str or self.side not in ("top", "bottom"): raise ValueError("side requires exact top/bottom")
        if any(type(v) is not int or v < 0 for v in (self.closed_away_runs, self.closed_home_runs)):
            raise ValueError("closed scores require nonnegative exact integers")
        if type(self.current) is not PitchHalfState: raise ValueError("current requires exact old PitchHalfState")

    @property
    def batting_team(self): return "away" if self.side == "top" else "home"

    @property
    def stage(self): return self.current.stage

    @property
    def score(self):
        return {"away": self.closed_away_runs+(self.current.half.runs if self.side == "top" else 0),
                "home": self.closed_home_runs+(self.current.half.runs if self.side == "bottom" else 0)}

    @classmethod
    def from_mapping(cls, value):
        if type(value) is not dict or set(value) != {"inning", "side", "closed_away_runs", "closed_home_runs", "current"}:
            raise ValueError("state JSON requires exactly the five stored fields")
        return cls(value["inning"], value["side"], value["closed_away_runs"], value["closed_home_runs"], PitchHalfState.from_mapping(value["current"]))

    def as_dict(self):
        return {"inning": self.inning, "side": self.side, "closed_away_runs": self.closed_away_runs,
                "closed_home_runs": self.closed_home_runs, "current": self.current.as_dict()}


@dataclass(frozen=True, slots=True)
class AdvanceHalf:
    def as_dict(self): return {"kind": "advance_half"}


def action_from_mapping(value):
    if type(value) is not dict or type(value.get("kind")) is not str: raise ValueError("action requires an exact object and string kind")
    if value["kind"] == "advance_half":
        if set(value) != {"kind"}: raise ValueError("AdvanceHalf JSON has no payload")
        return AdvanceHalf()
    return pitch_half_rules.action_from_mapping(value)


def _state(value):
    if type(value) is not HalfSequenceState: raise ValueError("explicit exact HalfSequenceState required")


def _action(value):
    if type(value) not in (BeginBatter, Pitch, Resolve, AdvanceHalf): raise ValueError("explicit exact approved action required")


@dataclass(frozen=True, slots=True)
class TraceEntry:
    action: BeginBatter | Pitch | Resolve | AdvanceHalf
    before: HalfSequenceState
    after: HalfSequenceState
    away_runs_added: int
    home_runs_added: int

    def __post_init__(self):
        _action(self.action); _state(self.before); _state(self.after)
        for key, value in (("away", self.away_runs_added), ("home", self.home_runs_added)):
            if type(value) is not int or value < 0 or value != self.after.score[key]-self.before.score[key]:
                raise ValueError("trace increments require exact nonnegative derived score differences")

    def as_dict(self):
        return {"action": self.action.as_dict(), "before": self.before.as_dict(), "after": self.after.as_dict(),
                "away_runs_added": self.away_runs_added, "home_runs_added": self.home_runs_added}


@dataclass(frozen=True, slots=True)
class ReplayResult:
    final_state: HalfSequenceState
    trace: tuple[TraceEntry, ...]

    def __post_init__(self):
        _state(self.final_state)
        if type(self.trace) is not tuple or any(type(entry) is not TraceEntry for entry in self.trace):
            raise ValueError("trace requires an immutable tuple of TraceEntry values")

    def as_dict(self): return {"final_state": self.final_state.as_dict(), "trace": [entry.as_dict() for entry in self.trace]}


def step(state, action):
    _state(state); _action(action)
    if type(action) is AdvanceHalf:
        if state.stage != "half_complete": raise ValueError("AdvanceHalf requires a confirmed completed half")
        away, home = state.closed_away_runs, state.closed_home_runs
        if state.side == "top": away += state.current.half.runs
        else: home += state.current.half.runs
        return HalfSequenceState(state.inning+(1 if state.side == "bottom" else 0),
                                 "bottom" if state.side == "top" else "top", away, home,
                                 PitchHalfState(None, HalfInningState()))
    current = pitch_half_rules.step(state.current, action)
    return HalfSequenceState(state.inning, state.side, state.closed_away_runs, state.closed_home_runs, current)


def replay(start_state, actions):
    _state(start_state)
    if type(actions) not in (list, tuple): raise ValueError("actions require an exact finite list or tuple")
    current, trace = start_state, []
    for action in tuple(actions):
        following = step(current, action)
        trace.append(TraceEntry(action, current, following, following.score["away"]-current.score["away"], following.score["home"]-current.score["home"]))
        current = following
    return ReplayResult(current, tuple(trace))
