import contextlib
from copy import deepcopy
from http.client import HTTPConnection
import io
import json
from pathlib import Path
import threading
import unittest
from unittest.mock import patch

from prototypes.phase4f.engine import GameSession
from prototypes.phase4f.protocol import CountState, FAIR, FairPlay, HalfInningState, HalfSequenceState, PitchHalfState, PitchInput
from prototypes.phase4f.source import ScriptedSource
from prototypes.phase4f.state import EventCounts, GameSnapshot
from prototypes.match_view.app import CommandError, MatchController, seed_from_text
from prototypes.match_view.server import BODY_LIMIT, STATIC, TOKEN_HEADER, create_server


class ControllerTests(unittest.TestCase):
    def test_manual_pa_finish_and_mixed_use_exact_engine_result_and_events(self):
        for seed in (0,42,2026,2**64-1):
            baseline=GameSession(seed);expected=baseline.run_to_end().as_dict();events=baseline.events_document()
            for commands in (("pitch",),("plate_appearance",),("finish",),("pitch","plate_appearance","pitch","finish")):
                with self.subTest(seed=seed,commands=commands):
                    controller=MatchController();controller.apply_command({"command":"new","seed":str(seed)});index=0
                    while not controller.game.snapshot.done:
                        controller.apply_command({"command":commands[index%len(commands)]});index+=1
                    self.assertEqual(controller.game.result().as_dict(),expected);self.assertEqual(controller.game.events_document(),events)

    def test_initial_next_batter_and_half_carryover_without_reads_drawing_rng(self):
        source=ScriptedSource([PitchInput(FAIR,FairPlay("out_hold"))]*54);controller=MatchController(GameSession(source=source))
        initial=controller.state();self.assertEqual(initial["current"]["batter_id"],"away_1");self.assertEqual(initial["current"]["bases"],[False]*3)
        for _ in range(5):self.assertEqual(controller.state(),initial)
        self.assertEqual(source.calls,0)
        controller.apply_command({"command":"plate_appearance"});self.assertEqual(controller.state()["current"]["batter_id"],"away_2")
        for _ in range(2):controller.apply_command({"command":"pitch"})
        self.assertEqual(controller.state()["current"]["batter_id"],"home_1");self.assertEqual(controller.state()["current"]["half_text"],"1回裏")
        for _ in range(3):controller.apply_command({"command":"pitch"})
        self.assertEqual(controller.state()["current"]["batter_id"],"away_4");self.assertEqual(controller.state()["current"]["half_text"],"2回表")
        self.assertEqual(source.calls,6);self.assertEqual(controller.state()["current"]["balls"],0)

    def test_one_pa_from_partway_count_stops_at_next_completion(self):
        source=ScriptedSource([PitchInput("called_ball")]*4+[PitchInput(FAIR,FairPlay("out_hold"))]);controller=MatchController(GameSession(source=source))
        controller.apply_command({"command":"pitch"});self.assertEqual(controller.state()["current"]["balls"],1)
        controller.apply_command({"command":"plate_appearance"});state=controller.state()
        self.assertEqual(state["pitches_accepted"],4);self.assertEqual(state["completed_pa"],1);self.assertEqual(source.calls,4)
        self.assertEqual(state["current"]["batter_id"],"away_2");self.assertEqual(state["current"]["bases"],[True,False,False])

    def test_walkoff_hides_unresolved_old_count_and_bases_and_rejects_more_progress(self):
        sequence=HalfSequenceState(9,"bottom",0,0,PitchHalfState(CountState(3,0,3),HalfInningState(0,(True,True,True),0)))
        snapshot=GameSnapshot(sequence,(0,0),tuple(EventCounts() for _ in range(18)),0,(0,)*18,history_origin="provided_snapshot")
        controller=MatchController(GameSession(source=ScriptedSource([PitchInput("called_ball")]),initial_snapshot=snapshot))
        controller.apply_command({"command":"pitch"});before=controller.game.events_document();state=controller.state()
        self.assertEqual(state["score"],{"away":0,"home":1});self.assertEqual(controller.game.snapshot.sequence.stage,"awaiting_resolution")
        self.assertIsNone(state["current"]);self.assertNotIn("last_projected_sequence",state);self.assertTrue(state["line_score"]["walkoff"])
        for command in ("pitch","plate_appearance","finish"):
            with self.assertRaises(CommandError) as error:controller.apply_command({"command":command})
            self.assertEqual(error.exception.status,409);self.assertEqual(controller.game.events_document(),before)

    def test_aborted_result_omits_current_projection_and_is_not_a_draw(self):
        controller=MatchController(GameSession(max_pitches=1,source=ScriptedSource([PitchInput("called_ball")])));controller.apply_command({"command":"finish"})
        state=controller.state();self.assertEqual(state["status"],"aborted");self.assertEqual(state["outcome"],"未完了");self.assertIsNone(state["current"])
        self.assertEqual(state["pitches_accepted"],1);self.assertIn("投球上限",state["reason"])

    def test_strict_new_seed_payload_rejection_and_state_rng_unchanged(self):
        controller=MatchController();controller.apply_command({"command":"pitch"});before=controller.game.events_document();rng=controller.game.source.rng.getstate()
        invalid_seeds=(None,True,42,42.0,"","-1","+1"," 42","42 ","01","4.2","１","1e3",str(2**64),"9"*100)
        for value in invalid_seeds:
            with self.assertRaises(CommandError):controller.apply_command({"command":"new","seed":value})
        for payload in (None,[],{},"pitch",{"command":False},{"command":"unknown"},{"command":"pitch","seed":"42"},{"command":"new"},{"command":"new","seed":"42","extra":1}):
            with self.assertRaises(CommandError):controller.apply_command(payload)
        self.assertEqual(controller.game.events_document(),before);self.assertEqual(controller.game.source.rng.getstate(),rng)
        for seed in (0,42,2**64-1):self.assertEqual(seed_from_text(str(seed)),seed)
        state=controller.apply_command({"command":"new","seed":str(2**64-1)});self.assertEqual(state["seed"],str(2**64-1));self.assertEqual(state["pitches_accepted"],0)

    def test_state_is_detached_and_reads_do_not_change_local_rng_or_engine(self):
        controller=MatchController();controller.apply_command({"command":"pitch"});before=controller.game.events_document();rng=controller.game.source.rng.getstate()
        state=controller.state();state["score"]["away"]=100;state["current"]["bases"][0]=True;state["line_score"]["home"][0]=5;state["recent"].clear()
        for _ in range(10):controller.state()
        self.assertEqual(controller.game.events_document(),before);self.assertEqual(controller.game.source.rng.getstate(),rng)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.server=create_server();self.thread=threading.Thread(target=self.server.serve_forever,kwargs={"poll_interval":0.01},daemon=True);self.thread.start()
        self.addCleanup(self.stop)

    def stop(self):self.server.shutdown();self.thread.join(timeout=3);self.server.server_close()

    def request(self,method="GET",path="/api/state",payload=None,headers=None,raw=None):
        connection=HTTPConnection("127.0.0.1",self.server.server_port,timeout=5)
        body=raw if raw is not None else (None if payload is None else json.dumps(payload).encode())
        values={"Host":self.server.allowed_host}
        if method=="POST":values|={"Origin":self.server.origin,TOKEN_HEADER:self.server.token,"Content-Type":"application/json"}
        values.update(headers or {})
        connection.request(method,path,body=body,headers=values);response=connection.getresponse();result=(response.status,dict(response.getheaders()),response.read());connection.close();return result

    def test_loopback_fixed_static_routes_and_get_are_readonly(self):
        self.assertEqual(self.server.server_address[0],"127.0.0.1");self.assertGreater(self.server.server_port,0)
        before=self.server.controller.game.events_document();rng=self.server.controller.game.source.rng.getstate()
        for path in ("/","/style.css","/app.js","/api/state"):
            status,headers,body=self.request(path=path);self.assertEqual(status,200);self.assertEqual(headers["Cache-Control"],"no-store")
            self.assertNotIn("Access-Control-Allow-Origin",headers);self.assertIn("frame-ancestors 'none'",headers["Content-Security-Policy"])
            if path=="/":self.assertIn(self.server.token.encode(),body);self.assertNotIn(b"__MATCH_TOKEN__",body)
            else:self.assertNotIn(self.server.token.encode(),body)
        for path in ("/README.md","/../README.md","/%2e%2e/README.md","/api/state?command=finish","/api/command","//app.js","/static/app.js"):
            self.assertEqual(self.request(path=path)[0],404)
        self.assertEqual(self.server.controller.game.events_document(),before);self.assertEqual(self.server.controller.game.source.rng.getstate(),rng)

    def test_http_new_64bit_seed_and_three_progress_commands(self):
        self.assertEqual(self.request("POST","/api/command",{"command":"new","seed":str(2**64-1)})[0],200)
        for command in ("pitch","plate_appearance","finish"):
            status,_,body=self.request("POST","/api/command",{"command":command});self.assertEqual(status,200);state=json.loads(body)
        expected=GameSession(2**64-1);expected.run_to_end();self.assertEqual(self.server.controller.game.events_document(),expected.events_document())
        self.assertTrue(state["done"]);self.assertIsNone(state["current"])
        before=self.server.controller.game.events_document();self.assertEqual(self.request("POST","/api/command",{"command":"pitch"})[0],409);self.assertEqual(self.server.controller.game.events_document(),before)

    def test_host_origin_token_and_content_type_rejection_does_not_change_game(self):
        before=self.server.controller.game.events_document();rng=self.server.controller.game.source.rng.getstate()
        for headers,status in (({"Host":"example.com"},403),({"Host":"localhost:"+str(self.server.server_port)},403),({"Origin":"https://example.com"},403),({"Origin":"null"},403),({"Origin":""},403),({TOKEN_HEADER:"wrong"},403),({TOKEN_HEADER:"é"},403),({"Content-Type":"text/plain"},415)):
            self.assertEqual(self.request("POST","/api/command",{"command":"pitch"},headers=headers)[0],status)
        self.assertEqual(self.request(headers={"Origin":"https://example.com"})[0],403)
        self.assertEqual(self.server.controller.game.events_document(),before);self.assertEqual(self.server.controller.game.source.rng.getstate(),rng)

    def test_json_limits_duplicates_nonfinite_invalid_values_and_routes_rejected(self):
        before=self.server.controller.game.events_document()
        for body in (b"{",b'[]',b'{"command":"new","seed":42}',b'{"command":"pitch","command":"finish"}',b'{"command":NaN}',b'{"command":"new","seed":Infinity}',b'\xff'):
            self.assertEqual(self.request("POST","/api/command",raw=body)[0],400)
        self.assertEqual(self.request("POST","/api/command",raw=b"x"*(BODY_LIMIT+1))[0],413)
        self.assertEqual(self.request("POST","/api/command",raw=b"",headers={"Content-Length":"9"*200})[0],413)
        self.assertEqual(self.request("POST","/api/command",raw=b"",headers={"Content-Length":"-1"})[0],400)
        self.assertEqual(self.request("POST","/api/command",raw=b"",headers={"Transfer-Encoding":"chunked"})[0],400)
        for path in ("/api/state","/","/api/command?x=1"):self.assertEqual(self.request("POST",path,{"command":"pitch"})[0],404)
        self.assertEqual(self.request("OPTIONS","/api/command")[0],405)
        self.assertEqual(self.server.controller.game.events_document(),before)

    def test_duplicate_headers_and_missing_security_headers_rejected(self):
        before=self.server.controller.game.events_document()
        original=[("Host",self.server.allowed_host),("Origin",self.server.origin),(TOKEN_HEADER,self.server.token),("Content-Type","application/json"),("Content-Length","19")]
        for modified in ([h for h in original if h[0]!="Origin"],[h for h in original if h[0]!=TOKEN_HEADER],original+[original[0]],original+[original[1]],original+[original[2]],original+[original[-1]]):
            connection=HTTPConnection("127.0.0.1",self.server.server_port,timeout=5);connection.putrequest("POST","/api/command",skip_host=True)
            for name,value in modified:connection.putheader(name,value)
            connection.endheaders(b'{"command":"pitch"}');response=connection.getresponse();self.assertIn(response.status,(400,403));response.read();connection.close()
        self.assertEqual(self.server.controller.game.events_document(),before)

    def test_requests_are_processed_sequentially_and_tokens_change_per_start(self):
        other=create_server()
        try:self.assertNotEqual(other.token,self.server.token);self.assertNotIn(self.server.token,self.server.origin)
        finally:other.server_close()
        totals=[]
        def pitch():totals.append(json.loads(self.request("POST","/api/command",{"command":"pitch"})[2])["pitches_accepted"])
        threads=[threading.Thread(target=pitch) for _ in range(2)]
        for thread in threads:thread.start()
        for thread in threads:thread.join(timeout=5)
        self.assertEqual(sorted(totals),[1,2]);expected=GameSession();expected.advance_one_pitch();expected.advance_one_pitch()
        self.assertEqual(self.server.controller.game.events_document(),expected.events_document())

    def test_static_accessible_controls_safe_dom_no_external_resources_and_busy_new_lock(self):
        html=(STATIC/"index.html").read_text(encoding="utf-8");script=(STATIC/"app.js").read_text(encoding="utf-8");css=(STATIC/"style.css").read_text(encoding="utf-8")
        for value in ('lang="ja"','name="viewport"','<label for="seed"','<button','<table','scope="col"','role="status"'):self.assertIn(value,html)
        self.assertNotIn("https://",html);self.assertNotIn("innerHTML",script);self.assertNotIn("Number(seed)",script)
        self.assertIn('JSON.stringify(command === "new" ? {command, seed}',script);self.assertIn('querySelectorAll("button, input")',script)
        self.assertIn('byId("current").hidden = data.done',script);self.assertIn("@media(max-width:680px)",css);self.assertIn(":focus-visible",css)


if __name__=="__main__":unittest.main()
