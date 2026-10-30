import copy
import unittest
from verify_visual_localization import upload_changes, console_proof_matches

class UploadReceiptTest(unittest.TestCase):
    def fixture(self):
        current = {f"{i:02}": f"hash-{i}" for i in range(1, 9)} | {"promo": "promo-hash"}
        icons = {"tablet_large_icon": "large", "tablet_small_icon": "small"}
        saved = {k: v for k, v in current.items() if k != "promo"} | icons | {"tablet_promotional_image": current["promo"]}
        row = {"screenshots": list(current)[:8], "local_files_sha256": [{"id": k, "sha256": v} for k, v in current.items()], "saved_asset_sha256": [{"id": k, "sha256": v} for k, v in saved.items()]}
        return row, current, icons

    def test_matching_receipt_has_no_pending_updates(self):
        row, current, icons = self.fixture()
        self.assertEqual([], upload_changes(row, current, icons))

    def test_new_local_art_is_pending_without_rewriting_historical_receipt(self):
        row, current, icons = self.fixture()
        before = copy.deepcopy(row)
        current["03"] = "new-capture"
        self.assertEqual(["03"], upload_changes(row, current, icons))
        self.assertEqual(before, row)

    def test_saved_amazon_bytes_must_match_the_recorded_upload(self):
        row, current, icons = self.fixture()
        row["saved_asset_sha256"][0]["sha256"] = "wrong"
        with self.assertRaises(AssertionError): upload_changes(row, current, icons)

    def proof(self):
        row, _, _ = self.fixture()
        row['locale'] = 'fr-FR'
        proof = dict(receiptSha256='receipt', manifestSha256='manifest', checkedAt='2026-10-30T10:00:00Z',
                     screenshot={'sha256': 'screen'}, localFileMatchVerified=True,
                     nativeLanguageApproved=False, appDataAccessed=False, paidServicesEnabled=False,
                     publicSubmissionPerformed=False, deploymentPerformed=False,
                     locales={'fr-FR': dict(persisted_after_reload=True, saved_files_match=True, problems=[],
                         screenshots=row['screenshots'], screenshot_order=[f'{i:02}' for i in range(1, 9)],
                         assets=row['saved_asset_sha256'])})
        return proof, {'locales': [row]}

    def test_reloaded_proof_binds_receipt_manifest_and_screenshot(self):
        proof, uploads = self.proof()
        self.assertTrue(console_proof_matches(proof, uploads, 'receipt', 'manifest', 'screen'))
        for values in [('changed', 'manifest', 'screen'), ('receipt', 'changed', 'screen'),
                       ('receipt', 'manifest', 'changed')]:
            self.assertFalse(console_proof_matches(proof, uploads, *values))

    def test_partial_or_reordered_browser_observations_are_rejected(self):
        proof, uploads = self.proof()
        for key, value in [('persisted_after_reload', False), ('screenshots', list(reversed(uploads['locales'][0]['screenshots']))),
                           ('assets', []), ('problems', ['upload failed'])]:
            changed = copy.deepcopy(proof)
            changed['locales']['fr-FR'][key] = value
            self.assertFalse(console_proof_matches(changed, uploads, 'receipt', 'manifest', 'screen'))
        self.assertFalse(console_proof_matches({}, uploads, 'receipt', 'manifest', 'screen'))

if __name__ == "__main__": unittest.main()
