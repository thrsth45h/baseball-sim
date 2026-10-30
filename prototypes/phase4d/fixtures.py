"""Static design cells/columns, independently pinned without executing steps."""
import json
from pathlib import Path
from types import MappingProxyType
from . import DESIGN
from .model import HalfSequenceState, action_from_mapping

FIXTURE_ROOT = Path(__file__).resolve().parent/"fixtures"
_PITCH = MappingProxyType(dict(zip("BCMTFUOP", ("called_ball", "called_strike_caught", "swinging_strike_caught", "foul_tip_caught", "ordinary_foul_uncaught", "bunt_foul_uncaught", "foul_fly_caught", "fair_ball_handoff"))))
_PLAY = MappingProxyType({"RW": "walk_forced_only", "RX": "batter_out_runners_hold", "RH": "legal_over_fence_home_run"})
_COUNT = MappingProxyType({"A0": (0,0,0,"active"), "A1": (1,0,1,"active"), "AW": (4,0,4,"walk_awarded"), "AK": (0,3,3,"strikeout_out"), "AP": (0,0,1,"fair_play_pending")})
_Z = (1,"top",0,0,None,0,"000",0)
_L = (1,"top",11,13,None,2,"111",7)
_COLUMNS = (
    ("empty_top", _Z, "", _Z, (0,0), "between_batters"),
    ("begin_top", _Z, "G", (1,"top",0,0,"A0",0,"000",0), (0,0), "pitching"),
    ("bottom_ball", (2,"bottom",3,5,None,0,"000",0), "G B", (2,"bottom",3,5,"A1",0,"000",0), (3,5), "pitching"),
    ("walk_pending", (1,"top",2,3,None,0,"111",7), "G B B B B", (1,"top",2,3,"AW",0,"111",7), (9,3), "awaiting_resolution"),
    ("loaded_walk_top", _L, "G B B B B RW", (1,"top",11,13,None,2,"111",8), (19,13), "between_batters"),
    ("grand_slam_bottom", (2,"bottom",11,13,None,1,"111",7), "G P RH", (2,"bottom",11,13,None,1,"000",11), (11,24), "between_batters"),
    ("third_out_wait", _L, "G C C C", (1,"top",11,13,"AK",2,"111",7), (18,13), "awaiting_resolution"),
    ("closed_no_advance", _L, "G C C C RX", (1,"top",11,13,None,3,"111",7), (18,13), "half_complete"),
    ("top_switch", _L, "G C C C RX A", (1,"bottom",18,13,None,0,"000",0), (18,13), "between_batters"),
    ("bottom_switch", (2,"bottom",11,13,None,3,"101",4), "A", (3,"top",11,17,None,0,"000",0), (11,17), "between_batters"),
    ("zero_switch", (1,"top",0,0,None,3,"111",0), "A", (1,"bottom",0,0,None,0,"000",0), (0,0), "between_batters"),
    ("next_begin", _L, "G C C C RX A G", (1,"bottom",18,13,"A0",0,"000",0), (18,13), "pitching"),
    ("two_scoring_halves", _Z, "G P RH D3 A G P RH D3 A", (2,"top",1,1,None,0,"000",0), (1,1), "between_batters"),
    ("two_zero_halves", _Z, "D3 A D3 A", (2,"top",0,0,None,0,"000",0), (0,0), "between_batters"),
    ("walk_close_switch", _L, "G B B B B RW D A", (1,"bottom",19,13,None,0,"000",0), (19,13), "between_batters"),
    ("switch_then_pending", (1,"top",11,13,None,3,"101",4), "A G P", (1,"bottom",15,13,"AP",0,"000",0), (15,13), "awaiting_resolution"),
)


def parse_json(text):
    def pairs(items):
        result = {}
        for key,value in items:
            if key in result: raise ValueError("duplicate JSON key: "+key)
            result[key] = value
        return result
    def reject(value): raise ValueError("non-finite JSON constant: "+value)
    return json.loads(text, object_pairs_hook=pairs, parse_constant=reject)


def load(name,key):
    value = parse_json((FIXTURE_ROOT/name).read_text(encoding="utf-8"))
    if type(value) is not dict or set(value) != {"design",key} or value["design"] != DESIGN or type(value[key]) is not list:
        raise ValueError("invalid fixed fixture document")
    return value[key]


def _state(spec):
    inning,side,a,h,count,outs,bits,runs = spec
    count = None if count is None else dict(zip(("balls","strikes","pitches_seen","status"),_COUNT[count]))
    return {"inning":inning,"side":side,"closed_away_runs":a,"closed_home_runs":h,
            "current":{"count":count,"half":{"outs":outs,"bases":[b=="1" for b in bits],"runs":runs}}}


def _actions(sequence):
    result = []
    for token in sequence.split():
        if token in ("D","D3"):
            result.extend(_actions("G O RX" if token=="D" else "G O RX G O RX G O RX"))
        elif token in ("G","A"): result.append({"kind":"begin_batter" if token=="G" else "advance_half"})
        elif token in _PITCH: result.append({"kind":"pitch","event":_PITCH[token]})
        else: result.append({"kind":"resolve","completed_play":_PLAY[token]})
    return result


def scenarios():
    rows = load("scenarios.json","scenarios")
    if len(rows)!=16: raise ValueError("fixed scenario count changed")
    for row,(name,initial,actions,final,score,stage) in zip(rows,_COLUMNS):
        if type(row) is not dict or set(row)!={"id","initial_state","actions","expected_final","expected_score","expected_stage"}:
            raise ValueError("fixed scenario fields changed")
        before = HalfSequenceState.from_mapping(row["initial_state"]).as_dict()
        after = HalfSequenceState.from_mapping(row["expected_final"]).as_dict()
        if type(row["actions"]) is not list: raise ValueError("fixed actions require an array")
        actual = [action_from_mapping(a).as_dict() for a in row["actions"]]
        value = row["expected_score"]
        if type(value) is not dict or set(value)!={"away","home"} or any(type(v) is not int or v<0 for v in value.values()):
            raise ValueError("fixed expected_score must contain exact nonnegative integers")
        if (type(row["id"]) is not str or row["id"]!=name or before!=_state(initial) or actual!=_actions(actions)
                or after!=_state(final) or value!={"away":score[0],"home":score[1]}
                or type(row["expected_stage"]) is not str or row["expected_stage"]!=stage):
            raise ValueError("fixed scenario differs from approved design columns")
    return rows


def control_table():
    rows = load("control.json","control")
    permitted = {("between_batters","begin_batter"):"delegate_old",("pitching","pitch"):"delegate_old",
                 ("awaiting_resolution","resolve"):"delegate_old_compatibility",("half_complete","advance_half"):"advance"}
    expected = [{"stage":stage,"kind":kind,"effect":permitted.get((stage,kind),"reject")}
                for stage in ("between_batters","pitching","awaiting_resolution","half_complete")
                for kind in ("begin_batter","pitch","resolve","advance_half")]
    if rows!=expected: raise ValueError("fixed control table differs from approved 16 cells")
    return rows
