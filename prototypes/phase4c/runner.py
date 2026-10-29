"""Fixed synthetic control exercise, with explicit allowed old rule delegation."""
from . import APPROVED_GAME_MAIN, CAPABILITIES, DESIGN, SCHEMA
from .fixtures import FIXTURE_ROOT, compatibility_table, control_table, scenarios
from .io import digest, implementation_identity, reserve_output, write_json
from .model import BeginBatter, CountState, HalfInningState, Pitch, PitchHalfState, Resolve, action_from_mapping, replay, step


def check_tables(control, compatibility):
    """Exercise static table decisions, without generating expected tables."""
    half = HalfInningState()
    waiting = {"walk_awarded": CountState(4, 0, 4, "walk_awarded"),
               "strikeout_out": CountState(0, 3, 3, "strikeout_out"),
               "caught_foul_out": CountState(0, 0, 1, "caught_foul_out"),
               "fair_play_pending": CountState(0, 0, 1, "fair_play_pending")}
    stages = {"between_batters": PitchHalfState(None, half), "pitching": PitchHalfState(CountState(), half),
              "awaiting_resolution": PitchHalfState(waiting["walk_awarded"], half),
              "half_complete": PitchHalfState(None, HalfInningState(3))}
    actions = {"begin_batter": BeginBatter(), "pitch": Pitch("called_ball"), "resolve": Resolve("walk_forced_only")}
    for row in control:
        try: step(stages[row["stage"]], actions[row["kind"]])
        except ValueError:
            if row["effect"] != "reject": raise ValueError("control table expected acceptance")
        else:
            if row["effect"] == "reject": raise ValueError("control table expected rejection")
    for row in compatibility:
        try: step(PitchHalfState(waiting[row["status"]], half), Resolve(row["completed_play"]))
        except ValueError:
            if row["allowed"]: raise ValueError("compatibility table expected acceptance")
        else:
            if not row["allowed"]: raise ValueError("compatibility table expected rejection")


def run(output):
    identity = implementation_identity()
    control, compatibility, rows = control_table(), compatibility_table(), scenarios()
    check_tables(control, compatibility)
    traces = []
    for row in rows:
        initial = PitchHalfState.from_mapping(row["initial_state"])
        result = replay(initial, [action_from_mapping(value) for value in row["actions"]])
        if result.final_state.as_dict() != row["expected_final"] or result.final_state.stage != row["expected_stage"]:
            raise ValueError("implementation disagrees with fixed scenario reference")
        traces.append({"id": row["id"], "initial_state": initial.as_dict(), "actions": row["actions"],
                       **result.as_dict(), "final_stage": result.final_state.stage})
    if identity != implementation_identity(): raise ValueError("implementation or fixtures changed during run")
    plan = {"design": DESIGN, "schema": SCHEMA, **CAPABILITIES,
            "approved_design": {"url": "https://github.com/thrsth45h/baseball-sim/pull/25", "game_main": APPROVED_GAME_MAIN,
                                "decision": "D044 limited explicit integration only"},
            "dependency_contracts": ["phase4a-design-v0.1", "phase4b-design-v0.1"],
            "input_preconditions": ["synthetic sequence has no unrepresented steal, wild/passed pitch, runner out, score or interference between begin and resolve",
                "omitted real-world events cannot be detected by this projection",
                "Resolve explicitly supplies completed adjudicated W/X/H facts with proper touches and no extra movement/outs/errors/appeals/walk-off",
                "count terminal status alone does not establish those facts; no data-to-action adapter"],
            "state_semantics": {"half": "last confirmed play snapshot, not general baseball instantaneous state",
                "awaiting_resolution": "orchestration boundary, not an assertion that the legal plate appearance remains incomplete",
                "count_consumed_on_resolve": True, "next_batter_requires_begin": True,
                "closed_half": "count None; stranded bases/runs retained; no next-half reset",
                "external_snapshot_history_proved": False, "persistent_exactly_once_guarantee": False,
                "trace": "action/before/after/runs_added; ordered with no local numbering", "product_save_format": False},
            "control_cells": control, "compatibility_cells": compatibility, "fixed_scenarios": rows,
            "scenario_count": len(rows), "action_count": sum(len(row["actions"]) for row in rows),
            "unsupported": ["automatic terminal-to-play selection", "general runner movement/singles/double plays/third-out scoring",
                "persistent play IDs, duplicate network delivery/checkpoint deduplication", "full half/game generation and official personal statistics",
                "research/Statcast/3d, probability/ability calibration, Android/UI/DB/save integration"],
            "source_access": {"old_model_source_import_and_identity_hash": "only five named dependency source paths",
                "old_fixture_or_result_data_used_by_runner": False, "research_data_or_models_used": False,
                "old_frozen_flags_changed": False},
            "limitations": ["synthetic fixtures are not event frequencies or league scoring rates",
                "pure step can be called again on an old snapshot; no external exactly-once guarantee",
                "Python reference does not select Android technology; D029/D031/D032 OPEN"], "implementation": identity}
    document = {"design": DESIGN, "schema": SCHEMA, **CAPABILITIES, "scenarios": traces}
    report = ["# Phase 4c: 投球と半回の明示接続", "", "合成アクションで旧4a/4bへ明示委任する限定検算。", "",
              "| 固定列 | stage | count status | outs | 一二三塁 | 半回得点 |", "|---|---|---|---:|---|---:|"]
    for row in traces:
        state = row["final_state"]
        bits = "".join("1" if b else "0" for b in state["half"]["bases"])
        status = "null" if state["count"] is None else state["count"]["status"]
        report.append(f"| {row['id']} | {row['final_stage']} | {status} | {state['half']['outs']} | {bits} | {state['half']['runs']} |")
    report += ["", "制御12セル/互換12セル、18列/82action/100分割は試験範囲であり、リーグ成績やモデル精度ではない。",
        "ResolveのW/X/Hは追加事実が完了・裁定済みという明示入力。statusだけから自動選択しない。",
        "halfは最後に確定反映したsnapshotで、現実の瞬間状態や打席終了時点を表すものではない。",
        "beginからresolveまで盗塁/暴投等の未対応イベントなしと置く合成前提。省略された実世界事実は検出できない。",
        "解決後も次打者は明示Begin。三アウトで残塁/得点を保持して停止し、次半回へ自動遷移しない。",
        "連続Resolve拒否は古いsnapshotの再投入・外部二重送信・並行更新のexactly-once保証ではない。",
        "旧型/stepとsource5パスを許可して利用する一方、旧fixture/結果・研究/3dはrunへ入力しない。",
        "source_kind=synthetic_fixture; scope=partial_pitch_half_orchestration。",
        "connected_to_phase4a=true; connected_to_phase4b=true; connected_to_statcast=false; connected_to_phase3d=false。",
        "probability_model=false; official_stats_generated=false; ability_calibrated=false; android_integrated=false; product_adoption=false。",
        "一般走塁・完全試合・能力・Android/UI/セーブの採用は別設計。D029/D031/D032はOPEN。", ""]
    out = reserve_output(output, input_roots=[FIXTURE_ROOT])
    write_json(out/"plan.json", plan)
    write_json(out/"traces.json", document)
    with (out/"REPORT.md").open("xb") as stream: stream.write("\n".join(report).encode("utf-8"))
    write_json(out/"SHA256SUMS.json", {p.name: digest(p) for p in sorted(out.iterdir()) if p.is_file()})
    return out
