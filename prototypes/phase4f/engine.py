"""One-pitch controller, limited game endings, and RNG-free recorded replay."""
from copy import deepcopy
from dataclasses import dataclass, replace
from . import CAPABILITIES, DESIGN, SCHEMA
from .protocol import FAIR, SOURCE_ABORT_REASONS, InputError, PitchInput, RecordedAbort, preflight, rules, validate_max_pitches, validate_seed
from .source import ScriptedSource, ToyPitchSource
from .state import GameResult, GameSnapshot, TEAMS, batter_id


@dataclass(frozen=True,slots=True)
class PitchContext:
    inning: int
    side: str
    bases: tuple[bool,bool,bool]
    count: object
    score: tuple[int,int]
    team: str
    batter_id: str
    slot: int


class GameSession:
    def __init__(self,seed=42,max_pitches=10000,source=None,initial_snapshot=None):
        self.seed=validate_seed(seed);self.max_pitches=validate_max_pitches(max_pitches)
        initial=GameSnapshot.initial() if initial_snapshot is None else initial_snapshot
        if type(initial) is not GameSnapshot:raise ValueError("initial_snapshot must be an exact immutable GameSnapshot")
        if not initial.done and initial.sequence.stage=="awaiting_resolution":raise ValueError("a new one-pitch call cannot resume an unresolved old action boundary")
        if not initial.done and initial.sequence.inning==9 and initial.sequence.side=="bottom" and initial.score["home"]>initial.score["away"]:
            raise ValueError("a provided running snapshot cannot start after the home winning score")
        if not initial.done and initial.pitches_accepted>self.max_pitches:raise ValueError("snapshot already exceeds the pitch budget")
        self.initial_snapshot=initial;self.snapshot=initial
        self.source=ToyPitchSource(seed) if source is None else source
        if not callable(getattr(self.source,"next_pitch",None)):raise ValueError("source must supply next_pitch(context)")
        self._events=[]

    @property
    def events(self):return tuple(deepcopy(self._events))

    def _append(self,value):self._events.append({"event_index":len(self._events)+1,**value})

    def _context(self):
        s=self.snapshot;seq=s.sequence;team=seq.batting_team;index=s.batting_indices[TEAMS.index(team)]
        return PitchContext(seq.inning,seq.side,seq.current.half.bases,seq.current.count,
            (s.score["away"],s.score["home"]),team,batter_id(team,index),index+1)

    def _context_json(self,context):
        return {"inning":context.inning,"side":context.side,"team":context.team,"batter_id":context.batter_id,"slot":context.slot}

    def _apply(self,action,input_value=None):
        before=self.snapshot;context=self._context();after=rules.step(before.sequence,action)
        lines=list(before.line_score);index=(after.inning-1)*2+(after.side=="bottom");lines[index]=after.current.half.runs
        pitches=before.pitches_accepted+(type(action) is rules.Pitch)
        self.snapshot=replace(before,sequence=after,line_score=tuple(lines),pitches_accepted=pitches)
        context_value=self._context_json(context)
        if type(action) is rules.AdvanceHalf:context_value=context_value|{"batter_id":None,"slot":None}
        self._append({"type":"action","action":action.as_dict(),"context":context_value,
            "pitches_accepted":pitches,"input":None if input_value is None else input_value.as_dict(),
            "before":before.sequence.as_dict(),"after":after.as_dict(),
            "away_runs_added":after.score["away"]-before.sequence.score["away"],"home_runs_added":after.score["home"]-before.sequence.score["home"]})

    def _pa_changes(self,context,category):
        team_index=TEAMS.index(context.team);index=context.slot-1;flat=team_index*9+index
        counts=list(self.snapshot.batter_counts);counts[flat]=counts[flat].plus(category)
        order=list(self.snapshot.batting_indices);order[team_index]=(index+1)%9
        return tuple(counts),tuple(order)

    def _pa_event(self,context,category,runs,terminal):
        self._append({"type":"plate_appearance_completed",**self._context_json(context),"category":category,
            "batting_index_before":context.slot-1,"batting_index_after":context.slot%9,"runs_added":runs,
            "terminal":terminal,"pitches_accepted":self.snapshot.pitches_accepted})

    def _record_completion(self,context,category,runs):
        counts,order=self._pa_changes(context,category)
        self.snapshot=replace(self.snapshot,batter_counts=counts,batting_indices=order)
        self._pa_event(context,category,runs,False)

    def _game_end_event(self):
        self._append({"type":"game_end","reason":self.snapshot.reason,"outcome":self.snapshot.outcome,
            "score":self.snapshot.score,"pitches_accepted":self.snapshot.pitches_accepted,
            "last_projected_sequence":self.snapshot.sequence.as_dict(),"terminal_score_delta":dict(zip(TEAMS,self.snapshot.terminal_score_delta))})

    def _normal_end_if_closed(self):
        seq=self.snapshot.sequence
        if seq.stage!="half_complete" or seq.inning!=9:return False
        away,home=self.snapshot.score["away"],self.snapshot.score["home"]
        if seq.side=="top":
            if home<=away:return False
            reason,outcome="home_lead_after_top9","home_win"
        else:
            if home>away:raise ValueError("a running bottom ninth cannot already contain an unhandled home lead")
            reason,outcome=("away_lead_after_bottom9","away_win") if away>home else ("tie_after_nine","draw")
        self.snapshot=replace(self.snapshot,status="completed",reason=reason,outcome=outcome)
        self._game_end_event();return True

    def _abort(self,reason):
        self.snapshot=replace(self.snapshot,status="aborted",reason=reason,outcome=None)
        # Never include the invalid arbitrary input object or its repr/exception.
        self._append({"type":"abort","reason":reason,"pitches_accepted":self.snapshot.pitches_accepted,
            "partial_snapshot":self.snapshot.as_dict(),"unaccepted_input_omitted":True})

    def _resolution(self,input_value):
        seq=self.snapshot.sequence;status=seq.current.count.status;half=seq.current.half
        if status=="walk_awarded":return rules.Resolve("walk_forced_only"),"walk",int(all(half.bases)),tuple([3] if all(half.bases) else [])
        if status in ("strikeout_out","caught_foul_out"):
            return rules.Resolve("batter_out_runners_hold"),"strikeout" if status=="strikeout_out" else "out_hold",0,()
        fair=input_value.fair
        if status!="fair_play_pending" or fair is None:raise ValueError("missing validated resolution boundary")
        if fair.kind=="out_hold":return rules.Resolve("batter_out_runners_hold"),"out_hold",0,()
        occupied=tuple(i for i in (3,2,1) if half.bases[i-1])
        if fair.kind=="home_run":return rules.Resolve("legal_over_fence_home_run"),"home_run",1+len(occupied),(*occupied,0)
        scoring=tuple(i for i in (3,2,1) if fair.runner_destinations[i-1]==4)
        return rules.ResolveSafeHit(fair.candidate_bases,fair.runner_destinations),"safe_hit",len(scoring),scoring

    def _walkoff(self,input_value,context,category,candidate_runs,scoring):
        seq=self.snapshot.sequence
        needed=self.snapshot.score["away"]-self.snapshot.score["home"]+1
        if seq.inning!=9 or seq.side!="bottom" or needed<1 or candidate_runs<needed:return False
        added=candidate_runs if category=="home_run" else needed
        reason={"walk":"walkoff_walk","safe_hit":"walkoff_safe_hit","home_run":"walkoff_home_run"}.get(category)
        if reason is None:raise ValueError("unsupported terminal scoring category")
        selected=scoring if category=="home_run" else scoring[:needed]
        counts,order=self._pa_changes(context,category);lines=list(self.snapshot.line_score);lines[self.snapshot.half_index]+=added
        before_sequence=seq.as_dict()
        self.snapshot=replace(self.snapshot,batter_counts=counts,batting_indices=order,line_score=tuple(lines),
            status="completed",reason=reason,outcome="home_win",terminal_score_delta=(0,added))
        self._append({"type":"terminal_play","reason":reason,"context":self._context_json(context),
            "last_projected_sequence":before_sequence,"input_candidate":input_value.as_dict(),
            "candidate_runs":candidate_runs,"runs_added":{"away":0,"home":added},
            "scored_runner_slots":list(selected),"runner_slot_semantics":"pre-play 1/2/3; 0=batter, included only for HR",
            "batter_first_base_touch_confirmed":True,"score":self.snapshot.score,
            "post_terminal_old_sequence_created":False,"official_hit_value_adjudicated":False})
        self._pa_event(context,category,added,True);self._game_end_event();return True

    def advance_one_pitch(self):
        if self.snapshot.done:raise ValueError("game has stopped; no further source or old action calls are allowed")
        if self._normal_end_if_closed():return self.snapshot
        if self.snapshot.pitches_accepted>=self.max_pitches:self._abort("pitch_limit");return self.snapshot
        if self.snapshot.sequence.stage=="half_complete":self._apply(rules.AdvanceHalf())
        if self.snapshot.sequence.stage=="between_batters":self._apply(rules.BeginBatter())
        context=self._context()
        try:
            offered=self.source.next_pitch(context)
            if type(offered) is RecordedAbort:self._abort(offered.reason);return self.snapshot
            value=preflight(offered,self.snapshot.sequence.current.half)
        except StopIteration:self._abort("source_exhausted");return self.snapshot
        except InputError as error:
            reason=error.code if type(error.code) is str and error.code in SOURCE_ABORT_REASONS else "source_error"
            self._abort(reason);return self.snapshot
        except Exception:self._abort("source_error");return self.snapshot
        self._apply(rules.Pitch(value.event),value)
        if self.snapshot.sequence.stage=="awaiting_resolution":
            action,category,candidate_runs,scoring=self._resolution(value)
            if self._walkoff(value,context,category,candidate_runs,scoring):return self.snapshot
            before=self.snapshot.sequence.score
            self._apply(action)
            runs=self.snapshot.sequence.score[context.team]-before[context.team]
            self._record_completion(context,category,runs)
        if self._normal_end_if_closed():return self.snapshot
        if self.snapshot.pitches_accepted>=self.max_pitches:self._abort("pitch_limit");return self.snapshot
        if self.snapshot.sequence.stage=="half_complete":self._apply(rules.AdvanceHalf())
        return self.snapshot

    def run_to_end(self):
        while not self.snapshot.done:self.advance_one_pitch()
        return self.result()

    def result(self):return GameResult(self.snapshot)

    def events_document(self):
        return {"schema":SCHEMA,"design":DESIGN,**CAPABILITIES,"seed":self.seed,"max_pitches":self.max_pitches,
            "initial_snapshot":self.initial_snapshot.as_dict(),"events":deepcopy(self._events),"final_snapshot":self.snapshot.as_dict()}


