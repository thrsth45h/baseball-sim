"""Run fixed completed-safe-hit declarations, never predict or adjudicate them."""
from . import APPROVED_GAME_MAIN, CAPABILITIES, DESIGN, SCHEMA
from .fixtures import FIXTURE_ROOT, control_table, pattern_table, scenarios
from .io import digest, implementation_identity, reserve_output, write_json
from .model import AdvanceHalf, BeginBatter, HalfInningState, HalfSequenceState, Pitch, PitchHalfState, Resolve, ResolveSafeHit, action_from_mapping, replay, sequence_rules, step


def check_control(table):
    count=sequence_rules.pitch_half_rules.CountState
    empty=HalfInningState()
    states={"between_batters":PitchHalfState(None,empty),"pitching":PitchHalfState(count(),empty),
        "awaiting_resolution":PitchHalfState(count(0,0,1,"fair_play_pending"),empty),"half_complete":PitchHalfState(None,HalfInningState(3))}
    actions={"begin_batter":BeginBatter(),"pitch":Pitch("called_ball"),"resolve":Resolve("batter_out_runners_hold"),
        "advance_half":AdvanceHalf(),"resolve_safe_hit":ResolveSafeHit(1,(None,None,None))}
    for row in table:
        try:step(HalfSequenceState(1,"top",0,0,states[row["stage"]]),actions[row["kind"]])
        except ValueError:
            if row["effect"]!="reject":raise ValueError("control table expected acceptance")
        else:
            if row["effect"]=="reject":raise ValueError("control table expected rejection")


def run(output):
    identity=implementation_identity()
    control,patterns,rows=control_table(),pattern_table(),scenarios()
    check_control(control)
    count=sequence_rules.pitch_half_rules.CountState(0,0,1,"fair_play_pending")
    for row in patterns:
        half=HalfInningState.from_mapping(row["before"])
        result=step(HalfSequenceState(1,"top",0,0,PitchHalfState(count,half)),ResolveSafeHit(row["hit_bases"],tuple(row["runner_destinations"])))
        if result.current.half.as_dict()!=row["after"] or result.current.count is not None or result.current.half.runs-half.runs!=row["runs_added"]:
            raise ValueError("implementation disagrees with static safe-hit pattern")
    traces=[]
    for row in rows:
        initial=HalfSequenceState.from_mapping(row["initial_state"])
        result=replay(initial,[action_from_mapping(a) for a in row["actions"]])
        if result.final_state.as_dict()!=row["expected_final"] or result.final_state.score!=row["expected_score"] or result.final_state.stage!=row["expected_stage"]:
            raise ValueError("implementation disagrees with fixed scenario")
        traces.append({"id":row["id"],"initial_state":initial.as_dict(),"actions":row["actions"],**result.as_dict(),
            "final_score":result.final_state.score,"final_stage":result.final_state.stage})
    if identity!=implementation_identity():raise ValueError("implementation or fixtures changed during run")
    plan={"design":DESIGN,"schema":SCHEMA,**CAPABILITIES,
        "approved_design":{"url":"https://github.com/thrsth45h/baseball-sim/pull/31","game_main":APPROVED_GAME_MAIN,"decision":"D046 limited completed safe-hit scope only"},
        "dependency_contracts":["phase4a-design-v0.1","phase4b-design-v0.1","phase4c-design-v0.1","phase4d-design-v0.1"],
        "rules":{"edition":"MLB Official Baseball Rules 2026","sections":["5.06(a)/(b)","5.09(b)(9)","9.05","9.06(a)/(b)"],
            "url":"https://mktg.mlbstatic.com/mlb/official-information/2026-official-baseball-rules.pdf","general_scoring_rules_adopted":False},
        "input_preconditions":["hit value 1/2/3 externally adjudicated; batter finishes at that base with no throw/error extra advance",
            "all declared runners safe with proper touches, all runs confirmed; no intermediate passing/out/error/interference/appeal/walk-off",
            "no unrepresented steal/wild pitch/runner out/score between begin and resolve; all inherited strong synthetic conditions apply"],
        "state_semantics":{"state":"exact old HalfSequenceState; five stored fields and derived score unchanged",
            "hit_value":"input, never inferred from batter final base","destination_validation":"final-position contradictions only, not full history legality",
            "none":"exact pre-play empty slot","holds_allowed":True,"multiple_home_arrivals_allowed":True,
            "omitted_real_world_facts_detected":False,"persistent_exactly_once_guarantee":False,"product_save_format":False},
        "control_cells":control,"accepted_patterns":patterns,"fixed_scenarios":rows,
        "scenario_count":len(rows),"action_count":sum(len(r["actions"]) for r in rows),
        "source_access":{"old_source_import_and_identity_hash":"only nine named old package/model source paths",
            "old_io_runner_fixture_results_used":False,"research_data_or_fitted_models_used":False,"old_frozen_flags_changed":False},
        "limitations":["48 accepted patterns and synthetic scores are not probabilities or league frequencies",
            "no hit/runner decision model, general adjudication, outs/errors/third-out scoring, game-end/winner or official individual stats",
            "inherited 9th/10th behavior has no automatic game ending or extra-inning runner; not real-game-rule adoption",
            "no Statcast/3d/probability/ability/Android/UI/DB/save adoption; D029/D031/D032 OPEN"],"implementation":identity}
    document={"design":DESIGN,"schema":SCHEMA,**CAPABILITIES,"scenarios":traces}
    report=["# Phase 4e: 確定済み全員safe安打と明示行先","","安打価値と各行先は裁定済みの入力で、予測・裁定した結果ではない。","",
        "| 固定列 | stage | away | home |","|---|---|---:|---:|"]
    for row in traces:report.append(f"| {row['id']} | {row['final_stage']} | {row['final_score']['away']} | {row['final_score']['home']} |")
    report += ["","制御20セル/48受理パターン/18列60action78分割は検査範囲で、リーグ成績やモデル精度ではない。",
        "打者最終塁は外部裁定済み安打価値1/2/3と等しい対象条件。最終塁から公式安打価値を判定しない。",
        "各走者の据置き・複数得点を許し、None不一致・逆走・最終位置衝突/順序逆転を拒否する。途中触塁/追越し等の合法性は証明しない。",
        "fair待ちの新hitだけがcountを消費。outs/回/側/closedを保持し、既存走者の本塁到達数を攻撃側へ加点。次打者Beginは明示。",
        "旧state/4actionはそのまま、旧parser/stepへ委任。旧9sourceのimport/hashと旧I/O/fixture/結果・研究非利用を区別する。",
        "省略実世界事実や古いsnapshot再投入の永続重複排除を保証せず、halfは最後に確定反映したsnapshot。",
        "source_kind=synthetic_fixture; scope=partial_completed_safe_hit_projection; connected_to_phase4a/phase4b/phase4c/phase4d=true。",
        "connected_to_statcast/connected_to_phase3d/probability_model/game_end_rules_implemented=false。",
        "official_stats_generated/ability_calibrated/android_integrated/product_adoption=false。",
        "旧4dの9回以降の空塁切替/終了判定なしを継承し、サヨナラ・勝敗や公式記録を扱わない。D029/D031/D032はOPEN。",""]
    out=reserve_output(output,input_roots=[FIXTURE_ROOT])
    write_json(out/"plan.json",plan);write_json(out/"traces.json",document)
    with (out/"REPORT.md").open("xb") as stream:stream.write("\n".join(report).encode("utf-8"))
    write_json(out/"SHA256SUMS.json",{p.name:digest(p) for p in sorted(out.iterdir()) if p.is_file()})
    return out
