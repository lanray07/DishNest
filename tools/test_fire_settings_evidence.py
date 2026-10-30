import copy
import json
import unittest
from fire_settings_evidence import ROOT, verify, CHECKS


class SettingsEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((ROOT / 'artifacts/fire-settings-qa.json').read_text(encoding='utf-8'))

    def test_observed_settings_and_cleanup(self):
        self.assertTrue(verify(self.report))

    def test_incomplete_stale_or_unrestored_evidence_is_rejected(self):
        for key, value in {'passed': False, 'apkSha256': 'changed', 'controllerSha256': 'changed',
                           'sourceHelperSha256': 'changed', 'qaLanguagePreferenceRestored': False,
                           'deviceSettingRestored': False, 'ordinaryApkAndPersistentFilesUnchanged': False,
                           'nativeLanguageApproved': True, 'screenshotsSha256': {}, 'checks': {}}.items():
            changed = copy.deepcopy(self.report)
            changed[key] = value
            self.assertFalse(verify(changed), key)
        for key in CHECKS:
            changed = copy.deepcopy(self.report)
            changed['checks'][key] = False
            self.assertFalse(verify(changed), key)


if __name__ == '__main__':
    unittest.main()
