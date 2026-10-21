"""Exercise the existing QA APK without connectivity; restore tablet settings in finally."""
import ctypes
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
QA = 'com.dishnest.app.qa'
OUT = ROOT / 'artifacts/fire-offline-qa.json'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def passed_trace(output, count):
    return (f'OK ({count} test' in output and
            len(re.findall(r'INSTRUMENTATION_STATUS_CODE: 0\s*$', output, re.M)) == count and
            not any(x in output for x in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed')))


def probe():
    devices = [x.split()[0] for x in subprocess.check_output([str(ADB), 'devices']).decode().splitlines()[1:]
               if x.endswith('\tdevice')]
    if len(devices) != 1:
        raise ValueError('Exactly one authorized Fire tablet is required')

    def adb(*args, timeout=30, check=True):
        try:
            result = subprocess.run([str(ADB), '-s', devices[0], *args], capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise ValueError('ADB timeout; private command details omitted') from None
        if check and result.returncode:
            raise ValueError('ADB operation failed; private output omitted')
        return result.stdout

    def setting(name):
        return adb('shell', 'settings', 'get', 'global', name).decode().strip()

    def ordinary():
        paths = adb('exec-out', 'run-as', 'com.dishnest.app', 'find', '.', '-type', 'f').decode().splitlines()
        hashes = {p: digest(adb('exec-out', 'run-as', 'com.dishnest.app', 'cat', p))
                  for p in paths if p.startswith(('./databases/', './shared_prefs/', './files/'))}
        installed = adb('shell', 'pm', 'path', 'com.dishnest.app').decode().strip()
        if not installed.startswith('package:') or '\n' in installed:
            raise ValueError('Single ordinary APK required for preservation check')
        return hashes, digest(adb('exec-out', 'cat', installed[8:], timeout=90))

    def disconnected():
        # Inspect connectivity state only; never persist network names or addresses.
        networks = adb('shell', 'dumpsys', 'connectivity').decode(errors='replace')
        return setting('airplane_mode_on') == '1' and setting('wifi_on') == '0' and not re.search(
            r'(?:state:\s*CONNECTED|state:\s*CONNECTED/CONNECTED|mActiveDefaultNetwork:\s*\d+)', networks)

    def radio(airplane, wifi):
        # Connectivity's shell API performs the protected broadcast itself. ADB
        # cannot send AIRPLANE_MODE directly on Fire OS.
        adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable' if airplane == '1' else 'disable')
        adb('shell', 'svc', 'wifi', 'enable' if wifi == '1' else 'disable')
        for _ in range(20):
            if setting('airplane_mode_on') == airplane and setting('wifi_on') == wifi:
                return
            time.sleep(0.5)
        raise ValueError('Radio settings did not reach requested state')

    model = adb('shell', 'getprop', 'ro.product.model').decode().strip()
    if not model.startswith('KF'):
        raise ValueError('Selected device is not a Fire tablet')
    lock = adb('shell', 'dumpsys', 'window', 'policy').decode()
    if any(x in lock for x in ('isStatusBarKeyguard=true', 'mIsShowing=true')):
        raise ValueError('Unlock the Fire before UI QA; secure authentication is never bypassed')
    prior = {n: setting(n) for n in ('airplane_mode_on', 'wifi_on', 'stay_on_while_plugged_in')}
    if prior['airplane_mode_on'] not in ('0', '1') or prior['wifi_on'] not in ('0', '1'):
        raise ValueError('Unexpected radio state; no settings changed')
    before = ordinary()
    journal = ROOT / 'build/fire-offline-settings.json'
    journal.parent.mkdir(exist_ok=True)
    journal.write_text(json.dumps(prior, indent=2) + '\n', encoding='utf-8')
    smoke = json.loads((ROOT / 'artifacts/fire-device-qa.json').read_text(encoding='utf-8-sig'))
    if not smoke.get('passed'):
        raise ValueError('Passing current QA smoke evidence required')
    for package, relative, expected in (
        (QA, 'app/build/outputs/apk/qa/app-qa.apk', smoke['apkSha256']),
        (QA + '.test', 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk', smoke['instrumentationApkSha256'])):
        installed = adb('shell', 'pm', 'path', package).decode().strip()
        if not installed.startswith('package:') or '\n' in installed:
            raise ValueError('Single installed QA APK required')
        if digest((ROOT / relative).read_bytes()) != expected or digest(adb('exec-out', 'cat', installed[8:], timeout=90)) != expected:
            raise ValueError('Local/installed QA APK differs from smoke evidence')
    record = dict(testedAt=datetime.now(timezone.utc).isoformat(), model=model, packageId=QA,
                  controllerSha256=digest(Path(__file__).read_bytes()),
                  apkSha256=smoke['apkSha256'], instrumentationApkSha256=smoke['instrumentationApkSha256'],
                  installedApksVerified=True, passed=False, privateIdentifiersStored=False,
                  paidServicesEnabled=False, ordinaryAppModified=False, phases={})
    OUT.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    runner = QA + '.test/androidx.test.runner.AndroidJUnitRunner'

    def phase(name, selector, count):
        if not disconnected():
            raise ValueError('Connectivity is available; no offline verdict')
        print(json.dumps({'offlinePhase': name, 'status': 'running'}), flush=True)
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', *selector, runner, timeout=600).decode(errors='replace')
        trace = ROOT / f'artifacts/fire-offline-{name}.txt'
        trace.write_text(result.replace('\r\r\n', '\n').replace('\r\n', '\n').rstrip() + '\n', encoding='utf-8')
        okay = passed_trace(result, count) and disconnected()
        record['phases'][name] = dict(passed=okay, tests=count, disconnectedBeforeAndAfter=True,
                                     trace=trace.relative_to(ROOT).as_posix(), traceSha256=digest(trace.read_bytes()))
        if not okay:
            raise ValueError('Offline instrumentation failed; inspect the sanitized trace')
        print(json.dumps({'offlinePhase': name, 'passed': True, 'tests': count}), flush=True)

    def saved_state():
        # The app is stopped before copying: logical rows survive WAL checkpoints.
        files = adb('exec-out', 'run-as', QA, 'find', 'files', '-type', 'f').decode().splitlines()
        preferences = {p: digest(adb('exec-out', 'run-as', QA, 'cat', p)) for p in files if '/datastore/' in p}
        names = adb('exec-out', 'run-as', QA, 'ls', '-1', 'databases').decode().splitlines()
        with tempfile.TemporaryDirectory(prefix='dishnest-offline-') as directory:
            for name in ('dishnest.db', 'dishnest.db-wal'):
                if name in names:
                    (Path(directory) / name).write_bytes(adb('exec-out', 'run-as', QA, 'cat', 'databases/' + name))
            db = sqlite3.connect(str(Path(directory) / 'dishnest.db'))
            try:
                if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                    raise ValueError('Offline QA database integrity failed')
                rows = list(db.iterdump())
                active = db.execute('SELECT name FROM plans WHERE active=1').fetchall()
            finally:
                db.close()
        return digest('\n'.join(rows).encode()), preferences, active

    try:
        adb('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', '3')
        radio('1', '0')
        time.sleep(2)
        phase('suite', ('notClass', 'com.dishnest.app.FireBackgroundAlarmTest'), 15)
        phase('selected-plan', ('class', 'com.dishnest.app.KitchenUiTest#namedPlanSelectionSurvivesActivityRecreation'), 1)
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'am', 'kill', QA)
        record['processAbsentBeforeRelaunch'] = not adb('shell', 'pidof', QA, check=False).strip()
        if not record['processAbsentBeforeRelaunch']:
            raise ValueError('QA process did not stop')
        saved = saved_state()
        record['namedPlanSavedBeforeRelaunch'] = saved[2] == [('Fire family plan',)]
        adb('shell', 'am', 'start', '-W', '-n', QA + '/com.dishnest.app.MainActivity')
        time.sleep(4)
        record['processPresentAfterOfflineRelaunch'] = bool(adb('shell', 'pidof', QA, check=False).strip())
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'am', 'kill', QA)
        recovered = saved_state()
        record['savedKitchenAndPreferencesUnchangedAfterOfflineProcessRestart'] = saved == recovered
        record['offlineAfterProcessRestart'] = disconnected()
    finally:
        # Attempt every cleanup even if a previous cleanup fails.
        errors = []
        for cleanup in (lambda: radio(prior['airplane_mode_on'], prior['wifi_on']),
                        lambda: adb('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', prior['stay_on_while_plugged_in'])):
            try:
                cleanup()
            except Exception:
                errors.append('Tablet setting restoration failed; reconnect and rerun cleanup')
        record['settingsRestored'] = all(setting(k) == v for k, v in prior.items())
        if record['settingsRestored']:
            journal.write_text(json.dumps(dict(prior, restored=True), indent=2) + '\n', encoding='utf-8')
        record['ordinaryApkAndPersistentFilesUnchanged'] = ordinary() == before
        record['ordinaryPersistentFileCount'] = len(before[0])
        record['cleanupErrors'] = errors
        record['passed'] = (len(record['phases']) == 2 and all(p['passed'] for p in record['phases'].values()) and
                            all(record.get(k) is True for k in ('processAbsentBeforeRelaunch', 'namedPlanSavedBeforeRelaunch',
                                'processPresentAfterOfflineRelaunch', 'savedKitchenAndPreferencesUnchangedAfterOfflineProcessRestart',
                                'offlineAfterProcessRestart', 'settingsRestored', 'ordinaryApkAndPersistentFilesUnchanged')) and not errors)
        record['notCovered'] = ['Full manual offline cooking/shopping flow', 'Document-provider UI',
                                'Live website import', 'Voice providers', 'Billing or Amazon LAT', 'Other devices']
        OUT.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(record), flush=True)
    if not record['passed']:
        raise ValueError('Offline checks incomplete; no passing claim')


if __name__ == '__main__':
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateMutexW.restype = ctypes.c_void_p
    kernel.CreateMutexW.argtypes = [ctypes.c_void_p, ctypes.c_bool, ctypes.c_wchar_p]
    kernel.WaitForSingleObject.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
    kernel.ReleaseMutex.argtypes = [ctypes.c_void_p]
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    mutex = kernel.CreateMutexW(None, False, 'Local\\DishNestFireQa')
    if not mutex or kernel.WaitForSingleObject(mutex, 0) not in (0, 0x80):
        if mutex:
            kernel.CloseHandle(mutex)
        raise SystemExit('Another Fire QA controller is active')
    try:
        probe()
    finally:
        kernel.ReleaseMutex(mutex)
        kernel.CloseHandle(mutex)
