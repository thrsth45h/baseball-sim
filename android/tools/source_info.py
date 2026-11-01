"""CI-only provenance for the debug APK; no tokens or user environment dump."""
import hashlib
import json
import os
from pathlib import Path
import subprocess


def command(*args):return subprocess.check_output(args,stderr=subprocess.STDOUT,text=True).strip()


def main():
    project=Path(__file__).resolve().parents[1]
    apk=project/"app/build/outputs/apk/debug/app-debug.apk"
    source=command("git","rev-parse","HEAD");expected=os.environ["SOURCE_SHA"]
    if source!=expected:raise RuntimeError("checkout differs from the requested source head")
    sdk=Path(os.environ["ANDROID_HOME"])
    packages={name:(sdk/name/"source.properties").read_text(encoding="utf-8") for name in ("platforms/android-36","build-tools/35.0.0")}
    record={"source_head":source,"source_tree":command("git","rev-parse","HEAD^{tree}"),"requested_source_head":expected,
        "jdk_actual":command("java","-version"),"sdk_packages_actual":packages,"apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),
        "pins":{"gradle":"8.13","agp":"8.11.1","kotlin_compiler":"2.2.21","compose_compiler":"2.2.21","compose_bom":"2025.10.01",
            "compile_sdk":36,"target_sdk":36,"min_sdk":26,"build_tools":"35.0.0","jdk":"temurin-17.0.21+9","rng":"sm64-v1 (this prototype only)"}}
    with (apk.parent/"source-info.json").open("x",encoding="utf-8",newline="\n") as stream:json.dump(record,stream,sort_keys=True,indent=2);stream.write("\n")


if __name__=="__main__":main()
