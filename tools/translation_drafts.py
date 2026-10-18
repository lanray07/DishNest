"""Generate public-UI-copy translation drafts; never pass user recipes to this tool.

Explicit opt-in network command: --generate. Uses Google's public translation
endpoint, with bounded batches and a resumable cache. Native review is required.
This is build tooling, never an app runtime dependency.
"""
import argparse, concurrent.futures, hashlib, json, re, time, urllib.parse, urllib.request, urllib.error
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = {'en-rGB':'en', 'en-rUS':'en', 'es':'es', 'fr':'fr', 'de':'de', 'it':'it',
             'pt-rBR':'pt', 'pt-rPT':'pt-PT', 'nl':'nl', 'pl':'pl', 'ar':'ar', 'hi':'hi'}
CACHE = ROOT/'localisation'/'draft-cache'

def android(text):
    return escape(text).replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')

def translate(language, text):
    key = hashlib.sha256((language + '\0' + text).encode()).hexdigest()
    file = CACHE/f'{key}.json'
    if file.exists(): return json.loads(file.read_text(encoding='utf-8'))
    params = urllib.parse.urlencode({'client':'gtx','sl':'en','tl':language,'dt':'t','q':text})
    for attempt in range(4):
        try:
            with urllib.request.urlopen('https://translate.googleapis.com/translate_a/single?'+params, timeout=40) as response:
                result = ''.join(segment[0] for segment in json.load(response)[0] if segment[0])
            file.write_text(json.dumps(result, ensure_ascii=False),encoding='utf-8')
            return result
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 3: raise
            delay=max(60, min(180,int(error.headers.get('Retry-After','60'))))
            print(f'{language}: provider throttled; waiting {delay}s',flush=True)
            time.sleep(delay)
        except Exception:
            if attempt == 3: raise
            time.sleep(2 ** attempt)

def draft(qualifier, language, nodes):
    out = []
    existing_file = ROOT/f'app/src/main/res/values-{qualifier}/strings.xml'
    existing = {n.attrib['name']: n.text for n in ET.parse(existing_file).getroot()} if existing_file.exists() else {}
    if language != 'en':
        pending=[]
        for node in nodes:
            if node.attrib.get('translatable') == 'false' or node.attrib['name'] in existing:continue
            source=(node.text or '').replace("\\'", "'").replace('\\n','\n').replace('\\"','"')
            safe=source.replace('DishNest Premium','DISHNESTPREMIUM').replace('DishNest','DISHNEST')
            for i,value in enumerate(re.findall(r'%(?:\d+\$)?[sdf]',source)):safe=safe.replace(value,f'PLACEHOLDER{i}TOKEN',1)
            key=hashlib.sha256((language+'\0'+safe).encode()).hexdigest()
            if not (CACHE/f'{key}.json').exists():pending.append((safe,key))
        while pending:
            batch=[];length=0
            while pending and length+len(pending[0][0])<2400 and len(batch)<16:
                item=pending.pop(0);batch.append(item);length+=len(item[0])+12
            if not batch:batch.append(pending.pop(0))
            if len(batch)==1:translate(language,batch[0][0]);continue
            marked='\n'.join(f'[[{i}]] {item[0]}' for i,item in enumerate(batch))
            result=translate(language,marked)
            parts=re.split(r'\[\[\s*(\d+)\s*\]\]',result)
            if len(parts)==len(batch)*2+1 and [parts[i] for i in range(1,len(parts),2)]==[str(i) for i in range(len(batch))]:
                for i,(_,key) in enumerate(batch):(CACHE/f'{key}.json').write_text(json.dumps(parts[2*i+2].strip(),ensure_ascii=False),encoding='utf-8')
            # Unrecognised separators fall back to individual, validated calls below.
    for node in nodes:
        name = node.attrib['name']
        if node.attrib.get('translatable') == 'false': continue
        source = (node.text or '').replace("\\'", "'").replace('\\n','\n').replace('\\"','"')
        # Existing, authored short navigation translations are retained.
        text = existing.get(name)
        if text is not None: text = text.replace("\\'", "'").replace('\\n','\n').replace('\\"','"')
        if language == 'en':
            text = source if qualifier == 'en-rGB' else source.replace('favourite','favorite').replace('recognised','recognized').replace('localisation','localization')
        elif text is None:
            protected = re.findall(r'%(?:\d+\$)?[sdf]', source)
            safe = source.replace('DishNest Premium','DISHNESTPREMIUM').replace('DishNest','DISHNEST')
            for i, value in enumerate(protected): safe = safe.replace(value, f'PLACEHOLDER{i}TOKEN', 1)
            text = translate(language, safe).replace('DISHNESTPREMIUM','DishNest Premium').replace('DISHNEST','DishNest')
            for i, value in enumerate(protected): text = text.replace(f'PLACEHOLDER{i}TOKEN', value)
            if sorted(re.findall(r'%(?:\d+\$)?[sdf]',text)) != sorted(protected): raise ValueError(f'Placeholder damaged: {qualifier}:{name}')
        attrs = ' formatted="false"' if node.attrib.get('formatted') == 'false' else ''
        out.append(f'    <string name="{name}"{attrs}>{android(text)}</string>')
    # Separate plural messages, with all CLDR categories required by our validator.
    templates = {
      'en-rGB':['%d recipe','%d recipes'], 'en-rUS':['%d recipe','%d recipes'],
      'es':['%d receta','%d recetas'], 'fr':['%d recette','%d recettes'], 'de':['%d Rezept','%d Rezepte'],
      'it':['%d ricetta','%d ricette'], 'pt-rBR':['%d receita','%d receitas'], 'pt-rPT':['%d receita','%d receitas'],
      'nl':['%d recept','%d recepten'], 'pl':['%d przepis','%d przepisy','%d przepisów','%d przepisu'],
      'ar':['%d وصفة','%d وصفة','%d وصفتان','%d وصفات','%d وصفة','%d وصفة'], 'hi':['%d रेसिपी','%d रेसिपी']}
    categories = ['zero','one','two','few','many','other'] if language=='ar' else ['one','few','many','other'] if language=='pl' else ['one','many','other'] if language in ['es','fr','it','pt','pt-PT'] else ['one','other']
    if categories==['one','many','other']:templates[qualifier]=[templates[qualifier][0],templates[qualifier][1],templates[qualifier][1]]
    out.append('    <plurals name="recipe_count">')
    out.extend(f'        <item quantity="{cat}">{android(value)}</item>' for cat,value in zip(categories,templates[qualifier]))
    out.append('    </plurals>')
    existing_file.parent.mkdir(exist_ok=True)
    existing_file.write_text('<resources>\n'+'\n'.join(out)+'\n</resources>\n',encoding='utf-8')
    return qualifier

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--generate',action='store_true');args=parser.parse_args()
    if not args.generate: parser.error('Network translation requires explicit --generate')
    CACHE.mkdir(parents=True,exist_ok=True)
    nodes=ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot().findall('string')
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures=[pool.submit(draft,q,l,nodes) for q,l in LANGUAGES.items()]
        for f in concurrent.futures.as_completed(futures): print('Completed',f.result(),flush=True)
    (ROOT/'localisation'/'DRAFT_PROVENANCE.json').write_text(json.dumps({'provider':'Google public translation endpoint','content':'App UI copy only; no recipe databases','method':'Automatic drafts plus existing authored navigation and plural resources','human_review':'pending','release_approved':False},indent=2),encoding='utf-8')
