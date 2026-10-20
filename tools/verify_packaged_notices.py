"""Technical notice integrity only; never infer publisher/legal approval."""
import hashlib
import json
import os
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
def digest(data): return hashlib.sha256(data).hexdigest()

def verify(report):
    if not isinstance(report, dict): return False
    try:
        if report.get('passed') is not True or report.get('controllerSha256') != digest(Path(__file__).read_bytes()): return False
        manifest_path = ROOT / 'legal/third-party/manifest.json'
        if report.get('manifestSha256') != digest(manifest_path.read_bytes()): return False
        manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
        if not all(digest((ROOT / 'legal/third-party' / item['file']).read_bytes()) == item['sha256'] for item in manifest['files']): return False
        if report.get('noticeAssetSha256') != digest((ROOT / 'app/src/main/assets/third-party-notices.txt').read_bytes()): return False
        if report.get('dependencyInventorySha256') != digest((ROOT / 'docs/dependencies/runtime-inventory.json').read_bytes()): return False
        expected = {'app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/qa/app-qa.apk', 'app/build/outputs/apk/release/app-release-unsigned.apk'}
        return set(report.get('apkSha256', {})) == expected and all(digest((ROOT / path).read_bytes()) == value for path, value in report['apkSha256'].items())
    except (OSError, KeyError, ValueError, TypeError): return False


def main():
    manifest_path = ROOT / 'legal/third-party/manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    notice = (ROOT / 'app/src/main/assets/third-party-notices.txt').read_bytes()
    text = notice.decode('utf-8').replace('\r\n', '\n')
    checked = []
    for item in manifest['files']:
        source = ROOT / 'legal/third-party' / item['file']
        if source.parent != ROOT / 'legal/third-party': raise ValueError('Notice source escapes its directory')
        raw = source.read_bytes()
        if digest(raw) != item['sha256']: raise ValueError('Notice source hash mismatch')
        if raw.decode('utf-8').replace('\r\n', '\n') not in text or item['url'] not in text:
            raise ValueError('Original notice or source URL absent from packaged text: ' + item['file'])
        checked.append(item['file'])
    binaries = {}
    for variant, relative in [('debug', 'app/build/outputs/apk/debug/app-debug.apk'),
                              ('qa', 'app/build/outputs/apk/qa/app-qa.apk'),
                              ('release', 'app/build/outputs/apk/release/app-release-unsigned.apk')]:
        path = ROOT / relative
        with ZipFile(path) as apk:
            if apk.read('assets/third-party-notices.txt') != notice:
                raise ValueError('APK notice asset differs from reviewed local source: ' + variant)
        binaries[relative] = digest(path.read_bytes())
    inventory_path = ROOT / 'docs/dependencies/runtime-inventory.json'
    inventory = json.loads(inventory_path.read_text(encoding='utf-8'))
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
    for component in inventory['components']:
        if not component.get('licenses'): raise ValueError('Dependency licence metadata missing')
        directory = cache / component['group'] / component['name'] / component['version']
        matches = list(directory.glob('*/' + component['file']))
        if not any(digest(p.read_bytes()) == component['sha256'] for p in matches):
            raise ValueError('Resolved dependency artifact missing or changed: ' + component['name'])
    result = dict(passed=True, scope='Original notice hashes, complete text/source inclusion and exact packaged asset bytes',
                  originalNoticeCount=len(checked), originalNoticeFiles=checked, apkSha256=binaries,
                  noticeAssetSha256=digest(notice), manifestSha256=digest(manifest_path.read_bytes()),
                  resolvedDependencyCount=len(inventory['components']), resolvedDependencyHashesVerified=True,
                  dependencyInventorySha256=digest(inventory_path.read_bytes()),
                  controllerSha256=digest(Path(__file__).read_bytes()), publisherLegalApproval=False,
                  proprietaryAmazonTermsApproval=False, sourceOrAppDataModified=False)
    (ROOT / 'artifacts/packaged-notices-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'packagedNoticeIntegrity': True, 'originalNotices': len(checked), 'apks': len(binaries), 'legalApproval': False}))


if __name__ == '__main__': main()
