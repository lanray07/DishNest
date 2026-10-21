import copy
import json
import unittest
from fire_offline_evidence import ROOT, verify


class OfflineEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((ROOT / 'artifacts/fire-offline-qa.json').read_text(encoding='utf-8'))

    def test_exact_observed_report(self):
        self.assertTrue(verify(self.report))

    def test_reject_stale_or_incomplete_observations(self):
        for key, value in {
            'apkSha256': '0' * 64, 'instrumentationApkSha256': '0' * 64,
            'controllerSha256': '0' * 64, 'processAbsentBeforeRelaunch': False,
            'ordinaryApkAndPersistentFilesUnchanged': False, 'settingsRestored': False,
            'offlineAfterProcessRestart': False, 'namedPlanSavedBeforeRelaunch': False,
            'savedKitchenAndPreferencesUnchangedAfterOfflineProcessRestart': False,
            'cleanupErrors': ['failed'], 'paidServicesEnabled': True,
            'packageId': 'com.dishnest.app', 'phases': {}
        }.items():
            with self.subTest(field=key):
                changed = copy.deepcopy(self.report)
                changed[key] = value
                self.assertFalse(verify(changed))
        for field, value in {'traceSha256': '0' * 64, 'tests': 0, 'disconnectedBeforeAndAfter': False}.items():
            changed = copy.deepcopy(self.report)
            changed['phases']['suite'][field] = value
            self.assertFalse(verify(changed))
        self.assertFalse(verify(None))
        self.assertFalse(verify({'passed': True}))


if __name__ == '__main__':
    unittest.main()
