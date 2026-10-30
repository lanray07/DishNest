"""Bind narrowly observed settings UI checks to their app, controller and captures."""
from PIL import Image
from build_evidence import ROOT, digest

CHECKS = {
    'frenchSelectionPersistsAcrossProcessRestart',
    'localizedFrenchDialogCancelPreservesSelection',
    'arabicSelectionPersistsAcrossProcessRestart',
    'deviceLanguageFallbackPreservesKitchen',
    'usageOptInPersistsAndSwitchReflectsIt',
    'qaKitchenDeletionOptsOutAndUpdatesSwitch',
}
SCREENS = {'artifacts/screens/fire-language-picker-fr.png',
           'artifacts/screens/fire-language-picker-ar.png',
           'artifacts/screens/fire-settings-usage-after-delete.png'}


def verify(report):
    if not isinstance(report, dict):
        return False
    if set(report.get('accessibleLabelLocales', [])) != {'en-GB', 'fr', 'ar'}:
        return False
    if (report.get('packageId') != 'com.dishnest.app.qa' or not str(report.get('model', '')).startswith('KF') or
            report.get('cleanupErrors') != [] or set(report.get('checks', {})) != CHECKS or
            not all(value is True for value in report['checks'].values())):
        return False
    if not all(report.get(key) is True for key in ('passed', 'installedApkVerified', 'qaLanguagePreferenceRestored',
            'deviceSettingRestored', 'ordinaryApkAndPersistentFilesUnchanged')):
        return False
    if any(report.get(key) is not False for key in ('ordinaryAppModified', 'otherProfilesModified',
            'paidServicesEnabled', 'privateIdentifiersStored', 'publicSubmissionPerformed', 'nativeLanguageApproved')):
        return False
    try:
        if not all(report.get(key) == digest(ROOT / path) for key, path in (
                ('apkSha256', 'app/build/outputs/apk/qa/app-qa.apk'),
                ('controllerSha256', 'tools/run_fire_settings_checks.py'),
                ('sourceHelperSha256', 'tools/run_fire_offline_checks.py'))):
            return False
        if set(report.get('screenshotsSha256', {})) != SCREENS:
            return False
        for path in SCREENS:
            if report['screenshotsSha256'][path] != digest(ROOT / path):
                return False
            with Image.open(ROOT / path) as frame:
                if frame.format != 'PNG' or min(frame.size) < 600 or max(frame.size) > 8192:
                    return False
                frame.verify()
        return True
    except (OSError, TypeError, KeyError):
        return False
