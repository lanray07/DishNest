"""Run production-activity QA flows under real system rotation/font changes."""
import ctypes
import json
import os
from pathlib import Path
import re
import subprocess
import time
from datetime import datetime, timezone
from run_fire_offline_checks import ROOT, ADB, QA, digest, passed_trace


def probe():
    devices = [x.split()[0] for x in subprocess.check_output([str(ADB), 'devices']).decode().splitlines()[1:]
               if x.endswith('\tdevice')]
    if len(devices) != 1:
        raise ValueError('Exactly one authorized Fire tablet required')

    def adb(*args, timeout=30, check=True):
        try:
            result = subprocess.run([str(ADB), '-s', devices[0], *args], capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise ValueError('ADB timeout; private command details omitted') from None
        if check and result.returncode:
            raise ValueError('ADB operation failed; private output omitted')
        return result.stdout

    def get(namespace, name):
        return adb('shell', 'settings', 'get', namespace, name).decode().strip()

    def put(namespace, name, value):
        adb('shell', 'settings', 'delete' if value == 'null' else 'put', namespace, name,
            *([] if value == 'null' else [value]))

    def ordinary():
        names = adb('exec-out', 'run-as', 'com.dishnest.app', 'find', '.', '-type', 'f').decode().splitlines()
        hashes = {n: digest(adb('exec-out', 'run-as', 'com.dishnest.app', 'cat', n))
                  for n in names if n.startswith(('./databases/', './shared_prefs/', './files/'))}
        installed = adb('shell', 'pm', 'path', 'com.dishnest.app').decode().strip()
        if not installed.startswith('package:') or '\n' in installed:
            raise ValueError('Single ordinary APK required')
        return hashes, digest(adb('exec-out', 'cat', installed[8:], timeout=90))

    model = adb('shell', 'getprop', 'ro.product.model').decode().strip()
    if not model.startswith('KF'):
        raise ValueError('Fire required')
    if any(x in adb('shell', 'dumpsys', 'window', 'policy').decode() for x in ('mIsShowing=true', 'isStatusBarKeyguard=true')):
        raise ValueError('Unlock the Fire for layout tests')
    smoke = json.loads((ROOT / 'artifacts/fire-device-qa.json').read_text(encoding='utf-8-sig'))
    if not smoke.get('passed'):
        raise ValueError('Passing current smoke evidence required')
    for package, path, expected in (
        (QA, 'app/build/outputs/apk/qa/app-qa.apk', smoke['apkSha256']),
        (QA + '.test', 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk', smoke['instrumentationApkSha256'])):
        installed = adb('shell', 'pm', 'path', package).decode().strip()
        if not installed.startswith('package:') or '\n' in installed:
            raise ValueError('Single installed QA APK required')
        if digest((ROOT / path).read_bytes()) != expected or digest(adb('exec-out', 'cat', installed[8:], timeout=90)) != expected:
            raise ValueError('QA binaries differ from smoke evidence')
    settings = [('system', 'font_scale'), ('system', 'accelerometer_rotation'), ('system', 'user_rotation'),
                ('global', 'stay_on_while_plugged_in')]
    prior = {(ns, n): get(ns, n) for ns, n in settings}
    journal = ROOT / 'build/fire-layout-settings.json'
    journal.write_text(json.dumps({f'{ns}/{n}': v for (ns, n), v in prior.items()}, indent=2) + '\n', encoding='utf-8')
    before = ordinary()
    record = dict(testedAt=datetime.now(timezone.utc).isoformat(), model=model, packageId=QA,
                  controllerSha256=digest(Path(__file__).read_bytes()),
                  helperSha256=digest((ROOT / 'tools/run_fire_offline_checks.py').read_bytes()),
                  apkSha256=smoke['apkSha256'], instrumentationApkSha256=smoke['instrumentationApkSha256'],
                  installedApksVerified=True, passed=False, phases={}, privateIdentifiersStored=False,
                  ordinaryAppModified=False, paidServicesEnabled=False)
    output = ROOT / 'artifacts/fire-layout-qa.json'
    output.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    try:
        put('global', 'stay_on_while_plugged_in', '3')
        put('system', 'accelerometer_rotation', '0')
        for name, rotation, font in (('portrait-large-text', '0', '2.0'), ('landscape-large-text', '1', '2.0')):
            put('system', 'user_rotation', rotation)
            put('system', 'font_scale', font)
            time.sleep(3)
            orientation = adb('shell', 'dumpsys', 'display').decode()
            if not re.search(r'mCurrentOrientation=' + rotation + r'\b', orientation):
                raise ValueError('Requested physical display rotation not observed')
            if get('system', 'font_scale') != font:
                raise ValueError('System font scale not applied')
            print(json.dumps({'layoutPhase': name, 'status': 'running'}), flush=True)
            trace = ROOT / f'artifacts/fire-layout-{name}.txt'
            raw = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'com.dishnest.app.KitchenUiTest',
                      QA + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=600).decode(errors='replace')
            trace.write_text(raw.replace('\r\r\n', '\n').replace('\r\n', '\n').rstrip() + '\n', encoding='utf-8')
            observed = re.search(r'mCurrentOrientation=' + rotation + r'\b', adb('shell', 'dumpsys', 'display').decode())
            okay = passed_trace(raw, 3) and bool(observed) and get('system', 'font_scale') == font
            record['phases'][name] = dict(passed=okay, tests=3, rotation=int(rotation), systemFontScale=2.0,
                                         settingsObservedBeforeAndAfter=True,
                                         trace=trace.relative_to(ROOT).as_posix(), traceSha256=digest(trace.read_bytes()))
            print(json.dumps({'layoutPhase': name, 'passed': okay}), flush=True)
            if not okay:
                raise ValueError('Physical layout check failed; inspect QA trace')
    finally:
        errors = []
        # Restore user rotation before returning control to the accelerometer.
        for ns, name in [('system', 'user_rotation'), ('system', 'font_scale'),
                         ('system', 'accelerometer_rotation'), ('global', 'stay_on_while_plugged_in')]:
            try:
                put(ns, name, prior[(ns, name)])
            except Exception:
                errors.append('Layout setting restoration failed')
        record['settingsRestored'] = all(get(ns, name) == v for (ns, name), v in prior.items())
        record['ordinaryApkAndPersistentFilesUnchanged'] = ordinary() == before
        record['cleanupErrors'] = errors
        record['passed'] = (len(record['phases']) == 2 and all(p['passed'] for p in record['phases'].values()) and
                            record['settingsRestored'] and record['ordinaryApkAndPersistentFilesUnchanged'] and not errors)
        record['notCovered'] = ['TalkBack', 'Display density scaling', 'Every dialog and cooking screen',
                                'Rotation during an open editor', 'Other devices', 'Publisher-signed release']
        output.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
        if record['settingsRestored']:
            journal.write_text(json.dumps({'restored': True}, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(record), flush=True)
    if not record['passed']:
        raise ValueError('Layout check incomplete; no passing claim')


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
