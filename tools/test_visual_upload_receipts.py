import copy
import unittest
from verify_visual_localization import upload_changes

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

if __name__ == "__main__": unittest.main()
