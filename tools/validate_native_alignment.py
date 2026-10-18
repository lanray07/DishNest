"""Inspect ELF LOAD alignment in an APK; independent of ZIP entry alignment.

64-bit libraries need 16 KiB LOAD alignment for Android's 16 KiB page devices.
This is a binary check, not evidence of a device launch or OCR accuracy.
"""
import argparse,hashlib,json,struct,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('apk',nargs='?',default='app/build/outputs/apk/release/app-release-unsigned.apk');args=parser.parse_args()
records=[]
with zipfile.ZipFile(ROOT/args.apk) as archive:
    for name in archive.namelist():
        if not name.startswith('lib/') or not name.endswith('.so'):continue
        data=archive.read(name)
        assert data[:4]==b'\x7fELF',name
        is64=data[4]==2;endian='<' if data[5]==1 else '>'
        offset=struct.unpack_from(endian+('Q' if is64 else 'I'),data,32 if is64 else 28)[0]
        size,count=struct.unpack_from(endian+'HH',data,54 if is64 else 42)
        alignment=[]
        for i in range(count):
            header=offset+i*size
            if struct.unpack_from(endian+'I',data,header)[0]==1:
                alignment.append(struct.unpack_from(endian+('Q' if is64 else 'I'),data,header+(48 if is64 else 28))[0])
        passed=bool(alignment) and (not is64 or all(value>=16384 for value in alignment))
        records.append({'path':name,'bits':64 if is64 else 32,'load_alignments':alignment,'supports_16k_load_alignment':passed})
result={'apk':args.apk,'apk_sha256':hashlib.sha256((ROOT/args.apk).read_bytes()).hexdigest(),'libraries':records,'passed':bool(records) and all(r['supports_16k_load_alignment'] for r in records),'device_tested':False}
(ROOT/'artifacts/native-alignment.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps({'libraries':len(records),'passed':result['passed'],'device_tested':False}))
if not result['passed']:raise SystemExit(1)
