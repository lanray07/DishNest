"""Validate the bounded Fire orientation/text-scale matrix, not accessibility approval."""
import re
from build_evidence import ROOT, digest


def verify(report):
    if not isinstance(report, dict):
        return False
    if not all(report.get(k) is True for k in ('passed', 'installedApksVerified', 'settingsRestored',
                                              'ordinaryApkAndPersistentFilesUnchanged')):
        return False
    if (report.get('cleanupErrors') != [] or report.get('packageId') != 'com.dishnest.app.qa' or
            not str(report.get('model', '')).startswith('KF') or
            any(report.get(k) is not False for k in ('privateIdentifiersStored', 'ordinaryAppModified', 'paidServicesEnabled'))):
        return False
    try:
        if not all(report.get(k) == digest(ROOT / p) for k, p in (
            ('apkSha256', 'app/build/outputs/apk/qa/app-qa.apk'),
            ('instrumentationApkSha256', 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'),
            ('controllerSha256', 'tools/run_fire_layout_checks.py'),
            ('helperSha256', 'tools/run_fire_offline_checks.py'))):
            return False
        phases = report.get('phases', {})
        if set(phases) != {'portrait-large-text', 'landscape-large-text'}:
            return False
        for name, rotation in (('portrait-large-text', 0), ('landscape-large-text', 1)):
            phase = phases[name]
            trace = ROOT / f'artifacts/fire-layout-{name}.txt'
            if (phase.get('passed') is not True or phase.get('tests') != 3 or phase.get('rotation') != rotation or
                    phase.get('systemFontScale') != 2.0 or phase.get('settingsObservedBeforeAndAfter') is not True or
                    phase.get('trace') != trace.relative_to(ROOT).as_posix() or phase.get('traceSha256') != digest(trace)):
                return False
            raw = trace.read_text(encoding='utf-8')
            if ('OK (3 tests)' not in raw or len(re.findall(r'INSTRUMENTATION_STATUS_CODE: 0\s*$', raw, re.M)) != 3 or
                    any(s in raw for s in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed'))):
                return False
        return True
    except (OSError, TypeError, KeyError):
        return False
