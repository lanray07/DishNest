"""Observe the existing disposable QA timer in forced deep idle; never launch the ordinary app."""
import argparse
import hashlib
from contextlib import closing
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tempfile
import time
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
QA = 'com.dishnest.app.qa'
OUT = ROOT / 'artifacts/fire-doze-qa.json'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--max-wait-seconds', type=int, default=720)
    args = parser.parse_args()
    if not 35 <= args.max_wait_seconds <= 900: raise ValueError('Observation bound must be 35..900 seconds')
    # Use the same named mutex as the UI/native/timer controllers on Windows.
    import ctypes
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateMutexW.restype = ctypes.c_void_p
    kernel.CreateMutexW.argtypes = [ctypes.c_void_p, ctypes.c_bool, ctypes.c_wchar_p]
    kernel.WaitForSingleObject.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
    kernel.ReleaseMutex.argtypes = [ctypes.c_void_p]
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    mutex = kernel.CreateMutexW(None, False, 'Local\\DishNestFireQa')
    if not mutex or kernel.WaitForSingleObject(mutex, 0) not in (0, 0x80):
        if mutex: kernel.CloseHandle(mutex)
        raise ValueError('Another Fire QA controller is active')
    try:
        probe(args.max_wait_seconds)
    finally:
        kernel.ReleaseMutex(mutex)
        kernel.CloseHandle(mutex)


