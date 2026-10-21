"""Require exact binaries, complete offline traces and restored state for this narrow gate."""
import re
from build_evidence import ROOT, digest


def verify(report):
    if not isinstance(report, dict):
        return False
    required = ('passed', 'installedApksVerified', 'processAbsentBeforeRelaunch',
                'namedPlanSavedBeforeRelaunch', 'processPresentAfterOfflineRelaunch',
                'savedKitchenAndPreferencesUnchangedAfterOfflineProcessRestart',
                'offlineAfterProcessRestart', 'settingsRestored', 'ordinaryApkAndPersistentFilesUnchanged')
    if not all(report.get(k) is True for k in required):
        return False
    if any(report.get(k) is not False for k in ('privateIdentifiersStored', 'ordinaryAppModified', 'paidServicesEnabled')):
        return False
    if report.get('cleanupErrors') != [] or report.get('packageId') != 'com.dishnest.app.qa':
        return False
    if not str(report.get('model', '')).startswith('KF'):
        return False
    try:
        if not all(report.get(k) == digest(ROOT / p) for k, p in (
            ('apkSha256', 'app/build/outputs/apk/qa/app-qa.apk'),
            ('instrumentationApkSha256', 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'),
            ('controllerSha256', 'tools/run_fire_offline_checks.py'))):
            return False
        phases = report.get('phases', {})
        if set(phases) != {'suite', 'selected-plan'}:
            return False
        for name, count in (('suite', 15), ('selected-plan', 1)):
            phase = phases[name]
            trace = ROOT / f'artifacts/fire-offline-{name}.txt'
            if (phase.get('passed') is not True or phase.get('tests') != count or
                    phase.get('disconnectedBeforeAndAfter') is not True or
                    phase.get('trace') != trace.relative_to(ROOT).as_posix() or
                    phase.get('traceSha256') != digest(trace)):
                return False
            output = trace.read_text(encoding='utf-8')
            if (f'OK ({count} test' not in output or
                    len(re.findall(r'INSTRUMENTATION_STATUS_CODE: 0\s*$', output, re.M)) != count or
                    any(x in output for x in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed'))):
                return False
        return True
    except (OSError, TypeError, KeyError):
        return False
