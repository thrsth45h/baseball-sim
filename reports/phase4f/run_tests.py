"""Record one suite at a time; keep test methods distinct from subTests."""
import argparse
import json
from pathlib import Path
import platform
import subprocess
import sys
import time
import unittest

SOURCE_COMMIT="a5fda901bc0a8cca9ae0eefc1d076fd8171c73a6"
PHASES=("phase4f","phase4e","phase4d","phase4c","phase4a","phase4b")


class Result(unittest.TextTestResult):
    subtests=0
    def addSubTest(self,test,subtest,err):
        self.subtests+=1
        super().addSubTest(test,subtest,err)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--phase",choices=PHASES,required=True);parser.add_argument("--output",required=True);args=parser.parse_args()
    root=Path.cwd().resolve();output=Path(args.output).resolve();namespace=root/"data/prototypes/phase4f"
    if output==namespace or not output.is_relative_to(namespace) or output.exists():raise ValueError("choose a new ignored Phase4f test record below its namespace")
    if subprocess.check_output(["git","diff","--name-only",SOURCE_COMMIT,"--","prototypes",".github/workflows"]).strip():raise ValueError("source/tests/CI differ from fixed core")
    sys.path.insert(0,str(root))
    suite=unittest.defaultTestLoader.discover("prototypes/"+args.phase+"/tests");start=time.perf_counter()
    result=unittest.TextTestRunner(verbosity=1,resultclass=Result).run(suite);elapsed=time.perf_counter()-start
    record={"phase":args.phase,"python":platform.python_version(),"source_commit":SOURCE_COMMIT,"verification_date_jst":"2026-10-31",
        "test_methods_run":result.testsRun,"subtests_observed_separate":result.subtests,"failures":len(result.failures),"errors":len(result.errors),"skipped":len(result.skipped),
        "successful":result.wasSuccessful(),"duration_seconds":elapsed,"test_case_directories_preserved":True,"windows_real_junction_cases":4 if platform.system()=="Windows" and result.wasSuccessful() else 0,
        "linux_real_directory_symlink_cases":4 if platform.system()=="Linux" and result.wasSuccessful() else 0}
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open("x",encoding="utf-8",newline="\n") as stream:json.dump(record,stream,indent=2,sort_keys=True);stream.write("\n")
    print(json.dumps(record));return 0 if record["successful"] and not record["skipped"] else 1


if __name__=="__main__":raise SystemExit(main())
