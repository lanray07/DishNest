"""Validate the supplemental publisher signature without changing build provenance."""
import hashlib,json,os,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def verify():
    report_path=ROOT/'artifacts/publisher-signing.json'
    if not report_path.exists():return None
    report=json.loads(report_path.read_text(encoding='utf-8-sig'))
    unsigned=ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk'
    signed=ROOT/'app/build/outputs/apk/release/app-release-publisher.apk'
    for path,key in [(unsigned,'unsignedSha256'),(signed,'signedSha256'),(ROOT/'tools/sign_publisher_release.ps1','scriptSha256')]:
        if not path.is_file() or digest(path)!=report.get(key):raise ValueError(f'Stale publisher signing evidence: {path.name}')
    tools=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/build-tools/35.0.0'
    signature=subprocess.run([str(tools/'apksigner.bat'),'verify','--verbose','--print-certs',str(signed)],capture_output=True,text=True,check=True).stdout
    if f"certificate SHA-256 digest: {report['certificateSha256']}" not in signature:raise ValueError('Publisher certificate mismatch')
    subprocess.run([str(tools/'zipalign.exe'),'-c','-P','16','4',str(signed)],capture_output=True,check=True)
    with zipfile.ZipFile(unsigned) as source,zipfile.ZipFile(signed) as target:
        for name in source.namelist():
            if source.read(name)!=target.read(name):raise ValueError(f'Publisher APK payload changed: {name}')
        for name in set(target.namelist())-set(source.namelist()):
            if not name.startswith('META-INF/') or name.rsplit('.',1)[-1] not in {'SF','RSA','DSA','EC','MF'}:raise ValueError('Unexpected signed APK payload')
        if target.testzip():raise ValueError('Signed APK CRC failed')
    return report
if __name__=='__main__':
    result=verify()
    if result is None:raise SystemExit('Publisher signature evidence missing')
    print(json.dumps({'publisher_signature_current':True,'certificateSha256':result['certificateSha256'],'hardware_release_approved':False}))
