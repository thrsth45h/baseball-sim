"""Immutable game bookkeeping; old sequence remains the last full projection."""
from dataclasses import dataclass, fields
from .protocol import ABORT_REASONS, HalfInningState, HalfSequenceState, PitchHalfState, exact_int

TEAMS=("away","home")
COUNT_FIELDS=("completed_plate_appearances","safe_hit_events","walk_events","strikeout_events","home_run_events")
END_REASONS=("home_lead_after_top9","away_lead_after_bottom9","tie_after_nine","walkoff_walk","walkoff_safe_hit","walkoff_home_run")


def batter_id(team,index):return team+"_"+str(index+1)


def teams_document():
    return [{"id":team,"name":"仮Away" if team=="away" else "仮Home",
        "lineup":[{"id":batter_id(team,i),"display_name":("Away " if team=="away" else "Home ")+str(i+1),"slot":i+1} for i in range(9)]} for team in TEAMS]


@dataclass(frozen=True,slots=True)
class EventCounts:
    completed_plate_appearances: int=0
    safe_hit_events: int=0
    walk_events: int=0
    strikeout_events: int=0
    home_run_events: int=0

    def __post_init__(self):
        if any(not exact_int(getattr(self,f)) for f in COUNT_FIELDS):raise ValueError("event counts require nonnegative exact integers")
        if self.home_run_events>self.safe_hit_events or self.safe_hit_events+self.walk_events+self.strikeout_events>self.completed_plate_appearances:
            raise ValueError("inconsistent event count subsets")

    def plus(self,category):
        if category not in ("out_hold","safe_hit","walk","strikeout","home_run"):raise ValueError("unknown completed-PA category")
        return EventCounts(self.completed_plate_appearances+1,self.safe_hit_events+(category in ("safe_hit","home_run")),
            self.walk_events+(category=="walk"),self.strikeout_events+(category=="strikeout"),self.home_run_events+(category=="home_run"))

    def as_dict(self):return {name:getattr(self,name) for name in COUNT_FIELDS}

    @classmethod
    def from_mapping(cls,value):
        if type(value) is not dict or set(value)!=set(COUNT_FIELDS):raise ValueError("exact event count keys required")
        return cls(**value)


