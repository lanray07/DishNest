"""Materialise explicitly authored translation tables; no network or private data."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'localisation/authored'
order_file=OUT/'source-order.json'
if order_file.exists():order=json.loads(order_file.read_text(encoding='utf-8'))
else:
    missing=json.loads((ROOT/'localisation/missing-drafts.json').read_text(encoding='utf-8'))
    order={'interface':missing['es'],'hi_additional':missing['hi'][:68]}
    assert len(order['interface'])==81
    order_file.write_text(json.dumps(order,ensure_ascii=False,indent=2),encoding='utf-8')
locales=['es','fr','de','it','pt-rBR','pt-rPT','nl','pl','ar','hi']
tables={locale:{} for locale in locales}
rows=(OUT/'interface.tsv').read_text(encoding='utf-8').splitlines()
assert len(rows)==81
for i,row in enumerate(rows):
    values=row.split('|');assert len(values)==11 and int(values[0])==i,(i,len(values))
    for locale,value in zip(locales,values[1:]):tables[locale][order['interface'][i]]=value
additional=(OUT/'hi-additional.txt').read_text(encoding='utf-8').splitlines()
assert len(additional)==len(order['hi_additional']),(len(additional),len(order['hi_additional']))
tables['hi'].update(zip(order['hi_additional'],additional))
for locale,table in tables.items():(OUT/f'{locale}.json').write_text(json.dumps(table,ensure_ascii=False,indent=2),encoding='utf-8')
print('Authored drafts compiled; native-speaker review remains pending.')
