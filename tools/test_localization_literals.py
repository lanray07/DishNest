import unittest
from localize import literal_candidates


class LocalizationLiteralTest(unittest.TestCase):
    def test_visible_errors_are_included_but_header_names_and_resets_are_not(self):
        source = '''getHeaderField("Location")
Text("Save")
error = "Please check the date."
error = ""
Text(stringResource(R.string.save))
'''
        self.assertEqual(['Save', 'Please check the date.'], literal_candidates(source))

    def test_escaped_quotes_and_interpolations_remain_reviewable(self):
        self.assertEqual(['Say \\"next\\"', '$value $label'],
                         literal_candidates(r'Text("Say \"next\""); Text("$value $label")'))


if __name__ == '__main__': unittest.main()
