"""Merge locally authored submission-readiness copy without network requests."""
import json
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
copy = json.loads((ROOT / 'localisation/authored/readiness.json').read_text(encoding='utf-8'))
mapping = {'values': 'en', 'values-en-rGB': 'en', 'values-en-rUS': 'en',
           'values-pt-rBR': 'pt-BR', 'values-pt-rPT': 'pt-PT'}
for path in (ROOT / 'app/src/main/res').glob('values*/strings.xml'):
    locale = mapping.get(path.parent.name, path.parent.name.removeprefix('values-'))
    tree = ET.parse(path)
    root = tree.getroot()
    for key, translations in copy.items():
        node = next((n for n in root.findall('string') if n.get('name') == key), None)
        if node is None:
            node = ET.SubElement(root, 'string', {'name': key})
        node.text = translations[locale].replace("'", "\\'")
    ET.indent(tree, space='    ')
    tree.write(path, encoding='utf-8', xml_declaration=True)
print(f'Merged {len(copy)} authored strings into all 12 locales and default resources.')
