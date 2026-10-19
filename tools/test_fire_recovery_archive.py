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


if __name__ == '__main__':
    unittest.main()
