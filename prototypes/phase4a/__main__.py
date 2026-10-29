"""Only the approved fixed-fixture run command is exposed."""
import argparse
from .runner import run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    command = commands.add_parser("run")
    command.add_argument("--output", required=True)
    arguments = parser.parse_args()
    print(run(arguments.output))


if __name__ == "__main__":
    main()
