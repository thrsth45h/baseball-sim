"""Check the built test APK contains the immutable independent v1 fixture.

Run in isolated CI after assembleDebugAndroidTest, before long emulator tests.
The .bin packaging alias contains the original .db.gz bytes without modification.
"""
import gzip
import hashlib
import json
from pathlib import Path
import zipfile


APK = Path("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
ASSET = "assets/lineup/one-game-v1.fixture.bin"
PACKED_SHA = "8596765ca1ef34c0e33b619529acf3ce52541801888691796b0c0f13a0da272f"
DATABASE_SHA = "4247d3c43dbce76e505c7676eb419df03c6602adf9ce020340e74fef6dd557c4"


def verify(apk: Path) -> dict:
    with zipfile.ZipFile(apk) as archive:
        if archive.namelist().count(ASSET) != 1:
            raise ValueError(f"expected exactly one {ASSET} in {apk}")
        packed = archive.read(ASSET)
    database = gzip.decompress(packed)
    packed_sha = hashlib.sha256(packed).hexdigest()
    database_sha = hashlib.sha256(database).hexdigest()
    if (len(packed), packed_sha) != (43832, PACKED_SHA):
        raise ValueError("packaged gzip does not match the independent fixture")
    if (len(database), database_sha) != (466944, DATABASE_SHA):
        raise ValueError("decompressed database does not match the independent fixture")
    return {"schema": "lineup-fixture-packaging-v1", "verified": True,
            "apk": str(apk), "asset": ASSET,
            "original_source_name": "one-game-v1.db.gz",
            "packed_bytes": len(packed), "packed_sha256": packed_sha,
            "database_bytes": len(database), "database_sha256": database_sha,
            "provenance": "constructed schema1 fixture; not actual old APK output or upgrade"}


if __name__ == "__main__":
    report = verify(APK)
    destination = Path("app/build/lineup-fixture-packaging.json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
