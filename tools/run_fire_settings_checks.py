"""Observe language selection and consent deletion in the disposable QA app only."""
import base64
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
import uuid
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from build_evidence import verify as build_current
from run_fire_offline_checks import ROOT, ADB, QA


def digest(data):
    return hashlib.sha256(data).hexdigest()


def probe():
    if not build_current():
        raise ValueError('Current full build evidence required')
    devices = [line.split()[0] for line in subprocess.check_output([str(ADB), 'devices']).decode().splitlines()[1:]
               if re.fullmatch(r'\S+\s+device\s*', line)]
    if len(devices) != 1:
        raise ValueError('Exactly one authorized Fire required')

    def adb(*args, data=None, timeout=40, check=True):
        try:
            result = subprocess.run([str(ADB), '-s', devices[0], *args], input=data,
                                    capture_output=True, timeout=timeout)
        except (subprocess.TimeoutExpired, OSError):
            raise ValueError('ADB failed; private details withheld') from None
        if check and result.returncode:
            raise ValueError('ADB failed; private details withheld')
        return result.stdout

    def private(package, name):
        if adb('shell', 'run-as', package, 'test', '-f', name, check=False):
            raise ValueError('Unexpected private-file test output')
        # Missing preferences are a valid default, not an ADB success assumption.
        listing = adb('exec-out', 'run-as', package, 'find', './shared_prefs', '-type', 'f', check=False).decode()
        if './' + name not in listing.splitlines():
            return None
        return adb('exec-out', 'run-as', package, 'cat', name)

    def preference(name, key, default):
        raw = private(QA, 'shared_prefs/' + name + '.xml')
        if raw is None:
            return default
        node = next((n for n in ET.fromstring(raw) if n.get('name') == key), None)
        if node is None:
            return default
        return node.get('value') == 'true' if node.tag == 'boolean' else (node.text or '')

    def ordinary():
        package = 'com.dishnest.app'
        path = adb('shell', 'pm', 'path', package).decode().strip()
        if not path.startswith('package:') or '\n' in path:
            raise ValueError('Verified ordinary installation required')
        names = adb('exec-out', 'run-as', package, 'find', '.', '-type', 'f').decode().splitlines()
        return digest(adb('exec-out', 'cat', path[8:], timeout=90)), {
            n: digest(adb('exec-out', 'run-as', package, 'cat', n)) for n in names
            if n.startswith(('./databases/', './files/', './shared_prefs/', './no_backup/'))}

    def kitchen():
        def snapshot():
            names = adb('exec-out', 'run-as', QA, 'ls', '-1', 'databases').decode().splitlines()
            return {n: adb('exec-out', 'run-as', QA, 'cat', 'databases/' + n)
                    for n in ('dishnest.db', 'dishnest.db-wal') if n in names}
        for _ in range(12):
            saved = snapshot()
            if 'dishnest.db' in saved and saved == snapshot():
                with tempfile.TemporaryDirectory(prefix='dishnest-settings-qa-') as directory:
                    for name, raw in saved.items():
                        (Path(directory) / name).write_bytes(raw)
                    db = sqlite3.connect(str(Path(directory) / 'dishnest.db'))
                    try:
                        if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                            raise ValueError('Disposable QA database integrity failed')
                        return digest('\n'.join(db.iterdump()).encode()), db.execute('SELECT COUNT(*) FROM recipes').fetchone()[0]
                    finally:
                        db.close()
            time.sleep(0.2)
        raise ValueError('No stable disposable QA database snapshot')

    def text(locale, key):
        folder = 'values' if not locale else 'values-' + locale
        root = ET.parse(ROOT / f'app/src/main/res/{folder}/strings.xml').getroot()
        value = next(n.text or '' for n in root if n.get('name') == key)
        return ' '.join(value.replace("\\'", "'").replace('\\n', '\n').split())

    def tree():
        path = '/data/local/tmp/dishnest-settings-' + uuid.uuid4().hex + '.xml'
        try:
            for _ in range(24):
                lock = adb('shell', 'dumpsys', 'window', 'policy').decode()
                if any(s in lock for s in ('mIsShowing=true', 'isStatusBarKeyguard=true')):
                    raise ValueError('Unlock Fire; secure authentication is never bypassed')
                adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
                adb('shell', 'uiautomator', 'dump', path)
                try:
                    root = ET.fromstring(adb('exec-out', 'cat', path))
                    if not any(n.get('package') == QA for n in root.iter('node')):
                        raise ValueError('Disposable QA app is not foreground; no input sent')
                    return root
                except ET.ParseError:
                    time.sleep(0.3)
            raise ValueError('No fresh UI hierarchy')
        finally:
            adb('shell', 'rm', '-f', path)

    def bounds(node):
        values = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
        if len(values) != 4 or values[2] <= values[0] or values[3] <= values[1]:
            raise ValueError('QA control has no visible bounds')
        return values

    def matches(root, label):
        return [n for n in root.iter('node') if n.get('package') == QA and
                ' '.join(n.get('text', '').split()) == label]

    def tap(node):
        if node.get('package') != QA or node.get('enabled') != 'true':
            raise ValueError('Refusing an unexpected UI input target')
        x1, y1, x2, y2 = bounds(node)
        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

    def find(label, scroll=False):
        for _ in range(16 if scroll else 5):
            root = tree()
            found = matches(root, label)
            if len(found) == 1:
                return found[0]
            if len(found) > 1:
                raise ValueError('Ambiguous QA control: ' + label)
            if scroll:
                lists = [n for n in root.iter('node') if n.get('package') == QA and n.get('scrollable') == 'true']
                if not lists:
                    raise ValueError('No QA scroll area for expected control')
                x1, y1, x2, y2 = bounds(lists[-1])
                adb('shell', 'input', 'swipe', str((x1+3*x2)//4), str((y1+4*y2)//5),
                    str((x1+3*x2)//4), str((2*y1+y2)//3), '350')
            else:
                time.sleep(0.3)
        raise ValueError('Expected QA control not found: ' + label)

    def click(label, scroll=False):
        tap(find(label, scroll))

    def launch(locale):
        adb('shell', 'am', 'force-stop', QA)
        adb('shell', 'am', 'start', '-W', '-n', QA + '/com.dishnest.app.MainActivity')
        find(text(locale, 'nav_recipes'))

    def usage_switch(locale=''):
        label = text(locale, 'ui_keep_aggregate_usage_counts_on_t_a9093e')
        find(label, scroll=True)
        root = tree()
        rows = matches(root, label)
        if len(rows) != 1:
            raise ValueError('Usage label is ambiguous')
        b = bounds(rows[0]); middle = (b[1]+b[3])//2
        switches = [n for n in root.iter('node') if n.get('package') == QA and
                    n.get('checkable') == 'true' and abs((bounds(n)[1]+bounds(n)[3])//2-middle) < 70]
        if len(switches) != 1:
            raise ValueError('Usage switch is ambiguous or offscreen')
        if ' '.join(switches[0].get('content-desc', '').split()) != label:
            raise ValueError('Usage switch has no localized accessible label')
        language = locale or 'en-GB'
        if language not in record['accessibleLabelLocales']:
            record['accessibleLabelLocales'].append(language)
        return switches[0]

    def capture(name):
        tree()  # Refuse a capture after another app or a secure lock takes over.
        path = ROOT / 'artifacts/screens' / name
        path.write_bytes(adb('exec-out', 'screencap', '-p'))
        record['screenshotsSha256'][path.relative_to(ROOT).as_posix()] = digest(path.read_bytes())

    model = adb('shell', 'getprop', 'ro.product.model').decode().strip()
    if not model.startswith('KF') or adb('shell', 'am', 'get-current-user').strip() != b'0':
        raise ValueError('One owner-profile Fire required')
    smoke = json.loads((ROOT/'artifacts/fire-device-qa.json').read_text(encoding='utf-8-sig'))
    apk = ROOT/'app/build/outputs/apk/qa/app-qa.apk'
    installed = adb('shell', 'pm', 'path', QA).decode().strip()
    if (not installed.startswith('package:') or '\n' in installed or smoke.get('passed') is not True or
            digest(apk.read_bytes()) != smoke.get('apkSha256') or
            digest(adb('exec-out', 'cat', installed[8:], timeout=90)) != smoke['apkSha256']):
        raise ValueError('Exact verified disposable QA APK required')
    if preference('app_language', 'tag', '') != '' or preference('local_usage', 'enabled', False):
        raise ValueError('QA must start with device language and usage counts off')
    original = ordinary()
    prior_xml = private(QA, 'shared_prefs/app_language.xml')
    awake = adb('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in').decode().strip()
    record = dict(testedAt=datetime.now(timezone.utc).isoformat(), model=model, packageId=QA,
                  apkSha256=smoke['apkSha256'], controllerSha256=digest(Path(__file__).read_bytes()),
                  sourceHelperSha256=digest((ROOT/'tools/run_fire_offline_checks.py').read_bytes()),
                  installedApkVerified=True, passed=False, checks={}, screenshotsSha256={}, accessibleLabelLocales=[],
                  ordinaryAppModified=False, otherProfilesModified=False, paidServicesEnabled=False,
                  privateIdentifiersStored=False, publicSubmissionPerformed=False, nativeLanguageApproved=False)
    output = ROOT/'artifacts/fire-settings-qa.json'
    output.write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    try:
        adb('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', '3')
        launch('')
        baseline = kitchen()
        click(text('', 'nav_settings'))
        click(text('', 'language_system'), scroll=True)
        find(text('', 'language_title'))
        click('Français')
        find(text('fr', 'nav_recipes'))
        assert preference('app_language', 'tag', '') == 'fr'
        launch('fr')
        record['checks']['frenchSelectionPersistsAcrossProcessRestart'] = kitchen() == baseline
        click(text('fr', 'nav_settings'))
        usage_switch('fr')
        click('Français', scroll=True)
        find(text('fr', 'language_title'))
        capture('fire-language-picker-fr.png')
        click(text('fr', 'cancel'))
        record['checks']['localizedFrenchDialogCancelPreservesSelection'] = preference('app_language', 'tag', '') == 'fr'
        click('Français', scroll=True)
        click('العربية', scroll=True)
        find(text('ar', 'nav_recipes'))
        assert preference('app_language', 'tag', '') == 'ar'
        launch('ar')
        record['checks']['arabicSelectionPersistsAcrossProcessRestart'] = kitchen() == baseline
        click(text('ar', 'nav_settings'))
        usage_switch('ar')
        click('العربية', scroll=True)
        find(text('ar', 'language_title'))
        capture('fire-language-picker-ar.png')
        click(text('ar', 'language_system'))
        find(text('', 'nav_recipes'))
        record['checks']['deviceLanguageFallbackPreservesKitchen'] = preference('app_language', 'tag', '') == '' and kitchen() == baseline
        click(text('', 'nav_settings'))
        switch = usage_switch()
        assert switch.get('checked') == 'false'
        tap(switch)
        assert preference('local_usage', 'enabled', False) is True
        launch('')
        click(text('', 'nav_settings'))
        record['checks']['usageOptInPersistsAndSwitchReflectsIt'] = usage_switch().get('checked') == 'true'
        click(text('', 'ui_delete_all_kitchen_data_013193'), scroll=True)
        find(text('', 'ui_delete_all_kitchen_data_3aa3db'))
        click(text('', 'ui_permanently_delete_9003e1'))
        for _ in range(20):
            if not preference('local_usage', 'enabled', False) and kitchen()[1] == 0:
                break
            time.sleep(0.3)
        click(text('', 'nav_home'))
        click(text('', 'nav_settings'))
        record['checks']['qaKitchenDeletionOptsOutAndUpdatesSwitch'] = (
            not preference('local_usage', 'enabled', False) and kitchen()[1] == 0 and
            usage_switch().get('checked') == 'false')
        capture('fire-settings-usage-after-delete.png')
        if not all(record['checks'].values()):
            raise ValueError('A targeted settings check failed')
    finally:
        cleanup = []
        try:
            adb('shell', 'am', 'force-stop', QA)
            if prior_xml is None:
                adb('shell', 'run-as', QA, 'rm', '-f', 'shared_prefs/app_language.xml')
            else:
                adb('shell', '-T', 'run-as', QA, 'sh', '-c', "'base64 -d > shared_prefs/app_language.xml'",
                    data=base64.encodebytes(prior_xml))
            record['qaLanguagePreferenceRestored'] = private(QA, 'shared_prefs/app_language.xml') == prior_xml
        except Exception:
            cleanup.append('QA language restoration failed')
        try:
            if awake == 'null':
                adb('shell', 'settings', 'delete', 'global', 'stay_on_while_plugged_in')
            else:
                adb('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', awake)
            record['deviceSettingRestored'] = adb('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in').decode().strip() == awake
        except Exception:
            cleanup.append('Keep-awake restoration failed')
        record['ordinaryApkAndPersistentFilesUnchanged'] = ordinary() == original
        record['cleanupErrors'] = cleanup
        record['passed'] = (len(record['checks']) == 6 and all(record['checks'].values()) and
            record.get('qaLanguagePreferenceRestored') is True and record.get('deviceSettingRestored') is True and
            record['ordinaryApkAndPersistentFilesUnchanged'] and not cleanup)
        record['notCovered'] = ['Other app locales', 'Android 13 locale settings on hardware', 'Native language review',
            'TalkBack', 'Every keyboard/font configuration', 'Production purchase analytics']
        output.write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
        print(json.dumps(record), flush=True)
    if not record['passed']:
        raise ValueError('Physical settings checks incomplete; no approval claimed')


if __name__ == '__main__':
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateMutexW.argtypes = [ctypes.c_void_p, ctypes.c_bool, ctypes.c_wchar_p]
    kernel.CreateMutexW.restype = ctypes.c_void_p
    kernel.WaitForSingleObject.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
    kernel.ReleaseMutex.argtypes = [ctypes.c_void_p]
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    handle = kernel.CreateMutexW(None, False, 'Local\\DishNestFireQa')
    if not handle or kernel.WaitForSingleObject(handle, 0) not in (0, 0x80):
        if handle:
            kernel.CloseHandle(handle)
        raise SystemExit('Another Fire QA controller is active')
    try:
        probe()
    finally:
        kernel.ReleaseMutex(handle)
        kernel.CloseHandle(handle)
