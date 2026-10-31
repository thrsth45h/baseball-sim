"""Typed source boundary and preflight checks, without applying old resolutions."""
from dataclasses import dataclass
import json
from prototypes.phase4e import model as rules

CountState=rules.sequence_rules.pitch_half_rules.CountState
HalfSequenceState=rules.HalfSequenceState
HalfInningState=rules.HalfInningState
PitchHalfState=rules.PitchHalfState
PITCH_EVENTS=rules.sequence_rules.pitch_half_rules.count_rules.EVENTS
FAIR="fair_ball_handoff"
ABORT_REASONS=("pitch_limit","source_exhausted","source_error","invalid_pitch_input","invalid_fair_result")
# Only the controller may declare that its own accepted-pitch budget was used.
SOURCE_ABORT_REASONS=ABORT_REASONS[1:]


def exact_int(value,minimum=0,maximum=None):
    return type(value) is int and value>=minimum and (maximum is None or value<maximum)


def validate_seed(seed):
    if not exact_int(seed,0,2**64):raise ValueError("seed must be an exact integer in [0, 2^64)")
    return seed


def validate_max_pitches(value):
    if not exact_int(value,1):raise ValueError("max_pitches must be an exact positive integer")
    return value


def parse_json(text):
    def pairs(items):
        result={}
        for key,value in items:
            if key in result:raise ValueError("duplicate JSON key: "+key)
            result[key]=value
        return result
    def reject(value):raise ValueError("non-finite JSON constant: "+value)
    return json.loads(text,object_pairs_hook=pairs,parse_constant=reject)


class InputError(ValueError):
    def __init__(self,code):
        if type(code) is not str or code not in SOURCE_ABORT_REASONS:raise ValueError("unknown source failure code")
        self.code=code
        super().__init__(code)


@dataclass(frozen=True,slots=True)
class RecordedAbort:
    reason: str

    def __post_init__(self):
        if type(self.reason) is not str or self.reason not in SOURCE_ABORT_REASONS:raise ValueError("unknown recorded source abort reason")


@dataclass(frozen=True,slots=True)
class FairPlay:
    kind: str
    candidate_bases: int | None=None
    runner_destinations: tuple[int | None,int | None,int | None] | None=None

    def __post_init__(self):
        if type(self.kind) is not str or self.kind not in ("out_hold","safe_hit","home_run"):raise InputError("invalid_fair_result")
        if self.kind=="safe_hit":
            try:rules.ResolveSafeHit(self.candidate_bases,self.runner_destinations)
            except (ValueError,TypeError):raise InputError("invalid_fair_result") from None
        elif self.candidate_bases is not None or self.runner_destinations is not None:raise InputError("invalid_fair_result")

    def as_dict(self):
        return {"kind":self.kind,"candidate_bases":self.candidate_bases,
            "runner_destinations":None if self.runner_destinations is None else list(self.runner_destinations)}

    @classmethod
    def from_mapping(cls,value):
        if type(value) is not dict or set(value)!={"kind","candidate_bases","runner_destinations"}:raise InputError("invalid_fair_result")
        destinations=value["runner_destinations"]
        if destinations is not None:
            if type(destinations) is not list:raise InputError("invalid_fair_result")
            destinations=tuple(destinations)
        return cls(value["kind"],value["candidate_bases"],destinations)


@dataclass(frozen=True,slots=True)
class PitchInput:
    event: str
    fair: FairPlay | None=None
    pitch_draw: int | None=None
    fair_draw: int | None=None

    def __post_init__(self):
        if type(self.event) is not str or self.event not in PITCH_EVENTS:raise InputError("invalid_pitch_input")
        if self.fair is not None and type(self.fair) is not FairPlay:raise InputError("invalid_fair_result")
        for draw in (self.pitch_draw,self.fair_draw):
            if draw is not None and not exact_int(draw,0,100):raise InputError("invalid_pitch_input")
        if self.event==FAIR and self.fair is None:raise InputError("invalid_fair_result")
        if self.event!=FAIR and (self.fair is not None or self.fair_draw is not None):raise InputError("invalid_pitch_input")

    def as_dict(self):
        return {"event":self.event,"fair":None if self.fair is None else self.fair.as_dict(),"pitch_draw":self.pitch_draw,"fair_draw":self.fair_draw}

    @classmethod
    def from_mapping(cls,value):
        if type(value) is not dict or set(value)!={"event","fair","pitch_draw","fair_draw"}:raise InputError("invalid_pitch_input")
        return cls(value["event"],None if value["fair"] is None else FairPlay.from_mapping(value["fair"]),value["pitch_draw"],value["fair_draw"])


def preflight(value,half):
    """Validate the whole offer before Pitch; never preview an old result step.

    The terminal boundary must check final-position contradictions itself:
    calling old ResolveSafeHit even on a preview would violate its no-walk-off
    precondition. Ordinary accepted transitions still delegate to old rules.
    """
    if type(half) is not HalfInningState:raise ValueError("exact half snapshot required")
    if type(value) is not PitchInput:value=PitchInput.from_mapping(value)
    fair=value.fair
    if fair is not None and fair.kind=="safe_hit":
        people={0:fair.candidate_bases}
        for origin,(occupied,destination) in enumerate(zip(half.bases,fair.runner_destinations),1):
            if occupied!=(destination is not None) or (destination is not None and destination<origin):raise InputError("invalid_fair_result")
            if occupied:people[origin]=destination
        for back in people:
            for front in people:
                if back<front and (people[back]>people[front] or (people[back]==people[front] and people[front]!=4)):
                    raise InputError("invalid_fair_result")
    return value
