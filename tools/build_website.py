"""Generate crawlable supporting pages. Set --origin only after owning a domain.
Draft sites use noindex and omit fictional canonical URLs or listing links.
"""
from pathlib import Path
from html import escape
from urllib.parse import urlparse
import argparse,hashlib,json,re,shutil
ROOT=Path(__file__).resolve().parents[1]; SITE=ROOT/'website'
PAGES={
'features':('A kitchen companion that keeps things together','Create and edit recipes, build seven-day meal plans, consolidate ingredients, manage your pantry and follow large cooking steps. Core kitchen data is stored on your device. This is a development preview with release work remaining.'),
'recipe-organisation':('Your recipes, beautifully organised','Save recipe names, serving sizes, times, dietary tags, cuisine, ingredients, numbered steps, notes and sources. Add a private photo, mark favourites and group recipes into collections. Text, supported Recipe JSON-LD pages and English photo OCR open a review screen before saving. Website import checks robots.txt before each recipe destination and does not bypass restricted content. A robots allowance is not permission to copy: save only content you are entitled to use.'),
'weekly-meal-planning':('A thoughtful weekly meal planner','Use breakfast, lunch, dinner and snack slots for every day of the week. Keep named meal plans, select the active plan, assign recipes by touch and adjust servings. Copy the previous week, save templates and generate seven dinners using saved recipe tags, total cooking time, cuisine and pantry ingredient names. Plans are deterministic and run offline; users must check dietary suitability.'),
'shopping-lists':('From meal plan to shopping list','Build a grouped shopping list from the selected week. The engine scales recipes to planned servings, adds compatible ingredient amounts and deducts compatible pantry quantities. It never converts volume to weight without ingredient data. Cups remain unconverted because cup sizes differ. Add manual items, edit quantities, tick items off, and share or export as text.'),
'voice-cooking':('A calmer cooking mode','Read one large step at a time, change servings, show ingredients, add labelled timers, hear a step aloud and use next or previous touch controls. Push-to-talk works only where a compatible SpeechRecognizer service is installed. An installed provider may require internet or send audio to its service. English has the widest command coverage in this preview; exact command dictionaries cover the target language families, while broader natural-language grammar remains limited. Background timer alerts can be delayed by power settings.'),
'pantry-management':('Make more of what you already have','Track ingredient quantities, units and optional check dates. Pantry suggestions rank saved recipes by matching ingredient names and show missing names. This is a planning aid, not an allergen checker or food-safety guarantee. Quantities still need checking. Compatible quantities reduce generated shopping lists. Opt in to approximate daily pantry reminders for approaching check dates; reminders are off by default and are not freshness guarantees.'),
'fire-tablets':('Designed with Fire tablets in mind','DishNest uses Kotlin, Jetpack Compose and local Room storage, with no mandatory Google Play Services. The app requires Android API 26 or later; Fire OS versions below that are excluded. Core journeys were tested on a KFTRWI Fire tablet (Android 9, API 28). Separate Amazon-delivered private builds completed monthly and annual test purchases and restores. Lifetime purchase and natural subscription expiry testing remain incomplete. This partial coverage does not establish compatibility with every Fire model; the full hardware and accessibility matrix remains pending. Microphone, camera and speech services are optional. Android phone layouts are included. Amazon billing is not available in this development build.'),
'pricing':('Simple beginnings. Room to grow.','The free recipe book allows up to 25 saved recipes, with meal planning, manual entry, shopping, pantry and essential cooking tools. Import and templates can be explored in the explicitly labelled demo kitchen in this development preview. Lifetime is the main Premium offer: GBP 19.99 in the UK or USD 24.99 in the US, with annual alternatives at GBP 9.99 / USD 12.99 and monthly alternatives at GBP 1.49 / USD 1.99. Amazon catalog drafts use these approved prices; other marketplace prices are calculated by Amazon. The app will display the actual price returned by Amazon. Receipt verification is configured on Cloudflare Workers Free, without paid hosting or paid service bindings. Purchases remain disabled in the ordinary preview APK. Amazon purchases are enabled only in a separate private Live App Testing build while purchase lifecycle checks and release approvals remain pending. There is no active trial or checkout. Actual store testing, further Premium scope and publisher approval remain release blockers.'),
'privacy':('Your kitchen is personal',''),
'support':('A little help in the kitchen','This is DishNest 0.1.0, a development preview. Start with an empty private kitchen or explicitly load demo recipes. Add at least one recipe, assign it in Meal planner, and generate a shopping list. Use Settings to export a JSON backup before clearing data. If microphone recognition is unavailable, continue with touch controls. For timer alerts, allow notifications and check device battery restrictions. Use the GitHub support link below to report general problems. Issues are public: omit private recipes, photos, purchase receipts, Amazon account identifiers and other sensitive information. For private support or privacy/deletion help, email banksmi@mail.com. The publisher is O. Bankole. Final privacy and production-service details still need approval. No Amazon listing has been published.'),
'faq':('Good questions, clear answers','Can saved recipes work offline? Yes: the recipe book, planner, pantry and shopping list are local. Does voice always work offline? No: recognition depends on the installed device service. Can I import from any website? No: public HTTPS pages with supported Recipe JSON-LD are supported; restrictions are respected. Is OCR multilingual? The bundled model reads English; other language models are not yet included. Is this ready for submission? No: Fire hardware QA, billing verification, complete localisation and approved screenshots are still required.')
}
# Keep the public repository policy and the local website disclosure in sync.
privacy_source = ROOT / 'PRIVACY.txt'
PAGES['privacy'] = (PAGES['privacy'][0], '\n'.join(privacy_source.read_text(encoding='utf-8').splitlines()[1:]).strip())
french = json.loads((ROOT / 'localisation/website-fr.json').read_text(encoding='utf-8'))
if french['privacySourceSha256'] != hashlib.sha256(privacy_source.read_text(encoding='utf-8').encode('utf-8')).hexdigest():
    raise SystemExit('Review the French privacy copy against the changed source before generating the website.')
