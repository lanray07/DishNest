import copy
import json
import unittest
from fire_doze_evidence import ROOT, verify


class DozeEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((ROOT / 'artifacts/fire-doze-qa.json').read_text(encoding='utf-8'))

    def test_exact_observed_report(self):
        self.assertTrue(verify(self.report))

    def test_reject_stale_or_incomplete_observations(self):
        for key, value in {
            'apkSha256': '0' * 64, 'instrumentationApkSha256': '0' * 64,
            'controllerSha256': '0' * 64, 'timerHandledBeforeVerifierRestart': False,
            'ordinaryDataHashesUnchanged': False, 'forcedIdleRestored': False,
            'installedApksVerified': False, 'qaExemptFromIdle': True,
            'idleStateBeforeSnapshot': 'ACTIVE', 'observationSeconds': float('nan'),
            'observationBoundSeconds': True, 'packageId': 'com.dishnest.app'
        }.items():
            with self.subTest(field=key):
                changed = copy.deepcopy(self.report)
                changed[key] = value
                self.assertFalse(verify(changed))
        self.assertFalse(verify(None))
        self.assertFalse(verify({'passed': True}))


if __name__ == '__main__':
    unittest.main()
