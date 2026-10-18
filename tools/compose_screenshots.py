"""Compose store-image drafts from actual JVM-rendered Compose screens.
Never fabricate an app screenshot. Missing captures remain explicitly missing.
Output sizes follow Amazon's supported 1920x1200 and portrait 1200x1920 sizes.
"""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from html import escape
import json,textwrap
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'marketing/screenshots';OUT.mkdir(exist_ok=True)
copy=json.loads((ROOT/'marketing/screenshot-copy.json').read_text(encoding='utf-8'))
def font(size,serif=False):return ImageFont.truetype('C:/Windows/Fonts/'+('georgia.ttf' if serif else 'segoeui.ttf'),size)
manifest=[]
for device,size in [('tablet',(1920,1200)),('phone',(1200,1920))]:
    for item in copy:
        capture=ROOT/f'artifacts/screens/{device}-{item["screen"]}.png'
        entry={**item,'device':device,'locale':'en-GB','size':size,'source':str(capture.relative_to(ROOT)),'capture_method':'Robolectric native graphics of production Compose screens','physical_device_verified':False,'release_approved':False}
        if not capture.exists():entry['status']='missing capture';manifest.append(entry);continue
        w,h=size;image=Image.new('RGB',size,'#faf7f0');d=ImageDraw.Draw(image)
        d.text((75,50),'DishNest',font=font(40,True),fill='#456b59')
        headline=item['headline'];lines=textwrap.wrap(headline,26 if device=='phone' else 60)
        y=130
        for line in lines:d.text((75,y),line,font=font(68 if device=='tablet' else 64,True),fill='#29312d');y+=82
        for line in textwrap.wrap(item['subheadline'],54 if device=='phone' else 100):d.text((78,y+10),line,font=font(30),fill='#626b62');y+=43
        screenshot=Image.open(capture).convert('RGB');maxw=w-150;maxh=h-y-125;screenshot.thumbnail((maxw,maxh),Image.Resampling.LANCZOS)
        x=(w-screenshot.width)//2;top=y+60;image.paste(screenshot,(x,top));d.rectangle((x-1,top-1,x+screenshot.width,top+screenshot.height),outline='#d8ded2',width=2)
        d.text((75,h-48),'Development preview · Demo data · Voice availability varies by device',font=font(20),fill='#626b62')
        name=f'{device}-{item["id"]}-en-GB.png';image.save(OUT/name)
        svg=f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{w}" height="{h}"><rect width="100%" height="100%" fill="#faf7f0"/><text x="75" y="90" font-size="40" font-family="Georgia" fill="#456b59">DishNest</text><text x="75" y="190" font-size="62" font-family="Georgia" fill="#29312d">{escape(headline)}</text><text x="75" y="255" font-size="30" fill="#626b62">{escape(item['subheadline'])}</text><image x="{x}" y="{top}" width="{screenshot.width}" height="{screenshot.height}" xlink:href="../../{escape(entry['source'])}"/></svg>'''
        (OUT/name.replace('.png','.svg')).write_text(svg,encoding='utf-8');entry.update(status='draft exported',output=name);manifest.append(entry)
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(f'Exported {sum(m["status"]=="draft exported" for m in manifest)} screenshot drafts; {sum(m["status"]=="missing capture" for m in manifest)} captures missing.')
