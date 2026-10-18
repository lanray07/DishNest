"""Apply cached and locally authored drafts without making network requests."""
import hashlib,json,re
import xml.etree.ElementTree as ET
from pathlib import Path
from translation_drafts import ROOT,LANGUAGES,CACHE,android

def source_text(node):return (node.text or '').replace("\\'", "'").replace('\\n','\n').replace('\\"','"')

def available(qualifier,language,nodes):
    file=ROOT/f'app/src/main/res/values-{qualifier}/strings.xml'
    existing={n.attrib['name']:source_text(n) for n in ET.parse(file).getroot().findall('string')} if file.exists() else {}
    by_source={source_text(n):existing[n.attrib['name']] for n in nodes if n.attrib['name'] in existing}
    for n in nodes:
        source=source_text(n)
        safe=source.replace('DishNest Premium','DISHNESTPREMIUM').replace('DishNest','DISHNEST')
        placeholders=re.findall(r'%(?:\d+\$)?[sdf]',source)
        for i,value in enumerate(placeholders):safe=safe.replace(value,f'PLACEHOLDER{i}TOKEN',1)
        cache=CACHE/(hashlib.sha256((language+'\0'+safe).encode()).hexdigest()+'.json')
        if cache.exists():
            value=json.loads(cache.read_text(encoding='utf-8')).replace('DISHNESTPREMIUM','DishNest Premium').replace('DISHNEST','DishNest')
            for i,p in enumerate(placeholders):value=value.replace(f'PLACEHOLDER{i}TOKEN',p)
            by_source.setdefault(source,value)
    overrides=ROOT/f'localisation/authored/{qualifier}.json'
    if overrides.exists():by_source.update(json.loads(overrides.read_text(encoding='utf-8')))
    by_source['DishNest']='DishNest';by_source['DishNest Premium']='DishNest Premium'
    return existing,by_source

if __name__=='__main__':
    nodes=[n for n in ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot().findall('string') if n.attrib.get('translatable')!='false']
    missing={}
    for qualifier,language in LANGUAGES.items():
        if language=='en':continue
        existing,translations=available(qualifier,language,nodes)
        absent=list(dict.fromkeys(source_text(n) for n in nodes if source_text(n) not in translations))
        if absent:missing[qualifier]=absent;continue
        out=['<resources>']
        for node in nodes:
            name=node.attrib['name'];value=translations[source_text(node)]
            attrs=' formatted="false"' if node.attrib.get('formatted')=='false' else ''
            out.append(f'    <string name="{name}"{attrs}>{android(value)}</string>')
        # Keep the manually defined, category-complete plural resources.
        file=ROOT/f'app/src/main/res/values-{qualifier}/strings.xml'
        plural=ET.parse(file).getroot().findall('plurals') if file.exists() else []
        if not plural:
            p=ET.Element('plurals',{'name':'recipe_count'})
            for category in ['one','other']:ET.SubElement(p,'item',{'quantity':category}).text='%d रेसिपी'
            plural=[p]
        out.extend(ET.tostring(p,encoding='unicode') for p in plural)
        out.append('</resources>')
        file.parent.mkdir(exist_ok=True);file.write_text('\n'.join(out)+'\n',encoding='utf-8')
    (ROOT/'localisation/missing-drafts.json').write_text(json.dumps(missing,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:len(v) for k,v in missing.items()}))
