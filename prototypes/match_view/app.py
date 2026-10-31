"""Display and command adapter. Baseball transitions stay in GameSession."""
import re
from prototypes.phase4f.engine import GameSession
from prototypes.phase4f.protocol import validate_seed
from prototypes.phase4f.runner import REASON_LABELS
from prototypes.phase4f.state import TEAMS, batter_id, teams_document

PITCH_LABELS={"called_ball":"ボール","called_strike_caught":"見逃しストライク","swinging_strike_caught":"空振り",
    "ordinary_foul_uncaught":"ファウル","fair_ball_handoff":"フェア","bunt_foul_uncaught":"バントファウル",
    "foul_fly_caught":"ファウル捕球","foul_tip_caught":"ファウルチップ"}
PA_LABELS={"out_hold":"打者アウト","safe_hit":"safe到達","home_run":"本塁打イベント","walk":"四球","strikeout":"三振"}


class CommandError(ValueError):
    def __init__(self,message,status=400):
        super().__init__(message);self.status=status


def seed_from_text(value):
    # JSON strings avoid the browser's Number rounding above 2**53.
    if type(value) is not str or re.fullmatch(r"0|[1-9][0-9]{0,19}",value) is None:raise CommandError("seedは0以上2^64未満の整数で入力してください。")
    try:return validate_seed(int(value))
    except ValueError:raise CommandError("seedは0以上2^64未満の整数で入力してください。") from None


def completed_pa(snapshot):return sum(c.completed_plate_appearances for c in snapshot.batter_counts)


class MatchController:
    def __init__(self,game=None):
        if game is not None and type(game) is not GameSession:raise ValueError("exact GameSession required")
        self.game=GameSession(seed=42) if game is None else game

    def apply_command(self,payload):
        if type(payload) is not dict or type(payload.get("command")) is not str:raise CommandError("操作を確認してください。")
        command=payload["command"]
        if command=="new":
            if set(payload)!={"command","seed"}:raise CommandError("新しい試合にはseedだけを指定してください。")
            seed=seed_from_text(payload["seed"])
            self.game=GameSession(seed=seed)
        else:
            if set(payload)!={"command"} or command not in ("pitch","plate_appearance","finish"):raise CommandError("操作を確認してください。")
            if self.game.snapshot.done:raise CommandError("試合は停止しています。新しい試合を開始してください。",409)
            initial_pa=completed_pa(self.game.snapshot)
            while True:
                self.game.advance_one_pitch()
                if command=="pitch" or self.game.snapshot.done:break
                if command=="plate_appearance" and completed_pa(self.game.snapshot)>initial_pa:break
        return self.state()

    def state(self):
        snapshot=self.game.snapshot;seq=snapshot.sequence;current=None
        if not snapshot.done:
            team=seq.batting_team;slot=snapshot.batting_indices[TEAMS.index(team)];count=seq.current.count
            name=next(t for t in teams_document() if t["id"]==team)["lineup"][slot]["display_name"]
            current={"inning":seq.inning,"side":seq.side,"half_text":str(seq.inning)+"回"+("表" if seq.side=="top" else "裏"),
                "team":team,"batter_id":batter_id(team,slot),"batter_name":name,"slot":slot+1,
                "balls":0 if count is None else count.balls,"strikes":0 if count is None else count.strikes,
                "outs":seq.current.half.outs,"bases":list(seq.current.half.bases)}
        recent=[]
        for event in self.game.events:
            kind=event["type"];detail=""
            if kind=="action" and event["action"]["kind"]=="pitch":
                text=PITCH_LABELS.get(event["action"]["event"],"投球");context=event["context"]
                detail=str(context["inning"])+"回"+("表" if context["side"]=="top" else "裏")+" · "+context["batter_id"].replace("_"," ")
            elif kind=="plate_appearance_completed":text=PA_LABELS[event["category"]]+(" · "+str(event["runs_added"])+"得点" if event["runs_added"] else "")
            elif kind=="game_end":text=REASON_LABELS[event["reason"]]
            elif kind=="abort":text=REASON_LABELS[event["reason"]]
            else:continue
            recent.append({"index":event["event_index"],"pitch":event["pitches_accepted"],"text":text,"detail":detail,"kind":kind})
        reason=REASON_LABELS.get(snapshot.reason)
        if snapshot.status=="completed":
            outcome={"away_win":"仮Awayの勝利","home_win":"仮Homeの勝利","draw":"試作用引分"}[snapshot.outcome]
        elif snapshot.status=="aborted":outcome="未完了"
        else:outcome=None
        return {"seed":str(self.game.seed),"status":snapshot.status,"done":snapshot.done,"outcome":outcome,"reason":reason,
            "score":snapshot.score,"pitches_accepted":snapshot.pitches_accepted,"completed_pa":completed_pa(snapshot),"current":current,
            "line_score":{"away":list(snapshot.line_score[::2]),"home":list(snapshot.line_score[1::2]),
                "walkoff":snapshot.reason is not None and snapshot.reason.startswith("walkoff_")},"recent":recent[-16:]}
