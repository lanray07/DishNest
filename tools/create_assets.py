"""Render DishNest brand assets and retain the authored premium icon exports."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,math
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'marketing/assets';OUT.mkdir(parents=True,exist_ok=True)
SVG='''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" fill="#456b59"/><g fill="none" stroke="#faf7f0" stroke-width="3.5" stroke-linecap="round"><path d="M76 52 A23 23 0 1 1 66 33 M80 62 C72 84 35 87 25 62 M77 72 C63 89 39 84 31 75"/></g><path fill="#e8d9b8" d="M54 52 C51 37 63 26 78 28 C78 43 69 53 54 52 M54 52 C43 52 35 44 35 35 C47 35 53 41 54 52"/></svg>'''
(OUT/'icon-master.svg').write_text(SVG,encoding='utf-8');(ROOT/'website/assets/icon.svg').write_text(SVG,encoding='utf-8')
# Rasterise the same mark at 4x resolution with antialiasing.
import aggdraw
def icon(size,mono=False,transparent=False):
    premium = OUT/'icon-premium-source.png'
    if premium.exists() and not mono and not transparent:
        with Image.open(premium) as source:
            return source.convert('RGBA').resize((size,size),Image.Resampling.LANCZOS)
    scale=4;side=size*scale;s=side/108
    image=Image.new('RGBA',(side,side),(0,0,0,0) if transparent else '#456b59');draw=ImageDraw.Draw(image)
    color='#ffffff' if mono else '#faf7f0'
    draw.arc(tuple(int(v*s) for v in (31,31,77,77)),0,300,fill=color,width=round(3.5*s))
    ctx=aggdraw.Draw(image)
    for coords in [[(80,62),(72,84),(35,87),(25,62)],[(77,72),(63,89),(39,84),(31,75)]]:
        p=aggdraw.Path();p.moveto(coords[0][0]*s,coords[0][1]*s);p.curveto(*[v*s for point in coords[1:] for v in point]);ctx.path(p,None,aggdraw.Pen(color,3*s))
    p=aggdraw.Path();p.moveto(54*s,52*s);p.curveto(51*s,37*s,63*s,26*s,78*s,28*s);p.curveto(78*s,43*s,69*s,53*s,54*s,52*s);p.close()
    p.moveto(54*s,52*s);p.curveto(43*s,52*s,35*s,44*s,35*s,35*s);p.curveto(47*s,35*s,53*s,41*s,54*s,52*s);p.close()
    ctx.path(p,aggdraw.Brush('#ffffff' if mono else '#e8d9b8'));ctx.flush();return image.resize((size,size),Image.Resampling.LANCZOS)
for size,name in [(2048,'icon-master.png'),(512,'icon-512.png'),(114,'icon-114.png')]:icon(size).save(OUT/name)
icon(1024,transparent=True).save(OUT/'adaptive-foreground.png');Image.new('RGB',(1024,1024),'#456b59').save(OUT/'adaptive-background.png');icon(1024,mono=True,transparent=True).save(OUT/'monochrome.png')
fontdir=Path('C:/Windows/Fonts')
def font(size,serif=False):return ImageFont.truetype(str(fontdir/('georgia.ttf' if serif else 'segoeui.ttf')),size)
canvas=Image.new('RGB',(1024,500),'#faf7f0');canvas.paste(icon(230),(70,135));d=ImageDraw.Draw(canvas);d.text((350,140),'DishNest',font=font(78,True),fill='#29312d');d.text((353,255),'Save recipes. Plan meals.',font=font(30),fill='#456b59');d.text((353,305),'Cook hands-free.',font=font(30),fill='#456b59');canvas.save(OUT/'promotional-1024x500.png')
for name,title,subtitle in [('onboarding-keep','Keep the good ones.','A home for recipes worth making again.'),('onboarding-plan','A calmer kind of week.','A little planning. A lot more joy.'),('onboarding-cook','Be here, in the kitchen.','One good step at a time.'),('empty-recipe-book','Your next favourite starts here.','Save a recipe and make yourself at home.'),('premium','Room for more.','DishNest Premium · In preparation'),('splash','DishNest','Save recipes. Plan meals. Cook hands-free.')]:
    svg=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 700"><rect width="1000" height="700" fill="#faf7f0"/><circle cx="500" cy="260" r="140" fill="#e9eddf"/><circle cx="500" cy="260" r="87" fill="none" stroke="#456b59" stroke-width="8"/><path d="M420 300 Q500 390 580 300 M440 320 Q500 380 560 320" fill="none" stroke="#456b59" stroke-width="6"/><path d="M500 260 Q490 180 570 190 Q570 260 500 260" fill="#456b59"/><text x="500" y="490" text-anchor="middle" fill="#29312d" font-family="Georgia,serif" font-size="45">{title}</text><text x="500" y="550" text-anchor="middle" fill="#626b62" font-family="sans-serif" font-size="23">{subtitle}</text></svg>'''
    (OUT/f'{name}.svg').write_text(svg,encoding='utf-8')
manifest={'brand':'DishNest','palette':{'sage':'#456B59','ivory':'#FAF7F0','terracotta':'#C97859','accessible_terracotta':'#9E5034','charcoal':'#29312D','cream':'#F1E8D8'},'icon':'Original plate, nest and leaf mark; editable SVG plus Android VectorDrawable','food_photo':'Original AI-generated image via built-in imagegen, 2026-10-18; illustrative demo recipe only','navigation':'Material Symbols via androidx.compose.material:material-icons-extended, Apache 2.0','ocr':'Tesseract4Android 4.9.0 and tessdata_fast English; see THIRD_PARTY_NOTICES.md','review':'Human visual and trademark review required before release'}
(OUT/'asset-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8');print('Created original icon layers, splash, onboarding, empty-state and promotional assets.')
if (OUT/'icon-premium-source.png').is_file():
    # Reapply canonical launcher/web/store exports after legacy monochrome assets.
    import subprocess
    subprocess.run(['node', str(ROOT/'tools/export_premium_icon.cjs')],check=True,cwd=ROOT)
