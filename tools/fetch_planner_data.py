"""Download pinned public factual upgrade tables; never execute downloaded code."""
import concurrent.futures, json, urllib.request
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
spec = json.loads((ROOT / 'tools/planner-sources.json').read_text())
out = ROOT / '.tools/planner-data'
out.mkdir(exist_ok=True)
def fetch(path):
    dest = out / path.replace('/', '__')
    if not dest.exists():
        url = f"https://raw.githubusercontent.com/chiefpansancolt/clash-of-clans-data/{spec['sha']}/{path}"
        with urllib.request.urlopen(url, timeout=45) as r: data = r.read(2_000_000)
        json.loads(data)
        dest.write_bytes(data)
    return path
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    results = list(pool.map(fetch, spec['paths']))
print(f'Downloaded and parsed {len(results)} pinned JSON files.')