def replay_events(document):
    """Replay accepted recorded inputs and sanitized abort markers, without RNG.

    This is an in-memory verification format, not product save/resume support.
    Full event comparison also rejects altered before/after/statistics logs.
    """
    if type(document) is not dict or document.get("schema")!=SCHEMA or document.get("design")!=DESIGN:raise ValueError("unsupported event document")
    if any(type(document.get(k)) is not type(v) or document.get(k)!=v for k,v in CAPABILITIES.items()):raise ValueError("event flags differ")
    if type(document.get("events")) is not list:raise ValueError("explicit event array required")
    inputs=[]
    for event in document["events"]:
        if type(event) is not dict:raise ValueError("event entries must be objects")
        if event.get("type")=="action" and type(event.get("action")) is dict and event["action"].get("kind")=="pitch":inputs.append(PitchInput.from_mapping(event["input"]))
        if event.get("type")=="abort" and event.get("reason")!="pitch_limit":inputs.append(RecordedAbort(event["reason"]))
    session=GameSession(document["seed"],document["max_pitches"],ScriptedSource(inputs),GameSnapshot.from_mapping(document["initial_snapshot"]))
    result=session.run_to_end()
    # Compare JSON encodings to keep booleans distinct from integers.
    from .io import canonical
    if canonical(session.events_document())!=canonical(document):raise ValueError("recorded events or snapshots do not reproduce")
    return result
