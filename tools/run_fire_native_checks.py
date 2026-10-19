"""Run only non-UI native fixtures in the disposable QA package, even while locked.

Never dismisses the lock, changes device settings or modifies the ordinary app.
Serials and individual ordinary file hashes remain in memory and are not reported.
"""
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from datetime import datetime, timezone
from build_evidence import verify, digest

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'
ADB = SDK / 'platform-tools/adb.exe'
PACKAGE = 'com.dishnest.app.qa'


def main():
    if not verify():
        raise ValueError('Current full build verification is required')
    devices = [line.split()[0] for line in subprocess.check_output([str(ADB), 'devices'], text=True).splitlines()
               if re.match(r'^\S+\s+device\s*$', line)]
    if len(devices) != 1:
        raise ValueError('Exactly one authorized Fire tablet is required')
    def adb(*parts, timeout=40):
        try:
            return subprocess.run([str(ADB), '-s', devices[0], *parts], capture_output=True, check=True, timeout=timeout).stdout
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            raise ValueError('ADB operation failed; no device identifiers or private output recorded') from None
    model = adb('shell', 'getprop', 'ro.product.model').decode().strip()
    if not model.startswith('KF'):
        raise ValueError('Selected device is not a Fire tablet')
    def ordinary_hashes():
        paths = adb('exec-out', 'run-as', 'com.dishnest.app', 'find', '.', '-type', 'f').decode().splitlines()
        return {path: hashlib.sha256(adb('exec-out', 'run-as', 'com.dishnest.app', 'cat', path)).hexdigest()
                for path in paths if path.startswith(('./databases/', './shared_prefs/', './files/'))}
    before = ordinary_hashes()
    apks = [(ROOT / 'app/build/outputs/apk/qa/app-qa.apk', PACKAGE),
            (ROOT / 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk', PACKAGE + '.test')]
    aapt = SDK / 'build-tools/35.0.0/aapt.exe'
    hashes = []
    for path, package in apks:
        metadata = subprocess.check_output([str(aapt), 'dump', 'badging', str(path)], text=True)
        if f"name='{package}'" not in metadata.splitlines()[0]:
            raise ValueError('Refusing a non-QA APK')
        hashes.append(digest(path))
    report = dict(testedAt=datetime.now(timezone.utc).isoformat(), model=model,
                  api=adb('shell', 'getprop', 'ro.build.version.sdk').decode().strip(),
                  packageId=PACKAGE, apkSha256=hashes[0], instrumentationApkSha256=hashes[1],
                  controllerSha256=digest(Path(__file__)), passed=False,
                  uiTested=False, lockBypassed=False, deviceSettingsChanged=False,
                  serialIdentifierStored=False, individualOrdinaryDataHashesStored=False,
                  ordinaryAppModified=False, storeSubmission=False)
    # Invalidate a previous pass before the first mutation or test attempt.
    output = ROOT / 'artifacts/fire-native-qa.json'
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    try:
        for (path, package), expected in zip(apks, hashes):
            adb('install', '-r', str(path), timeout=120)
            installed = adb('shell', 'pm', 'path', package).decode().strip()
            if not installed.startswith('package:') or '\n' in installed:
                raise ValueError('Unexpected installed QA APK path')
            actual = hashlib.sha256(adb('exec-out', 'cat', installed.removeprefix('package:'), timeout=120)).hexdigest()
            if actual != expected:
                raise ValueError('Installed QA APK does not match local input')
        report['installedApksVerified'] = True
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                     'com.dishnest.app.NativeReadinessTest',
                     PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=240).decode(errors='replace')
        (ROOT / 'artifacts/fire-native-instrumentation.txt').write_text(result, encoding='utf-8')
        match = re.search(r'OK \((\d+) tests?\)', result)
        report['passedTests'] = len(re.findall(r'(?m)^INSTRUMENTATION_STATUS_CODE: 0\r?$', result))
        report['passed'] = bool(match and int(match[1]) >= 6 and report['passedTests'] >= 6
                                and not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed', result))
    finally:
        report['ordinaryDataHashesUnchanged'] = ordinary_hashes() == before
        report['passed'] = report['passed'] and report['ordinaryDataHashesUnchanged']
        output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))
    if not report['passed']:
        raise ValueError('Native device checks did not all pass')


if __name__ == '__main__':
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateMutexW.argtypes = [ctypes.c_void_p, wintypes.BOOL, wintypes.LPCWSTR]
    kernel.CreateMutexW.restype = wintypes.HANDLE
    kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel.ReleaseMutex.argtypes = [wintypes.HANDLE]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.CreateMutexW(None, False, 'Local\\DishNestFireQa')
    if not handle:
        raise OSError('Cannot acquire Fire QA mutex')
    acquired = kernel.WaitForSingleObject(handle, 0) in (0, 128)
    try:
        if not acquired:
            raise RuntimeError('Another Fire controller is active')
        main()
    finally:
        if acquired:
            kernel.ReleaseMutex(handle)
        kernel.CloseHandle(handle)
