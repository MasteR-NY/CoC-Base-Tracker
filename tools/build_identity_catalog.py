"""Vendor pinned identity metadata and verified artwork; never execute upstream code."""
import argparse
import concurrent.futures
import hashlib
import io
import json
from pathlib import Path
import urllib.parse
import urllib.request
import threading
from collections import Counter
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.tools/identity-data'
ASSET_REV = 'fdac291ae7fbc2901221ff945834be534d0344ae'
DATA_REV = '33ca78371dda26e6ccda76c49fb7d07242eeb2f0'
TABLE_REV = '7afaa0375e5865c04021836a3a4192ffcfea092d'
ART_BASE = f'https://raw.githubusercontent.com/ClashKingInc/ClashKingAssets/{ASSET_REV}/assets/'
LOCKS = {}
LOCKS_GUARD = threading.Lock()


def download(url, path, expected=None):
    with LOCKS_GUARD:
        lock = LOCKS.setdefault(str(path), threading.Lock())
    with lock:
        return download_locked(url, path, expected)


def download_locked(url, path, expected=None):
    if not path.exists():
        with urllib.request.urlopen(url, timeout=40) as response:
            data = response.read(8_000_001)
        if len(data) > 8_000_000:
            raise ValueError('Oversized source: ' + url)
        if expected and hashlib.sha256(data).hexdigest() != expected:
            raise ValueError('Artwork hash mismatch: ' + url)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    data = path.read_bytes()
    if expected and hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('Cached hash mismatch: ' + str(path))
    return data


def clean(name):
    return name.lower().replace(' ', '_').replace('.', '').replace('?', '').replace('\\q', '').replace('’', '')


def main(fetch_art):
    CACHE.mkdir(parents=True, exist_ok=True)
    raw = download(f'https://raw.githubusercontent.com/ClashKingInc/clashy.py/{DATA_REV}/coc/static/static_data.json', CACHE / 'static_data.json')
    data = json.loads(raw)
    manifest_raw = download(ART_BASE + 'manifest.json', CACHE / 'manifest.json')
    manifest = json.loads(manifest_raw)
    files = {item['path']: item for group in manifest['assets'].values() for item in group}
    entries = {}
    for slug in ('hot-candle', 'hero-hunter', 'cake-a-pult'):
        path = f'data/home/crafted-defenses/{slug}.json'
        download(f'https://raw.githubusercontent.com/chiefpansancolt/clash-of-clans-data/{TABLE_REV}/' + path, ROOT / '.tools/planner-data' / path.replace('/', '__'))
    for section in ('helpers', 'skins', 'sceneries', 'decorations', 'obstacles', 'capital_house_parts'):
        for item in data[section]:
            item_id, name = item['_id'], item.get('name')
            if not name:
                continue
            row = {'name': name, 'kind': section}
            slug = clean(name)
            village = 'builder-base' if item.get('village') == 'builderBase' else 'home-village'
            if section == 'skins': candidates = [f'skins/{slug}/icon.webp']
            elif section == 'sceneries': candidates = [item.get('thumbnail', ''), f'sceneries/{slug}/thumbnail.webp', f'sceneries/{slug}/icon.webp']
            elif section == 'helpers': candidates = [f'helpers/{slug}.webp']
            elif section == 'capital_house_parts': candidates = [f'capital_house_parts/{item_id}.webp']
            else: candidates = [f'{section}/{village}/{slug}.webp']
            for path in candidates:
                if path in files:
                    row.update(icon=f'identity_{item_id}', artwork=path, artwork_sha256=files[path]['sha'], animated_source=bool(files[path].get('animated')))
                    break
            entries[str(item_id)] = row
    # Nested module IDs and labels are explicit in both pinned data sources.
    for building in data['buildings']:
        for defense in building.get('seasonal_defenses', []):
            parent = defense['_id']
            table_path = next((p for p in (ROOT / '.tools/planner-data').glob('data__home__crafted-defenses__*.json') if json.loads(p.read_text(encoding='utf-8')).get('dataId') == parent), None)
            if table_path is None:
                continue
            table = json.loads(table_path.read_text(encoding='utf-8'))
            image = table['images'][0]['normal']
            parent_row = {'name': defense['name'], 'kind': 'crafted-defense', 'icon': f'identity_{parent}', 'table_artwork': image}
            entries[str(parent)] = parent_row
            for module in defense.get('modules', []):
                match = next((m for m in table['modules'] if m['dataId'] == module['_id']), None)
                if match is None:
                    continue
                cap = max(level['level'] for level in module['levels'])
                assert cap == max(level['level'] for level in match['upgrades'])
                prefix = defense['name'] + ' '
                short_name = module['name'][len(prefix):] if module['name'].startswith(prefix) else module['name']
                entries[str(module['_id'])] = {'name': module['name'], 'short_name': short_name, 'kind': 'crafting-module', 'parent': parent, 'cap': cap, 'icon': f'identity_{parent}'}
    shared = Counter(row['artwork'] for row in entries.values() if 'artwork' in row)
    for row in entries.values():
        if row.get('artwork') and shared[row['artwork']] > 1:
            row['shared_artwork'] = True
    result = {'reviewed': '2026-11-08', 'metadata_revision': DATA_REV, 'artwork_revision': ASSET_REV, 'module_table_revision': TABLE_REV,
              'metadata_sha256': hashlib.sha256(raw).hexdigest(), 'manifest_sha256': hashlib.sha256(manifest_raw).hexdigest(), 'items': entries}
    (ROOT / 'app/src/main/assets/item-identities.json').write_text(json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    jobs = [(key, row) for key, row in entries.items() if 'artwork' in row or 'table_artwork' in row]
    print(json.dumps({'identities': len(entries), 'artwork_entries': len(jobs), 'module_names': [x['name'] for x in entries.values() if x['kind'] == 'crafting-module']}, indent=2), flush=True)
    if not fetch_art:
        return
    out = ROOT / 'app/src/main/res/drawable-nodpi'
    out.mkdir(parents=True, exist_ok=True)

    def art(job):
        key, row = job
        destination = out / (row['icon'] + '.webp')
        if 'artwork' in row:
            path = row['artwork']; source = download(ART_BASE + urllib.parse.quote(path, safe='/'), CACHE / 'artwork' / path, row['artwork_sha256'])
        else:
            path = row['table_artwork']; source = download(f'https://raw.githubusercontent.com/chiefpansancolt/clash-of-clans-data/{TABLE_REV}/' + path, CACHE / 'table-artwork' / path)
        with Image.open(io.BytesIO(source)) as image:
            if image.width * image.height > 20_000_000:
                raise ValueError('Oversized image')
            image = image.convert('RGBA')
            bounds = image.getbbox()
            if bounds is None:
                raise ValueError('Empty artwork: ' + path)
            image = image.crop(bounds); image.thumbnail((128, 128), Image.Resampling.LANCZOS)
            image.save(destination, 'WEBP', quality=85, method=6)
        return key

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        for count, _ in enumerate(pool.map(art, jobs), 1):
            if count % 100 == 0 or count == len(jobs):
                print(f'Verified and bundled {count}/{len(jobs)} artwork entries.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--fetch-art', action='store_true')
    main(parser.parse_args().fetch_art)
