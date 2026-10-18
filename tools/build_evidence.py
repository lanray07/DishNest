"""Bind local build evidence to current Android inputs and exact output files; never publishes."""
import argparse
import hashlib
import json
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'artifacts/build-provenance.json'
START = ROOT / 'artifacts/build-start.json'

def digest(path):
    hasher = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            hasher.update(block)
    return hasher.hexdigest()

def input_hashes():
    inputs = [p for folder in ('app/src', 'app/schemas', 'gradle') for p in (ROOT / folder).rglob('*') if p.is_file()]
    inputs += [ROOT / name for name in ('app/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat')]
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(inputs)}

def current():
    outputs = [ROOT / 'artifacts/delivery-verification.log', ROOT / 'app/build/reports/lint-results-debug.xml']
    outputs += list((ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    outputs += [ROOT / path for path in ('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/release/app-release-unsigned.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]
    if not outputs[0].is_file() or 'BUILD SUCCESSFUL' not in outputs[0].read_text(encoding='utf-8', errors='replace'):
        raise ValueError('Full Gradle verification must succeed before recording evidence.')
    if len(outputs) < 5 or any(not p.is_file() for p in outputs):
        raise ValueError('Missing test/lint/APK evidence; unsigned local verification outputs are required.')
    return {'inputs': input_hashes(), 'outputs': {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(outputs)}}

def verify():
    try:
        return json.loads(REPORT.read_text(encoding='utf-8')) == current()
    except (OSError, ValueError):
        return False

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--record', action='store_true')
    parser.add_argument('--begin', action='store_true')
    args = parser.parse_args()
    if args.begin:
        START.write_text(json.dumps({'started': time.time(), 'inputs': input_hashes()}, indent=2) + '\n', encoding='utf-8')
        print('Build input hashes captured. Run full Gradle verification before --record.')
        raise SystemExit(0)
    if args.record:
        start = json.loads(START.read_text(encoding='utf-8'))
        result = current()
        if start['inputs'] != result['inputs'] or (ROOT / 'artifacts/delivery-verification.log').stat().st_mtime < start['started']:
            raise SystemExit('Inputs changed during verification, or the full build log is older than --begin. Repeat the build.')
        REPORT.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    passed = verify()
    print(json.dumps({'current_build_evidence': passed}))
    if not passed:
        raise SystemExit('Build evidence is missing or stale. Run full Gradle verification, then record provenance.')
