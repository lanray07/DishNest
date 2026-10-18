"""Package original generated photographs at app-friendly resolutions."""
from pathlib import Path
from PIL import Image
import json
ROOT=Path(__file__).resolve().parents[1]
GENERATED=Path('C:/Users/User/.codex/generated_images/01a14f63-3e42-7460-8e96-53aa09b26ee6')
files={'pasta':'exec-35b10981-406d-4cc5-bd3f-320f631c60da.png','soup':'exec-268c3884-1ca1-4409-aa63-3f5b3336537a.png','oats':'exec-346ca753-2bb9-48a8-8760-339024730f30.png'}
manifest=[]
for name,file in files.items():
    target=ROOT/f'app/src/main/res/drawable-nodpi/food_{name}.jpg'
    target.parent.mkdir(exist_ok=True)
    if (GENERATED/file).exists():
        image=Image.open(GENERATED/file);image.thumbnail((1200,900));image.convert('RGB').save(target,quality=90)
        manifest.append({'asset':str(target.relative_to(ROOT)),'provenance':'Original built-in imagegen output, 2026-10-18','use':'Explicit demo recipe only','source':file})
(ROOT/'marketing/assets/food-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(f'Packaged {len(manifest)} original food photos.')
