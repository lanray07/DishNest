"""Bind local settings checks to the current complete build; never accesses a device."""
import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
from build_evidence import verify as verify_build, digest

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'artifacts/local-settings-qa.json'
CASES = {
    'AppLanguagesTest': {'choicePersistsAcrossDelegateResetAndLocalizesColdBackgroundContext', 'everyChoiceHasPackagedLabelsAndUnknownTagsAreRejected'},
    'AppLanguagesSystemTest': {'android13UsesSystemSelectionAndPreservesItDuringMigration'},
    'LocalMetricsTest': {'optOutCollectsNothingAndRepeatedConsentDoesNotEraseHistory', 'unknownEventsAndPrivateTextAreNeverStored', 'onceOnlyEventsSurviveAnotherMetricsInstanceAndRetainExactDayMeaning', 'concurrentWritersCannotLoseEventsAndCountersCannotWrap'},
    'LanguagePickerUiTest': {'frenchPickerUsesRealRadioControlsAndUpdatesTheSystemPreference', 'arabicPickerRendersLocalizedCopyAndCancellationDoesNotChangePreference', 'kitchenDeletionDisablesUsageCountsAndUpdatesItsToggle'},
}

def current():
    if not verify_build():
        raise ValueError('Current full build evidence required')
    reports = {}
    for suite, required in CASES.items():
        file = ROOT / f'app/build/test-results/testDebugUnitTest/TEST-com.dishnest.app.{suite}.xml'
        root = ET.parse(file).getroot()
        cases = root.findall('testcase')
        if (not required.issubset({c.get('name') for c in cases}) or
                any(c.find(tag) is not None for c in cases for tag in ('failure', 'error', 'skipped'))):
            raise ValueError('Required settings cases did not all execute and pass: ' + suite)
        reports[file.relative_to(ROOT).as_posix()] = digest(file)
    started = json.loads((ROOT / 'artifacts/build-start.json').read_text())['started']
    screenshots = {}
    for locale in ('fr', 'ar'):
        file = ROOT / f'artifacts/screens/host-language-picker-{locale}.png'
        if file.stat().st_mtime < started or Image.open(file).size != (800, 1280):
            raise ValueError('Fresh host picker capture required')
        screenshots[file.relative_to(ROOT).as_posix()] = digest(file)
    return {'passed': True, 'coverage': 'Executed host Robolectric API 28/35 checks and real Compose controls; not physical-device QA',
            'requiredCasesPassed': sum(map(len, CASES.values())), 'cases': {k: sorted(v) for k, v in CASES.items()},
            'testReportsSha256': reports, 'screenshotsSha256': screenshots,
            'buildProvenanceSha256': digest(ROOT / 'artifacts/build-provenance.json'),
            'controllerSha256': digest(Path(__file__)), 'physicalDeviceVerified': False,
            'nativeLanguageApproved': False, 'productionPurchaseConversionTested': False,
            'paidServicesEnabled': False, 'publicSubmissionPerformed': False}

def verify():
    try:
        return json.loads(REPORT.read_text()) == current()
    except (OSError, ValueError, KeyError, ET.ParseError):
        return False

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--verify', action='store_true'); args = parser.parse_args()
    if not args.verify:
        REPORT.write_text(json.dumps(current(), indent=2) + '\n')
    passed = verify()
    print(json.dumps({'currentLocalSettingsEvidence': passed, 'physicalDeviceApproval': False}))
    if not passed: raise SystemExit('Local settings evidence is missing or stale')
