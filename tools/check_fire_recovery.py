"""Prove encrypted backup readability and byte-exact restore into isolated QA cache.

Never installs, stops, launches, clears, or uninstalls an app. Never writes the
ordinary app's storage. This does NOT authorize a signing-identity migration or
prove restoration into a non-debuggable Amazon APK. Private bytes stay in memory
on the PC and inside the disposable QA app's private cache on the Fire.
"""
import ctypes
import base64
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile
import uuid

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'
PRIVATE = Path(os.environ['LOCALAPPDATA']) / 'DishNest/publisher-private/fire-recovery'
QA = 'com.dishnest.app.qa'
ORIGINAL = 'com.dishnest.app'
LIMIT = 128 * 1024 * 1024


def checked(command, data=None, timeout=60, env=None):
    try:
        result = subprocess.run(command, input=data, capture_output=True, timeout=timeout, env=env)
    except (subprocess.SubprocessError, OSError):
        raise ValueError('Recovery subprocess failed; private command/output withheld') from None
    if result.returncode:
        raise ValueError('Recovery operation failed; private subprocess output withheld')
    return result.stdout


def decrypt(data):
    class Blob(ctypes.Structure):
        _fields_ = [('size', ctypes.c_ulong), ('data', ctypes.POINTER(ctypes.c_ubyte))]
    memory = (ctypes.c_ubyte * len(data)).from_buffer_copy(data)
    source, target = Blob(len(data), memory), Blob()
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    if not crypt.CryptUnprotectData(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(target)):
        raise ValueError('Backup cannot be decrypted by this Windows account')
    try:
        if target.size > LIMIT:
            raise ValueError('Backup exceeds recovery limit')
        return ctypes.string_at(target.data, target.size)
    finally:
        kernel.LocalFree(target.data)


