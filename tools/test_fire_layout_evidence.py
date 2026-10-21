import copy
import json
import unittest
from fire_layout_evidence import ROOT, verify


class LayoutEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((ROOT / 'artifacts/fire-layout-qa.json').read_text(encoding='utf-8'))

    def test_observed_matrix(self):
        self.assertTrue(verify(self.report))

    def test_reject_stale_incomplete_or_unrestored_matrix(self):
        for key, value in {'apkSha256': '0' * 64, 'instrumentationApkSha256': '0' * 64,
                           'controllerSha256': '0' * 64, 'helperSha256': '0' * 64,
                           'settingsRestored': False, 'ordinaryApkAndPersistentFilesUnchanged': False,
                           'cleanupErrors': ['failed'], 'phases': {}, 'paidServicesEnabled': True}.items():
            changed = copy.deepcopy(self.report)
            changed[key] = value
            self.assertFalse(verify(changed))
        for key, value in {'rotation': 3, 'systemFontScale': 1.0, 'traceSha256': '0' * 64,
                           'tests': 2, 'settingsObservedBeforeAndAfter': False}.items():
            changed = copy.deepcopy(self.report)
            changed['phases']['portrait-large-text'][key] = value
            self.assertFalse(verify(changed))
        self.assertFalse(verify(None))


if __name__ == '__main__':
    unittest.main()