if set(french['pages']) != set(PAGES):
    raise SystemExit('French copy must cover every supporting page.')
parser=argparse.ArgumentParser();parser.add_argument('--origin',default='');args=parser.parse_args()
origin=args.origin.rstrip('/')
if origin and (urlparse(origin).scheme!='https' or not urlparse(origin).hostname):raise SystemExit('Origin must be an owned HTTPS domain.')
assets=SITE/'assets';assets.mkdir(exist_ok=True)
style_href='site.css?v='+hashlib.sha256((SITE/'site.css').read_bytes()).hexdigest()[:12]
source=ROOT/'app/src/main/res/drawable/lemon_chicken.png'
if source.exists():
    from PIL import Image
    image=Image.open(source);image.thumbnail((1200,1200));image.convert('RGB').save(assets/'lemon-chicken.jpg',quality=90)

def page_path(slug, locale):
    prefix = '/fr/' if locale == 'fr' else '/'
    return prefix + ('' if slug == 'index' else f'{slug}.html')


def page_metadata(slug, locale):
    if not origin:
        return '<meta name="robots" content="noindex,follow">'
    url = escape(origin + page_path(slug, locale), quote=True)
    alternates = ''.join(
        f'<link rel="alternate" hreflang="{tag}" href="{escape(origin + page_path(slug, target), quote=True)}">'
        for tag, target in (('en-GB', 'en-GB'), ('fr', 'fr'), ('x-default', 'en-GB'))
    )
    return f'<link rel="canonical" href="{url}">{alternates}'


def language_link(slug, locale):
    if locale == 'fr':
        return f'<a href="../{slug}.html" lang="en-GB" hreflang="en-GB">English</a>'
    return f'<a href="fr/{slug}.html" lang="fr" hreflang="fr">Français</a>'


def github_links(locale):
    support, privacy = ('Assistance sur GitHub', 'Confidentialité sur GitHub') if locale == 'fr' else ('GitHub support', 'Privacy information on GitHub')
    return (f'<p><a href="https://github.com/lanray07/DishNest/issues">{support}</a> · '
            f'<a href="https://github.com/lanray07/DishNest/blob/main/PRIVACY.txt">{privacy}</a></p>')


def render_page(slug, locale, title, description, main):
    if locale == 'fr':
        asset_prefix = '../'
        skip, navigation, features, support, privacy, status = (
            'Aller au contenu', 'Navigation principale', 'Fonctionnalités', 'Assistance', 'Confidentialité', 'Version de développement')
    else:
        asset_prefix = ''
        skip, navigation, features, support, privacy, status = (
            'Skip to content', 'Main navigation', 'Features', 'Support', 'Privacy', 'Development preview')
    return f'''<!doctype html>
<html lang="{locale}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{escape(title)} — DishNest</title><meta name="description" content="{escape(description,quote=True)}">{page_metadata(slug,locale)}<link rel="stylesheet" href="{asset_prefix}{style_href}"><link rel="icon" href="{asset_prefix}assets/icon.svg"></head>
<body><a class="skip" href="#main">{skip}</a><header><a class="brand" href="index.html"><img src="{asset_prefix}assets/icon.svg" alt="">DishNest</a><nav aria-label="{navigation}"><a href="features.html">{features}</a><a href="faq.html">FAQ</a><a href="support.html">{support}</a>{language_link(slug,locale)}</nav></header>
<main id="main">{main}</main><footer><span>DishNest · {status}</span><span><a href="privacy.html">{privacy}</a> · <a href="support.html">{support}</a></span></footer></body></html>
'''


