import copy
import json
import unittest
from fire_document_evidence import ROOT, CHECKS, verify


class DocumentEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((ROOT / 'artifacts/fire-document-qa.json').read_text(encoding='utf-8'))

    def test_observed_provider_report(self):
        self.assertTrue(verify(self.report))

    def test_reject_stale_incomplete_or_destructive_results(self):
        for key, value in {'apkSha256': '0' * 64, 'instrumentationApkSha256': '0' * 64,
                           'controllerSha256': '0' * 64, 'helperSha256': '0' * 64,
                           'seedTraceSha256': '0' * 64, 'ocrHelperSha256': '0' * 64,
                           'refusalFramesSha256': {}, 'settingsRestored': False,
                           'ordinaryApkAndPersistentFilesUnchanged': False, 'testExportsRemoved': False,
                           'paidServicesEnabled': True, 'cleanupErrors': ['failure'], 'checks': {}}.items():
            changed = copy.deepcopy(self.report)
            changed[key] = value
            self.assertFalse(verify(changed))
        for key in CHECKS:
            changed = copy.deepcopy(self.report)
            changed['checks'][key] = False
            self.assertFalse(verify(changed))
        changed = copy.deepcopy(self.report)
        changed['backupCoverage']['timersAndPreferencesExcluded'] = False
        self.assertFalse(verify(changed))
        self.assertFalse(verify(None))


if __name__ == '__main__':
    unittest.main()
