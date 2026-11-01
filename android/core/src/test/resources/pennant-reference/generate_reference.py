"""Independent Phase5a arithmetic references; no season implementation imports.

Python standard library only. All writes are confined to a new task child.
"""
from __future__ import annotations

import argparse
from collections import Counter
from copy import deepcopy
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parent
TECHNICAL_DESIGN = "2186928851a0db1921e6dd11651fa24b3627137f"
ACCEPTED_DESIGN = "8b2f23f73fee1c5285e5794f375b778a9ad17a32"
OLD_CORE_MAIN = "470467a27d6390b6415e5b16b44c0c1a0aa1704f"
SOURCE_FILES = [f"android/core/src/main/kotlin/com/example/baseballsim/core/{name}.kt"
                for name in ("GameSession", "Model", "RandomSource", "Rules")]
COUNT_KEYS = ("completed_plate_appearances", "safe_hit_events", "walk_events",
              "strikeout_events", "home_run_events")
MASK = (1 << 64) - 1
GAMMA = 0x9E3779B97F4A7C15
WEEKDAYS = ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def encode(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False) + "\n").encode("utf-8")


def git_blob(repo, commit, relative):
    return subprocess.run(["git", "-C", str(repo), "show", f"{commit}:{relative}"],
                          check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def raw64(state):
    state = (state + GAMMA) & MASK
    z = state
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK
    return state, z ^ (z >> 31)


def multiply_by_limbs(a, b):
    lo = (a & 0xFFFFFFFF) * (b & 0xFFFFFFFF)
    mid = (a >> 32) * (b & 0xFFFFFFFF) + (a & 0xFFFFFFFF) * (b >> 32)
    return (lo + ((mid % (1 << 32)) << 32)) % (1 << 64)


def raw64_alternative(state):
    state = (state + GAMMA) % (1 << 64)
    z = state
    z = multiply_by_limbs(z ^ (z // (1 << 30)), 0xBF58476D1CE4E5B9)
    z = multiply_by_limbs(z ^ (z // (1 << 27)), 0x94D049BB133111EB)
    return state, z ^ (z // (1 << 31))


def club(league, number):
    return f"L{league}-T{number:02}"


def player(club_id, slot):
    return f"{club_id}-P{slot:02}"


def club_ids():
    return [club(league, number) for league in (1, 2) for number in range(1, 7)]


def roster():
    return {"schema": "pennant-reference-roster-v1", "season_id": "S1",
            "clubs": [{"club_id": c, "league_id": c[:2], "display_name": f"仮球団{index + 1}",
                       "players": [{"player_id": player(c, slot), "slot": slot} for slot in range(1, 10)]}
                      for index, c in enumerate(club_ids())]}


def schedule():
    rotating = list(range(1, 7))
    rounds = []
    for r in range(5):
        pairs = []
        for i in range(3):
            left, right = rotating[i], rotating[5 - i]
            home, away = (left, right) if (r + i) % 2 == 0 else (right, left)
            pairs.append((away, home))
        rounds.append(pairs)
        rotating = [rotating[0], rotating[-1], *rotating[1:-1]]
    assert rotating == list(range(1, 7))
    rows = []
    for series in range(1, 41):
        week = (series - 1) // 2 + 1
        start = (week - 1) * 7 + (2 if series % 2 else 5)
        round_in_cycle = (series - 1) % 10 + 1
        base_round = (round_in_cycle - 1) % 5
        for series_game in range(1, 4):
            day = start + series_game - 1
            for league in (1, 2):
                for matchup, (first_away, first_home) in enumerate(rounds[base_round], 1):
                    away, home = (first_home, first_away) if round_in_cycle > 5 else (first_away, first_home)
                    rows.append({"season_id": "S1", "day_index": day, "week": week,
                                 "weekday": WEEKDAYS[(day - 1) % 7], "league_id": f"L{league}",
                                 "series_round": series, "cycle": (series - 1) // 10 + 1,
                                 "round_in_cycle": round_in_cycle, "base_round": base_round + 1,
                                 "matchup_index": matchup, "series_game": series_game,
                                 "away_club_id": club(league, away), "home_club_id": club(league, home),
                                 "game_id": f"S1-D{day:03}-L{league}-R{series:02}-M{matchup}-G{series_game}"})
    rows.sort(key=lambda row: (row["day_index"], row["league_id"], row["matchup_index"]))
    for ordinal, row in enumerate(rows, 1):
        row["schedule_ordinal"] = ordinal
    # Independent calendar-first enumeration with a modular ring, no rotated-list reuse.
    independent = []
    for day in range(1, 141):
        weekday = (day - 1) % 7 + 1
        if weekday == 1:
            continue
        week = (day - 1) // 7 + 1
        series = (week - 1) * 2 + (1 if weekday <= 4 else 2)
        r = (series - 1) % 5
        mirrored = (series - 1) % 10 >= 5
        ring = lambda p: 1 if p == 0 else 2 + ((p - 1 - r) % 5)
        for league in (1, 2):
            for i in range(3):
                a, b = ring(i), ring(5 - i)
                h, v = (a, b) if (r + i) % 2 == 0 else (b, a)
                if mirrored:
                    h, v = v, h
                independent.append((day, f"L{league}", i + 1, club(league, v), club(league, h)))
    assert [(r["day_index"], r["league_id"], r["matchup_index"], r["away_club_id"], r["home_club_id"])
            for r in rows] == independent
    assert len(rows) == len({r["game_id"] for r in rows}) == 720
    games, homes, opponents, pair_home, day_club, days = (Counter() for _ in range(6))
    for row in rows:
        a, h = row["away_club_id"], row["home_club_id"]
        assert a != h and a[:2] == h[:2] == row["league_id"]
        games.update((a, h)); homes[h] += 1
        opponents[(a, h)] += 1; opponents[(h, a)] += 1
        pair_home[(h, a)] += 1
        day_club[(row["day_index"], a)] += 1; day_club[(row["day_index"], h)] += 1
        days[row["day_index"]] += 1
    assert set(games.values()) == {120} and set(homes.values()) == {60}
    assert set(opponents.values()) == {24} and set(pair_home.values()) == {12}
    assert set(day_club.values()) == {1} and set(days.values()) == {6} and len(days) == 120
    calendar = [{"day_index": day, "week": (day - 1) // 7 + 1,
                 "weekday": WEEKDAYS[(day - 1) % 7], "is_rest_day": (day - 1) % 7 == 0,
                 "game_ordinals": [r["schedule_ordinal"] for r in rows if r["day_index"] == day]}
                for day in range(1, 141)]
    assert sum(d["is_rest_day"] for d in calendar) == 20
    return rows, calendar


def assign_seeds(rows, master):
    state = master
    values = []
    for row in rows:
        before = state
        state, value = raw64(state)
        assert (state, value) == raw64_alternative(before)
        values.append({"schedule_ordinal": row["schedule_ordinal"], "game_id": row["game_id"], "seed_u64": str(value)})
    assert len(values) == len({v["seed_u64"] for v in values}) == 720
    assert state == (master + 720 * GAMMA) & MASK
    return {"schema": "pennant-reference-seeds-v1", "seed_derivation_version": "season-seeds-v1",
            "rng_version": "sm64-v1", "master_seed": str(master), "raw_calls": 720,
            "final_allocator_state_u64": str(state), "assignments": values}


def counts(values):
    result = dict(zip(COUNT_KEYS, values))
    pa, safe, walk, strikeout, hr = values
    assert all(type(v) is int and v >= 0 for v in values) and hr <= safe and safe + walk + strikeout <= pa
    return result


def standings(results):
    totals = {c: dict(club_id=c, league_id=c[:2], played=0, wins=0, losses=0, draws=0,
                      runs_for=0, runs_against=0) for c in club_ids()}
    for game in results:
        assert game["status"] == "completed"
        a, h = game["away_club_id"], game["home_club_id"]
        av, hv = game["score"]["away"], game["score"]["home"]
        for own, other, own_runs, other_runs in ((a, h, av, hv), (h, a, hv, av)):
            row = totals[own]
            row["played"] += 1; row["runs_for"] += own_runs; row["runs_against"] += other_runs
            row["wins"] += int(own_runs > other_runs); row["losses"] += int(own_runs < other_runs)
            row["draws"] += int(own_runs == other_runs)
    table = []
    for league in ("L1", "L2"):
        group = [totals[c] for c in club_ids() if c.startswith(league)]
        ratio = lambda r: Fraction(r["wins"], r["wins"] + r["losses"]) if r["wins"] + r["losses"] else None
        group.sort(key=lambda r: (ratio(r) is None, -ratio(r) if ratio(r) is not None else 0, r["club_id"]))
        for row in group:
            value = ratio(row)
            row["win_ratio"] = None if value is None else {"numerator": value.numerator, "denominator": value.denominator}
            row["rank"] = None if value is None else 1 + sum(ratio(other) is not None and ratio(other) > value for other in group)
        table.extend(group)
    return table


def manual_standings(which):
    # Hand-entered independent totals: played,wins,losses,draws,RF,RA,ratio,rank.
    if which == 6:
        raw = [[(1,1,0,0,3,2,1,1),(1,0,0,1,1,1,None,None),(1,0,1,0,0,4,0,3),
                (1,1,0,0,4,0,1,1),(1,0,0,1,1,1,None,None),(1,0,1,0,2,3,0,3)],
               [(1,1,0,0,2,0,1,1),(1,0,0,1,3,3,None,None),(1,0,1,0,1,2,0,3),
                (1,1,0,0,2,1,1,1),(1,0,0,1,3,3,None,None),(1,0,1,0,0,2,0,3)]]
        order = [[1,4,3,6,2,5], [1,4,3,6,2,5]]
    else:
        assert which == 12
        raw = [[(2,1,1,0,4,7,"half",3),(2,1,0,1,3,1,1,1),(2,0,1,1,2,6,0,5),
                (2,1,0,1,6,2,1,1),(2,0,1,1,1,3,0,5),(2,1,1,0,7,4,"half",3)],
               [(2,1,0,1,2,0,1,1),(2,0,1,1,3,4,0,4),(2,0,2,0,1,3,0,4),
                (2,2,0,0,3,1,1,1),(2,1,0,1,4,3,1,1),(2,0,1,1,0,2,0,4)]]
        order = [[2,4,1,6,3,5], [1,4,5,2,3,6]]
    expected = []
    for league in (1, 2):
        for number in order[league - 1]:
            p,w,l,d,rf,ra,value,rank = raw[league - 1][number - 1]
            expected.append(dict(club_id=club(league,number), league_id=f"L{league}", played=p,wins=w,losses=l,draws=d,
                                 runs_for=rf,runs_against=ra,win_ratio=None if value is None else
                                 {"numerator":1 if value == "half" else value,"denominator":2 if value == "half" else 1},rank=rank))
    return expected


def result_fixture(row, score, seed):
    av, hv = score
    outcome = "away_win" if av > hv else "home_win" if hv > av else "draw"
    reason = {"away_win":"away_lead_after_bottom9","home_win":"home_lead_after_top9","draw":"tie_after_nine"}[outcome]
    lines = [av] + [0] * 8, [hv] + [0] * (7 if outcome == "home_win" else 8)
    home_lines = lines[1] + ([None] if outcome == "home_win" else [])
    people = []
    for side, team, runs in ((0,row["away_club_id"],av),(1,row["home_club_id"],hv)):
        for slot in range(1,10):
            outs = 2 if side == 1 and outcome == "home_win" and slot >= 7 else 3
            # Input arithmetic summaries, not an assertion of a complete legal pitch history.
            hr = sum((n + row["schedule_ordinal"] + side) % 9 + 1 == slot for n in range(runs))
            walk = row["schedule_ordinal"] % 3 if slot == (row["schedule_ordinal"] + side) % 9 + 1 else 0
            people.append({"side":"away" if side == 0 else "home", "slot":slot,"player_id":player(team,slot),
                           **counts((outs+hr+walk,hr,walk,1,hr))})
    pitches = sum(p["completed_plate_appearances"] + 3*p["walk_events"] + 2*p["strikeout_events"] for p in people)
    return {"game_id":row["game_id"],"schedule_ordinal":row["schedule_ordinal"],"season_id":"S1",
            "away_club_id":row["away_club_id"],"home_club_id":row["home_club_id"],"seed_u64":seed,
            "versions":{"schedule":"season-schedule-v1","seed_derivation":"season-seeds-v1","rng":"sm64-v1","core_source":OLD_CORE_MAIN},
            "status":"completed","outcome":outcome,"reason":reason,"score":{"away":av,"home":hv},
            "line_score":{"away":lines[0],"home":home_lines},"terminal_score_delta":{"away":0,"home":0},
            "pitches_accepted":pitches,"batter_counts":people}


def small_ledger(rows, seed_rows):
    scores = [(2,3),(1,1),(4,0),(0,2),(3,3),(2,1),(5,1),(2,0),(2,2),(0,0),(0,1),(1,0)]
    games = [result_fixture(row, score, seed["seed_u64"]) for row,score,seed in zip(rows[:12],scores,seed_rows[:12])]
    checkpoints = []
    for end in (6,12):
        derived = standings(games[:end])
        expected = manual_standings(end)
        assert encode(derived) == encode(expected)
        checkpoints.append({"accepted_game_count":end,"expected_standings":expected})
    assert sum(r["wins"] for r in expected) == sum(r["losses"] for r in expected) == 8
    assert sum(r["draws"] for r in expected) == 8
    assert sum(r["runs_for"] for r in expected) == sum(r["runs_against"] for r in expected) == 36
    different = deepcopy(games[0])
    a, b = different["batter_counts"][1], different["batter_counts"][2]
    # g1's away BB is slot2. Move it to slot3; team totals/score remain unchanged.
    assert a["walk_events"] == 1
    a["walk_events"] -= 1; a["completed_plate_appearances"] -= 1
    b["walk_events"] += 1; b["completed_plate_appearances"] += 1
    for p in different["batter_counts"]:
        counts(tuple(p[k] for k in COUNT_KEYS))
    assert games[0]["score"] == different["score"] and encode(games[0]) != encode(different)
    active = {"game_id":rows[12]["game_id"],"opaque_test_marker":"preserve-this-active-record"}
    return {"schema":"pennant-reference-small-ledger-v1",
            "semantics":"hand arithmetic summaries, not core-generated games or proof of legal full pitch histories",
            "generation_key":{"world_id":"W-fixture","generation_id":"G-fixture","season_id":"S1"},
            "master_seed":"42","input_results":games,"checkpoints":checkpoints,
            "after_12_hand_totals":{"completed_games":12,"decisive_games":8,"draw_games":4,"team_games":24,
                                    "wins":8,"losses":8,"team_draws":8,"runs_for":36,"runs_against":36},
            "receipt_cases":[
                {"id":"same_result_is_full_noop","input":games[0],"expected":"duplicate_noop",
                 "before_cursor_ordinal":13,"after_cursor_ordinal":13,"before_active":active,"after_active":active,
                 "result_ledger_and_all_aggregates_unchanged":True},
                {"id":"same_score_different_bb_owner_conflicts","input":different,"expected":"conflict_rejected",
                 "before_cursor_ordinal":13,"after_cursor_ordinal":13,"before_active":active,"after_active":active,
                 "result_ledger_and_all_aggregates_unchanged":True}],
            "aborted_policy":{"status":"aborted","registered_as_result":False,"counted_as_draw_or_loss":False,
                              "expected_game_state":"incomplete_error_stop","season_completed":False}}


def attribution(rows):
    def input_people(a_base,a_mul,a_bb,a_k,a_mod,h_base,h_mul,h_bb,h_k,h_mod):
        return [{"side":side,"slot":s,**counts((base+s,mul*s,bb,k,s%mod))}
                for side,base,mul,bb,k,mod in (("away",a_base,a_mul,a_bb,a_k,a_mod),("home",h_base,h_mul,h_bb,h_k,h_mod))
                for s in range(1,10)]
    examples = [dict(game_id=rows[0]["game_id"],away_club_id=rows[0]["away_club_id"],home_club_id=rows[0]["home_club_id"],
                     local_batter_counts=input_people(10,1,1,2,2,30,2,2,3,3)),
                dict(game_id=rows[90]["game_id"],away_club_id=rows[90]["away_club_id"],home_club_id=rows[90]["home_club_id"],
                     local_batter_counts=input_people(50,3,3,4,4,80,4,7,8,5)),
                dict(game_id=rows[3]["game_id"],away_club_id=rows[3]["away_club_id"],home_club_id=rows[3]["home_club_id"],
                     local_batter_counts=input_people(90,5,5,6,6,110,6,6,7,7))]
    assert (examples[0]["away_club_id"],examples[0]["home_club_id"]) == (club(1,6),club(1,1))
    assert (examples[1]["away_club_id"],examples[1]["home_club_id"]) == (club(1,1),club(1,6))
    derived = {player(c,s):[0]*5 for c in club_ids() for s in range(1,10)}
    for game in examples:
        assert len(game["local_batter_counts"]) == 18
        for p in game["local_batter_counts"]:
            target = player(game[p["side"]+"_club_id"],p["slot"])
            derived[target] = [a+p[k] for a,k in zip(derived[target],COUNT_KEYS)]
    expected = []
    for c in club_ids():
        for s in range(1,10):
            if c == club(1,6): value=(90+2*s,5*s,8,10,(s%2)+(s%5))
            elif c == club(1,1): value=(80+2*s,5*s,5,7,(s%3)+(s%4))
            elif c == club(2,6): value=(90+s,5*s,5,6,s%6)
            elif c == club(2,1): value=(110+s,6*s,6,7,s%7)
            else: value=(0,0,0,0,0)
            assert derived[player(c,s)] == list(value)
            expected.append({"player_id":player(c,s),"club_id":c,"slot":s,**counts(value)})
    hand_totals = {club(1,6):(900,225,72,90,25),club(1,1):(810,225,45,63,22),
                   club(2,6):(855,225,45,54,21),club(2,1):(1035,270,54,63,24)}
    for c, value in hand_totals.items():
        assert tuple(sum(p[k] for p in expected if p["club_id"]==c) for k in COUNT_KEYS) == value
    assert tuple(sum(p[k] for p in expected) for k in COUNT_KEYS) == (3600,945,216,270,92)
    return {"schema":"pennant-reference-attribution-v1",
            "semantics":"sparse projection arithmetic, not a chronological season-progress or legal GameSession fixture",
            "input_games":examples,"expected_all_108_players":expected,
            "hand_team_totals":[{"club_id":c,**counts(v)} for c,v in hand_totals.items()],
            "hand_grand_totals":counts((3600,945,216,270,92)),"zero_record_players":72}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--repo",required=True,type=Path)
    parser.add_argument("--output",required=True,type=Path)
    args=parser.parse_args()
    repo=args.repo.resolve(strict=True); output=args.output.resolve()
    if output == ROOT or not output.is_relative_to(ROOT) or output.exists():
        raise ValueError("output must be a fresh child under this task directory")
    rows,calendar=schedule()
    seeds={master:assign_seeds(rows,master) for master in (0,42,2026)}
    context=json.loads((ROOT/"SOURCE_CONTEXT.json").read_text(encoding="utf-8"))
    records=[]
    for relative in SOURCE_FILES:
        data=git_blob(repo,OLD_CORE_MAIN,relative).replace(b"\r\n",b"\n")
        records.append({"path":relative,"source_commit":OLD_CORE_MAIN,"sha256_lf":sha(data),"bytes_lf":len(data)})
    design_records=[]
    for commit in (TECHNICAL_DESIGN,ACCEPTED_DESIGN):
        data=git_blob(repo,commit,"docs/PENNANT_BASIC_DESIGN.md").replace(b"\r\n",b"\n")
        design_records.append({"commit":commit,"path":"docs/PENNANT_BASIC_DESIGN.md","sha256_lf":sha(data)})
    provenance={"schema":"pennant-independent-reference-provenance-v1",**context,
                "generator_sha256":sha(Path(__file__).read_bytes()),"source_context_sha256":sha((ROOT/"SOURCE_CONTEXT.json").read_bytes()),
                "contract_sha256":sha((ROOT/"CONTRACT.md").read_bytes()),"python_implementation":sys.implementation.name,
                "python_version":sys.version.split()[0],"design_revisions":design_records,"existing_core_sources":records,
                "new_season_implementation_read":False,"worker_code_read":False,"parent_private_checks_read":False,
                "old_core_executed":False,"research_raw_or_model_read":False}
    values={"roster.json":roster(),"schedule.json":{"schema":"pennant-reference-schedule-v1","schedule_version":"season-schedule-v1","games":rows},
            "calendar.json":{"schema":"pennant-reference-calendar-v1","days":calendar},
            "small_ledger.json":small_ledger(rows,seeds[42]["assignments"]),"player_attribution.json":attribution(rows),
            "provenance.json":provenance,
            "checks.json":{"calendar_first_and_rotating_list_agree":True,"sm64_mask_and_limb_routes_agree":True,
                           "games":720,"clubs":12,"players":108,"games_per_club":120,"home_per_club":60,"away_per_club":60,
                           "games_per_opponent":24,"home_per_opponent":12,"calendar_days":140,"game_days":120,"rest_days":20,
                           "seed_assignments":2160,"manual_ledger_results":12,"manual_standings_checkpoints":2,
                           "attribution_input_games":3,"attribution_input_rows":54,"expected_player_rows":108,
                           "all_assertions_pass":True}}
    for master,value in seeds.items(): values[f"seeds_master_{master}.json"]=value
    output.mkdir(parents=True,exist_ok=False)
    for name,value in values.items(): (output/name).write_bytes(encode(value))
    manifest={p.name:{"bytes":p.stat().st_size,"sha256":sha(p.read_bytes())} for p in sorted(output.iterdir())}
    (output/"SHA256_MANIFEST.json").write_bytes(encode(manifest))
    print(json.dumps({"files":manifest,"games":720,"seed_assignments":2160,"manual_ledger_results":12,"expected_player_rows":108}))


if __name__ == "__main__":
    main()
