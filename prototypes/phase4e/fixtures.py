"""Design-transcribed inputs and independent fixed-column pins, not step output."""
import json
from pathlib import Path
from . import DESIGN
from .model import HalfInningState, HalfSequenceState, ResolveSafeHit, action_from_mapping

FIXTURE_ROOT=Path(__file__).resolve().parent/"fixtures"
# Explicit destination choices from section 6, grouped by occupancy and hit value.
_PATTERN_COLUMNS=(
    ("000",1,((None,None,None),)),("000",2,((None,None,None),)),("000",3,((None,None,None),)),
    ("001",1,((None,None,3),(None,None,4))),("001",2,((None,None,3),(None,None,4))),("001",3,((None,None,4),)),
    ("010",1,((None,2,None),(None,3,None),(None,4,None))),("010",2,((None,3,None),(None,4,None))),("010",3,((None,4,None),)),
    ("011",1,((None,2,3),(None,2,4),(None,3,4),(None,4,4))),("011",2,((None,3,4),(None,4,4))),("011",3,((None,4,4),)),
    ("100",1,((2,None,None),(3,None,None),(4,None,None))),("100",2,((3,None,None),(4,None,None))),("100",3,((4,None,None),)),
    ("101",1,((2,None,3),(2,None,4),(3,None,4),(4,None,4))),("101",2,((3,None,4),(4,None,4))),("101",3,((4,None,4),)),
    ("110",1,((2,3,None),(2,4,None),(3,4,None),(4,4,None))),("110",2,((3,4,None),(4,4,None))),("110",3,((4,4,None),)),
    ("111",1,((2,3,4),(2,4,4),(3,4,4),(4,4,4))),("111",2,((3,4,4),(4,4,4))),("111",3,((4,4,4),)),
)
_Z=(1,"top",0,0,None,0,"000",0)
_L=(1,"top",11,13,None,2,"111",7)
_COLUMNS=(
    ("single_empty",_Z,"G P S1:-,-,-",(1,"top",0,0,None,0,"100",0),(0,0),"between_batters"),
    ("double_empty",_Z,"G P S2:-,-,-",(1,"top",0,0,None,0,"010",0),(0,0),"between_batters"),
    ("triple_empty",_Z,"G P S3:-,-,-",(1,"top",0,0,None,0,"001",0),(0,0),"between_batters"),
    ("single_first_second",(1,"top",0,0,None,0,"100",0),"G P S1:2,-,-",(1,"top",0,0,None,0,"110",0),(0,0),"between_batters"),
    ("single_first_third",(1,"top",0,0,None,0,"100",0),"G P S1:3,-,-",(1,"top",0,0,None,0,"101",0),(0,0),"between_batters"),
    ("single_second_holds",(2,"top",11,13,None,1,"010",7),"G P S1:-,2,-",(2,"top",11,13,None,1,"110",7),(18,13),"between_batters"),
    ("single_third_holds",(1,"top",0,0,None,0,"001",0),"G P S1:-,-,3",(1,"top",0,0,None,0,"101",0),(0,0),"between_batters"),
    ("single_third_scores",(1,"top",0,0,None,0,"001",0),"G P S1:-,-,4",(1,"top",0,0,None,0,"100",1),(1,0),"between_batters"),
    ("double_first_third",(1,"top",0,0,None,0,"100",0),"G P S2:3,-,-",(1,"top",0,0,None,0,"011",0),(0,0),"between_batters"),
    ("double_first_scores",(1,"top",0,0,None,0,"100",0),"G P S2:4,-,-",(1,"top",0,0,None,0,"010",1),(1,0),"between_batters"),
    ("double_third_holds",(1,"top",0,0,None,1,"001",0),"G P S2:-,-,3",(1,"top",0,0,None,1,"011",0),(0,0),"between_batters"),
    ("loaded_single_one",_L,"G P S1:2,3,4",(1,"top",11,13,None,2,"111",8),(19,13),"between_batters"),
    ("loaded_single_two",_L,"G P S1:3,4,4",(1,"top",11,13,None,2,"101",9),(20,13),"between_batters"),
    ("loaded_single_three",_L,"G P S1:4,4,4",(1,"top",11,13,None,2,"100",10),(21,13),"between_batters"),
    ("loaded_triple_bottom",(2,"bottom",11,13,None,1,"111",7),"G P S3:4,4,4",(2,"bottom",11,13,None,1,"001",10),(11,23),"between_batters"),
    ("fair_pending",_Z,"G P",(1,"top",0,0,"AP",0,"000",0),(0,0),"awaiting_resolution"),
    ("chained_hits",_Z,"G P S1:-,-,- G P S2:4,-,-",(1,"top",0,0,None,0,"010",1),(1,0),"between_batters"),
    ("hit_retire_switch",(1,"top",11,13,None,2,"001",7),"G P S1:-,-,4 G O RX A",(1,"bottom",19,13,None,0,"000",0),(19,13),"between_batters"),
)


