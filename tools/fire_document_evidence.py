"""Validate bounded document-provider observations without approving all backup scenarios."""
from build_evidence import ROOT, digest

CHECKS = {'cancelJsonExport', 'cancelZipExport', 'cancelJsonImport', 'cancelZipImport',
          'zipCrcAndPayloadMatchJson', 'exportsDoNotChangeKitchen', 'jsonExportHasRecipesMealsAndSelectedPlan',
          'jsonNonemptyKitchenRejected', 'zipNonemptyKitchenRejected', 'jsonProviderRoundTrip', 'zipProviderRoundTrip'}


def verify(report):
    if not isinstance(report, dict):
        return False
    if not all(report.get(k) is True for k in ('passed', 'installedApksVerified', 'settingsRestored',
                                              'ordinaryApkAndPersistentFilesUnchanged', 'testExportsRemoved')):
        return False
    if (report.get('cleanupErrors') != [] or report.get('packageId') != 'com.dishnest.app.qa' or
            not str(report.get('model', '')).startswith('KF') or
            any(report.get(k) is not False for k in ('ordinaryAppModified', 'paidServicesEnabled', 'privateIdentifiersStored'))):
        return False
    checks = report.get('checks', {})
    if set(checks) != CHECKS or not all(v is True for v in checks.values()):
        return False
    if report.get('backupCoverage') != dict(recipes=4, meals=5, plans=2, bundledDemoPhotosOnly=True,
                                           timersAndPreferencesExcluded=True):
        return False
    try:
        if not all(report.get(k) == digest(ROOT / p) for k, p in (
            ('apkSha256', 'app/build/outputs/apk/qa/app-qa.apk'),
            ('instrumentationApkSha256', 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'),
            ('controllerSha256', 'tools/run_fire_document_checks.py'),
            ('helperSha256', 'tools/run_fire_offline_checks.py'),
            ('seedTraceSha256', 'artifacts/fire-document-seed.txt'))):
            return False
        trace = (ROOT / 'artifacts/fire-document-seed.txt').read_text(encoding='utf-8')
        return 'OK (1 test)' in trace and not any(v in trace for v in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed'))
    except (OSError, TypeError):
        return False
