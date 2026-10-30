"""Malformed recovery archives must fail before any device mutation."""
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import fire_temporary_lat as recovery


class RecoveryArchiveTests(unittest.TestCase):
    def read(self, entries, limit=None):
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode='w:gz') as archive:
            for name, data, kind in entries:
                item = tarfile.TarInfo(name)
                item.type = kind
                item.size = len(data) if kind == tarfile.REGTYPE else 0
                if kind == tarfile.SYMTYPE:
                    item.linkname = '/data/user/0/com.dishnest.app/databases/dishnest.db'
                archive.addfile(item, io.BytesIO(data))
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'fixture.dpapi'
            path.write_bytes(buffer.getvalue())
            with patch.object(recovery, 'decrypt', side_effect=lambda value: value), \
                    patch.object(recovery, 'LIMIT', limit or recovery.LIMIT):
                return recovery.archive_files(path)

    def test_preserves_binary_database_wal_and_preferences(self):
        entries = [('databases/dishnest.db', bytes(range(256)), tarfile.REGTYPE),
                   ('databases/dishnest.db-wal', b'\x00\r\n\x04\xff', tarfile.REGTYPE),
                   ('shared_prefs/verified_billing.xml', b'<map/>', tarfile.REGTYPE)]
        self.assertEqual({name: data for name, data, _ in entries}, self.read(entries))

    def test_rejects_paths_outside_persistent_directories(self):
        for name in ['../databases/dishnest.db', '/databases/dishnest.db',
                     'databases/../../escape', 'cache/payload', 'files/a;command']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.read([(name, b'data', tarfile.REGTYPE)])

    def test_rejects_symlinks_and_duplicate_database(self):
        for entries in [[('databases/dishnest.db', b'', tarfile.SYMTYPE)],
                        [('databases/dishnest.db', b'first', tarfile.REGTYPE),
                         ('databases/dishnest.db', b'second', tarfile.REGTYPE)]]:
            with self.assertRaises(ValueError):
                self.read(entries)

    def test_rejects_total_size_limit_and_missing_database(self):
        with self.assertRaises(ValueError):
            self.read([('databases/dishnest.db', b'12345', tarfile.REGTYPE)], limit=4)
        with self.assertRaises(ValueError):
            self.read([('files/only', b'data', tarfile.REGTYPE)])


class ProfileRecoveryTests(unittest.TestCase):
    def runner(self, apk=None, current='0'):
        calls = []
        def run(*args):
            calls.append(args)
            if args == ('shell', 'am', 'get-current-user'):
                return current.encode()
            if args == ('shell', 'pm', 'list', 'users'):
                return b'UserInfo{0:Owner:13}\nUserInfo{10:Other:8010}'
            if args[:4] == ('shell', 'pm', 'list', 'packages'):
                return b'package:com.dishnest.app\n' if args[5] == '10' and apk else b''
            if args[:3] == ('shell', 'pm', 'path'):
                return b'package:/data/app/known/base.apk'
            if args == ('exec-out', 'cat', '/data/app/known/base.apk'):
                return apk
            if args[:4] == ('shell', 'cmd', 'package', 'install-existing'):
                return b'Package com.dishnest.app installed for user: 0'
            if args[0] == 'install':
                return b'Success'
            raise AssertionError('Unexpected device command')
        return run, calls

    def test_other_profile_exact_original_is_enabled_only_for_owner(self):
        run, calls = self.runner(b'original')
        recovery.restore_missing_install(run, Path('original.apk'), recovery.digest(b'original'))
        self.assertIn(('shell', 'cmd', 'package', 'install-existing', '--user', '0', recovery.PACKAGE), calls)
        self.assertFalse(any(c[0] in ('install', 'uninstall') for c in calls))

    def test_other_profile_unknown_binary_fails_without_mutation(self):
        run, calls = self.runner(b'different')
        with self.assertRaises(ValueError):
            recovery.restore_missing_install(run, Path('original.apk'), recovery.digest(b'original'))
        self.assertFalse(any(c[0] == 'install' or 'install-existing' in c for c in calls))

    def test_missing_global_package_installs_only_for_owner(self):
        run, calls = self.runner()
        recovery.restore_missing_install(run, Path('original.apk'), recovery.digest(b'original'))
        self.assertIn(('install', '--user', '0', '--no-streaming', 'original.apk'), calls)

    def test_other_active_profile_is_refused_before_inventory_or_mutation(self):
        run, calls = self.runner(current='10')
        with self.assertRaises(ValueError):
            recovery.restore_missing_install(run, Path('original.apk'), recovery.digest(b'original'))
        self.assertEqual([('shell', 'am', 'get-current-user')], calls)


if __name__ == '__main__':
    unittest.main()