def parse_json(text):
    def pairs(items):
        result={}
        for key,value in items:
            if key in result:raise ValueError("duplicate JSON key: "+key)
            result[key]=value
        return result
    def reject(value):raise ValueError("non-finite JSON constant: "+value)
    return json.loads(text,object_pairs_hook=pairs,parse_constant=reject)


def load(name,key):
    value=parse_json((FIXTURE_ROOT/name).read_text(encoding="utf-8"))
    if type(value) is not dict or set(value)!={"design",key} or value["design"]!=DESIGN or type(value[key]) is not list:
        raise ValueError("invalid fixed fixture document")
    return value[key]


def _state(spec):
    i,s,a,h,c,o,b,r=spec
    count=None if c is None else {"balls":0,"strikes":0,"pitches_seen":1,"status":"fair_play_pending"}
    return {"inning":i,"side":s,"closed_away_runs":a,"closed_home_runs":h,"current":{"count":count,"half":{"outs":o,"bases":[v=="1" for v in b],"runs":r}}}


def _actions(sequence):
    simple={"G":{"kind":"begin_batter"},"P":{"kind":"pitch","event":"fair_ball_handoff"},
            "O":{"kind":"pitch","event":"foul_fly_caught"},"RX":{"kind":"resolve","completed_play":"batter_out_runners_hold"},"A":{"kind":"advance_half"}}
    result=[]
    for token in sequence.split():
        if token in simple:result.append(dict(simple[token]))
        else:
            hit,dest=token.split(":")
            result.append({"kind":"resolve_safe_hit","hit_bases":int(hit[1:]),"runner_destinations":[None if d=="-" else int(d) for d in dest.split(",")]})
    return result


def scenarios():
    rows=load("scenarios.json","scenarios")
    if len(rows)!=18:raise ValueError("fixed scenario count changed")
    for row,(name,initial,actions,final,score,stage) in zip(rows,_COLUMNS):
        if type(row) is not dict or set(row)!={"id","initial_state","actions","expected_final","expected_score","expected_stage"}:raise ValueError("fixed scenario fields changed")
        before=HalfSequenceState.from_mapping(row["initial_state"]).as_dict();after=HalfSequenceState.from_mapping(row["expected_final"]).as_dict()
        if type(row["actions"]) is not list:raise ValueError("fixed actions require an array")
        actual=[action_from_mapping(a).as_dict() for a in row["actions"]];value=row["expected_score"]
        if type(value) is not dict or set(value)!={"away","home"} or any(type(v) is not int or v<0 for v in value.values()):raise ValueError("fixed scores require exact integers")
        if (type(row["id"]) is not str or row["id"]!=name or before!=_state(initial) or actual!=_actions(actions) or after!=_state(final)
                or value!={"away":score[0],"home":score[1]} or type(row["expected_stage"]) is not str or row["expected_stage"]!=stage):
            raise ValueError("fixed scenario differs from approved design columns")
    return rows


def pattern_table():
    rows=load("patterns.json","patterns")
    expected={(tuple(b=="1" for b in bits),hit,dest) for bits,hit,choices in _PATTERN_COLUMNS for dest in choices}
    keys=set()
    for row in rows:
        if type(row) is not dict or set(row)!={"before","hit_bases","runner_destinations","after","runs_added"}:raise ValueError("invalid fixed pattern keys")
        before,after=(HalfInningState.from_mapping(row[k]) for k in ("before","after"))
        action=action_from_mapping({"kind":"resolve_safe_hit","hit_bases":row["hit_bases"],"runner_destinations":row["runner_destinations"]})
        key=(before.bases,action.hit_bases,action.runner_destinations)
        if (before.outs!=0 or before.runs!=0 or after.outs!=0 or type(row["runs_added"]) is not int or not 0<=row["runs_added"]<=3
                or after.runs!=row["runs_added"] or key not in expected or key in keys):raise ValueError("fixed safe-hit pattern differs from design")
        keys.add(key)
    if keys!=expected or len(rows)!=48:raise ValueError("fixed patterns require all 48 design choices")
    return rows


def control_table():
    rows=load("control.json","control")
    permitted={("between_batters","begin_batter"):"delegate_old",("pitching","pitch"):"delegate_old",
        ("awaiting_resolution","resolve"):"delegate_old_compatibility",("half_complete","advance_half"):"delegate_old",
        ("awaiting_resolution","resolve_safe_hit"):"fair_and_destinations"}
    expected=[{"stage":s,"kind":k,"effect":permitted.get((s,k),"reject")} for s in ("between_batters","pitching","awaiting_resolution","half_complete")
              for k in ("begin_batter","pitch","resolve","advance_half","resolve_safe_hit")]
    if rows!=expected:raise ValueError("fixed control table differs from approved 20 cells")
    return rows