def main():
    # The C: folder must have protected ACLs and only this user/SYSTEM access.
    audit = r'''$ErrorActionPreference='Stop';
    $folder=Join-Path $env:LOCALAPPDATA 'DishNest\publisher-private\fire-recovery';
    $sidValue=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value;
    $aclValue=Get-Acl -LiteralPath $folder;
    $rulesValue=$aclValue.GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier]);
    [pscustomobject]@{protected=$aclValue.AreAccessRulesProtected;
    unexpected=@($rulesValue|Where-Object {$_.IdentityReference.Value -notin @($sidValue,'S-1-5-18')}).Count;
    denied=@($rulesValue|Where-Object AccessControlType -eq Deny).Count}|ConvertTo-Json -Compress'''
    # Windows PowerShell cannot load PowerShell Core's inherited security module.
    # Let it discover its own system modules; do not weaken or skip the ACL check.
    audit_env = {key: value for key, value in os.environ.items() if key.upper() != 'PSMODULEPATH'}
    acl = json.loads(checked(['powershell.exe', '-NoProfile', '-Command', audit], env=audit_env))
    if not acl['protected'] or acl['unexpected'] or acl['denied']:
        raise ValueError('Protected recovery folder required')
    backup = max(PRIVATE.glob('dishnest-original-*.tar.gz.dpapi'), key=lambda p: p.stat().st_mtime)
    if backup.stat().st_size > LIMIT:
        raise ValueError('Encrypted backup exceeds recovery limit')
    files = {}
    with tarfile.open(fileobj=io.BytesIO(decrypt(backup.read_bytes())), mode='r:gz') as archive:
        total = 0
        for entry in archive:
            path = PurePosixPath(entry.name)
            if (not entry.isfile() or path.is_absolute() or '..' in path.parts or
                    not path.parts or path.parts[0] not in ('databases', 'shared_prefs', 'files', 'no_backup') or
                    not re.fullmatch(r'[A-Za-z0-9_./-]+', entry.name) or entry.name in files):
                raise ValueError('Unexpected recovery entry')
            total += entry.size
            if entry.size < 0 or total > LIMIT or len(files) >= 10000:
                raise ValueError('Recovery limits exceeded')
            files[entry.name] = archive.extractfile(entry).read()
    if 'databases/dishnest.db' not in files:
        raise ValueError('Kitchen database missing')
    adb = str(SDK / 'platform-tools/adb.exe')
    devices = [line.split()[0] for line in checked([adb, 'devices']).decode().splitlines()
               if re.fullmatch(r'\S+\s+device\s*', line)]
    if len(devices) != 1:
        raise ValueError('Exactly one authorized Fire required')
    base = [adb, '-s', devices[0]]

    def run(*args, data=None):
        return checked(base + list(args), data=data)

    if not run('shell', 'getprop', 'ro.product.model').decode().strip().startswith('KF'):
        raise ValueError('Expected Fire tablet')

    def ordinary():
        paths = run('exec-out', 'run-as', ORIGINAL, 'find', '.', '-type', 'f').decode().splitlines()
        return {path[2:]: run('exec-out', 'run-as', ORIGINAL, 'cat', path)
                for path in sorted(paths) if path.startswith(('./databases/', './shared_prefs/', './files/', './no_backup/'))}

    before = ordinary()
    if before != files:
        raise ValueError('Encrypted backup no longer matches current app data; take a fresh backup first')
    output = ROOT / 'build/recovery-probe'
    output.mkdir(parents=True, exist_ok=True)
    java = Path(r'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin')
    checked([str(java / 'javac.exe'), '-source', '8', '-target', '8', '-cp',
             str(SDK / 'platforms/android-35/android.jar'), '-d', str(output),
             str(ROOT / 'tools/recovery/RecoveryProbe.java')])
    env = os.environ.copy()
    env['JAVA_HOME'] = str(java.parent)
    result = subprocess.run([str(SDK / 'build-tools/35.0.0/d8.bat'), '--min-api', '26',
                             '--lib', str(SDK / 'platforms/android-35/android.jar'),
                             '--output', str(output), str(output / 'RecoveryProbe.class')],
                            env=env, capture_output=True)
    if result.returncode:
        raise ValueError('Recovery probe compilation failed')
    folder = 'cache/recovery-' + uuid.uuid4().hex
    # The generated folder is a private QA cache subtree, never ordinary storage.
    run('exec-out', 'run-as', QA, 'mkdir', '-p', folder)
    qa_root = run('exec-out', 'run-as', QA, 'pwd').decode().strip()
    if qa_root not in ('/data/user/0/' + QA, '/data/data/' + QA):
        raise ValueError('Unexpected QA sandbox root')
    for name, data in files.items():
        destination = folder + '/' + name
        run('exec-out', 'run-as', QA, 'mkdir', '-p', str(PurePosixPath(destination).parent))
        run('shell', '-T', 'run-as', QA, 'sh', '-c', "'base64 -d > " + destination + "'",
            data=base64.encodebytes(data))
        restored = run('exec-out', 'run-as', QA, 'cat', destination)
        if restored != data:
            raise ValueError(f'Recovery byte verification failed (expected {len(data)}, received {len(restored)})')
    dex = (output / 'classes.dex').read_bytes()
    run('shell', '-T', 'run-as', QA, 'sh', '-c', "'base64 -d > " + folder + "/probe.dex'",
        data=base64.encodebytes(dex))
    result = run('exec-out', 'run-as', QA, 'env',
                 'CLASSPATH=' + qa_root + '/' + folder + '/probe.dex',
                 'app_process', '/system/bin', 'RecoveryProbe', qa_root + '/' + folder).decode()
    if not re.search(r'RECOVERY_SQLITE_OK version=[12]', result):
        raise ValueError('Native restored database integrity check did not pass')
    unchanged = ordinary() == before
    report = dict(encryptedBackupReadable=True, persistentFiles=len(files),
                  encryptedArchiveSha256=hashlib.sha256(backup.read_bytes()).hexdigest(),
                  qaByteExactRestore=True, nativeSqliteIntegrity=True,
                  ordinaryDataUnchanged=unchanged, ordinaryAppModified=False,
                  amazonApkInstalled=False, amazonRestoreProven=False,
                  androidManagedStateRestored=False,
                  privateRecoveryCopyRetainedInQaCache=True)
    (ROOT / 'build/fire-recovery-verification.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    if not unchanged:
        raise ValueError('Ordinary app changed during recovery test')


if __name__ == '__main__':
    main()
