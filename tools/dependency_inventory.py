"""Inventory the exact resolved release artifacts and their cached POM licences.

Run Gradle with dependency-inventory.init.gradle first. Missing licence metadata
is reported for review rather than guessed or presented as legal approval.
"""
import concurrent.futures,hashlib,json,sys,urllib.request,uuid,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
NS={'m':'http://maven.apache.org/POM/4.0.0'}
FETCH='--fetch-metadata' in sys.argv
META=ROOT/'legal/metadata'

def pom(group,name,version):
    local=META/group/name/version/(name+'-'+version+'.pom')
    if local.exists():return local
    files=list((CACHE/group/name/version).glob('*/*.pom'))
    if files:return files[0]
    if not FETCH:return None
    origin='https://dl.google.com/dl/android/maven2/' if group.startswith('androidx.') else 'https://repo.maven.apache.org/maven2/'
    url=origin+'/'.join([group.replace('.','/'),name,version,name+'-'+version+'.pom'])
    try:
        with urllib.request.urlopen(url,timeout=20) as response:data=response.read()
        ET.fromstring(data)
        local.parent.mkdir(parents=True,exist_ok=True)
        temporary=local.with_suffix('.'+uuid.uuid4().hex+'.tmp')
        temporary.write_bytes(data);temporary.replace(local)
        local.with_suffix('.source.json').write_text(json.dumps({'url':url,'sha256':hashlib.sha256(data).hexdigest()},indent=2),encoding='utf-8')
        return local
    except Exception:return None

def licenses(group,name,version,seen=None):
    seen=set() if seen is None else seen
    identity=(group,name,version)
    if identity in seen:return []
    seen.add(identity)
    file=pom(group,name,version)
    if file is None:return []
    tree=ET.parse(file).getroot()
    result=[{'name':n.findtext('m:name',namespaces=NS),'url':n.findtext('m:url',namespaces=NS)} for n in tree.findall('m:licenses/m:license',NS)]
    parent=tree.find('m:parent',NS)
    if not result and parent is not None:
        result=licenses(*(parent.findtext('m:'+key,namespaces=NS) for key in ['groupId','artifactId','version']),seen)
    return result

records=json.loads((ROOT/'artifacts/runtime-components.json').read_text(encoding='utf-8'))
def populate(record):
    record['licenses']=licenses(record['group'],record['name'],record['version'])
    if record['group']=='cz.adaptech.tesseract4android' and record['version']=='4.9.0':
        record['licenses']=[{'name':'Apache License 2.0','url':'https://github.com/adaptech-cz/Tesseract4Android/blob/4.9.0/LICENSE','local_evidence':'legal/third-party/Tesseract4Android-4.9.0-LICENSE.txt'}]
    record['review']='POM metadata recorded; distribution obligations require review' if record['licenses'] else 'Missing POM licence metadata; upstream review required'
    return record
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:records=list(pool.map(populate,records))
report={'release_approved':False,'scope':'Resolved releaseRuntimeClasspath; native OCR subcomponents separately inventoried','components':records}
output=ROOT/'docs/dependencies';output.mkdir(exist_ok=True)
(output/'runtime-inventory.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
rows=['# Resolved dependency inventory','',f'{len(records)} resolved release artifacts. Hashes identify the actual cached artifacts used by this build. Licence metadata is evidence, not a completed legal review. Native OCR notices and Amazon proprietary terms require separate review.','','| Component | POM licence |','| --- | --- |']
for r in records:rows.append('| '+':'.join(r[k] for k in ['group','name','version'])+' | '+(', '.join(n['name'] or 'Unnamed' for n in r['licenses']) or '**Review required: missing metadata**')+' |')
(output/'README.md').write_text('\n'.join(rows)+'\n',encoding='utf-8')
print(json.dumps({'resolved_artifacts':len(records),'missing_license_metadata':sum(not r['licenses'] for r in records),'release_approved':False}))
