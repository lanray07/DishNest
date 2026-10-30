"""Exercise Fire's actual document picker using only disposable DishNest QA fixtures."""
import ctypes
import io
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
import zipfile
from datetime import datetime, timezone
from run_fire_offline_checks import ROOT, ADB, QA, digest, passed_trace

PICKER = 'com.android.documentsui'


class UiTransitionTimeout(ValueError):
    """Expected window/control did not appear; ADB failures stay distinct."""


def null_root_error(stdout, stderr):
    return b'null root node returned by uitestautomationbridge' in (stdout + stderr).lower()


REFUSAL = 'Import requires an empty kitchen. Export existing data before deleting it.'


def refusal_visible(text):
    normalize = lambda value: re.sub(r'[^a-z0-9]', '', value.casefold())
    return normalize(REFUSAL) in normalize(text)


def probe():
    devices = [x.split()[0] for x in subprocess.check_output([str(ADB), 'devices']).decode().splitlines()[1:]
               if x.endswith('\tdevice')]
    if len(devices) != 1:
        raise ValueError('Exactly one authorized Fire required')

    def adb(*args, timeout=30, allow_null_root=False):
        try:
            result = subprocess.run([str(ADB), '-s', devices[0], *args], capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise ValueError('ADB timeout; private details omitted') from None
        if allow_null_root and null_root_error(result.stdout, result.stderr):
            return None
        if result.returncode:
            raise ValueError('ADB operation failed; private details omitted')
        return result.stdout

    def ordinary():
        names = adb('exec-out', 'run-as', 'com.dishnest.app', 'find', '.', '-type', 'f').decode().splitlines()
        hashes = {p: digest(adb('exec-out', 'run-as', 'com.dishnest.app', 'cat', p))
                  for p in names if p.startswith(('./databases/', './shared_prefs/', './files/'))}
        apk = adb('shell', 'pm', 'path', 'com.dishnest.app').decode().strip()
        if not apk.startswith('package:') or '\n' in apk:
            raise ValueError('Single ordinary APK required')
        return hashes, digest(adb('exec-out', 'cat', apk[8:], timeout=90))

    def tree():
        path = '/data/local/tmp/dishnest-qa-document-' + uuid.uuid4().hex + '.xml'
        try:
            # Fire sometimes refuses an idle snapshot during a provider/activity
            # transition. Retry the dump itself; never read a previous window.
            adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
            for _ in range(24):
                # Wake an already unlocked display without dismissing keyguard.
                # Each CLI automation connection can transiently return a null
                # root on this Fire; a missing snapshot never counts as a pass.
                dump = adb('shell', 'uiautomator', 'dump', path, allow_null_root=True)
                if dump is None:
                    record['nullRootRetries'] += 1
                    lock = adb('shell', 'dumpsys', 'window', 'policy').decode()
                    if any(x in lock for x in ('mIsShowing=true', 'isStatusBarKeyguard=true')):
                        raise ValueError('Fire locked during UI QA; unlock required')
                    time.sleep(0.5)
                    continue
                try:
                    return ET.fromstring(adb('exec-out', 'cat', path))
                except ET.ParseError:
                    lock = adb('shell', 'dumpsys', 'window', 'policy').decode()
                    if any(x in lock for x in ('mIsShowing=true', 'isStatusBarKeyguard=true')):
                        raise ValueError('Fire locked during UI QA; unlock required')
                    time.sleep(0.5)
            raise ValueError('No fresh device UI hierarchy')
        finally:
            adb('shell', 'rm', '-f', path)

    def wait(predicate, message, seconds=45):
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            value = predicate()
            if value:
                return value
            time.sleep(0.5)
        raise UiTransitionTimeout(message)

    def nodes(predicate):
        return [n for n in tree().iter('node') if predicate(n)]

    def foreground(package):
        # Activity transitions can keep the old automation root briefly even
        # after the real window has changed. Use window-manager focus to wait;
        # actionable controls still require a fresh matching-package hierarchy.
        window = adb('shell', 'dumpsys', 'window', 'windows').decode(errors='replace')
        focused = next((line for line in window.splitlines() if 'mCurrentFocus=' in line), '')
        return package + '/' in focused

    def click_node(node):
        if node.get('package') not in (QA, PICKER) or node.get('enabled') != 'true':
            raise ValueError('Refusing an unexpected or disabled UI target')
        b = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
        if len(b) != 4 or b[2] <= b[0] or b[3] <= b[1]:
            raise ValueError('Target has no visible bounds')
        adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))

    def click(text, package=QA):
        matches = wait(lambda: nodes(lambda n: n.get('package') == package and n.get('text') == text),
                       'Expected visible QA/picker control missing: ' + text)
        if len(matches) != 1:
            raise ValueError('UI target is ambiguous: ' + text)
        click_node(matches[0])

    def scroll_to(text):
        for attempt in range(18):
            root = tree()
            found = [n for n in root.iter('node') if n.get('package') == QA and n.get('text') == text]
            viewport = list(map(int, re.findall(r'\d+', root.find('node').get('bounds'))))
            if len(found) == 1:
                target = list(map(int, re.findall(r'\d+', found[0].get('bounds', ''))))
                # Compose exposes partly clipped lazy-column text in its tree.
                # Scroll it clear of the navigation bar before tapping.
                if len(target) == 4 and target[1] > viewport[1] + 20 and target[3] < viewport[3] - 20:
                    return click_node(found[0])
            if not any(n.get('package') == QA for n in root.iter('node')):
                raise ValueError('Refusing to scroll outside QA')
            b = list(map(int, re.findall(r'\d+', root.find('node').get('bounds'))))
            x = (b[0] + 3 * b[2]) // 4
            # Searches may begin below an earlier control after a picker return.
            # Search toward the top first, then traverse the entire column down.
            start, end = b[3] // 3, b[3] * 4 // 5
            if attempt >= 6 or (len(found) == 1 and target[3] >= viewport[3] - 20):
                start, end = end, start
            adb('shell', 'input', 'swipe', str(x), str(start), str(x), str(end), '350')
            time.sleep(0.5)
        raise ValueError('QA control not reachable: ' + text)

    def kitchen():
        # Read matching, stable snapshots; never copy or display real user records.
        def snapshot():
            names = adb('exec-out', 'run-as', QA, 'ls', '-1', 'databases').decode().splitlines()
            return {n: adb('exec-out', 'run-as', QA, 'cat', 'databases/' + n)
                    for n in ('dishnest.db', 'dishnest.db-wal') if n in names}
        for _ in range(10):
            saved = snapshot()
            if 'dishnest.db' in saved and saved == snapshot():
                with tempfile.TemporaryDirectory(prefix='dishnest-document-qa-') as directory:
                    for n, raw in saved.items():
                        (Path(directory) / n).write_bytes(raw)
                    db = sqlite3.connect(str(Path(directory) / 'dishnest.db'))
                    try:
                        if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                            raise ValueError('QA database integrity failure')
                        return digest('\n'.join(db.iterdump()).encode()), db.execute('SELECT COUNT(*) FROM recipes').fetchone()[0]
                    finally:
                        db.close()
            time.sleep(0.3)
        raise ValueError('No stable QA database snapshot')

    def picker_open(label):
        for attempt in range(3):
            if not foreground(QA):
                raise ValueError('Refusing a picker action outside QA')
            scroll_to(label)
            try:
                return wait(lambda: foreground(PICKER), 'Document picker did not open', seconds=8)
            except UiTransitionTimeout:
                if attempt == 2 or not foreground(QA):
                    raise

    def downloads():
        roots = nodes(lambda n: n.get('package') == PICKER and n.get('content-desc') == 'Show roots')
        if len(roots) != 1:
            raise ValueError('Expected document root control missing')
        click_node(roots[0])
        entries = wait(lambda: nodes(lambda n: n.get('package') == PICKER and n.get('text') == 'Downloads'
                                     and n.get('resource-id') == 'android:id/title'), 'Downloads navigation entry missing')
        if len(entries) != 1:
            raise ValueError('Ambiguous Downloads navigation entry')
        click_node(entries[0])

    token = uuid.uuid4().hex
    created = []
    record = dict(testedAt=datetime.now(timezone.utc).isoformat(), packageId=QA, passed=False,
                  controllerSha256=digest(Path(__file__).read_bytes()),
                  helperSha256=digest((ROOT / 'tools/run_fire_offline_checks.py').read_bytes()),
                  ordinaryAppModified=False, paidServicesEnabled=False, privateIdentifiersStored=False,
                  checks={}, nullRootRetries=0, refusalFramesSha256={},
                  ocrHelperSha256=digest((ROOT/'tools/ocr_local_image.ps1').read_bytes()))
    output = ROOT / 'artifacts/fire-document-qa.json'

    def export(label, suffix):
        picker_open(label)
        downloads()
        fields = wait(lambda: nodes(lambda n: n.get('package') == PICKER and n.get('class') == 'android.widget.EditText'
                                    and n.get('resource-id') == 'android:id/title'), 'Export filename field missing')
        if len(fields) != 1:
            raise ValueError('Ambiguous export filename field')
        name = 'dishnest-qa-' + token + '-' + str(len(created)) + suffix
        path = '/sdcard/Download/' + name
        # Only unique test-owned names can be created or later removed.
        created.append(path)
        for attempt in range(3):
            fields = nodes(lambda n: n.get('package') == PICKER and n.get('class') == 'android.widget.EditText'
                           and n.get('resource-id') == 'android:id/title')
            if len(fields) != 1:
                raise ValueError('Export filename field became ambiguous')
            click_node(fields[0])
            # Do not infer successful editing from the focus flag. Allow
            # keyboard relayout, then use fresh text and require exact readback.
            time.sleep(0.5)
            fields = nodes(lambda n: n.get('package') == PICKER and n.get('class') == 'android.widget.EditText'
                           and n.get('resource-id') == 'android:id/title')
            if len(fields) != 1:
                raise ValueError('Export filename editor disappeared after keyboard relayout')
            field = fields[0]
            adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
            adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL'] * (len(field.get('text', '')) + 1)))
            adb('shell', 'input', 'text', name)
            if nodes(lambda n: n.get('package') == PICKER and n.get('class') == 'android.widget.EditText'
                     and n.get('text') == name):
                break
        else:
            raise ValueError('Unique QA export filename not applied')
        click('SAVE', PICKER)
        wait(lambda: foreground(QA), 'QA did not resume after export')

        def read_valid():
            raw = adb('exec-out', 'cat', path)
            try:
                if suffix == '.json':
                    json.loads(raw)
                else:
                    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                        if archive.testzip() is not None:
                            return False
                        json.loads(archive.read('kitchen.json'))
                return raw
            except (ValueError, KeyError, zipfile.BadZipFile):
                return False
        return name, wait(read_valid, 'No valid completed QA export', seconds=60)

    def import_file(label, name, json_confirm=False):
        picker_open(label)
        downloads()
        for attempt in range(3):
            click(name, PICKER)
            try:
                wait(lambda: foreground(QA), 'QA did not resume after import selection', seconds=8)
                break
            except UiTransitionTimeout:
                if attempt == 2 or not foreground(PICKER):
                    raise
        if json_confirm:
            click('Validate and import')

    def observe_refusal(kind):
        # A new CLI hierarchy connection can miss a four-second snackbar. Capture
        # the actual app pixels first; OCR only those local test-owned frames.
        frames = []
        for index in range(3):
            end = time.monotonic() + 15
            while True:
                window = adb('shell', 'dumpsys', 'window', 'windows').decode(errors='replace')
                focused = next((line for line in window.splitlines() if 'mCurrentFocus=' in line), '')
                if QA + '/' in focused:
                    break
                if PICKER + '/' not in focused and 'mCurrentFocus=null' not in focused:
                    raise ValueError('Refusing a screenshot outside the QA app/provider transition')
                if time.monotonic() >= end:
                    raise ValueError('QA did not resume for refusal capture')
                time.sleep(0.1)
            frame = ROOT/'build'/f'document-refusal-{token}-{kind}-{index}.png'
            frame.write_bytes(adb('exec-out', 'screencap', '-p'))
            frames.append(frame)
            time.sleep(0.25)
        env = {key: value for key, value in os.environ.items() if key.upper() != 'PSMODULEPATH'}
        for frame in frames:
            result = subprocess.run(['powershell.exe', '-NoProfile', '-File',
                str(ROOT/'tools/ocr_local_image.ps1'), '-ImagePath', str(frame)],
                capture_output=True, timeout=60, env=env)
            if result.returncode:
                raise ValueError('Local Windows image OCR failed; private output omitted')
            observed = json.loads(result.stdout.decode('utf-8-sig'))
            if observed.get('localOnly') is not True:
                raise ValueError('Only local screenshot OCR is permitted')
            if refusal_visible(observed.get('text', '')):
                destination = ROOT/'artifacts/screens'/f'fire-document-nonempty-{kind}-refusal.png'
                destination.write_bytes(frame.read_bytes())
                record['refusalFramesSha256'][destination.relative_to(ROOT).as_posix()] = digest(destination.read_bytes())
                return True
        raise ValueError('Expected populated-kitchen refusal was not visible in local captured frames')

    def clear_qa():
        # The destructive controls are matched only in the QA package hierarchy.
        scroll_to('Delete all kitchen data')
        click('Permanently delete')
        wait(lambda: kitchen()[1] == 0, 'Disposable QA kitchen did not clear')
        # Return to the top of Settings after its lazy content changed.
        click('My kitchen')
        click('Settings')

    model = adb('shell', 'getprop', 'ro.product.model').decode().strip()
    if not model.startswith('KF'):
        raise ValueError('Fire tablet required')
    record['model'] = model
    if any(x in adb('shell', 'dumpsys', 'window', 'policy').decode() for x in ('mIsShowing=true', 'isStatusBarKeyguard=true')):
        raise ValueError('Unlock Fire for document-provider QA')
    smoke = json.loads((ROOT / 'artifacts/fire-device-qa.json').read_text(encoding='utf-8-sig'))
    if smoke.get('passed') is not True:
        raise ValueError('Current passing QA smoke required')
    for package, path, key in ((QA, 'app/build/outputs/apk/qa/app-qa.apk', 'apkSha256'),
                               (QA + '.test', 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk', 'instrumentationApkSha256')):
        installed = adb('shell', 'pm', 'path', package).decode().strip()
        if not installed.startswith('package:') or '\n' in installed or digest((ROOT / path).read_bytes()) != smoke[key]:
            raise ValueError('Local QA identity mismatch')
        if digest(adb('exec-out', 'cat', installed[8:], timeout=90)) != smoke[key]:
            raise ValueError('Installed QA APK mismatch')
        record[key] = smoke[key]
    record['installedApksVerified'] = True
    before = ordinary()
    previous = adb('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in').decode().strip()
    journal = ROOT / 'build/fire-document-settings.json'
    journal.write_text(json.dumps({'stay_on_while_plugged_in': previous}) + '\n', encoding='utf-8')
    output.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    try:
        adb('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', '3')
        trace = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                    'com.dishnest.app.KitchenUiTest#namedPlanSelectionSurvivesActivityRecreation',
                    QA + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=180).decode(errors='replace')
        trace_path = ROOT / 'artifacts/fire-document-seed.txt'
        trace_path.write_text(trace.replace('\r\r\n', '\n').replace('\r\n', '\n').rstrip() + '\n', encoding='utf-8')
        record['seedTraceSha256'] = digest(trace_path.read_bytes())
        if not passed_trace(trace, 1):
            raise ValueError('QA fixture seed failed')
        adb('shell', 'am', 'start', '-W', '-n', QA + '/com.dishnest.app.MainActivity')
        click('Settings')
        baseline = kitchen()
        if baseline[1] != 4:
            raise ValueError('Expected four disposable demo recipes')
        for label, key in (('Export JSON backup', 'cancelJsonExport'),
                           ('Export backup with photos (ZIP)', 'cancelZipExport'),
                           ('Import JSON backup', 'cancelJsonImport'),
                           ('Import backup with photos (ZIP)', 'cancelZipImport')):
            picker_open(label)
            adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
            wait(lambda: foreground(QA), 'QA did not resume after cancellation')
            record['checks'][key] = kitchen() == baseline
            if not record['checks'][key]:
                raise ValueError('Cancelled provider operation changed QA kitchen')
            print(json.dumps({'documentCheck': key, 'passed': True}), flush=True)
        print(json.dumps({'documentPhase': 'export-and-round-trip', 'status': 'running'}), flush=True)
        json_name, raw = export('Export JSON backup', '.json')
        backup = json.loads(raw)
        zip_name, zipped = export('Export backup with photos (ZIP)', '.zip')
        with zipfile.ZipFile(io.BytesIO(zipped)) as archive:
            record['checks']['zipCrcAndPayloadMatchJson'] = archive.testzip() is None and json.loads(archive.read('kitchen.json')) == backup
        record['checks']['exportsDoNotChangeKitchen'] = kitchen() == baseline
        record['checks']['jsonExportHasRecipesMealsAndSelectedPlan'] = (len(backup['recipes']) == 4 and len(backup['meals']) == 5 and
            any(p.get('active') is True and p.get('name') == 'Fire family plan' for p in backup['plans']))
        for label, name, confirm, key in (('Import JSON backup', json_name, True, 'jsonNonemptyKitchenRejected'),
                                         ('Import backup with photos (ZIP)', zip_name, False, 'zipNonemptyKitchenRejected')):
            import_file(label, name, confirm)
            record['checks'][key] = observe_refusal('json' if confirm else 'zip') and kitchen() == baseline
        for label, name, confirm, key in (('Import JSON backup', json_name, True, 'jsonProviderRoundTrip'),
                                         ('Import backup with photos (ZIP)', zip_name, False, 'zipProviderRoundTrip')):
            clear_qa()
            import_file(label, name, confirm)
            wait(lambda: kitchen()[1] == 4, 'QA restore did not recover recipes')
            _, restored = export('Export JSON backup', '.json')
            record['checks'][key] = json.loads(restored) == backup
            if not record['checks'][key]:
                raise ValueError('QA provider round trip changed backup records')
            print(json.dumps({'documentCheck': key, 'passed': True}), flush=True)
        record['backupCoverage'] = dict(recipes=len(backup['recipes']), meals=len(backup['meals']), plans=len(backup['plans']),
                                      bundledDemoPhotosOnly=True, timersAndPreferencesExcluded=True)
    finally:
        errors = []
        try:
            for _ in range(2):
                if foreground(PICKER):
                    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
                else:
                    break
        except Exception:
            errors.append('Document picker cleanup failed')
        for path in created:
            if not re.fullmatch('/sdcard/Download/dishnest-qa-' + token + r'-\d+\.(json|zip)', path):
                errors.append('Refused unexpected cleanup path')
                continue
            try:
                adb('shell', 'rm', '-f', path)
            except Exception:
                errors.append('Could not remove a QA export')
        try:
            adb('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', previous)
        except Exception:
            errors.append('Keep-awake restoration failed')
        record['settingsRestored'] = adb('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in').decode().strip() == previous
        record['ordinaryApkAndPersistentFilesUnchanged'] = ordinary() == before
        record['testExportsRemoved'] = all(adb('shell', 'test', '!', '-e', p) == b'' for p in created)
        record['cleanupErrors'] = errors
        record['passed'] = (len(record['checks']) == 11 and all(record['checks'].values()) and not errors and
                            record['settingsRestored'] and record['ordinaryApkAndPersistentFilesUnchanged'] and record['testExportsRemoved'])
        record['notCovered'] = ['Provider write failure/unavailable storage', 'External cloud providers',
                                'Photo-inclusive real user attachments', 'Complete app-data migration', 'Publisher-signed release']
        output.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
        if record['settingsRestored']:
            journal.write_text(json.dumps({'restored': True}) + '\n', encoding='utf-8')
        print(json.dumps(record), flush=True)
    if not record['passed']:
        raise ValueError('Document-provider QA incomplete; no passing claim')


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
