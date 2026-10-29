"""Control stages only. Delegate all count and base/out rules to old pure steps."""
from dataclasses import dataclass
from prototypes.phase4a import model as count_rules
from prototypes.phase4b import model as half_rules

CountState = count_rules.CountState
HalfInningState = half_rules.HalfInningState
STAGES = ("between_batters", "pitching", "awaiting_resolution", "half_complete")
COMPATIBLE = frozenset({
    ("walk_awarded", "walk_forced_only"),
    ("strikeout_out", "batter_out_runners_hold"),
    ("caught_foul_out", "batter_out_runners_hold"),
    ("fair_play_pending", "batter_out_runners_hold"),
    ("fair_play_pending", "legal_over_fence_home_run"),
})


@dataclass(frozen=True, slots=True)
class PitchHalfState:
    count: CountState | None
    half: HalfInningState

    def __post_init__(self):
        if (self.count is not None and type(self.count) is not CountState) or type(self.half) is not HalfInningState:
            raise ValueError("state requires exact old CountState or None, and exact old HalfInningState")
        if self.half.outs == 3 and self.count is not None:
            raise ValueError("completed half snapshot must have count=None")

    @property
    def stage(self):
        if self.half.outs == 3: return "half_complete"
        if self.count is None: return "between_batters"
        if self.count.status == "active": return "pitching"
        return "awaiting_resolution"

    @classmethod
    def from_mapping(cls, value):
        if type(value) is not dict or set(value) != {"count", "half"}:
            raise ValueError("state JSON requires exactly count/half")
        count = None if value["count"] is None else CountState.from_mapping(value["count"])
        return cls(count, HalfInningState.from_mapping(value["half"]))

    def as_dict(self):
        return {"count": None if self.count is None else self.count.as_dict(), "half": self.half.as_dict()}


@dataclass(frozen=True, slots=True)
class BeginBatter:
    def as_dict(self): return {"kind": "begin_batter"}


@dataclass(frozen=True, slots=True)
class Pitch:
    event: str

    def __post_init__(self):
        if type(self.event) is not str or self.event not in count_rules.EVENTS:
            raise ValueError("Pitch requires an exact approved eight-event string")

    def as_dict(self): return {"kind": "pitch", "event": self.event}


@dataclass(frozen=True, slots=True)
class Resolve:
    completed_play: str

    def __post_init__(self):
        if type(self.completed_play) is not str or self.completed_play not in half_rules.EVENTS:
            raise ValueError("Resolve requires an exact approved completed-play string")

    def as_dict(self): return {"kind": "resolve", "completed_play": self.completed_play}


def action_from_mapping(value):
    if type(value) is not dict or type(value.get("kind")) is not str:
        raise ValueError("action JSON requires an exact object and string kind")
    kind = value["kind"]
    if kind == "begin_batter" and set(value) == {"kind"}: return BeginBatter()
    if kind == "pitch" and set(value) == {"kind", "event"}: return Pitch(value["event"])
    if kind == "resolve" and set(value) == {"kind", "completed_play"}: return Resolve(value["completed_play"])
    raise ValueError("unsupported action kind or incorrect JSON key set")


def _state(value):
    if type(value) is not PitchHalfState: raise ValueError("explicit PitchHalfState required")


def _action(value):
    if type(value) not in (BeginBatter, Pitch, Resolve): raise ValueError("explicit exact action type required")


@dataclass(frozen=True, slots=True)
class TraceEntry:
    action: BeginBatter | Pitch | Resolve
    before: PitchHalfState
    after: PitchHalfState
    runs_added: int

    def __post_init__(self):
        _action(self.action)
        _state(self.before)
        _state(self.after)
        if type(self.runs_added) is not int or self.runs_added < 0 or self.runs_added != self.after.half.runs-self.before.half.runs:
            raise ValueError("trace run increment must be the exact nonnegative half-run difference")

    def as_dict(self):
        return {"action": self.action.as_dict(), "before": self.before.as_dict(),
                "after": self.after.as_dict(), "runs_added": self.runs_added}


@dataclass(frozen=True, slots=True)
class ReplayResult:
    final_state: PitchHalfState
    trace: tuple[TraceEntry, ...]

    def __post_init__(self):
        _state(self.final_state)
        if type(self.trace) is not tuple or any(type(entry) is not TraceEntry for entry in self.trace):
            raise ValueError("trace requires an immutable tuple of TraceEntry values")

    def as_dict(self):
        return {"final_state": self.final_state.as_dict(), "trace": [entry.as_dict() for entry in self.trace]}


def step(state, action):
    """Apply one explicit action; no automatic result selection or next batter."""
    _state(state)
    _action(action)
    stage = state.stage
    if type(action) is BeginBatter and stage == "between_batters":
        return PitchHalfState(CountState(), state.half)
    if type(action) is Pitch and stage == "pitching":
        return PitchHalfState(count_rules.step(state.count, action.event), state.half)
    if type(action) is Resolve and stage == "awaiting_resolution":
        if (state.count.status, action.completed_play) not in COMPATIBLE:
            raise ValueError("completed play is incompatible with waiting count contract")
        return PitchHalfState(None, half_rules.step(state.half, action.completed_play))
    raise ValueError("action is not permitted at this derived stage")


def replay(start_state, actions):
    """Finite memory replay. An invalid tail yields no partial public result."""
    _state(start_state)
    if type(actions) not in (list, tuple): raise ValueError("actions require an exact finite list or tuple")
    current, trace = start_state, []
    for action in tuple(actions):
        following = step(current, action)
        trace.append(TraceEntry(action, current, following, following.half.runs-current.half.runs))
        current = following
    return ReplayResult(current, tuple(trace))