@dataclass(frozen=True,slots=True)
class GameSnapshot:
    sequence: HalfSequenceState
    batting_indices: tuple[int,int]
    batter_counts: tuple[EventCounts,...]
    pitches_accepted: int
    line_score: tuple[int | None,...]
    status: str="in_progress"
    reason: str | None=None
    outcome: str | None=None
    terminal_score_delta: tuple[int,int]=(0,0)
    history_origin: str="initial_zero"

    def __post_init__(self):
        if type(self.sequence) is not HalfSequenceState or not 1<=self.sequence.inning<=9:raise ValueError("exact old sequence within innings 1..9 required")
        if type(self.batting_indices) is not tuple or len(self.batting_indices)!=2 or any(not exact_int(i,0,9) for i in self.batting_indices):raise ValueError("two exact next-batter indices 0..8 required")
        if type(self.batter_counts) is not tuple or len(self.batter_counts)!=18 or any(type(c) is not EventCounts for c in self.batter_counts):raise ValueError("18 immutable batter event counts required")
        if not exact_int(self.pitches_accepted):raise ValueError("accepted pitch count requires a nonnegative exact integer")
        if type(self.terminal_score_delta) is not tuple or len(self.terminal_score_delta)!=2 or any(not exact_int(v) for v in self.terminal_score_delta):raise ValueError("two exact nonnegative terminal deltas required")
        if type(self.status) is not str or self.status not in ("in_progress","completed","aborted"):raise ValueError("unknown game status")
        if type(self.history_origin) is not str or self.history_origin not in ("initial_zero","provided_snapshot"):raise ValueError("unknown snapshot history origin")
        if self.status=="in_progress":
            if self.reason is not None or self.outcome is not None or self.terminal_score_delta!=(0,0):raise ValueError("running game cannot contain a result")
        elif self.status=="aborted":
            if type(self.reason) is not str or self.reason not in ABORT_REASONS or self.outcome is not None or self.terminal_score_delta!=(0,0):raise ValueError("aborted game has no winner or terminal scoring")
        else:
            if type(self.reason) is not str or self.reason not in END_REASONS or type(self.outcome) is not str or self.outcome not in ("away_win","home_win","draw"):raise ValueError("completed game needs its limited-rule result")
            a,h=self.score["away"],self.score["home"]
            if self.outcome!=("away_win" if a>h else "home_win" if h>a else "draw"):raise ValueError("outcome differs from derived final score")
            if self.reason.startswith("walkoff_"):
                if self.sequence.inning!=9 or self.sequence.side!="bottom" or self.sequence.stage!="awaiting_resolution" or self.terminal_score_delta[0]!=0 or self.terminal_score_delta[1]<1 or self.outcome!="home_win":raise ValueError("invalid walk-off boundary snapshot")
                expected_status="walk_awarded" if self.reason=="walkoff_walk" else "fair_play_pending"
                if self.sequence.current.count.status!=expected_status:raise ValueError("terminal reason differs from the unresolved count")
            elif self.terminal_score_delta!=(0,0) or self.sequence.inning!=9 or self.sequence.stage!="half_complete":raise ValueError("normal ending must retain the completed ninth-half projection")
            elif ((self.reason=="home_lead_after_top9" and (self.sequence.side!="top" or self.outcome!="home_win"))
                  or (self.reason=="away_lead_after_bottom9" and (self.sequence.side!="bottom" or self.outcome!="away_win"))
                  or (self.reason=="tie_after_nine" and (self.sequence.side!="bottom" or self.outcome!="draw"))):raise ValueError("normal ending reason differs from the half or score")
        if type(self.line_score) is not tuple or len(self.line_score)!=18 or any(v is not None and not exact_int(v) for v in self.line_score):raise ValueError("line score requires 18 nonnegative integers or null")
        index=self.half_index
        if any(v is None for v in self.line_score[:index+1]) or any(v is not None for v in self.line_score[index+1:]):raise ValueError("line score must identify reached halves, preserving future nulls")
        if self.line_score[index]!=self.sequence.current.half.runs+sum(self.terminal_score_delta):raise ValueError("current line score differs from projection plus terminal increment")
        if any(sum(v or 0 for v in self.line_score[offset::2])!=self.score[team] for offset,team in enumerate(TEAMS)):raise ValueError("line score does not reconcile to derived score")

    @property
    def half_index(self):return (self.sequence.inning-1)*2+(self.sequence.side=="bottom")

    @property
    def score(self):return {team:self.sequence.score[team]+self.terminal_score_delta[i] for i,team in enumerate(TEAMS)}

    @property
    def done(self):return self.status!="in_progress"

    @classmethod
    def initial(cls):
        return cls(HalfSequenceState(1,"top",0,0,PitchHalfState(None,HalfInningState())),(0,0),tuple(EventCounts() for _ in range(18)),0,(0,*([None]*17)))

    def as_dict(self):
        return {"sequence":self.sequence.as_dict(),"batting_indices":list(self.batting_indices),"batter_counts":[c.as_dict() for c in self.batter_counts],
            "pitches_accepted":self.pitches_accepted,"line_score":list(self.line_score),"status":self.status,"reason":self.reason,"outcome":self.outcome,
            "terminal_score_delta":list(self.terminal_score_delta),"history_origin":self.history_origin}

    @classmethod
    def from_mapping(cls,value):
        if type(value) is not dict or set(value)!={f.name for f in fields(cls)}:raise ValueError("snapshot requires exactly its stored fields")
        for key in ("batting_indices","batter_counts","line_score","terminal_score_delta"):
            if type(value[key]) is not list:raise ValueError("snapshot arrays must be explicit JSON arrays")
        return cls(HalfSequenceState.from_mapping(value["sequence"]),tuple(value["batting_indices"]),tuple(EventCounts.from_mapping(c) for c in value["batter_counts"]),
            value["pitches_accepted"],tuple(value["line_score"]),value["status"],value["reason"],value["outcome"],tuple(value["terminal_score_delta"]),value["history_origin"])


@dataclass(frozen=True,slots=True)
class GameResult:
    snapshot: GameSnapshot

    def __post_init__(self):
        if type(self.snapshot) is not GameSnapshot or not self.snapshot.done:raise ValueError("GameResult requires a completed or aborted snapshot")

    @property
    def status(self):return self.snapshot.status

    @property
    def score(self):return self.snapshot.score

    def as_dict(self):
        s=self.snapshot;batter_rows=[];team_counts={}
        for team_index,team in enumerate(TEAMS):
            group=s.batter_counts[team_index*9:(team_index+1)*9]
            team_counts[team]={key:sum(getattr(c,key) for c in group) for key in COUNT_FIELDS}
            for slot,counts in enumerate(group):batter_rows.append({"batter_id":batter_id(team,slot),"team":team,"slot":slot+1,**counts.as_dict()})
        return {"status":s.status,"reason":s.reason,"outcome":s.outcome,"score":s.score,
            "final_score":s.score if s.status=="completed" else None,"partial_score":s.score if s.status=="aborted" else None,
            "pitches_accepted":s.pitches_accepted,"line_score":{"innings":list(range(1,10)),"away":list(s.line_score[::2]),"home":list(s.line_score[1::2]),
                "walkoff_half":s.half_index if s.reason is not None and s.reason.startswith("walkoff_") else None},
            "teams":teams_document(),"team_events":team_counts,"batter_events":batter_rows,
            "next_batting_indices":dict(zip(TEAMS,s.batting_indices)),"last_projected_sequence":s.sequence.as_dict(),
            "last_projected_sequence_semantics":"last complete old-rule projection; terminal play remains unresolved here" if sum(s.terminal_score_delta) else "last complete old-rule projection",
            "terminal_score_delta":dict(zip(TEAMS,s.terminal_score_delta)),"history_origin":s.history_origin,
            "event_count_semantics":"completed PA and safe-arrival/BB/SO/HR events only; no official AB/RBI/2B/3B or batting average"}
