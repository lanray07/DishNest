import unittest
from run_fire_document_checks import null_root_error, refusal_visible, REFUSAL


class DocumentBridgeErrorTest(unittest.TestCase):
    def test_observed_bridge_error_is_recognized_in_both_streams(self):
        message = b'ERROR: null root node returned by UiTestAutomationBridge.\r\n'
        self.assertTrue(null_root_error(message, b''))
        self.assertTrue(null_root_error(b'', message))

    def test_unrelated_adb_failures_are_not_classified_as_retryable(self):
        for message in [b'error: device unauthorized', b'error: no devices/emulators found',
                        b'Permission denied', b'UI hierchary dumped to: /data/local/tmp/qa.xml']:
            self.assertFalse(null_root_error(message, b''))

    def test_full_refusal_survives_ocr_line_breaks_but_help_copy_is_not_a_pass(self):
        self.assertTrue(refusal_visible(REFUSAL.upper().replace('. ', '.\n')))
        self.assertFalse(refusal_visible('ZIP includes recipe photos. Import requires an empty kitchen. Timers and purchase records are excluded.'))
        self.assertFalse(refusal_visible('Import validates the entire backup before writing and requires an empty kitchen.'))
        self.assertFalse(refusal_visible(''))


if __name__ == '__main__':
    unittest.main()
