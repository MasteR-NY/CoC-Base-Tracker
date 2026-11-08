"""Bundle Supercell's Goblin Builder portrait as a compact hired-worker head icon."""
from pathlib import Path
import hashlib, json, urllib.request
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
URL = 'https://sc-clashofclans.zendesk.com/hc/article_attachments/49530538892315'
SHA256 = 'bd395df21abbf2b2b0c1a19d091d2d98066a0edac61434dc076fd5a690cd15b5'
cache = ROOT / '.tools/goblin-icons/official-goblin-builder.png'
cache.parent.mkdir(parents=True, exist_ok=True)
if not cache.exists():
    with urllib.request.urlopen(URL, timeout=30) as response:
        data = response.read(2_000_001)
    assert len(data) <= 2_000_000
    cache.write_bytes(data)
assert hashlib.sha256(cache.read_bytes()).hexdigest() == SHA256
target = ROOT / 'app/src/main/res/drawable-nodpi/hired_goblin_head.webp'
with Image.open(cache) as original:
    assert original.size == (250, 375)
    image = original.convert('RGBA').crop((50, 29, 223, 154))
    image.thumbnail((96, 96), Image.Resampling.LANCZOS)
    image.save(target, 'WEBP', quality=90, method=6)
(ROOT / 'docs/hired-worker-art.json').write_text(json.dumps({
    'source_page': 'https://sc-clashofclans.zendesk.com/hc/en-us/articles/49529091450779-Work-for-Hire-Event',
    'url': URL, 'sha256': SHA256, 'crop': [50, 29, 223, 154],
    'output_sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
    'credit': 'Supercell; head cropped from its official Goblin Builder portrait; not a recreated or invented face'
}, indent=2) + '\n', encoding='utf-8')
print('Bundled official hired-worker head icon.')
