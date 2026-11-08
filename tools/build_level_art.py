"""Bundle level-specific game art from pinned public sources; execute no upstream code."""
import concurrent.futures
import hashlib
import io
import json
from pathlib import Path
import re
import threading
import urllib.error
import urllib.parse
import urllib.request
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.tools/level-art'
OUT = ROOT / 'app/src/main/res/drawable-nodpi'
TABLE = json.loads((ROOT / 'tools/planner-sources.json').read_text(encoding='utf-8'))
ART_REV = 'fdac291ae7fbc2901221ff945834be534d0344ae'
ART_BASE = f'https://raw.githubusercontent.com/ClashKingInc/ClashKingAssets/{ART_REV}/assets/'
TABLE_BASE = f"https://raw.githubusercontent.com/chiefpansancolt/clash-of-clans-data/{TABLE['sha']}/"
CACHE.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)
manifest = json.loads((ROOT / '.tools/identity-data/manifest.json').read_text(encoding='utf-8'))
files = {row['path']: row for group in manifest['assets'].values() for row in group}
jobs = []
for path in TABLE['paths']:
    item = json.loads((ROOT / '.tools/planner-data' / path.replace('/', '__')).read_text(encoding='utf-8'))
    item_id = item.get('dataId')
    if not item_id:
        continue
    slug = re.sub(r'[^a-z0-9_\x27-]', '', item['name'].lower().replace(' ', '_'))
    village = 'builder-base' if item['base'] == 'builder' else 'home-village'
    for level in item.get('levels', []):
        if level.get('supercharge'):
            continue
        number = level.get('level')
        candidate = f'buildings/{village}/{slug}/level_{number}.webp'
        normal = level.get('images', {}).get('normal')
        if candidate in files:
            jobs.append((str(item_id), str(number), ART_BASE + urllib.parse.quote(candidate, safe='/'), files[candidate]['sha']))
        elif normal:
            jobs.append((str(item_id), str(number), TABLE_BASE + normal, None))
currencies = {name: name.lower().replace(' ', '_') for name in ('Gold', 'Elixir', 'Dark Elixir', 'Builder Gold', 'Builder Elixir', 'Shiny Ore', 'Glowy Ore', 'Starry Ore', 'Gems')}
for name, slug in currencies.items():
    path = f'resources/{slug}.webp'
    assert path in files
    jobs.append(('currency', slug, ART_BASE + path, files[path]['sha']))
print(json.dumps({'source_level_entries': len(jobs) - len(currencies), 'resource_icons': len(currencies)}), flush=True)
lock = threading.Lock()
outputs = {}
missing = []


def bundle(job):
    item, level, url, expected = job
    cache = CACHE / (hashlib.sha256(url.encode()).hexdigest() + '.image')
    if cache.exists():
        source = cache.read_bytes()
    else:
        try:
            with urllib.request.urlopen(url, timeout=35) as response:
                source = response.read(3_000_001)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            return item, level, None, url, None
        if len(source) > 3_000_000:
            raise ValueError('Oversized artwork: ' + url)
        cache.write_bytes(source)
    sha = hashlib.sha256(source).hexdigest()
    if expected and sha != expected:
        raise ValueError('Artwork source hash mismatch: ' + url)
    with Image.open(io.BytesIO(source)) as image:
        if image.width * image.height > 20_000_000:
            raise ValueError('Oversized image dimensions')
        image = image.convert('RGBA')
        bounds = image.getbbox()
        if bounds is None:
            return item, level, None, url, sha
        image = image.crop(bounds)
        image.thumbnail((128, 128), Image.Resampling.LANCZOS)
        encoded = io.BytesIO()
        image.save(encoded, 'WEBP', quality=85, method=6)
    data = encoded.getvalue()
    resource = 'currency_' + level if item == 'currency' else 'level_art_' + hashlib.sha256(data).hexdigest()[:20]
    with lock:
        if resource not in outputs:
            (OUT / (resource + '.webp')).write_bytes(data)
            outputs[resource] = hashlib.sha256(data).hexdigest()
    return item, level, resource, url, sha


levels = {}
sources = {}
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    for index, (item, level, resource, url, sha) in enumerate(pool.map(bundle, jobs), 1):
        if resource:
            levels.setdefault(item, {})[level] = resource
            sources[f'{item}:{level}'] = {'url': url, 'sha256': sha}
        else:
            missing.append(url)
        if index % 100 == 0:
            print(f'Bundled {index}/{len(jobs)} source entries', flush=True)
result = {'reviewed': '2026-11-08', 'table_revision': TABLE['sha'], 'artwork_revision': ART_REV, 'items': levels}
(ROOT / 'app/src/main/assets/level-art.json').write_text(json.dumps(result, separators=(',', ':')) + '\n', encoding='utf-8')
(ROOT / 'docs/level-art-sources.json').write_text(json.dumps({'sources': sources, 'resources': outputs, 'missing': missing}, separators=(',', ':')) + '\n', encoding='utf-8')
print(json.dumps({'mapped_items': len(levels) - 1, 'mapped_levels': sum(len(v) for k, v in levels.items() if k != 'currency'), 'unique_artwork': len(outputs), 'unavailable_source_images': len(missing)}), flush=True)
