"""Explicitly authorized temporary LAT swap with an exact original-app rollback.

prepare and record-lat are read-only on the tablet. restore removes only the
Amazon-delivered APK recorded for this session, reinstalls the protected original
APK, and restores the verified private-file archive without launching the app.
This is not permanent migration into the Amazon build. No paid services are used.
"""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile
import zipfile
from datetime import datetime, timezone
from check_fire_recovery import checked, decrypt, PRIVATE, SDK, ROOT, LIMIT

PACKAGE = 'com.dishnest.app'
PLAN = PRIVATE / 'temporary-lat-plan.json'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def archive_files(path):
    files = {}
    total = 0
    with tarfile.open(fileobj=io.BytesIO(decrypt(path.read_bytes())), mode='r:gz') as archive:
        for entry in archive:
            parts = PurePosixPath(entry.name)
            if (not entry.isfile() or parts.is_absolute() or '..' in parts.parts or
                    not parts.parts or parts.parts[0] not in ('files', 'databases', 'shared_prefs', 'no_backup') or
                    not re.fullmatch(r'[A-Za-z0-9_./-]+', entry.name) or entry.name in files):
                raise ValueError('Unexpected private recovery entry')
            total += entry.size
            if entry.size < 0 or total > LIMIT or len(files) >= 10000:
                raise ValueError('Recovery archive exceeds limits')
            files[entry.name] = archive.extractfile(entry).read()
    if 'databases/dishnest.db' not in files:
        raise ValueError('Recovery database missing')
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'record-lat', 'restore'])
    args = parser.parse_args()
    # Fail closed on access-control inspection errors, including PS module failures.
    audit = r'''$ErrorActionPreference='Stop';
    $folder=Join-Path $env:LOCALAPPDATA 'DishNest\publisher-private\fire-recovery';
    $sidValue=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value;
    $aclValue=Get-Acl -LiteralPath $folder;
    $rulesValue=$aclValue.GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier]);
    [pscustomobject]@{protected=$aclValue.AreAccessRulesProtected;
    unexpected=@($rulesValue|Where-Object {$_.IdentityReference.Value -notin @($sidValue,'S-1-5-18')}).Count;
    denied=@($rulesValue|Where-Object AccessControlType -eq Deny).Count}|ConvertTo-Json -Compress'''
    env = {k: v for k, v in os.environ.items() if k.upper() != 'PSMODULEPATH'}
    acl = json.loads(checked(['powershell.exe', '-NoProfile', '-Command', audit], env=env))
    if not acl['protected'] or acl['unexpected'] or acl['denied']:
        raise ValueError('Protected rollback storage required')
    adb = str(SDK / 'platform-tools/adb.exe')
    devices = [line.split()[0] for line in checked([adb, 'devices']).decode().splitlines()
               if re.fullmatch(r'\S+\s+device\s*', line)]
    if len(devices) != 1:
        raise ValueError('Exactly one authorized Fire required')
    base = [adb, '-s', devices[0]]

    def run(*parts, data=None):
        return checked(base + list(parts), data=data, timeout=180)

    def installed():
        output = run('shell', 'pm', 'path', PACKAGE).decode().strip()
        if not output:
            return None
        if not output.startswith('package:') or '\n' in output:
            raise ValueError('Unexpected installed package path')
        return run('exec-out', 'cat', output[8:])

    def persistent():
        paths = run('exec-out', 'run-as', PACKAGE, 'find', '.', '-type', 'f').decode().splitlines()
        return {path[2:]: run('exec-out', 'run-as', PACKAGE, 'cat', path)
                for path in sorted(paths) if path.startswith(('./databases/', './files/', './shared_prefs/', './no_backup/'))}

    def permissions():
        text = run('shell', 'dumpsys', 'package', PACKAGE).decode()
        return dict(re.findall(r'(android\.permission\.[A-Z_]+): granted=(true|false)', text))

    def appops():
        modes = {}
        for operation in ['RECORD_AUDIO', 'POST_NOTIFICATION', 'RUN_IN_BACKGROUND', 'RUN_ANY_IN_BACKGROUND']:
            text = run('shell', 'cmd', 'appops', 'get', PACKAGE, operation).decode()
            match = re.search(r'\b' + operation + r':\s*(allow|ignore|deny|default|foreground|errored)\b', text)
            modes[operation] = match[1] if match else 'default'
        return modes

    model = run('shell', 'getprop', 'ro.product.model').decode().strip()
    if not model.startswith('KF'):
        raise ValueError('Expected Fire tablet')
    current = installed()
    original = PRIVATE / 'original-debug.apk'
    expected = json.loads((ROOT / 'artifacts/publisher-device-compatibility.json').read_text())['installedApkSha256']
    if digest(original.read_bytes()) != expected:
        raise ValueError('Original APK recovery identity mismatch')
    if args.action == 'prepare':
        if current is None or digest(current) != expected:
            raise ValueError('Original app must still be installed before preparing rollback')
        proof = json.loads((ROOT / 'build/fire-recovery-verification.json').read_text())
        if not all(proof.get(k) is True for k in ['encryptedBackupReadable', 'qaByteExactRestore',
                                                 'nativeSqliteIntegrity', 'ordinaryDataUnchanged']):
            raise ValueError('Current isolated recovery proof required')
        backup = max(PRIVATE.glob('dishnest-original-*.tar.gz.dpapi'), key=lambda p: p.stat().st_mtime)
        if proof.get('encryptedArchiveSha256') != digest(backup.read_bytes()):
            raise ValueError('Recovery proof does not bind the current encrypted archive')
        files = archive_files(backup)
        if files != persistent() or files != persistent():
            raise ValueError('Current original data differs from rollback archive')
        plan = dict(preparedAt=datetime.now(timezone.utc).isoformat(),
                    deviceBinding=digest(devices[0].encode()), originalApkSha256=expected,
                    backup=backup.name, backupSha256=digest(backup.read_bytes()),
                    runtimePermissions=permissions(), appops=appops(), latApkSha256=None)
        PLAN.write_text(json.dumps(plan, indent=2) + '\n')
        print(json.dumps(dict(rollbackPrepared=True, originalAppUnchanged=True,
                              persistentFiles=len(files), originalApkVerified=True)))
        return
    plan = json.loads(PLAN.read_text())
    if plan['deviceBinding'] != digest(devices[0].encode()) or plan['originalApkSha256'] != expected:
        raise ValueError('Rollback plan targets a different device or original APK')
    if Path(plan['backup']).name != plan['backup']:
        raise ValueError('Unexpected archive filename')
    backup = PRIVATE / plan['backup']
    if digest(backup.read_bytes()) != plan['backupSha256']:
        raise ValueError('Rollback archive changed')
    files = archive_files(backup)
    if args.action == 'record-lat':
        if current is None or digest(current) == expected:
            raise ValueError('No new Amazon APK is installed')
        installer = run('shell', 'pm', 'list', 'packages', '-i', PACKAGE).decode()
        if 'installer=com.amazon.venezia' not in installer:
            raise ValueError('Installed package was not delivered by Amazon Appstore')
        delivered = PRIVATE / 'amazon-delivered.apk'
        delivered.write_bytes(current)
        metadata = checked([str(SDK / 'build-tools/35.0.0/aapt.exe'), 'dump', 'badging', str(delivered)]).decode()
        if ("name='com.dishnest.app'" not in metadata.splitlines()[0] or
                "versionCode='2'" not in metadata.splitlines()[0] or 'application-debuggable' in metadata):
            raise ValueError('Amazon package identity/debug status mismatch')
        candidate = json.loads((ROOT / 'artifacts/lat-candidate.json').read_text())
        with zipfile.ZipFile(io.BytesIO(current)) as apk:
            dex = b''.join(apk.read(name) for name in apk.namelist() if name.endswith('.dex'))
        if candidate['endpoint'].encode() not in dex or candidate['publicKey'].encode() not in dex:
            raise ValueError('Amazon package lacks the reviewed LAT verifier binding')
        signature = checked([str(SDK / 'build-tools/35.0.0/apksigner.bat'), 'verify', '--print-certs', str(delivered)]).decode()
        cert = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', signature)
        if not cert:
            raise ValueError('Delivered APK signature could not be verified')
        plan['latApkSha256'] = digest(current)
        plan['latCertificateSha256'] = cert[1]
        PLAN.write_text(json.dumps(plan, indent=2) + '\n')
        report = dict(amazonDelivered=True, installer='com.amazon.venezia', apkSha256=digest(current),
                      certificateSha256=cert[1], debuggable=False, signatureVerified=True,
                      originalRecoveryPreserved=True, purchasesVerified=False)
        (ROOT / 'build/amazon-delivered-fire.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report))
        return
    # A partial prior rollback can resume with the original APK or no app present.
    # Never remove a package whose exact binary was not recorded for this session.
    if current is not None and digest(current) != expected:
        if not plan.get('latApkSha256') or digest(current) != plan['latApkSha256']:
            raise ValueError('Refusing to remove an unrecorded APK')
        result = run('uninstall', PACKAGE).decode()
        if 'Success' not in result:
            raise ValueError('Amazon test APK removal did not succeed')
        current = None
    if current is None:
        result = run('install', '--no-streaming', str(original)).decode()
        if 'Success' not in result:
            raise ValueError('Original APK reinstall failed; protected recovery remains intact')
    elif (persistent() == files and permissions() == plan['runtimePermissions'] and
          appops() == plan['appops']):
        print(json.dumps(dict(originalAlreadyRestored=True, originalDataExact=True)))
        return
    # An interrupted restore may resume, but a startup receiver or ordinary process
    # must never race with raw database writes. Stop only during explicit rollback.
    run('shell', 'am', 'force-stop', PACKAGE)
    for name, data in files.items():
        parent = str(PurePosixPath(name).parent)
        run('exec-out', 'run-as', PACKAGE, 'mkdir', '-p', parent)
        run('shell', '-T', 'run-as', PACKAGE, 'sh', '-c', "'base64 -d > " + name + "'",
            data=base64.encodebytes(data))
        if run('exec-out', 'run-as', PACKAGE, 'cat', name) != data:
            raise ValueError('Original private-file readback mismatch; do not launch the app')
    # Only restore settings observed before this explicitly authorized test.
    now_permissions = permissions()
    for permission, granted in plan['runtimePermissions'].items():
        if now_permissions.get(permission) != granted:
            run('shell', 'pm', 'grant' if granted == 'true' else 'revoke', PACKAGE, permission)
    for operation, mode in plan['appops'].items():
        run('shell', 'cmd', 'appops', 'set', PACKAGE, operation, mode)
    exact = persistent() == files
    report = dict(originalApkRestored=digest(installed()) == expected,
                  originalPrivateFilesExact=exact, persistentFiles=len(files),
                  runtimePermissionsRestored=permissions() == plan['runtimePermissions'],
                  appopsRestored=appops() == plan['appops'], originalAppLaunched=False,
                  encryptedRecoveryRetained=True, permanentAmazonMigration=False)
    (ROOT / 'build/fire-original-restored.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    if not all(report[key] for key in ['originalApkRestored', 'originalPrivateFilesExact',
                                       'runtimePermissionsRestored', 'appopsRestored']):
        raise ValueError('Rollback verification incomplete; do not launch the ordinary app')


if __name__ == '__main__':
    main()
