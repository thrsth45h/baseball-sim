"""One command to a readable complete toy game, with deterministic evidence."""
from pathlib import Path
from . import APPROVED_GAME_MAIN, CAPABILITIES, DESIGN, SCHEMA
from .engine import GameSession
from .io import digest, implementation_identity, reserve_output, write_json
from .protocol import parse_json, validate_max_pitches, validate_seed
from .source import PROFILE_ROOT, ToyAdvancePolicy, ToyPitchSource, load_profile

FIXTURE_ROOT=Path(__file__).resolve().parent/"fixtures"
REASON_LABELS={"home_lead_after_top9":"9回表終了時にホーム先行・裏省略","away_lead_after_bottom9":"9回裏三アウト・ビジター勝利",
    "tie_after_nine":"9回同点・試作用引分（延長なし）","walkoff_walk":"サヨナラ四球","walkoff_safe_hit":"サヨナラsafe到達（候補の公式安打価値は未裁定）",
    "walkoff_home_run":"サヨナラ本塁打・全得点","pitch_limit":"投球上限で未完了","source_exhausted":"入力不足で未完了",
    "source_error":"入力部品のエラーで未完了","invalid_pitch_input":"一球入力不正で未完了","invalid_fair_result":"fair候補不正・その球は未受理"}


def fixture_contract():
    value=parse_json((FIXTURE_ROOT/"scripted_cases.json").read_text(encoding="utf-8"))
    expected={"design":DESIGN,"cases":[{"id":"all_outs_draw","source_event":"fair_ball_handoff","fair_kind":"out_hold","repetitions":54,
        "expected_reason":"tie_after_nine","expected_score":{"away":0,"home":0},"expected_pitches":54,"expected_completed_pa":54}]}
    from .io import canonical
    if canonical(value)!=canonical(expected):raise ValueError("fixed scripted contract differs")
    return value


def report_text(result):
    data=result.as_dict();score=data["score"]
    label="試合終了" if data["status"]=="completed" else "未完了（部分得点）"
    lines=["# Phase 4f — 1試合の統合試作", "",f"**{label}: 仮Away {score['away']} — {score['home']} 仮Home**", "",
        REASON_LABELS[data["reason"]]+f"。受理投球数: {data['pitches_accepted']}。", "",
        "| チーム | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 合計 |", "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for team,name in (("away","仮Away"),("home","仮Home")):
        values=["—" if value is None else str(value) for value in data["line_score"][team]]
        lines.append("| "+" | ".join([name,*values,str(score[team])])+" |")
    if data["line_score"]["walkoff_half"] is not None:lines += ["", "9回裏はサヨナラで途中終了。—は未実行/未到達、0は実行した無得点。"]
    else:lines += ["", "—は未実行/未到達、0は実行した無得点。"]
    lines += ["", "| 打者 | 完了打席 | safe到達打球（HR含む） | BBイベント | SOイベント | HRイベント |", "|---|---:|---:|---:|---:|---:|"]
    for row in data["batter_events"]:
        lines.append(f"| {row['batter_id']} | {row['completed_plate_appearances']} | {row['safe_hit_events']} | {row['walk_events']} | {row['strikeout_events']} | {row['home_run_events']} |")
    lines += ["", "各チーム9人で循環し、完了打席だけ打順を進める。半回ごとに1番へ戻さない。",
        "整数重みtoy-v1と一定進塁は未較正の仮設定。全員共通で、MLB成績や能力1〜20の効果ではない。",
        "投球真値/知覚/接触/打球物理は未実装stub。EV/LA・Savant値は後付けしない。",
        "表示はイベント件数で、公式AB/RBI/2B/3B/打率・投手成績ではない。サヨナラ候補の塁数を公式長打価値と表示しない。",
        "サヨナラの旧stateは解決直前snapshotのまま。終端後の塁配置や消費済みcountを捏造せず、最終得点は専用終端増分を一度だけ加える。",
        "9回同点引分/延長なしは試作用独自条件。全プレー/リーグ規約・Android/UI/経営/セーブや製品採用は未実装。",
        "probability_model=true（unvalidated_toy_pitch_event_weights）、game_end_rules_implemented=true（限定条件だけ）。",
        "上流/研究接続/MLB較正/能力/公式成績/Savant/Android/製品採用はfalse。D029/D030/D031/D032はOPEN。", ""]
    return "\n".join(lines)


def run(output,seed=42,max_pitches=10000):
    validate_seed(seed);validate_max_pitches(max_pitches)
    identity=implementation_identity();profile=load_profile();fixture_contract()
    policy=ToyAdvancePolicy();source=ToyPitchSource(seed,profile,policy);game=GameSession(seed,max_pitches,source)
    result=game.run_to_end()
    if identity!=implementation_identity():raise ValueError("source/profile/fixture identity changed during run")
    plan={"schema":SCHEMA,"design":DESIGN,**CAPABILITIES,"seed":seed,"max_pitches":max_pitches,
        "approved_design":{"url":"https://github.com/thrsth45h/baseball-sim/pull/34","game_main":APPROVED_GAME_MAIN,"decision":"D047 limited integration prototype"},
        "profile":profile.as_dict(),"source_version":source.version,"advance_policy_version":policy.version,
        "advance_policy":"each original runner i goes to min(i+candidate_bases,4); this provider assumption does not narrow old 4e rules",
        "rng":{"owner":"one random.Random(seed) per game","algorithm":"Python random.Random; Python version recorded; no cross-language/future-version guarantee",
            "pitch_calls":source.pitch_calls,"fair_calls":source.fair_calls,"randrange_100_calls":source.draw_count,"global_rng_used":False},
        "ending_policy":{"innings":9,"tied_after_nine":"prototype draw; no extra innings","top9_home_lead":"skip bottom9 without source/RNG calls",
            "bottom9_home_lead":"dedicated walk-off before old resolution; nonHR only needed runs, HR all runs"},
        "source_access":{"old_source_import_and_identity_hash":"only eleven named old package/model source paths","old_io_runner_fixture_results_used":False,
            "research_raw_or_fitted_models_used":False,"old_frozen_flags_changed":False},
        "limitations":["unvalidated shared toy event weights; no ability or real-data calibration","upstream pitch/perception/contact/physics stub; no EV/LA/Savant backfill",
            "all safe/touch-complete synthetic play assumptions; no general running/error/DP/interference/appeal handling",
            "game snapshot/replay is in-memory verification, not product save/resume","D029/D030/D031/D032 remain OPEN"],"implementation":identity}
    out=reserve_output(output,input_roots=[PROFILE_ROOT,FIXTURE_ROOT])
    write_json(out/"plan.json",plan);write_json(out/"events.json",game.events_document())
    write_json(out/"result.json",{"schema":SCHEMA,"design":DESIGN,**CAPABILITIES,**result.as_dict()})
    with (out/"REPORT.md").open("xb") as stream:stream.write(report_text(result).encode("utf-8"))
    write_json(out/"SHA256SUMS.json",{p.name:digest(p) for p in sorted(out.iterdir()) if p.is_file()})
    return out
