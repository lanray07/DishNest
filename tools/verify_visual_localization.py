"""Validate localized exports and bind them to exact captions and Compose captures."""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def upload_changes(upload, current, icons):
    """Validate a historical receipt against itself, then compare with current art."""
    assert len(upload['screenshots']) == len(set(upload['screenshots'])) == 8
    recorded = {row['id']: row['sha256'] for row in upload['local_files_sha256']}
    assert len(recorded) == len(upload['local_files_sha256']) and set(recorded) == set(current)
    saved = {row['id']: row['sha256'] for row in upload['saved_asset_sha256']}
    historical = {key: value for key, value in recorded.items() if key != 'promo'}
    historical.update(icons)
    historical['tablet_promotional_image'] = recorded['promo']
    assert len(saved) == len(upload['saved_asset_sha256']) and saved == historical, 'Historical Amazon receipt bytes differ'
    return sorted(key for key in current if recorded[key] != current[key])

def verify():
    copies = json.loads((ROOT/'marketing/visual-localization.json').read_text(encoding='utf-8'))
    manifest = json.loads((ROOT/'marketing/screenshots/localized/manifest.json').read_text(encoding='utf-8'))
    expected = {(locale, screen) for locale in copies for screen in [*map(lambda i: f'{i:02}', range(1, 9)), 'promo']}
    assert {(item['locale'], item['id']) for item in manifest} == expected
    assert len(manifest) == len(expected), 'Duplicate exports'
    for item in manifest:
        output = ROOT/item['output']
        assert item['icon_sha256'] == sha(ROOT/'marketing/assets/icon-512.png'), output
        assert item['renderer_sha256'] == sha(ROOT/'tools/prepare_visual_renderer.py'), output
        assert item['copy_sha256'] == sha(ROOT/'marketing/visual-localization.json'), output
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
    for row in uploads['verification']:
        assert row.get('saved_files_match') is True, row['locale']
        assert row.get('screenshot_order') == [f'{i:02}' for i in range(1, 9)], row['locale']
        assert not row.get('problems'), row['locale']
    changed = {}
    for upload in uploads['locales']:
        current = {row['id']: row['sha256'] for row in manifest if row['locale'] == upload['locale']}
        changes = upload_changes(upload, current, {
            'tablet_large_icon': sha(ROOT/'marketing/assets/icon-512.png'),
            'tablet_small_icon': sha(ROOT/'marketing/assets/icon-114.png'),
        })
        if changes: changed[upload['locale']] = changes
    result['validator_sha256'] = sha(Path(__file__))
    result['manifest_sha256'] = sha(ROOT/'marketing/screenshots/localized/manifest.json')
    result['upload_history_sha256'] = sha(ROOT/'marketing/amazon-artwork-uploads.json')
    result['amazon_live_console_rechecked'] = False
    result['amazon_verification_scope'] = 'Historical saved upload receipts compared with current local artwork; no live console access'
    result['local_assets_passed'] = True
    result['amazon_uploads_match_current_assets'] = not changed
    result['amazon_artwork_locales_historical_receipts'] = sorted(offered)
    result['amazon_artwork_locales_verified'] = sorted(offered - set(changed))
    result['amazon_artwork_updates_pending'] = changed
    result['passed'] = not changed
    (ROOT/'artifacts/visual-localization.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(result))
    return result

if __name__ == '__main__':
    if not verify()['passed']:
        raise SystemExit('Local assets pass; Amazon uploads differ from the current artwork and remain pending.')
