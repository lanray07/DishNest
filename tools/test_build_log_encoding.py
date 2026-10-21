import unittest
from build_evidence import build_log_text


class BuildLogEncodingTest(unittest.TestCase):
    def test_success_is_read_in_both_windows_shell_encodings(self):
        message = 'Recipe · وصفة\nBUILD SUCCESSFUL in 4m 20s\n'
        for encoding in ('utf-8', 'utf-8-sig', 'utf-16', 'utf-16-be'):
            with self.subTest(encoding=encoding):
                raw = message.encode(encoding)
                if encoding == 'utf-16-be':
                    raw = b'\xfe\xff' + raw
                self.assertEqual(message, build_log_text(raw))

    def test_failure_and_incomplete_logs_cannot_become_success(self):
        for message in ('BUILD FAILED in 4m', 'Task :app:lintAnalyzeQa', ''):
            for encoding in ('utf-8', 'utf-16'):
                self.assertNotIn('BUILD SUCCESSFUL', build_log_text(message.encode(encoding)))


if __name__ == '__main__':
    unittest.main()
