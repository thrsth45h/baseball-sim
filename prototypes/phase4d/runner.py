"""Run only approved synthetic columns; never infer game completion."""
from . import APPROVED_GAME_MAIN, CAPABILITIES, DESIGN, SCHEMA
from .fixtures import FIXTURE_ROOT, control_table, scenarios
from .io import digest, implementation_identity, reserve_output, write_json
from .model import AdvanceHalf, BeginBatter, HalfInningState, HalfSequenceState, Pitch, PitchHalfState, Resolve, action_from_mapping, pitch_half_rules, replay, step


def check_control(table):
    half = HalfInningState()
    states = {"between_batters":PitchHalfState(None,half),"pitching":PitchHalfState(pitch_half_rules.CountState(),half),
              "awaiting_resolution":PitchHalfState(pitch_half_rules.CountState(4,0,4,"walk_awarded"),half),
              "half_complete":PitchHalfState(None,HalfInningState(3))}
    actions = {"begin_batter":BeginBatter(),"pitch":Pitch("called_ball"),"resolve":Resolve("walk_forced_only"),"advance_half":AdvanceHalf()}
    for row in table:
        try: step(HalfSequenceState(1,"top",0,0,states[row["stage"]]),actions[row["kind"]])
        except ValueError:
            if row["effect"]!="reject": raise ValueError("control table expected acceptance")
        else:
            if row["effect"]=="reject": raise ValueError("control table expected rejection")


def run(output):
    identity = implementation_identity()
    control,rows = control_table(),scenarios()
    check_control(control)
    traces = []
    for row in rows:
        initial = HalfSequenceState.from_mapping(row["initial_state"])
        result = replay(initial,[action_from_mapping(a) for a in row["actions"]])
        if (result.final_state.as_dict()!=row["expected_final"] or result.final_state.score!=row["expected_score"]
                or result.final_state.stage!=row["expected_stage"]): raise ValueError("implementation disagrees with fixed column reference")
        traces.append({"id":row["id"],"initial_state":initial.as_dict(),"actions":row["actions"],**result.as_dict(),
                       "final_score":result.final_state.score,"final_stage":result.final_state.stage})
    if identity!=implementation_identity(): raise ValueError("implementation or fixtures changed during run")
    plan = {"design":DESIGN,"schema":SCHEMA,**CAPABILITIES,
            "approved_design":{"url":"https://github.com/thrsth45h/baseball-sim/pull/28","game_main":APPROVED_GAME_MAIN,"decision":"D045 limited half-sequence scope only"},
            "dependency_contracts":["phase4a-design-v0.1","phase4b-design-v0.1","phase4c-design-v0.1"],
            "rules":{"edition":"MLB Official Baseball Rules 2026","sections":["5.01(a)","5.09(e)"],
                "url":"https://mktg.mlbstatic.com/mlb/official-information/2026-official-baseball-rules.pdf","game_end_or_league_rules_adopted":False},
            "input_preconditions":["finite synthetic sequence; no unrepresented steal/wild pitch/runner out/score/interference between begin and resolve",
                "W/X/H are externally completed adjudicated plays with required touches and no extra advances/outs/errors/appeals/walk-off",
                "omitted real-world facts cannot be detected; no real-data adapter"],
            "state_semantics":{"closed_scores":"switched halves only; current half excluded","score":"derived from closed scores plus current half for batting team; never stored twice",
                "advance":"explicit after confirmed three outs; preserves displayed score and resets only new current snapshot",
                "half":"last confirmed play snapshot, not general instantaneous baseball state","external_history_proved":False,
                "persistent_exactly_once_guarantee":False,"product_save_format":False},
            "control_cells":control,"fixed_scenarios":rows,"scenario_count":len(rows),"action_count":sum(len(r["actions"]) for r in rows),
            "game_end_boundary":["no winner/final score/game termination","top ninth with home lead still accepts Advance; not a ruling to play the real bottom ninth",
                "tenth and later reset to empty bases; not MLB extra-inning runner placement or fictional league adoption"],
            "source_access":{"old_source_import_and_identity_hash":"only the seven declared old model/package sources",
                "old_io_runner_fixture_results_used":False,"research_data_or_fitted_models_used":False,"old_frozen_flags_changed":False},
            "limitations":["synthetic scores are not league scoring rates or ability effects","no general runner movement/third-out scoring/full game",
                "no probability/ability/Statcast/3d/Android integration; D029/D031/D032 OPEN"],"implementation":identity}
    document = {"design":DESIGN,"schema":SCHEMA,**CAPABILITIES,"scenarios":traces}
    report = ["# Phase 4d: 表裏切替と導出得点の限定参照","","終了判定のない有限合成半回列。試合の最終得点/勝者の判定器ではない。","",
        "| 固定列 | 回/側 | stage | away | home |","|---|---|---|---:|---:|"]
    for row in traces:
        state=row["final_state"];score=row["final_score"]
        report.append(f"| {row['id']} | {state['inning']}/{state['side']} | {row['final_stage']} | {score['away']} | {score['home']} |")
    report += ["","16制御セル/16列/100action/116分割は検査範囲であり、リーグ成績や予測精度ではない。",
        "表示scoreは切替済み累計と現在halfから導出し、Advance前後で不変。旧snapshotの残塁/得点は変更しない。",
        "Advanceはhalf_completeだけ。結果待ちの第三ストライクでは不可。切替後のBeginも明示する。",
        "9回/10回の自動終了・延長走者・勝者を作らない。実試合で次半回を行うべきという判断ではない。",
        "旧4c.stepへ旧3actionを1回ずつ委任し、旧ソース7パスをimport/hashで利用。旧I/O/fixture/結果・研究モデルはrunへ入力しない。",
        "強い合成前提とhalf snapshotを引継ぎ、省略実世界事実や古いsnapshot再投入の永続重複排除を保証しない。",
        "source_kind=synthetic_fixture; scope=partial_half_sequence_controller; connected_to_phase4a/phase4b/phase4c=true。",
        "connected_to_statcast/connected_to_phase3d/probability_model/game_end_rules_implemented=false。",
        "official_stats_generated/ability_calibrated/android_integrated/product_adoption=false。",
        "一般走塁・完全試合・試合終了/延長/勝敗・公式記録・研究/能力・Android採用は別設計。D029/D031/D032はOPEN。",""]
    out = reserve_output(output,input_roots=[FIXTURE_ROOT])
    write_json(out/"plan.json",plan);write_json(out/"traces.json",document)
    with (out/"REPORT.md").open("xb") as stream:stream.write("\n".join(report).encode("utf-8"))
    write_json(out/"SHA256SUMS.json",{p.name:digest(p) for p in sorted(out.iterdir()) if p.is_file()})
    return out
