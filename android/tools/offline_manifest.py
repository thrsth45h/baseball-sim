"""CI-only check of dependency-merged manifests, after debug assembly."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

project=Path(__file__).resolve().parents[1]
candidates=[]
for area in ("merged_manifest","merged_manifests","packaged_manifests"):
    candidates.extend((project/"app/build/intermediates"/area/"debug").rglob("AndroidManifest.xml"))
if not candidates:raise RuntimeError("no merged debug manifest to inspect")
records=[]
for path in sorted(candidates):
    root=ET.parse(path).getroot()
    permissions=[node.get("{http://schemas.android.com/apk/res/android}name") for node in root if node.tag.startswith("uses-permission")]
    if "android.permission.INTERNET" in permissions:raise RuntimeError("merged manifest requests INTERNET")
    records.append({"manifest":path.relative_to(project).as_posix(),"permissions":permissions,"internet_permission_absent":True})
target=project/"app/build/outputs/apk/debug/offline-manifest-check.json"
with target.open("x",encoding="utf-8",newline="\n") as stream:json.dump({"checked":records},stream,sort_keys=True,indent=2);stream.write("\n")
