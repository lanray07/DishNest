"""Stage a loopback-only browser renderer for shaped multilingual store artwork.

Chromium renders Arabic bidi and Devanagari shaping with system fonts. Native app
pixels are placed unchanged. Read the visible Export PNG link through CUA to save
1920x1200 screenshots or 1024x500 promotional artwork, independent of browser
viewport scaling; this tool never uploads or publishes.
"""
from pathlib import Path
import json
import shutil
import argparse

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'build/visual-localization'
SCREENS = ['recipes', 'plan', 'shopping', 'cooking', 'import', 'pantry', 'recipe-detail', 'plan']

def prepare(available=False):
    OUT.mkdir(parents=True, exist_ok=True)
    copy = json.loads((ROOT/'marketing/visual-localization.json').read_text(encoding='utf-8'))
    staged = []
    for locale in copy:
        if available and not all((ROOT/f'artifacts/screens/localized/{locale}/tablet-{screen}.png').is_file() for screen in set(SCREENS)):
            continue
        folder = OUT/'captures'/locale
        folder.mkdir(parents=True, exist_ok=True)
        for screen in set(SCREENS):
            source = ROOT/f'artifacts/screens/localized/{locale}/tablet-{screen}.png'
            if not source.is_file():
                raise SystemExit(f'Missing real Compose capture: {source}')
            shutil.copy2(source, folder/f'{screen}.png')
        staged.append(locale)
    shutil.copy2(ROOT/'marketing/assets/icon-512.png', OUT/'icon.png')
    (OUT/'copy.json').write_text(json.dumps(copy, ensure_ascii=False), encoding='utf-8')
    (OUT/'index.html').write_text(HTML, encoding='utf-8')
    print(f'Staged {len(staged)} locales at {OUT}; loopback preview only.')

HTML = '''<!doctype html><html><head><meta charset="utf-8"><title>DishNest localized artwork</title>
<style>body{margin:0;background:#e8e9e4;font:16px 'Segoe UI',sans-serif}header{padding:15px;display:flex;gap:20px;align-items:center}canvas{width:100%;height:auto;display:block}button{padding:12px 24px}output{white-space:pre-wrap}</style></head>
<body><header><a id="export">Export PNG</a><output>Rendering…</output></header><canvas aria-label="Localized DishNest store artwork"></canvas>
<script>
const params=new URLSearchParams(location.search),locale=params.get('locale')||'en-GB',id=params.get('screen')||'01';
const screens=['recipes','plan','shopping','cooking','import','pantry','recipe-detail','plan'];
fetch('copy.json').then(r=>r.json()).then(async copy=>{
 const item=copy[locale],promo=id==='promo',i=promo?0:Number(id)-1,rtl=locale==='ar';
 document.documentElement.lang=locale;
 const canvas=document.querySelector('canvas'),ctx=canvas.getContext('2d');
 canvas.width=promo?1024:1920;canvas.height=promo?500:1200;
 const w=canvas.width,h=canvas.height,margin=promo?50:72;
 let icon=new Image();icon.src='icon.png';await icon.decode();
 let capture=new Image();if(!promo){capture.src='captures/'+locale+'/'+screens[i]+'.png';await capture.decode();}
 await document.fonts.ready;
 ctx.fillStyle='#faf7f0';ctx.fillRect(0,0,w,h);
 ctx.fillStyle='#456b59';ctx.font=(promo?'72':'38')+'px Georgia';ctx.textAlign='left';ctx.direction='ltr';
 ctx.drawImage(icon,margin,promo?55:38,promo?100:42,promo?100:42);ctx.fillText('DishNest',margin+(promo?125:58),promo?130:73);
 ctx.direction=rtl?'rtl':'ltr';ctx.textAlign=rtl?'right':'left';ctx.textBaseline='top';
 const x=rtl?w-margin:margin;let overflow=false;
 function text(value,size,y,color,maxWidth){
   ctx.font='600 '+size+'px "Segoe UI", "Nirmala UI", sans-serif';ctx.fillStyle=color;
   const words=value.split(' ');let line='',lines=[];
   for(const word of words){let next=line?line+' '+word:word;if(ctx.measureText(next).width>maxWidth&&line){lines.push(line);line=word;}else line=next;}
   if(line)lines.push(line);
   for(const line of lines){if(ctx.measureText(line).width>maxWidth)overflow=true;ctx.fillText(line,x,y);y+=size*1.35;}
   return y;
 }
 let y=text(item.headlines[i],promo?38:60,promo?195:115,'#29312d',w-margin*2);
 y=text(promo?item.headlines[1]:item.subheadlines[i],promo?28:30,y+12,'#626b62',w-margin*2);
 const footerY=h-(promo?65:48);
 if(!promo){const top=y+32,maxHeight=footerY-top-25,scale=Math.min((w-margin*2)/capture.width,maxHeight/capture.height);
   if(scale<=0)overflow=true;const iw=capture.width*scale,ih=capture.height*scale;
   ctx.drawImage(capture,(w-iw)/2,top,iw,ih);ctx.strokeStyle='#d8ded2';ctx.lineWidth=2;ctx.strokeRect((w-iw)/2,top,iw,ih);
   if(top+ih>footerY-20)overflow=true;
 }
 const footerBottom=text(item.footer,promo?16:19,footerY,'#626b62',w-margin*2);if(footerBottom>h-10)overflow=true;
 const output=document.querySelector('output');output.dataset.overflow=String(overflow);output.dataset.imagesLoaded='true';
 output.textContent=locale+' / '+id+' · '+w+'×'+h+' · '+(overflow?'OVERFLOW':'Ready');output.dataset.testid='ready';
 const link=document.querySelector('#export');link.download=locale+'-'+id+'.png';if(!overflow)link.href=canvas.toDataURL('image/png');
});
</script></body></html>'''

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--available', action='store_true', help='Stage only completed capture sets during an active run')
    prepare(parser.parse_args().available)
