import copy
import tempfile
import unittest
from pathlib import Path
from website_evidence import inputs, verify


class WebsiteEvidenceTest(unittest.TestCase):
    def test_complete_report_rejects_source_change_partial_pages_and_errors(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name in ['website/index.html', 'website/privacy.html', 'PRIVACY.txt', 'tools/build_website.py', 'tools/verify_website.cjs', 'tools/website_evidence.py']:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text('fixture', encoding='utf-8')
            result = {'identity': True, 'faqOpened': True, 'http': 200, 'overflow': False, 'brokenImages': [], 'pageErrors': [],
                      'checkedPages': [{'file': p, 'http': 200, 'overflow': False, 'contentVerified': True, 'brokenImages': []} for p in ['index.html', 'privacy.html']]}
            results = []
            for device, width, height in [('desktop', 1440, 1000), ('phone', 390, 844)]:
                current = copy.deepcopy(result)
                current['device'] = device
                for page in current['checkedPages']:
                    page.update(viewportWidth=width, viewportHeight=height, navigationVisible=True, skipLinkPresent=True, landmarkPresent=True)
                results.append(current)
            report = {'passed': True, 'inputs': inputs(root), 'results': results}
            self.assertTrue(verify(report, root))
            for mutation in ['page', 'console', 'viewport', 'wrong_dimensions', 'hidden_navigation', 'missing_skip', 'missing_landmark', 'failed', 'missing_check']:
                changed = copy.deepcopy(report)
                if mutation == 'page': changed['results'][0]['checkedPages'].pop()
                if mutation == 'console': changed['results'][0]['pageErrors'].append('runtime error')
                if mutation == 'viewport': changed['results'].pop()
                if mutation == 'wrong_dimensions': changed['results'][1]['checkedPages'][0]['viewportWidth'] = 1440
                if mutation == 'hidden_navigation': changed['results'][1]['checkedPages'][0]['navigationVisible'] = False
                if mutation == 'missing_skip': del changed['results'][0]['checkedPages'][0]['skipLinkPresent']
                if mutation == 'missing_landmark': changed['results'][0]['checkedPages'][0]['landmarkPresent'] = False
                if mutation == 'failed': changed['passed'] = False
                if mutation == 'missing_check': del changed['results'][0]['checkedPages'][0]['overflow']
                self.assertFalse(verify(changed, root), mutation)
            (root / 'website/privacy.html').write_text('changed', encoding='utf-8')
            self.assertFalse(verify(report, root))

    def test_legacy_and_malformed_evidence_is_not_a_pass(self):
        for report in [[], {}, {'passed': True}, {'passed': False}]:
            self.assertFalse(verify(report))


if __name__ == '__main__':
    unittest.main()