def write_support_page(folder, slug, locale, title, paragraphs):
    body = ''.join(f'<p>{escape(part)}</p>' for part in paragraphs)
    links = github_links(locale) if slug in {'support', 'privacy'} else ''
    back = 'Retour à l’accueil' if locale == 'fr' else 'Make yourself at home'
    eyebrow = '<span class="eyebrow">Save recipes. Plan meals. Cook hands-free.</span>' if locale == 'en-GB' else ''
    main = f'<section class="prose">{eyebrow}<h1>{escape(title)}</h1>{body}{links}<p><a href="index.html">← {back}</a></p></section>'
    (folder / f'{slug}.html').write_text(render_page(slug,locale,title,paragraphs[0][:155],main),encoding='utf-8')


for slug, (title, body) in PAGES.items():
    write_support_page(SITE, slug, 'en-GB', title, body.split('\n\n'))

french_dir = SITE / 'fr'
french_dir.mkdir(exist_ok=True)
for slug, copy in french['pages'].items():
    write_support_page(french_dir, slug, 'fr', copy['title'], copy['paragraphs'])
home = french['home']
feature_links = ''.join(
    f'<li><a href="{slug}.html">{escape(copy["title"])}</a></li>'
    for slug, copy in french['pages'].items() if slug not in {'features', 'faq', 'support', 'privacy'}
)
french_main = f'''<section class="hero"><div><span class="eyebrow">DishNest pour Android et tablettes Fire</span><h1>{escape(home['title'])}</h1><p class="intro">{escape(home['description'])}</p><a class="pill" href="features.html">Découvrir DishNest</a></div><div class="hero-art"><img src="../assets/lemon-chicken.jpg" alt="{escape(home['imageAlt'],quote=True)}" width="720" height="780"><div class="note">{escape(home['photoCaption'])}</div></div></section>
<section class="prose"><h2>Explorez les fonctionnalités</h2><ul>{feature_links}</ul><details><summary>{escape(home['offlineQuestion'])}</summary><p>{escape(home['offlineAnswer'])}</p></details></section>'''
(french_dir / 'index.html').write_text(
    render_page('index','fr',home['title'],home['description'],french_main), encoding='utf-8')

index_file = SITE / 'index.html'
index = index_file.read_text(encoding='utf-8-sig')
index = re.sub(r'href="site\.css(?:\?v=[a-f0-9]+)?"', f'href="{style_href}"', index)
index = re.sub(r'<meta name="robots" content="noindex,follow">|<link rel="canonical"[^>]*>|<link rel="alternate"[^>]*>', '', index)
index = re.sub(r'<a [^>]*hreflang="fr"[^>]*>Français</a>', '', index)
index = index.replace('</nav>', language_link('index','en-GB') + '</nav>', 1)
index = index.replace('Physical Fire-device compatibility testing and Amazon submission are still pending.',
                      'Core flows were tested on one Fire model. Broader compatibility testing and Amazon submission are pending.')
index = index.replace('purchases are disabled pending store configuration and verified receipt handling.',
                      'purchases are disabled in the development APK while private Amazon purchase tests continue.')
index_file.write_text(index.replace('</head>',page_metadata('index','en-GB')+'</head>'),encoding='utf-8')

urls = [page_path(slug, locale) for locale in ('en-GB', 'fr') for slug in ('index', *PAGES)]
(SITE/'robots.txt').write_text('User-agent: *\n'+('Allow: /\nSitemap: '+origin+'/sitemap.xml\n' if origin else 'Disallow: /\n'),encoding='utf-8')
(SITE/'sitemap.xml').write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'+''.join(f'<url><loc>{escape(origin+url)}</loc></url>' for url in urls if origin)+'</urlset>',encoding='utf-8')
(SITE/'locale-manifest.json').write_text(json.dumps({
    'generated_locales':['en-GB','fr'],
    'planned_locales':['en-US','es','de','it','pt-BR','pt-PT','nl','pl','ar','hi'],
    'translation_status':'French draft generated; native review pending.',
    'canonical_origin':origin or None,
    'deployed':False
},indent=2)+'\n',encoding='utf-8')
print(f'Built {len(urls)} local HTML pages.')
