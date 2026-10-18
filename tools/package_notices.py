"""Bundle original licence texts for the offline in-app notices viewer."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
directory=ROOT/'legal/third-party'
manifest=json.loads((directory/'manifest.json').read_text(encoding='utf-8'))
parts=['DishNest — Third-party software notices',
 'Kotlin, AndroidX, Compose, Material icons, Room, DataStore, Coroutines, Gson and Coil use the Apache License 2.0. The licence text is reproduced below. Source and resolved artifact details are recorded in the accompanying source distribution.',
 'Tesseract4Android: Copyright 2019 Adaptech s.r.o., Robert Pösel. Native components: Tesseract OCR 5.5.1, Leptonica 1.85.0, libjpeg 9f, libpng 1.6.48. English recognition data: tessdata_fast.',
 'This software is based in part on the work of the Independent JPEG Group.',
 'The DataStore preferences external Protocol Buffers component is BSD-3-Clause licensed. Its Google copyright and BSD terms are reproduced below. The upstream licence text reference is not a claim about the shaded protobuf binary version. OkHttp includes Public Suffix List data under Mozilla Public License 2.0; its original notice, source location and licence follow.',
 'Amazon Appstore SDK 3.0.9 is proprietary Program Materials, subject to https://developer.amazon.com/support/legal/pml . It is not relicensed by DishNest.']
for item in manifest['files']:
 data=(directory/item['file']).read_bytes()
 assert hashlib.sha256(data).hexdigest()==item['sha256']
 parts.append(item['file']+'\nSource: '+item['url']+'\n\n'+data.decode('utf-8'))
target=ROOT/'app/src/main/assets/third-party-notices.txt'
target.write_text('\n\n'.join(parts)+'\n',encoding='utf-8')
print('Offline in-app notices assembled from verified original files.')
