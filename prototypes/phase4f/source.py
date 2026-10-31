"""Exchangeable toy source/policy. Every game owns its Random(seed)."""
from dataclasses import dataclass
from pathlib import Path
import random
from .protocol import FAIR, FairPlay, InputError, PitchInput, exact_int, parse_json, validate_seed

PROFILE_ROOT=Path(__file__).resolve().parent/"profiles"
PITCH_WEIGHTS=(("called_ball",35),("called_strike_caught",15),("swinging_strike_caught",15),("ordinary_foul_uncaught",15),(FAIR,20))
FAIR_WEIGHTS=(("out_hold",65),("safe_1",22),("safe_2",7),("safe_3",1),("home_run",5))


@dataclass(frozen=True,slots=True)
class ToyProfile:
    version: str="toy-v1"
    pitch_weights: tuple=PITCH_WEIGHTS
    fair_weights: tuple=FAIR_WEIGHTS

    def __post_init__(self):
        if type(self.version) is not str or self.version!="toy-v1":raise ValueError("unsupported toy profile")
        for actual,expected in ((self.pitch_weights,PITCH_WEIGHTS),(self.fair_weights,FAIR_WEIGHTS)):
            if type(actual) is not tuple or actual!=expected or any(type(r) is not tuple or type(r[0]) is not str or not exact_int(r[1],1) for r in actual):
                raise ValueError("toy-v1 weights are fixed, unvalidated research assumptions")

    def as_dict(self):return {"version":self.version,"pitch_weights":[list(row) for row in self.pitch_weights],"fair_weights":[list(row) for row in self.fair_weights],"mlb_calibrated":False}


def load_profile():
    value=parse_json((PROFILE_ROOT/"toy-v1.json").read_text(encoding="utf-8"))
    expected=ToyProfile().as_dict()
    if type(value) is not dict or set(value)!=set(expected) or value!=expected or value["mlb_calibrated"] is not False:raise ValueError("fixed toy profile differs")
    for key in ("pitch_weights","fair_weights"):
        if type(value[key]) is not list or any(type(r) is not list or len(r)!=2 or type(r[0]) is not str or type(r[1]) is not int for r in value[key]):raise ValueError("invalid toy weight types")
    return ToyProfile()


def choose(draw,weights):
    if not exact_int(draw,0,100):raise ValueError("draw must be an exact integer 0..99")
    boundary=0
    for name,weight in weights:
        boundary+=weight
        if draw<boundary:return name
    raise ValueError("incomplete weight profile")


class ToyAdvancePolicy:
    version="toy-fixed-advance-v1"

    def destinations(self,bases,candidate_bases):
        if type(bases) is not tuple or len(bases)!=3 or any(type(b) is not bool for b in bases) or not exact_int(candidate_bases,1,4):raise InputError("invalid_fair_result")
        return tuple(min(origin+candidate_bases,4) if present else None for origin,present in enumerate(bases,1))


class ToyPitchSource:
    version="toy-pitch-source-v1"

    def __init__(self,seed,profile=None,policy=None):
        self.seed=validate_seed(seed)
        self.profile=load_profile() if profile is None else profile
        if type(self.profile) is not ToyProfile:raise ValueError("exact toy profile required")
        self.policy=ToyAdvancePolicy() if policy is None else policy
        self.rng=random.Random(seed)
        self.pitch_calls=self.fair_calls=self.draw_count=0

    def next_pitch(self,context):
        self.pitch_calls+=1;draw=self.rng.randrange(100);self.draw_count+=1
        event=choose(draw,self.profile.pitch_weights);fair=None;fair_draw=None
        if event==FAIR:
            self.fair_calls+=1;fair_draw=self.rng.randrange(100);self.draw_count+=1
            kind=choose(fair_draw,self.profile.fair_weights)
            if kind.startswith("safe_"):
                value=int(kind[-1]);fair=FairPlay("safe_hit",value,self.policy.destinations(context.bases,value))
            else:fair=FairPlay(kind)
        return PitchInput(event,fair,draw,fair_draw)


class ScriptedSource:
    """Test/source adapter. Raw invalid objects stay private to this boundary."""
    version="scripted-source-v1"

    def __init__(self,values):
        if type(values) not in (list,tuple):raise ValueError("script requires an explicit finite list or tuple")
        self.values=tuple(values);self.calls=0;self.position=0

    def next_pitch(self,context):
        self.calls+=1
        if self.position>=len(self.values):raise StopIteration
        value=self.values[self.position];self.position+=1
        return value
