"""Extract remaining literal copy inside Compose functions, including callbacks.

Leaves interpolated text and storage/protocol constants for deliberate review.
Uses Resources in callback bodies so extraction never calls a composable there.
"""
import hashlib, json, re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res/values/strings.xml'
xml=RES.read_text(encoding='utf-8')
additions={}
literal=re.compile(r'"(?:[^"\\]|\\.)*"|//[^\n]*|/\*[\s\S]*?\*/')
excluded={'Try Soon','Quick Meals','Batch Cooking','Everyday Favourites','Christmas','UTF-8','HTTPS','YYYY-MM-DD'}

def matching(source,start,left,right):
    masked=literal.sub(lambda m:' '*len(m.group()),source)
    depth=0
    for i in range(start,len(masked)):
        if masked[i]==left:depth+=1
        elif masked[i]==right:
            depth-=1
            if depth==0:return i
    raise ValueError('Unbalanced function')

for path in (ROOT/'app/src/main/java/com/dishnest/app/ui').glob('*.kt'):
    source=path.read_text(encoding='utf-8-sig');edits=[]
    for match in re.finditer(r'@Composable\s+fun\s+\w+\s*\(',source):
        opening=source.index('(',match.start())
        endargs=matching(source,opening,'(',')')
        body=source.index('{',endargs)
        end=matching(source,body,'{','}')
        replacements=[]
        for token in literal.finditer(source,body+1,end):
            raw=token.group()
            if not raw.startswith('"') or '$' in raw:continue
            try:value=json.loads(raw)
            except ValueError:continue
            if value in excluded or not re.search('[A-Za-z]',value):continue
            if not (re.search('[A-Za-z] [A-Za-z]',value) or re.match(r'^[A-Z][a-z]{2,}$',value)):continue
            if any(x in value for x in ['https://','application/','text/','image/','YYYY','yyyy','EEE','MMM']):continue
            key='copy_'+hashlib.sha256(value.encode()).hexdigest()[:10]
            additions[key]=value
            replacements.append((token.start(),token.end(),f'uiResources.getString(com.dishnest.app.R.string.{key})'))
        if replacements:
            edits.extend(replacements)
            if 'val uiResources =' not in source[body+1:end]:
                edits.append((body+1,body+1,'\n    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources\n'))
    for a,b,text in sorted(edits,reverse=True):source=source[:a]+text+source[b:]
    if edits:path.write_text(source,encoding='utf-8')

def android(value):return escape(value).replace("'","\\'").replace('"','\\"').replace('\n','\\n')
new=[f'    <string name="{key}" formatted="false">{android(value)}</string>' for key,value in sorted(additions.items()) if f'name="{key}"' not in xml]
RES.write_text(xml.replace('</resources>','\n'.join(new)+'\n</resources>'),encoding='utf-8')
print(f'Added {len(new)} resources; interpolations still require manual formatting/plural review.')
