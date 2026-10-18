"""Fetch exact upstream notices from the OCR dependency's pinned source tag."""
import concurrent.futures,hashlib,json,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='https://raw.githubusercontent.com/adaptech-cz/Tesseract4Android/4.9.0/'
SOURCES={
 'Tesseract4Android-4.9.0-LICENSE.txt':BASE+'LICENSE',
 'Tesseract-5.5.1-LICENSE.txt':BASE+'tesseract4android/src/main/cpp/tesseract/src/LICENSE',
 'Leptonica-1.85.0-LICENSE.txt':BASE+'tesseract4android/src/main/cpp/leptonica/src/leptonica-license.txt',
 'libpng-1.6.48-LICENSE.txt':BASE+'tesseract4android/src/main/cpp/libpng/src/LICENSE',
 'libjpeg-9f-README.txt':BASE+'tesseract4android/src/main/cpp/libjpeg/src/README',
 'Jsoup-1.18.3-LICENSE.txt':'https://raw.githubusercontent.com/jhy/jsoup/jsoup-1.18.3/LICENSE',
 'tessdata_fast-LICENSE.txt':'https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/LICENSE',
 'Protocol-Buffers-BSD-3-Clause.txt':'https://raw.githubusercontent.com/protocolbuffers/protobuf/v3.25.3/LICENSE',
 'OkHttp-4.12.0-publicsuffix-NOTICE.txt':'https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/resources/okhttp3/internal/publicsuffix/NOTICE',
 'Mozilla-Public-License-2.0.txt':'https://www.mozilla.org/MPL/2.0/index.txt',
}
OUT=ROOT/'legal/third-party';OUT.mkdir(parents=True,exist_ok=True)
def collect(item):
 name,url=item
 with urllib.request.urlopen(url,timeout=30) as response:data=response.read()
 assert len(data)>100
 (OUT/name).write_bytes(data)
 return {'file':name,'url':url,'sha256':hashlib.sha256(data).hexdigest()}
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:records=list(pool.map(collect,SOURCES.items()))
(OUT/'manifest.json').write_text(json.dumps({'source_tag':'Tesseract4Android 4.9.0','files':records,'legal_approval':False},indent=2),encoding='utf-8')
print(f'Collected {len(records)} original licence/notice files.')