def probe(max_wait):
    devices = [line.split()[0] for line in subprocess.check_output([str(ADB), 'devices']).decode().splitlines()[1:] if line.endswith('\tdevice')]
    if len(devices) != 1: raise ValueError('Exactly one authorized Fire tablet is required')
    def adb(*args, timeout=30, check=True):
        try:
            result = subprocess.run([str(ADB), '-s', devices[0], *args], capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise ValueError('ADB timed out; private command details omitted') from None
        if check and result.returncode: raise ValueError('ADB operation failed; private output omitted')
        return result.stdout
    def state(kind): return adb('shell', 'dumpsys', 'deviceidle', 'get', kind).decode().strip()
    def ordinary():
        paths = adb('exec-out', 'run-as', 'com.dishnest.app', 'find', '.', '-type', 'f').decode().splitlines()
        return {p: hashlib.sha256(adb('exec-out', 'run-as', 'com.dishnest.app', 'cat', p)).hexdigest()
                for p in paths if p.startswith(('./databases/', './shared_prefs/', './files/'))}
    model = adb('shell', 'getprop', 'ro.product.model').decode().strip()
    if not model.startswith('KF'): raise ValueError('Selected device is not a Fire tablet')
    if state('force') != 'false': raise ValueError('Device is already forced idle; leave its state unchanged')
    if adb('shell', 'dumpsys', 'deviceidle', 'enabled', 'deep').strip() != b'1':
        raise ValueError('Deep idle is disabled; this controller will not enable it')
    before = ordinary()
    smoke = json.loads((ROOT / 'artifacts/fire-device-qa.json').read_text(encoding='utf-8-sig'))
    if smoke.get('passed') is not True: raise ValueError('Current isolated QA smoke pass required')
    expected = [(QA, ROOT / 'app/build/outputs/apk/qa/app-qa.apk', smoke['apkSha256']),
                (QA + '.test', ROOT / 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk', smoke['instrumentationApkSha256'])]
    for package, path, digest in expected:
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest: raise ValueError('Local QA APK differs from smoke evidence')
        installed = adb('shell', 'pm', 'path', package).decode().strip()
        if not installed.startswith('package:') or '\n' in installed: raise ValueError('Single installed QA APK required')
        if hashlib.sha256(adb('exec-out', 'cat', installed[8:], timeout=90)).hexdigest() != digest:
            raise ValueError('Installed QA APK differs from smoke evidence')
    whitelist = adb('shell', 'dumpsys', 'deviceidle', 'whitelist').decode().splitlines()
    if any(QA in line.split(',') for line in whitelist): raise ValueError('QA package is exempt from idle; no whitelist change will be made')
    record = dict(testedAt=datetime.now(timezone.utc).isoformat(), model=model, packageId=QA,
                  apkSha256=expected[0][2], instrumentationApkSha256=expected[1][2],
                  controllerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  installedApksVerified=True, qaExemptFromIdle=False, passed=False,
                  ordinaryAppModified=False, privateIdentifiersStored=False, paidServicesEnabled=False)
    OUT.write_text(json.dumps(record, indent=2) + '\n')
    runner = QA + '.test/androidx.test.runner.AndroidJUnitRunner'
    def phase(name):
        output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'com.dishnest.app.FireBackgroundAlarmTest', '-e', 'phase', name, runner, timeout=120).decode(errors='replace')
        (ROOT / f'artifacts/fire-doze-{name}.txt').write_text(output, encoding='utf-8')
        return 'OK (1 test)' in output and not any(s in output for s in ['FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed'])
    forced = seeded = False
    try:
        seeded = phase('seed')
        if not seeded: raise ValueError('QA kitchen/timer seed failed')
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'am', 'kill', QA)
        record['processAbsentAfterKill'] = not adb('shell', 'pidof', QA, check=False).strip()
        if not record['processAbsentAfterKill']: raise ValueError('QA process did not stop')
        # Mark cleanup required before sending the command, even if its transport fails.
        forced = True
        adb('shell', 'dumpsys', 'deviceidle', 'force-idle', 'deep')
        record['idleStateAtStart'] = state('deep')
        if record['idleStateAtStart'] != 'IDLE': raise ValueError('Forced deep idle not observed')
        started = time.monotonic()
        # Read a stable QA-only SQLite snapshot before instrumentation can wake/restart the app.
        def snapshot():
            names = adb('exec-out', 'run-as', QA, 'ls', '-1', 'databases').decode().splitlines()
            return {n: adb('exec-out', 'run-as', QA, 'cat', 'databases/' + n)
                    for n in ('dishnest.db', 'dishnest.db-wal') if n in names}
        record['observationBoundSeconds'] = max_wait
        record['timerHandledBeforeVerifierRestart'] = False
        time.sleep(35)
        while True:
            record['idleStateBeforeSnapshot'] = state('deep')
            if record['idleStateBeforeSnapshot'] != 'IDLE': raise ValueError('Tablet left forced idle; no idle verdict')
            files = snapshot()
            if 'dishnest.db' not in files: raise ValueError('QA database missing; no idle verdict')
            if files == snapshot():
                with tempfile.TemporaryDirectory(prefix='dishnest-qa-doze-') as folder:
                    for name, data in files.items(): (Path(folder) / name).write_bytes(data)
                    with closing(sqlite3.connect(str(Path(folder) / 'dishnest.db'))) as db:
                        if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok': raise ValueError('QA snapshot integrity failed')
                        rows = db.execute('SELECT notified FROM timers WHERE label=?', ('QA background alarm fixture',)).fetchall()
                        record['timerHandledBeforeVerifierRestart'] = rows == [(1,)]
            record['observationSeconds'] = round(time.monotonic() - started, 1)
            if record['timerHandledBeforeVerifierRestart'] or record['observationSeconds'] >= max_wait: break
            print(json.dumps({'dozeObservationSeconds': record['observationSeconds'], 'timerHandled': False}), flush=True)
            time.sleep(min(30, max_wait - record['observationSeconds']))
        record['idleStateAfterSnapshot'] = state('deep')
    finally:
        if forced: adb('shell', 'dumpsys', 'deviceidle', 'unforce')
        record['forcedIdleRestored'] = state('force') == 'false'
        # Existing instrumentation checks the own notification and cleans only its named fixtures.
        if seeded: record['kitchenAndNotificationVerificationPassed'] = phase('verify')
        record['ordinaryDataHashesUnchanged'] = ordinary() == before
        record['passed'] = all(record.get(k) is True for k in [
            'processAbsentAfterKill', 'timerHandledBeforeVerifierRestart', 'forcedIdleRestored',
            'kitchenAndNotificationVerificationPassed', 'ordinaryDataHashesUnchanged']) and all(
                record.get(k) == 'IDLE' for k in ['idleStateAtStart', 'idleStateBeforeSnapshot', 'idleStateAfterSnapshot'])
        record['notCovered'] = ['Natural idle entry/long idle quotas', 'Exact notification latency', 'Reboot', 'Wall-clock changes', 'Other Fire models']
        OUT.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(record))
    if not record['passed']: raise ValueError('Doze probe incomplete; no passing claim')


if __name__ == '__main__': main()
