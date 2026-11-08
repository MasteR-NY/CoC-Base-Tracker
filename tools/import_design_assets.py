"""Import the previously inspected item art/font, preserving source names and notices."""
from pathlib import Path
import hashlib
import json
import zipfile

root = Path(__file__).resolve().parents[1]
apk = root / 'apk-review/ClashUpgrades.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == '57cb1c74ea3e1977ec5a7dbcf6eba30a366cfd1cd721f61f20d1eace69849172'
report = json.loads((root / 'apk-review/inspection.json').read_text(encoding='utf-8'))
count = 0
with zipfile.ZipFile(apk) as z:
    for item in report['bundled_asset_comparison']['files']:
        name = item['source_path']
        if name.endswith(('.webp', '.ttf')):
            assert name.startswith('app/src/main/res/') and item['matches_source']
            out = root / name
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(z.read(item['apk_path']))
            count += 1
print(f'Imported {count} verified icons/font resources from the reviewed build.')
