"""Validate exact-build forced-idle evidence, without approving the full device matrix."""
from build_evidence import ROOT, digest


def verify(report):
    if not isinstance(report, dict):
        return False
    required = ('passed', 'installedApksVerified', 'processAbsentAfterKill',
                'timerHandledBeforeVerifierRestart', 'forcedIdleRestored',
                'kitchenAndNotificationVerificationPassed', 'ordinaryDataHashesUnchanged')
    if not all(report.get(key) is True for key in required):
        return False
    if any(report.get(key) is not False for key in
           ('qaExemptFromIdle', 'ordinaryAppModified', 'paidServicesEnabled', 'privateIdentifiersStored')):
        return False
    if report.get('packageId') != 'com.dishnest.app.qa' or not str(report.get('model', '')).startswith('KF'):
        return False
    if any(report.get(key) != 'IDLE' for key in
           ('idleStateAtStart', 'idleStateBeforeSnapshot', 'idleStateAfterSnapshot')):
        return False
    bound, elapsed = report.get('observationBoundSeconds'), report.get('observationSeconds')
    if type(bound) is not int or not 35 <= bound <= 900:
        return False
    if type(elapsed) not in (int, float) or not 0 < elapsed <= bound + 30:
        return False
    try:
        return all(report.get(key) == digest(ROOT / path) for key, path in (
            ('apkSha256', 'app/build/outputs/apk/qa/app-qa.apk'),
            ('instrumentationApkSha256', 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'),
            ('controllerSha256', 'tools/run_fire_doze_probe.py')))
    except OSError:
        return False
