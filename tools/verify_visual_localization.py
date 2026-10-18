"""Validate localized exports and bind them to exact captions and Compose captures."""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def verify():
    copies = json.loads((ROOT/'marketing/visual-localization.json').read_text(encoding='utf-8'))
    manifest = json.loads((ROOT/'marketing/screenshots/localized/manifest.json').read_text(encoding='utf-8'))
    expected = {(locale, screen) for locale in copies for screen in [*map(lambda i: f'{i:02}', range(1, 9)), 'promo']}
    assert {(item['locale'], item['id']) for item in manifest} == expected
    assert len(manifest) == len(expected), 'Duplicate exports'
    for item in manifest:
        output = ROOT/item['output']
        image = Image.open(output)
        assert image.size == ((1024, 500) if item['id'] == 'promo' else (1920, 1200)), output
        assert image.mode in {'RGB', 'RGBA'}, output
        assert sha(output) == item['sha256'], output
        assert item['overflow'] is False and item['images_loaded'] is True, output
        assert item['native_language_approved'] is False
        if item['id'] != 'promo':
            assert sha(ROOT/item['source']) == item['source_sha256'], output
            assert Image.open(ROOT/item['source']).size == (1280, 800), output
            index = int(item['id'])-1
            assert item['headline'] == copies[item['locale']]['headlines'][index], output
            assert item['subheadline'] == copies[item['locale']]['subheadlines'][index], output
    result = {'passed': True, 'locales': list(copies), 'screenshots': len(copies)*8,
              'promotional_images': len(copies), 'export_count': len(manifest),
              'copy_sha256': sha(ROOT/'marketing/visual-localization.json'),
              'capture_method': 'Production Compose UI with isolated translated sample records; Robolectric native graphics',
              'artwork_renderer': 'CUA Chromium with native script shaping',
              'native_language_approved': False, 'physical_device_verified': False, 'release_approved': False}
    uploads = json.loads((ROOT/'marketing/amazon-artwork-uploads.json').read_text(encoding='utf-8'))
    offered = {'en-US', 'en-GB', 'de-DE', 'es-ES', 'fr-FR', 'it-IT', 'pt-BR'}
    assert {row['locale'] for row in uploads['locales']} == offered
    assert uploads.get('verified_after_reload') is True
    assert {row['locale'] for row in uploads['verification'] if row.get('persisted_after_reload')} == offered
    for upload in uploads['locales']:
        assert len(upload['screenshots']) == len(set(upload['screenshots'])) == 8
        local = {row['id']: row['sha256'] for row in upload['local_files_sha256']}
        expected = {row['id']: row['sha256'] for row in manifest if row['locale'] == upload['locale']}
        assert local == expected, f"{upload['locale']}: upload source changed after receipt"
    result['amazon_artwork_locales_verified'] = sorted(offered)
    (ROOT/'artifacts/visual-localization.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(result))

if __name__ == '__main__':
    verify()
