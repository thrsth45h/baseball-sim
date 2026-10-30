"""Validate declared final destinations; delegate all old actions unchanged."""
from dataclasses import dataclass
from prototypes.phase4d import model as sequence_rules

HalfSequenceState = sequence_rules.HalfSequenceState
PitchHalfState, HalfInningState = sequence_rules.PitchHalfState, sequence_rules.HalfInningState
BeginBatter, Pitch, Resolve, AdvanceHalf = sequence_rules.BeginBatter, sequence_rules.Pitch, sequence_rules.Resolve, sequence_rules.AdvanceHalf


@dataclass(frozen=True, slots=True)
class ResolveSafeHit:
    hit_bases: int
    runner_destinations: tuple[int | None, int | None, int | None]

    def __post_init__(self):
        if type(self.hit_bases) is not int or self.hit_bases not in (1,2,3):
            raise ValueError("hit_bases requires exact integer 1/2/3, supplied as an adjudicated fact")
        if type(self.runner_destinations) is not tuple or len(self.runner_destinations)!=3:
            raise ValueError("destinations require an exact three-slot tuple")
        if any(d is not None and (type(d) is not int or not 1<=d<=4) for d in self.runner_destinations):
            raise ValueError("each destination requires None or exact integer 1..4")

    def as_dict(self):
        return {"kind":"resolve_safe_hit", "hit_bases":self.hit_bases, "runner_destinations":list(self.runner_destinations)}


def action_from_mapping(value):
    if type(value) is not dict or type(value.get("kind")) is not str: raise ValueError("action requires an exact JSON object and string kind")
    if value["kind"]=="resolve_safe_hit":
        if set(value)!={"kind","hit_bases","runner_destinations"} or type(value["runner_destinations"]) is not list or len(value["runner_destinations"])!=3:
            raise ValueError("safe-hit JSON requires exactly kind/hit_bases/three-item destination array")
        return ResolveSafeHit(value["hit_bases"],tuple(value["runner_destinations"]))
    return sequence_rules.action_from_mapping(value)


def _state(value):
    if type(value) is not HalfSequenceState: raise ValueError("exact old HalfSequenceState required")


def _action(value):
    if type(value) not in (BeginBatter,Pitch,Resolve,AdvanceHalf,ResolveSafeHit): raise ValueError("explicit exact approved action required")


@dataclass(frozen=True, slots=True)
class TraceEntry:
    action: BeginBatter | Pitch | Resolve | AdvanceHalf | ResolveSafeHit
    before: HalfSequenceState
    after: HalfSequenceState
    away_runs_added: int
    home_runs_added: int

    def __post_init__(self):
        _action(self.action); _state(self.before); _state(self.after)
        for team,value in (("away",self.away_runs_added),("home",self.home_runs_added)):
            if type(value) is not int or value<0 or value!=self.after.score[team]-self.before.score[team]:
                raise ValueError("trace increments require exact nonnegative derived-score differences")

    def as_dict(self):
        return {"action":self.action.as_dict(),"before":self.before.as_dict(),"after":self.after.as_dict(),
                "away_runs_added":self.away_runs_added,"home_runs_added":self.home_runs_added}


@dataclass(frozen=True, slots=True)
class ReplayResult:
    final_state: HalfSequenceState
    trace: tuple[TraceEntry,...]

    def __post_init__(self):
        _state(self.final_state)
        if type(self.trace) is not tuple or any(type(entry) is not TraceEntry for entry in self.trace):
            raise ValueError("trace requires an immutable tuple of exact TraceEntry values")

    def as_dict(self): return {"final_state":self.final_state.as_dict(),"trace":[entry.as_dict() for entry in self.trace]}


def step(state,action):
    _state(state); _action(action)
    if type(action) is not ResolveSafeHit: return sequence_rules.step(state,action)
    if state.stage!="awaiting_resolution" or state.current.count.status!="fair_play_pending":
        raise ValueError("safe hit requires a fair-result waiting snapshot")
    half=state.current.half
    destinations=action.runner_destinations
    for origin,(occupied,destination) in enumerate(zip(half.bases,destinations),1):
        if occupied!=(destination is not None): raise ValueError("occupied slots and None must match exactly")
        if destination is not None and destination<origin: raise ValueError("a declared runner cannot finish behind the original base")
    ordered=[action.hit_bases,*[d for d in destinations if d is not None]]
    if any(not (a<b or a==b==4) for a,b in zip(ordered,ordered[1:])):
        raise ValueError("declared final positions collide or reverse runner order")
    after=HalfInningState(half.outs,tuple(base in ordered for base in (1,2,3)),half.runs+sum(d==4 for d in destinations))
    return HalfSequenceState(state.inning,state.side,state.closed_away_runs,state.closed_home_runs,PitchHalfState(None,after))


def replay(start_state,actions):
    _state(start_state)
    if type(actions) not in (list,tuple): raise ValueError("actions require an exact finite list or tuple")
    current,trace=start_state,[]
    for action in tuple(actions):
        following=step(current,action)
        trace.append(TraceEntry(action,current,following,following.score["away"]-current.score["away"],following.score["home"]-current.score["home"]))
        current=following
    return ReplayResult(current,tuple(trace))
