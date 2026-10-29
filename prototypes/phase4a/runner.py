"""Run only fixed synthetic fixtures; no statistical model or RNG."""
from . import APPROVED_GAME_MAIN, CAPABILITIES, DESIGN, SCHEMA
from .fixtures import FIXTURE_ROOT, scenarios, transition_table
from .io import canonical, digest, implementation_identity, reserve_output, write_json
from .model import CountState, replay, step


def run(output):
    identity = implementation_identity()
    rows, table = scenarios(), transition_table()
    # Validate and compute all fixtures before creating any output directory.
    for row in table:
        before = CountState.from_mapping(row["before"])
        if step(before, row["event"]).as_dict() != row["after"]:
            raise ValueError("implementation disagrees with fixed transition reference")
    traces = []
    for row in rows:
        result = replay(CountState(), row["events"])
        if result.final_state.as_dict() != row["expected_final"]:
            raise ValueError("implementation disagrees with fixed scenario reference")
        traces.append({"id": row["id"], "initial_state": CountState().as_dict(),
                       "events": row["events"], **result.as_dict()})
    if identity != implementation_identity():
        raise ValueError("implementation or fixtures changed during run")
    plan = {"design": DESIGN, "schema": SCHEMA, **CAPABILITIES,
            "approved_design": {"url": "https://github.com/thrsth45h/baseball-sim/pull/19",
                                "game_main": APPROVED_GAME_MAIN, "decision": "D042 limited reference only"},
            "rules": {"edition": "MLB Official Baseball Rules 2026",
                      "url": "https://mktg.mlbstatic.com/mlb/official-information/2026-official-baseball-rules.pdf",
                      "sections": ["5.04(c)", "5.05(a)(1-2)", "5.05(b)(1)", "5.09(a)(1-4)",
                                   "9.15(a)(4)", "Definitions: STRIKE, FOUL TIP"],
                      "complete_league_rules_adopted": False},
            "transition_count": len(table), "scenario_count": len(rows),
            "fixed_scenarios": rows,
            "count_semantics": {"pitches_seen": "accepted event count in the eight-event subset, not league official pitch totals",
                                "trace_ordinal": "n+1 before each event; never reset for a chunk",
                                "fair_play_pending": "counter handoff only; hit/out unresolved, no assertion of legal PA incompletion",
                                "memory_chunk_replay": True, "product_save_format": False},
            "unsupported": ["uncaught or catch-unknown strikes, including third strikes", "ambiguous strike/foul",
                            "HBP, interference, intentional walk, automatic ball/strike, canceled calls",
                            "runner/scoring/inning play, official statistics, statistical generation"],
            "limitations": ["adjudicated synthetic event fixtures only, not estimated event probabilities",
                            "no Statcast description/events mapping, no Phase3d opportunity-to-catch mapping",
                            "no EV/LA backfill or old model imports", "not a complete PA/game engine",
                            "Python reference does not select Android language/architecture/DB; D029/D031/D032 OPEN"],
            "implementation": identity}
    document = {"design": DESIGN, "schema": SCHEMA, **CAPABILITIES, "scenarios": traces}
    report = ["# Phase 4a: カウントと限定終端の参照実装", "",
              "裁定済みの合成イベントを順に適用した。発生確率やリーグ成績を推定していない。", "",
              "| 固定列 | b | s | 受理イベント数n | status |", "|---|---:|---:|---:|---|"]
    for row in traces:
        state = row["final_state"]
        report.append(f"| {row['id']} | {state['balls']} | {state['strikes']} | {state['pitches_seen']} | {state['status']} |")
    report += ["", "96遷移表と16列は試験用fixture。通常ファウル、合法チップ、未捕球バント、捕球飛球を区別する。",
               "未捕球ストライク等は拒否し、未対応をアウトや成功率0に置き換えない。",
               "fair_play_pendingは受渡し停止であり、安打/アウトや規則上の打席未終了を意味しない。",
               "メモリ上の途中処理一致は製品のセーブ形式やDBの採用ではない。",
               "実データ/既存研究/能力/走者/得点/公式成績/UI/セーブへ接続していない。",
               "Pythonはルール検算用。Android言語/DBを選定せずD029/D031/D032はOPEN。",
               "source_kind=synthetic_fixture; scope=partial_count_rules; probability_model=false。",
               "connected_to_statcast=false; connected_to_phase3d=false; official_stats_generated=false。",
               "ability_calibrated=false; android_integrated=false; product_adoption=false。", ""]
    out = reserve_output(output, input_roots=[FIXTURE_ROOT])
    write_json(out/"plan.json", plan)
    write_json(out/"traces.json", document)
    with (out/"REPORT.md").open("xb") as stream:
        stream.write("\n".join(report).encode("utf-8"))
    write_json(out/"SHA256SUMS.json", {p.name: digest(p) for p in sorted(out.iterdir()) if p.is_file()})
    return out
