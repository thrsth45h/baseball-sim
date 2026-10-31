"""Reproducible command, declared inputs, and actual filesystem boundaries."""
import builtins
import contextlib
import hashlib
import io as text_io
import itertools
import os
from pathlib import Path
import random
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from prototypes.phase4f import CAPABILITIES, DEPENDENCY_SOURCES
from prototypes.phase4f import io, runner, source
from prototypes.phase4f.protocol import parse_json


class RunnerTests(unittest.TestCase):
    def setUp(self):
        parent=io.SOURCE_ROOT/"data/prototypes/phase4f/unittest";parent.mkdir(parents=True,exist_ok=True)
        self.work=Path(tempfile.mkdtemp(prefix="case-",dir=parent));root_patch=patch.object(io,"ROOT",self.work);root_patch.start();self.addCleanup(root_patch.stop)

    def test_two_runs_five_files_hashes_flags_and_no_absolute_paths(self):
        first=runner.run(self.work/"data/prototypes/phase4f/one");second=runner.run(self.work/"reports/phase4f/two")
        names={"plan.json","events.json","result.json","REPORT.md","SHA256SUMS.json"}
        self.assertEqual({p.name for p in first.iterdir()},names)
        for p in first.iterdir():
            self.assertEqual(p.read_bytes(),(second/p.name).read_bytes());self.assertNotIn(str(self.work),p.read_text(encoding="utf-8"))
        manifest=parse_json((first/"SHA256SUMS.json").read_text());self.assertEqual(set(manifest),names-{"SHA256SUMS.json"})
        for name,wanted in manifest.items():self.assertEqual(io.digest(first/name),wanted)
        for name in ("plan.json","events.json","result.json"):
            value=parse_json((first/name).read_text())
            for key,wanted in CAPABILITIES.items():self.assertEqual(value[key],wanted)
        report=(first/"REPORT.md").read_text(encoding="utf-8");self.assertIn("仮Away 4 — 2 仮Home",report)
        self.assertNotIn("打率 |",report);self.assertNotIn("2B |",report)

    def test_output_namespaces_existing_and_input_overlap(self):
        for path in (self.work/"data/prototypes/phase4e/new",self.work/"reports/phase4d/new",self.work/"data/raw/new",self.work/"reports/phase4f",self.work.parent/"outside"):
            with self.assertRaises(ValueError):io.reserve_output(path)
        output=self.work/"data/prototypes/phase4f/one"
        for p in (output,output/"child",output.parent):
            with self.assertRaises(ValueError):io.reserve_output(output,[p])
        self.assertFalse(output.exists());io.reserve_output(output)
        with self.assertRaises(FileExistsError):io.reserve_output(output)

    def test_real_junction_symlink_whole_child_to_old_outside(self):
        for location,kind in itertools.product(("namespace","child"),("old","outside")):
            with self.subTest(location=location,kind=kind):
                root=self.work/(location+"-"+kind);target=root/"reports/phase4e" if kind=="old" else self.work/(root.name+"-outside");target.mkdir(parents=True)
                namespace=root/"data/prototypes/phase4f";alias=namespace if location=="namespace" else namespace/"shortcut";alias.parent.mkdir(parents=True,exist_ok=True)
                if os.name=="nt":subprocess.run(["cmd","/c","mklink","/J",str(alias),str(target)],check=True,capture_output=True,text=True)
                else:alias.symlink_to(target,target_is_directory=True)
                with patch.object(io,"ROOT",root),self.assertRaises(ValueError):io.reserve_output(alias/"forbidden")
                self.assertFalse((target/"forbidden").exists())

    def test_identity_complete_eleven_old_sources_new_profile_fixtures_and_lf_rules(self):
        identity=io.implementation_identity();self.assertEqual(identity["old_dependency_sources"],list(DEPENDENCY_SOURCES));self.assertEqual(len(DEPENDENCY_SOURCES),11)
        required=set(DEPENDENCY_SOURCES)|{"prototypes/phase4f/.gitattributes","prototypes/phase4f/fixtures/scripted_cases.json","prototypes/phase4f/fixtures/ending_cases.json","prototypes/phase4f/profiles/toy-v1.json"}
        required|={p.relative_to(io.SOURCE_ROOT).as_posix() for p in (io.SOURCE_ROOT/"prototypes/phase4f").glob("*.py")}
        self.assertEqual(set(identity["sources_and_fixtures"]),required);self.assertEqual(hashlib.sha256(io.canonical(identity["sources_and_fixtures"])).hexdigest(),identity["bundle_sha256"])
        root=self.work/"identity-copy";originals={name:(io.SOURCE_ROOT/name).read_text(encoding="utf-8").replace("\r\n","\n").replace("\r","\n") for name in required}
        for newline in ("\n","\r\n","\r"):
            for name,text in originals.items():
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(text.replace("\n",newline).encode())
            with patch.object(io,"SOURCE_ROOT",root),patch.object(io,"__file__",str(root/"prototypes/phase4f/io.py")):self.assertEqual(io.implementation_identity(),identity)

    def test_run_only_allowed_source_profile_fixture_reads_owned_rng_and_no_clock(self):
        allowed={(io.SOURCE_ROOT/name).resolve() for name in io.implementation_identity()["sources_and_fixtures"]};opened=[]
        original_open,original_path_open,original_import=builtins.open,Path.open,builtins.__import__
        imports={"prototypes.phase4"+phase+suffix for phase in ("a","b","c","d","e") for suffix in ("",".model")}
        def guard(path,mode):
            if not isinstance(path,(str,bytes,Path)) or not ('r' in mode or '+' in mode):return
            path=Path(path).resolve()
            if path not in allowed and not path.is_relative_to(self.work):raise AssertionError("unapproved old/data/source read")
            opened.append(path)
        def file_open(path,mode='r',*args,**kwargs):guard(path,mode);return original_open(path,mode,*args,**kwargs)
        def path_open(path,mode='r',*args,**kwargs):guard(path,mode);return original_path_open(path,mode,*args,**kwargs)
        def imported(name,*args,**kwargs):
            if (any("phase4"+p in name for p in "abcde") and name not in imports) or any(word in name for word in ("statcast","phase3")):raise AssertionError("unapproved old/research import")
            return original_import(name,*args,**kwargs)
        def forbidden(*a,**kw):raise AssertionError("global RNG/clock prohibited")
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(builtins,"open",file_open));stack.enter_context(patch.object(Path,"open",path_open));stack.enter_context(patch.object(builtins,"__import__",imported))
            for name in ("seed","random","uniform","randint","randrange","choice","gauss","getrandbits","SystemRandom"):stack.enter_context(patch.object(random,name,forbidden))
            for name in ("time","time_ns","monotonic","perf_counter"):stack.enter_context(patch.object(time,name,forbidden))
            runner.run(self.work/"data/prototypes/phase4f/audited")
        for name in DEPENDENCY_SOURCES:self.assertIn((io.SOURCE_ROOT/name).resolve(),opened)

    def test_profile_fixed_fixture_and_identity_drift_rejected_before_output(self):
        output=self.work/"data/prototypes/phase4f/changed";identity=io.implementation_identity()
        with patch.object(runner,"implementation_identity",side_effect=[identity,identity|{"python":"changed"}]),self.assertRaises(ValueError):runner.run(output)
        self.assertFalse(output.exists())
        folder=self.work/"fixtures";folder.mkdir();value=parse_json((runner.FIXTURE_ROOT/"scripted_cases.json").read_text());value["cases"][0]["repetitions"]=55
        (folder/"scripted_cases.json").write_bytes(io.canonical(value))
        with patch.object(runner,"FIXTURE_ROOT",folder),self.assertRaises(ValueError):runner.run(output)
        self.assertFalse(output.exists())
        profile=source.load_profile().as_dict();profile["pitch_weights"][0][1]=36
        with patch.object(source,"parse_json",return_value=profile),self.assertRaises(ValueError):source.load_profile()

    def test_cli_seed42_abort_exit_and_external_options_rejected(self):
        from prototypes.phase4f.__main__ import main
        for option in ("--input","--raw-root","--model","--resume","--weights"):
            with patch.object(sys,"argv",["phase4f","run","--output","x",option,"x"]),contextlib.redirect_stderr(text_io.StringIO()),self.assertRaises(SystemExit):main()
        output=self.work/"reports/phase4f/cli"
        with patch.object(sys,"argv",["phase4f","run","--seed","42","--output",str(output)]),contextlib.redirect_stdout(text_io.StringIO()) as console:self.assertEqual(main(),0)
        self.assertIn("Away 4 - Home 2",console.getvalue());self.assertTrue((output/"events.json").is_file())
        with patch.object(sys,"argv",["phase4f","run","--max-pitches","1","--output",str(output.parent/"abort")]),contextlib.redirect_stdout(text_io.StringIO()):self.assertEqual(main(),2)

    def test_optimized_python_keeps_explicit_validation(self):
        code="""from prototypes.phase4f.engine import GameSession
from prototypes.phase4f.protocol import PitchInput,FairPlay
cases=[lambda:GameSession(True),lambda:GameSession(2**64),lambda:GameSession(42,False),lambda:PitchInput('bad'),lambda:FairPlay('safe_hit',True,(None,)*3)]
for case in cases:
    try:case()
    except ValueError:continue
    raise RuntimeError('optimized validation vanished')
"""
        subprocess.run([sys.executable,"-O","-c",code],cwd=io.SOURCE_ROOT,check=True,capture_output=True)
