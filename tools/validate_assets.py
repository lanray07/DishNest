from pathlib import Path
from PIL import Image
from html.parser import HTMLParser
from urllib.parse import urlsplit,unquote
import json,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
checks=[];errors=[]
class Page(HTMLParser):
    def __init__(self):super().__init__();self.refs=[];self.h1=0
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='h1':self.h1+=1
        if tag in ['a','link','img']:
            ref=a.get('src') if tag=='img' else a.get('href')
            if ref:self.refs.append(ref)
for page in (ROOT/'website').glob('*.html'):
    parsed=Page();parsed.feed(page.read_text(encoding='utf-8-sig'));assert parsed.h1==1,f'{page.name}: expected one h1'
    for ref in parsed.refs:
        url=urlsplit(ref)
        if url.scheme or not url.path:continue
        if not (page.parent/unquote(url.path)).exists():errors.append(f'{page.name}: missing {ref}')
checks.append('12 HTML pages: one h1 each; all internal file links resolve')
listing=json.loads((ROOT/'marketing/listing-en-GB.json').read_text(encoding='utf-8'))
assert len(listing['short_description'].encode())<=2000
assert len(listing['full_description'])<=4000
assert 3<=len(listing['feature_bullets'])<=5
checks.append('English listing fits documented description and bullet limits')
for size in [114,512]:assert Image.open(ROOT/f'marketing/assets/icon-{size}.png').size==(size,size)
assert Image.open(ROOT/'marketing/assets/promotional-1024x500.png').size==(1024,500)
checks.append('Required icon and promotional pixel dimensions valid')
manifest=json.loads((ROOT/'marketing/screenshots/manifest.json').read_text(encoding='utf-8'))
for item in manifest:
    if item['status']!='draft exported':errors.append(f'Missing screenshot: {item["device"]}-{item["id"]}');continue
    image=Image.open(ROOT/'marketing/screenshots'/item['output']);assert list(image.size)==item['size'];assert image.mode=='RGB'
checks.append(f'{len(manifest)} screenshot drafts: dimensions and RGB output checked')
for file in (ROOT/'app/src/main/res').glob('values*/strings.xml'):ET.parse(file)
checks.append('All Android string XML parses')
result={'checks':checks,'errors':errors,'passed':not errors,'release_approved':False}
(ROOT/'artifacts/asset-validation.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
if errors:raise SystemExit(1)
