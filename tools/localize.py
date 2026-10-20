"""Build-time localisation: extract, translate through an optional service, validate.

The translation service is a developer-owned HTTPS JSON API accepting
{source_language,target_language,strings,glossary} and returning {strings:{key:text}}.
Set DISHNEST_TRANSLATION_URL / DISHNEST_TRANSLATION_TOKEN outside this repo.
Only app copy is read; private recipe databases are never inputs.
"""
import argparse, hashlib, json, os, re, sys, urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
LOCALES = ['en-GB','en-US','es','fr','de','it','pt-BR','pt-PT','nl','pl','ar','hi']
OUT = ROOT / 'localisation'
OUT.mkdir(exist_ok=True)

def literal_candidates(text):
    # Match complete UI call names, not getHeaderField("Location"). Also inspect
    # error assignments: these reach dialogs without a direct Text("...") call.
    pattern = r'(?:\b(?:Text|Field|PageTitle|EmptyState|SectionTitle)\(\s*|\berror\s*=\s*)"((?:[^"\\]|\\.)*)"'
    return [match.group(1) for match in re.finditer(pattern, text) if match.group(1)]

def extract():
    strings = {n.attrib['name']: n.text or '' for n in ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot().findall('string') if n.attrib.get('translatable') != 'false'}
    hardcoded=[]
    for file in (ROOT/'app/src/main/java').rglob('*.kt'):
        for value in literal_candidates(file.read_text(encoding='utf-8-sig')):
            hardcoded.append({'file':str(file.relative_to(ROOT)), 'text':value})
    (OUT/'catalog.json').write_text(json.dumps(strings,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'hardcoded-report.json').write_text(json.dumps(hardcoded,ensure_ascii=False,indent=2),encoding='utf-8')
    return strings, hardcoded

def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[sdf]|\{\w+\}',value))

def translate(locale, source):
    endpoint=os.getenv('DISHNEST_TRANSLATION_URL','')
    if not endpoint.startswith('https://'): raise RuntimeError('Configure DISHNEST_TRANSLATION_URL with an HTTPS translation service.')
    glossary=json.loads((OUT/'glossary.json').read_text(encoding='utf-8'))
    key=hashlib.sha256(json.dumps([locale,source,glossary],sort_keys=True).encode()).hexdigest()
    cache=OUT/'cache'/f'{key}.json';cache.parent.mkdir(exist_ok=True)
    if cache.exists(): return json.loads(cache.read_text(encoding='utf-8'))
    payload=json.dumps({'source_language':'en-GB','target_language':locale,'strings':source,'glossary':glossary}).encode()
    req=urllib.request.Request(endpoint,data=payload,headers={'Content-Type':'application/json','Authorization':'Bearer '+os.getenv('DISHNEST_TRANSLATION_TOKEN','')})
    with urllib.request.urlopen(req,timeout=60) as response: result=json.load(response)['strings']
    for k,v in source.items():
        if not isinstance(result.get(k),str) or placeholders(v)!=placeholders(result[k]): raise ValueError(f'Invalid placeholders: {locale}:{k}')
    cache.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/f'{locale}.draft.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    return result

def validate(source,hardcoded):
    results=[]
    mapping={'en-GB':'en-rGB','en-US':'en-rUS','pt-BR':'pt-rBR','pt-PT':'pt-rPT'}
    for locale in LOCALES:
        file=ROOT/f'app/src/main/res/values-{mapping.get(locale,locale)}/strings.xml'
        values={n.attrib['name']:n.text or '' for n in ET.parse(file).getroot().findall('string')} if file.exists() else {}
        missing=sorted(set(source)-set(values));invalid=[k for k in values if k in source and placeholders(values[k])!=placeholders(source[k])]
        plural_errors=[]
        if file.exists():
            categories={'ar':{'zero','one','two','few','many','other'},'pl':{'one','few','many','other'},'es':{'one','many','other'},'fr':{'one','many','other'},'it':{'one','many','other'},'pt-BR':{'one','many','other'},'pt-PT':{'one','many','other'}}.get(locale,{'one','other'})
            for node in ET.parse(file).getroot().findall('plurals'):
                present={item.attrib.get('quantity') for item in node}
                if not categories.issubset(present):plural_errors.append({'name':node.attrib['name'],'missing_categories':sorted(categories-present)})
                for item in node:
                    if placeholders(item.text or '') != placeholders(next(iter(node)).text or ''):plural_errors.append({'name':node.attrib['name'],'error':'placeholder mismatch'})
            base_plurals={n.attrib['name'] for n in ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot().findall('plurals')}
            present_plurals={n.attrib['name'] for n in ET.parse(file).getroot().findall('plurals')}
            for name in base_plurals-present_plurals:plural_errors.append({'name':name,'error':'missing plural resource'})
        results.append({'locale':locale,'missing':missing,'invalid_placeholders':invalid,'plural_errors':plural_errors,'human_review':'pending','status':'partial' if missing or invalid or plural_errors else 'resource-complete'})
    plural_nodes=list(ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot().iter('plurals'))
    technical=all(r['status']=='resource-complete' for r in results)
    report={'locales':results,'hardcoded_text_count':len(hardcoded),'plural_resources':len(plural_nodes),'plural_review':'recipe count localised; other inline counts need extraction','rtl':'manifest supportsRtl=true; device visual review pending','resource_complete':technical,'release_approved':False,'complete':False}
    (OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'resource_locales':len(results),'hardcoded_text_count':len(hardcoded),'resource_complete':technical,'release_approved':False}))
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--translate',choices=LOCALES);parser.add_argument('--strict',action='store_true');args=parser.parse_args()
    source,hardcoded=extract()
    if args.translate: translate(args.translate,source)
    result=validate(source,hardcoded)
    if args.strict and not result['resource_complete']: sys.exit(1)
