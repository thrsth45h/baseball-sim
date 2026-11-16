"""Generate test-only store copies; removing timer wrappers must recover the source.

This runs on Python only. It never invokes Gradle/SDK/adb or changes production files.
The baseline is a frozen Git object, not a manually maintained approximation.
"""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = "android/app/src/main/java/com/example/baseballsim/data/RoomSeasonStore.kt"
BASELINE = "7ebb4a46a319ffde5e390a86b8f0a3a0352e4866"
BASELINE_BLOB = "4cebd3e9bd283d0b0e70e70c81070edf17fae704"
OUTPUT = ROOT / "android/app/src/androidTest/java/com/example/baseballsim/data/generated"
OPEN = re.compile(r'/\* STORE_BENCH_OPEN_\d+ \*/StoreBenchmarkTimers\.measure\("[a-z_]+"\) \{ ')
CLOSE = re.compile(r' \}/\* STORE_BENCH_CLOSE_\d+ \*/')


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def digest(value):
    return hashlib.sha256(value).hexdigest()


def normalized(path):
    return (ROOT / path).read_text(encoding="utf-8").encode("utf-8")


def stripped(text):
    return CLOSE.sub("", OPEN.sub("", text))


def closing(text, opening, left="{", right="}"):
    # Kotlin source anchors below have no braces/parentheses in string literals.
    # A source change violating this condition fails the recovered-source assertion.
    depth = 1
    for index in range(opening + 1, len(text)):
        if text[index] == left:
            depth += 1
        elif text[index] == right:
            depth -= 1
            if depth == 0:
                return index + 1
    raise AssertionError("Unbalanced source anchor")


def instrument(original, class_name):
    assert original.count("class RoomSeasonStore(") == 1
    plain = original.replace("class RoomSeasonStore(", f"class {class_name}(")
    start = plain.index("    override suspend fun commit(")
    end = plain.index("    override suspend fun saveSlot(", start)
    body = plain[start:end]
    spans = []

    def expression(needle, phase, mode="context"):
        hits = list(re.finditer(re.escape(needle), body))
        assert len(hits) == 1, (class_name, phase, len(hits), needle)
        begin = hits[0].start()
        if mode == "context":
            finish = closing(body, body.index("{", begin))
        elif mode == "call":
            finish = closing(body, body.index("(", begin), "(", ")")
        else:
            finish = begin + len(needle)
        spans.append((begin, finish, phase))

    expression("withContext(computation) { CheckpointCodec.encodeSeason(candidate) }", "candidate_encode")
    expression("withContext(computation) { checkedState(bytes, bodyHash) }", "candidate_check")
    # Both implementations retain one fully checked pre-update read.
    before = "verified()" if "verified()" in body else "read()"
    expression(before, "before_read", "literal")
    tx = body.index("val committed = ")
    transaction = body.index("withContext(io)", tx)
    finish = closing(body, body.index("{", transaction))
    spans.append((transaction, finish, "transaction"))
    # Final acknowledgement only; duplicate/no-op return sites remain uninstrumented
    # because they are correctness-only paths, never benchmark samples.
    final_return = body.rindex("        return ")
    expression_start = final_return + len("        return ")
    expression_end = body.rindex("\n    }")
    spans.append((expression_start, expression_end, "after_commit"))
    for (a, b, _), (c, d, _) in zip(sorted(spans), sorted(spans)[1:]):
        assert b <= c, (a, b, c, d)
    for identifier, (begin, finish, phase) in reversed(list(enumerate(sorted(spans)))):
        prefix = f'/* STORE_BENCH_OPEN_{identifier} */StoreBenchmarkTimers.measure("{phase}") {{ '
        suffix = f' }}/* STORE_BENCH_CLOSE_{identifier} */'
        body = body[:begin] + prefix + body[begin:finish] + suffix + body[finish:]
    generated = plain[:start] + body + plain[end:]
    assert stripped(generated) == plain, f"Timer removal changed {class_name}"
    assert len(OPEN.findall(generated)) == 5
    return plain, generated


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify committed generated copies without editing")
    parser.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    assert git("rev-parse", f"{BASELINE}:{SOURCE}").decode().strip() == BASELINE_BLOB
    baseline = git("show", f"{BASELINE}:{SOURCE}")
    candidate = normalized(SOURCE)
    dependencies = [
        "android/app/src/main/java/com/example/baseballsim/data/SeasonStore.kt",
        "android/app/src/main/java/com/example/baseballsim/data/PennantDatabase.kt",
        *[p.decode() for p in git("ls-tree", "-r", "--name-only", BASELINE, "android/core/src/main").splitlines()],
        "android/app/schemas/com.example.baseballsim.data.PennantDatabase/1.json",
    ]
    shared = []
    for path in dependencies:
        frozen = git("show", f"{BASELINE}:{path}")
        current = normalized(path)
        assert current == frozen, f"Benchmark shared dependency changed: {path}"
        shared.append({"path": path, "sha256": digest(frozen), "baseline_blob": git("rev-parse", f"{BASELINE}:{path}").decode().strip()})
    records = []
    for name, source in (("BaselineRoomSeasonStore", baseline), ("CandidateRoomSeasonStore", candidate)):
        plain, generated = instrument(source.decode("utf-8"), name)
        output = OUTPUT / f"{name}.kt"
        if args.check:
            assert output.read_text(encoding="utf-8") == generated, f"Regenerate {output.name}"
        else:
            OUTPUT.mkdir(parents=True, exist_ok=True)
            output.write_text(generated, encoding="utf-8", newline="\n")
        records.append({"class": name, "source_sha256": digest(source), "rename_only_sha256": digest(plain.encode()),
                        "instrumented_sha256": digest(generated.encode()), "timer_wrapper_count": 5,
                        "timer_removal_matches_rename_only_source": True,
                        "plain_source_differs_only_by_class_name": True,
                        "production_source_instrumented": False})
        if args.evidence:
            args.evidence.parent.mkdir(parents=True, exist_ok=True)
            (args.evidence.parent / f"{name}-rename-only.kt").write_text(plain, encoding="utf-8", newline="\n")
            diff = "".join(difflib.unified_diff(plain.splitlines(keepends=True), generated.splitlines(keepends=True),
                fromfile=f"{name}-rename-only.kt", tofile=f"{name}-test-only-timers.kt"))
            (args.evidence.parent / f"{name}-timers.diff").write_text(diff, encoding="utf-8", newline="\n")
    evidence = {"schema": "pennant-store-benchmark-generation-v1", "baseline": BASELINE,
                "baseline_blob": BASELINE_BLOB, "candidate_source_head": git("rev-parse", "HEAD").decode().strip(),
                "sources": records, "shared_dependencies": shared,
                "scope": "Test APK contains instrumented class-renamed copies; distribution APK uses original production class, without timers or benchmark flags."}
    if args.evidence:
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Verified frozen baseline, {len(shared)} unchanged dependencies, and reversible timer injection for two test copies.")


if __name__ == "__main__":
    main()
