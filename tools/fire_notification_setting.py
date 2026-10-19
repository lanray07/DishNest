"""Operate only the observed DishNest QA notification switch on the selected Fire."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def run(serial, state, adb=None):
    adb = adb or str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')

    def command(*args):
        try:
            return subprocess.check_output([adb, '-s', serial, *args], stderr=subprocess.STDOUT, timeout=15)
        except subprocess.CalledProcessError as error:
            detail = error.output.decode(errors='replace').replace(serial, '<device>')[:200]
            raise ValueError(f'QA notification ADB {args[0]} {args[1]} failed: {detail}') from None
        except subprocess.TimeoutExpired:
            raise ValueError(f'QA notification ADB {args[0]} {args[1]} timed out; verify the QA switch before continuing.') from None

    command('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    policy = command('shell', 'dumpsys', 'window', 'policy').decode(errors='replace')
    if re.search(r'isStatusBarKeyguard=true|mIsShowing=true', policy):
        raise ValueError('Unlock the Fire before changing the QA notification switch.')
    command('shell', 'am', 'start', '-W', '-a', 'android.settings.APP_NOTIFICATION_SETTINGS',
            '--es', 'android.provider.extra.APP_PACKAGE', 'com.dishnest.app.qa')

    recovered_settings = False

    def snapshot():
        nonlocal recovered_settings
        path = '/data/local/tmp/dishnest-qa-window.xml'
        root = None
        for attempt in range(4):
            try:
                dumped = command('shell', 'uiautomator', 'dump', path)
                # Fire's dumper can return exit code zero with a null accessibility root.
                if b'dumped to:' in dumped:
                    try:
                        root = ET.fromstring(command('exec-out', 'cat', path))
                    except ET.ParseError:
                        pass
            finally:
                command('shell', 'rm', '-f', path)
            if root is not None:
                break
            if attempt < 3:
                time.sleep(0.3)
        if root is None:
            # Fire can keep an existing Settings window with a permanently null
            # accessibility root. Recreate only the exact notification activity
            # we opened, once, without clearing Settings data or changing a switch.
            policy = command('shell', 'dumpsys', 'window', 'policy').decode(errors='replace')
            focused = command('shell', 'dumpsys', 'activity', 'activities').decode(errors='replace')
            own_settings = re.search(r'mResumedActivity:[^\n]* com\.android\.settings/\.Settings\$AppNotificationSettingsActivity(?:\s|\})', focused)
            if not recovered_settings and own_settings and not re.search(r'isStatusBarKeyguard=true|mIsShowing=true', policy):
                recovered_settings = True
                command('shell', 'am', 'force-stop', 'com.android.settings')
                command('shell', 'am', 'start', '-W', '-a', 'android.settings.APP_NOTIFICATION_SETTINGS',
                        '--es', 'android.provider.extra.APP_PACKAGE', 'com.dishnest.app.qa')
                return snapshot()
            raise ValueError('QA notification settings did not expose a valid UI hierarchy; no further tap attempted.')
        nodes = list(root.iter('node'))
        titles = [n for n in nodes if n.get('resource-id') == 'com.android.settings:id/entity_header_title']
        labels = [n for n in nodes if n.get('resource-id') == 'com.android.settings:id/switch_text']
        switches = [n for n in nodes if n.get('resource-id') == 'com.android.settings:id/switch_widget']
        if len(titles) != 1 or titles[0].get('text') != 'DishNest QA' or len(labels) != 1 or labels[0].get('text') != 'Show notifications' or len(switches) != 1:
            raise ValueError('Expected the observed DishNest QA notification settings screen; no switch changed.')
        switch = switches[0]
        if switch.get('package') != 'com.android.settings' or switch.get('checkable') != 'true' or switch.get('enabled') != 'true':
            raise ValueError('The QA notification switch is unavailable.')
        if switch.get('checked') not in ('true', 'false'):
            raise ValueError('Unknown QA notification switch state.')
        return switch, switch.get('checked') == 'true'

    switch, before = snapshot()
    desired = before if state == 'read' else state == 'enabled'
    after = before
    if before != desired:
        bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', switch.get('bounds', ''))
        if not bounds:
            raise ValueError('The QA notification switch has invalid bounds.')
        x1, y1, x2, y2 = map(int, bounds.groups())
        if x2 <= x1 or y2 <= y1:
            raise ValueError('The QA notification switch is not visible.')
        command('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        _, after = snapshot()
        if after != desired:
            raise ValueError('QA notification switch change did not persist.')
    return {'packageId': 'com.dishnest.app.qa', 'before': before, 'enabled': after, 'verified': True,
            'settingsWindowRecovered': recovered_settings}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--state', choices=['read', 'enabled', 'disabled'], required=True)
    parser.add_argument('--adb')
    args = parser.parse_args()
    try:
        print(json.dumps(run(args.serial, args.state, args.adb)))
    except ValueError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        # Do not expose the selected device serial from a subprocess exception.
        print(f'QA notification UI operation failed ({type(error).__name__}); verify the connected, unlocked tablet.', file=sys.stderr)
        sys.exit(1)
