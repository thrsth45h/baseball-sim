"""Additional provenance for the Phase5a APK; this does not alter old APK evidence."""
import hashlib
import json
from pathlib import Path
import re


def main():
    project = Path(__file__).resolve().parents[1]
    output = project / 'app/build/outputs/apk/debug'
    base = json.loads((output / 'source-info.json').read_bytes())
    apk = output / 'app-debug.apk'
    app = (project / 'app/build.gradle.kts').read_text()
    plugins = (project / 'build.gradle.kts').read_text()
    schema = project / 'app/schemas/com.example.baseballsim.data.PennantDatabase/1.json'
    assert base['apk_sha256'] == hashlib.sha256(apk.read_bytes()).hexdigest()
    pins = {'room': re.search(r'androidx\.room:room-runtime:([0-9.]+)', app).group(1),
            'ksp': re.search(r'com\.google\.devtools\.ksp"\) version "([^"]+)', plugins).group(1)}
    info = {'schema':'pennant-apk-source-v1','source_head':base['source_head'],
        'source_tree':base['source_tree'],'apk_sha256':base['apk_sha256'],
        'application_id':'com.example.baseballsim',
        'version_code':int(re.search(r'versionCode = ([0-9]+)',app).group(1)),
        'version_name':re.search(r'versionName = "([^"]+)',app).group(1),
        'additional_pins':pins,'room_schema_sha256':hashlib.sha256(schema.read_bytes()).hexdigest(),
        'scope':'Phase5a single-season technical prototype; no permanent product save compatibility promise'}
    with (output/'pennant-source-info.json').open('x',encoding='utf-8',newline='\n') as f:
        json.dump(info,f,sort_keys=True,indent=2);f.write('\n')


if __name__=='__main__':main()
