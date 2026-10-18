"""Migrate static first arguments of Compose text helpers into Android resources.
Interpolated Kotlin strings are left in the coverage report for manual plural work.
Run on a clean checkout and inspect the diff before committing.
"""
from pathlib import Path
from xml.sax.saxutils import escape
import hashlib,json,re
ROOT=Path(__file__).resolve().parents[1]
resources=ROOT/'app/src/main/res/values/strings.xml'
xml=resources.read_text(encoding='utf-8'); additions={}; count=0
pattern=re.compile(r'((?:Text|Field|PageTitle|EmptyState|SectionTitle)\(\s*)"((?:[^"\\]|\\.)*)"')
for path in (ROOT/'app/src/main/java/com/dishnest/app/ui').glob('*.kt'):
    source=path.read_text(encoding='utf-8-sig');changed=[False]
    def replace(match):
        global count
        literal=match.group(2)
        if '$' in literal:return match.group(0)
        try:value=json.loads('"'+literal+'"')
        except ValueError:return match.group(0)
        if not value.strip():return match.group(0)
        slug=re.sub('[^a-z0-9]+','_',value.lower()).strip('_')[:32]
        key='ui_'+(slug or 'text')+'_'+hashlib.sha256(value.encode()).hexdigest()[:6]
        if f'name="{key}"' not in xml:additions[key]=value
        changed[0]=True;count+=1
        return match.group(1)+f'stringResource(com.dishnest.app.R.string.{key})'
    source=pattern.sub(replace,source)
    if changed[0]:
        if 'import androidx.compose.ui.res.stringResource' not in source:source=source.replace('package com.dishnest.app.ui','package com.dishnest.app.ui\n\nimport androidx.compose.ui.res.stringResource',1)
        path.write_text(source,encoding='utf-8')
def android(value):return escape(value).replace("'","\\'").replace('"','\\"').replace('\n','\\n')
xml=xml.replace('</resources>',''.join(f'    <string name="{key}" formatted="false">{android(value)}</string>\n' for key,value in sorted(additions.items()))+'</resources>')
resources.write_text(xml,encoding='utf-8');print(f'Migrated {count} static UI strings; added {len(additions)} resources.')
