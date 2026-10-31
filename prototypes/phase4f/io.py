"""New Phase4f output only. Identity covers normalized text and Python version."""
import hashlib
import json
from pathlib import Path
import platform
from . import DEPENDENCY_SOURCES

SOURCE_ROOT = Path(__file__).resolve().parents[2]
ROOT = SOURCE_ROOT


def canonical(value):
    return (json.dumps(value, ensure_ascii=False, allow_nan=False, sort_keys=True, indent=2)+"\n").encode("utf-8")


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write_json(path, value):
    with Path(path).open("xb") as stream:
        stream.write(canonical(value))


def implementation_identity():
    folder = Path(__file__).resolve().parent
    paths = [*(SOURCE_ROOT/name for name in DEPENDENCY_SOURCES), *folder.glob("*.py"), folder/".gitattributes",
             *sorted((folder/"fixtures").glob("*.json")), *sorted((folder/"profiles").glob("*.json"))]
    sources = {}
    for path in sorted(paths):
        value = path.read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
        sources[path.relative_to(SOURCE_ROOT).as_posix()] = hashlib.sha256(value.encode("utf-8")).hexdigest()
    return {"old_dependency_sources": list(DEPENDENCY_SOURCES), "text_hash_rule": "UTF-8; CRLF and CR normalized to LF before SHA256",
            "sources_and_fixtures": sources, "bundle_sha256": hashlib.sha256(canonical(sources)).hexdigest(),
            "python": platform.python_version(), "python_implementation": platform.python_implementation(),
            "third_party_runtime_dependencies": []}


def reserve_output(output, input_roots=()):
    root, path = ROOT.resolve(), Path(output).resolve()
    # A symlink/junction must not redefine the declared namespace positions.
    allowed = (root/"data/prototypes/phase4f", root/"reports/phase4f")
    if not path.is_relative_to(root) or not any(path != base and path.is_relative_to(base) for base in allowed):
        raise ValueError("new output must stay below the declared Phase4f namespace in this checkout")
    for item in input_roots:
        item = Path(item).resolve()
        if path.is_relative_to(item) or item.is_relative_to(path):
            raise ValueError("output overlaps protected input")
    path.mkdir(parents=True, exist_ok=False)
    return path
