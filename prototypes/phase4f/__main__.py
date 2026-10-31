"""Run one complete toy game; no external models, free weights, or resume CLI."""
import argparse
import sys
from .protocol import parse_json
from .runner import REASON_LABELS, run


def main():
    if hasattr(sys.stdout,"reconfigure"):sys.stdout.reconfigure(encoding="utf-8")
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest="command",required=True);command=sub.add_parser("run")
    command.add_argument("--seed",type=int,default=42);command.add_argument("--max-pitches",type=int,default=10000);command.add_argument("--output",required=True)
    args=parser.parse_args()
    try:out=run(args.output,args.seed,args.max_pitches)
    except (ValueError,OSError) as error:parser.error(str(error))
    result=parse_json((out/"result.json").read_text(encoding="utf-8"));score=result["score"]
    print(f"{result['status']}: Away {score['away']} - Home {score['home']} / {REASON_LABELS[result['reason']]} / {result['pitches_accepted']} pitches")
    print(str(out))
    return 0 if result["status"]=="completed" else 2


if __name__=="__main__":raise SystemExit(main())
