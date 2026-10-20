"""Hash-bind local browser verification to the exact website and verification scripts."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def inputs(root=ROOT):
    files = [p for p in (root / 'website').rglob('*') if p.is_file()]
    files += [root / name for name in ('PRIVACY.txt', 'tools/build_website.py', 'tools/verify_website.cjs', 'tools/website_evidence.py')]
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(files)}


def verify(report=None, root=ROOT):
    try:
        if report is None:
            report = json.loads((root / 'artifacts/website-verification.json').read_text(encoding='utf-8'))
        if not isinstance(report, dict) or report.get('passed') is not True or report.get('inputs') != inputs(root):
            return False
        results = report.get('results', [])
        if len(results) != 2 or {r['device'] for r in results} != {'desktop', 'phone'}:
            return False
        pages = {p.name for p in (root / 'website').glob('*.html')}
        for result in results:
            expected_size = (1440, 1000) if result.get('device') == 'desktop' else (390, 844)
            if result.get('identity') is not True or result.get('faqOpened') is not True or result.get('http') != 200 or result.get('overflow') is not False or result.get('brokenImages') != [] or result.get('pageErrors') != []:
                return False
            checked = result.get('checkedPages', [])
            if len(checked) != len(pages) or {p['file'] for p in checked} != pages:
                return False
            if any(p.get('http') != 200 or p.get('overflow') is not False or p.get('contentVerified') is not True or p.get('brokenImages') != [] for p in checked):
                return False
            if any((p.get('viewportWidth'), p.get('viewportHeight')) != expected_size for p in checked):
                return False
            if any(p.get('navigationVisible') is not True or p.get('skipLinkPresent') is not True or p.get('landmarkPresent') is not True for p in checked):
                return False
        return True
    except (OSError, ValueError, KeyError, TypeError):
        return False


if __name__ == '__main__':
    import sys
    if '--inputs' in sys.argv:
        print(json.dumps(inputs()))
    else:
        passed = verify()
        print(json.dumps({'current_website_evidence': passed}))
        raise SystemExit(0 if passed else 1)
