"""Fixed-fixture runner. No external/raw/model/seed/resume options."""
import argparse
from .runner import run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    command = sub.add_parser("run", help="run only the approved fixed synthetic fixtures")
    command.add_argument("--output", required=True)
    args = parser.parse_args()
    run(args.output)


if __name__ == "__main__":
    main()
