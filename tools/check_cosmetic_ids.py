"""Read four IDs from pinned official tables. No game/extractor code is executed."""
import csv
import hashlib
import io
import json
import lzma
from pathlib import Path
import struct
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
REVIEW = json.loads((ROOT / 'tools/identity-overrides.json').read_text(encoding='utf-8'))
SOURCE = REVIEW['sources']
CACHE = ROOT / '.tools/official-cosmetic-check'
CACHE.mkdir(parents=True, exist_ok=True)


def fetch(path):
    target = CACHE / path.replace('/', '-')
    if not target.exists():
        with urllib.request.urlopen(SOURCE['official_base'] + path, timeout=30) as response:
            data = response.read(4 * 1024 * 1024 + 1)
        if len(data) > 4 * 1024 * 1024:
            raise ValueError('Oversized source table')
        target.write_bytes(data)
    data = target.read_bytes()
    assert hashlib.sha256(data).hexdigest() == SOURCE['files'][path]['sha256'], path
    return data


def csv_rows(path):
    data = fetch(path)
    if data.startswith(b'Sig:'):
        data = data[68:]  # Signed container; source bytes were hash-checked above.
    decoder = lzma.LZMADecompressor()
    data = decoder.decompress(data[:9] + b'\0' * 4 + data[9:], max_length=8 * 1024 * 1024)
    assert decoder.eof, 'Incomplete/oversized table'
    return list(csv.DictReader(io.StringIO(data.decode('utf-8'))))[1:]


class Reader:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, length):
        if length < 0 or self.offset + length > len(self.data):
            raise ValueError('Invalid table bounds')
        value = self.data[self.offset:self.offset + length]
        self.offset += length
        return value

    def u32(self):
        return struct.unpack('<I', self.take(4))[0]

    def blob(self):
        return self.take(self.u32())


translations = {row['TID']: row['EN'] for row in csv_rows('localization/texts.csv')}
skins = [row for row in csv_rows('logic/skins.csv') if row['Name']]
result = {}
for skin_id in (52000268, 52000337):
    row = skins[skin_id - 52000000]
    result[str(skin_id)] = {key: row.get(key, '') for key in ('Name', 'Character', 'TID', 'Icon')}
    result[str(skin_id)]['verified_display_name'] = translations.get(row['TID'])

reader = Reader(fetch('logic/decos_logic.scindex'))
assert reader.u32() == 1
reader.take(16)
declared, count = reader.u32(), reader.u32()
assert count < 100000
values = [reader.take(16) for _ in range(count)]
for _ in range(count):
    flag = reader.take(1)[0]
    assert flag in (0, 1)
    if flag:
        reader.blob()
rows_read = 0
for _ in range(reader.u32()):
    table, count = reader.blob().decode('utf-8'), reader.u32()
    for _ in range(count):
        key = reader.u32()
        reader.take(8)
        row = Reader(reader.blob())
        global_id, marker = struct.unpack('<QQ', values[key])
        rows_read += 1
        if str(global_id) not in REVIEW['items']:
            continue
        assert table == 'logic-deco-data' and marker == 1 << 63
        assert not any(row.take(row.take(1)[0]))
        names = Reader(row.blob())
        internal, tid = names.blob().decode('utf-8'), names.blob().decode('utf-8')
        name = translations[tid]
        assert name == REVIEW['items'][str(global_id)]['name']
        assert tid == REVIEW['items'][str(global_id)]['official_tid']
        result[str(global_id)] = {'internal_name': internal, 'TID': tid, 'verified_display_name': name}
assert rows_read == declared and reader.offset == len(reader.data)
assert all(str(item) in result for item in (18000244, 18000248))
print(json.dumps({'game_version': SOURCE['game_version'], 'fingerprint': SOURCE['fingerprint'], 'items': result}, indent=2))
