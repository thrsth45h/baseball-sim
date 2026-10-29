"""Run approved fixed synthetic plays; no RNG, Phase4a or research imports."""
from . import APPROVED_GAME_MAIN, CAPABILITIES, DESIGN, SCHEMA
from .fixtures import FIXTURE_ROOT, scenarios, transition_table
from .io import digest, implementation_identity, reserve_output, write_json
from .model import HalfInningState, replay, step


def run(output):
    identity = implementation_identity()
    rows, table = scenarios(), transition_table()
    # Complete validation/calculation before reserving any output directory.
    for row in table:
        before = HalfInningState.from_mapping(row["before"])
        after = step(before, row["event"])
        if after.as_dict() != row["after"] or after.runs-before.runs != row["runs_added"]:
            raise ValueError("implementation disagrees with static transition reference")
    traces = []
    for row in rows:
        initial = HalfInningState.from_mapping(row["initial_state"])
        result = replay(initial, row["events"])
        if result.final_state.as_dict() != row["expected_final"]:
            raise ValueError("implementation disagrees with fixed scenario reference")
        traces.append({"id": row["id"], "initial_state": initial.as_dict(), "events": row["events"], **result.as_dict()})
    if identity != implementation_identity():
        raise ValueError("implementation or fixtures changed during run")
    plan = {"design": DESIGN, "schema": SCHEMA, **CAPABILITIES,
            "approved_design": {"url": "https://github.com/thrsth45h/baseball-sim/pull/22",
                                "game_main": APPROVED_GAME_MAIN, "decision": "D043 limited reference only"},
            "rules": {"edition": "MLB Official Baseball Rules 2026",
                      "url": "https://mktg.mlbstatic.com/mlb/official-information/2026-official-baseball-rules.pdf",
                      "sections": ["5.05(b)(1)", "5.06(b)(1)", "5.06(b)(3)(B)", "5.06(b)(4)(A)",
                                   "5.05(a)(5)", "5.08(a)/(b)", "5.09(e)"],
                      "complete_league_rules_adopted": False},
            "input_preconditions": ["completed, adjudicated synthetic plays, not incomplete walk/strikeout/flyout labels",
                "all required bases legally touched, no additional advances, errors, runner outs, interference or appeals",
                "no walk-off condition; H is a legal over-fence fair home run with all scoring confirmed",
                "X confirms only batter out and all other runners hold; not a general strikeout/flyout rule"],
            "state_semantics": {"bases_order": ["first", "second", "third"], "runs": "runs already confirmed in this half inning",
                "arbitrary_external_start": True, "history_reachability_proved": False,
                "three_outs": "stopped snapshot retains stranded bases and runs; no automatic reset",
                "trace": "ordered immutable entries; event/before/after/runs_added, no chunk-local numbering",
                "memory_chunk_replay": True, "product_save_format": False},
            "transition_count": len(table), "scenario_count": len(rows), "fixed_scenarios": rows,
            "unsupported": ["Phase4a status adapters and statistical/trajectory result conversion",
                "singles/doubles/triples, intentional/automatic walk/HBP, runner decisions, double plays",
                "tag-up/steal/wild pitch/passed ball/error/runner out/interference/appeal/walk-off",
                "same-play third-out scoring decisions, runner IDs/batting order, full game/official personal stats"],
            "limitations": ["fixed synthetic counts are not league scoring rates or ability effects",
                "no probability/ability calibration, no EV/LA backfill, no old model imports",
                "Python reference does not select Android language/architecture/DB; D029/D031/D032 OPEN"],
            "implementation": identity}
    document = {"design": DESIGN, "schema": SCHEMA, **CAPABILITIES, "scenarios": traces}
    report = ["# Phase 4b: 塁・アウト・得点の限定参照", "",
              "完了・裁定済みの合成3プレーを適用した検算。発生確率やリーグ得点率を推定していない。", "",
              "| 固定列 | outs | 一二三塁の占有 | 半回得点 |", "|---|---:|---|---:|"]
    for row in traces:
        state = row["final_state"]
        bits = "".join("1" if b else "0" for b in state["bases"])
        report.append(f"| {row['id']} | {state['outs']} | {bits} | {state['runs']} |")
    report += ["", "72静的遷移・16列/30イベント/46分割位置は試験範囲で、リーグ得点や公式個人成績ではない。",
        "四球は追加進塁なし、Xは打者のみアウト/走者据置き、Hは合法本塁打と全正規触塁/得点の確定が前提。",
        "第三アウト後も残塁/得点を保持して停止し、次半回へ自動リセットしない。",
        "任意の外部開始局面を受けるが、過去履歴の合法性や到達可能性を証明しない。",
        "4a/Statcast/3d・確率・能力・走塁判断・公式成績・Android/UI/セーブへ接続しない。",
        "Pythonは検算用。D029/D031/D032はOPENで、完全な試合生成や製品採用ではない。",
        "source_kind=synthetic_fixture; scope=partial_base_out_rules; probability_model=false。",
        "connected_to_phase4a=false; connected_to_statcast=false; connected_to_phase3d=false。",
        "official_stats_generated=false; ability_calibrated=false; android_integrated=false; product_adoption=false。", ""]
    out = reserve_output(output, input_roots=[FIXTURE_ROOT])
    write_json(out/"plan.json", plan)
    write_json(out/"traces.json", document)
    with (out/"REPORT.md").open("xb") as stream:
        stream.write("\n".join(report).encode("utf-8"))
    write_json(out/"SHA256SUMS.json", {p.name: digest(p) for p in sorted(out.iterdir()) if p.is_file()})
    return out
