"""Check the built test APK contains the immutable independent v1 fixture.

Run in isolated CI after assembleDebugAndroidTest, before long emulator tests.
The .bin packaging alias contains the original .db.gz bytes without modification.
"""
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
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


def verify_v2(apk: Path) -> dict:
    asset = "assets/batter-profile/one-game-v2.fixture.bin"
    with zipfile.ZipFile(apk) as archive:
        if archive.namelist().count(asset) != 1:
            raise ValueError(f"expected exactly one {asset} in {apk}")
        packed = archive.read(asset)
    database = gzip.decompress(packed)
    packed_sha = hashlib.sha256(packed).hexdigest()
    database_sha = hashlib.sha256(database).hexdigest()
    if (len(packed), packed_sha) != (44266, "6f50ba22f38304a15dcd5925665355d2ced1915dd223fbcd5781a5869c4e377a"):
        raise ValueError("packaged v2 gzip does not match the independent fixture")
    if (len(database), database_sha) != (475136, "85e6d5316017ecccbd05ee2b8c6773e5337c78abdb42ed9ca66bcb21549481a9"):
        raise ValueError("decompressed v2 database does not match the independent fixture")
    return {"asset": asset, "verified": True, "packed_bytes": len(packed), "packed_sha256": packed_sha,
            "database_bytes": len(database), "database_sha256": database_sha,
            "provenance": "manager constructed schema1 DB from historical v2 frame/DDL; not actual old APK output or upgrade"}


def verify_completed_v3(apk: Path) -> dict:
    asset = "assets/season-history/completed-v3.db.gz"
    with zipfile.ZipFile(apk) as archive:
        if archive.namelist().count(asset) != 1:
            raise ValueError(f"expected exactly one {asset} in {apk}")
        packed = archive.read(asset)
    packed_sha = hashlib.sha256(packed).hexdigest()
    if (len(packed), packed_sha) != (459205, "05136adb14a7fc531c590a8f41377b919d67639c42ae53e771f946cd0c66986c"):
        raise ValueError("packaged v3 gzip does not match the independent fixture")
    db = sqlite3.connect(":memory:")
    try:
        db.deserialize(gzip.decompress(packed))
        schema = db.execute("PRAGMA user_version").fetchone()[0]
        (head,) = db.execute("SELECT payload FROM working_state").fetchone()
        (slot,) = db.execute("SELECT payload FROM manual_slot").fetchone()
        (games,) = db.execute("SELECT COUNT(*) FROM completed_results").fetchone()
    finally:
        db.close()
    frame = hashlib.sha256(head).hexdigest()
    if frame != "d255bd19f2a9de71439b82334968d96723bccd79cfe222afc512f09c67e3fc86" or head != slot or schema != 1 or games != 720:
        raise ValueError("packaged v3 fixture does not hold the verified historical season")
    return {"asset": asset, "verified": True, "packed_bytes": len(packed), "packed_sha256": packed_sha,
            "frame_sha256": frame, "schema_version": schema, "completed_games": games,
            "provenance": "constructed schema1 DB from the actual previously verified v3 frame; not an old APK upgrade"}


if __name__ == "__main__":
    report = verify(APK)
    report["additional_fixtures"] = [verify_v2(APK), verify_completed_v3(APK)]
    destination = Path("app/build/lineup-fixture-packaging.json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
