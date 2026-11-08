"""Compile a small offline catalog from pinned JSON, retaining source requirements.
Run fetch_planner_data.py first. No gameplay code or images are downloaded here.
"""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
spec = json.loads((ROOT / 'tools/planner-sources.json').read_text())
items = []
for path in spec['paths']:
    data = json.loads((ROOT / '.tools/planner-data' / path.replace('/', '__')).read_text())
    if not data.get('dataId') or not data.get('levels') or data.get('category') == 'other' and data['dataId'] >= 93000000:
        continue
    keys = ['id', 'dataId', 'name', 'base', 'category', 'hero', 'rarity', 'unlockRequirement',
            'availablePerTownHall', 'availablePerBuilderHall', 'troopType', 'spellType']
    item = {k: data[k] for k in keys if k in data}
    item['requirements'] = {k: v for k, v in data.items() if 'Required' in k}
    item['levels'] = [{k: v for k, v in level.items() if k == 'level' or k == 'supercharge' or 'Required' in k
                       or k.endswith(('Cost', 'CostResource', 'Time', 'Ore')) and k.startswith(('build', 'upgrade', 'research'))}
                      for level in data['levels']]
    item['source'] = path
    items.append(item)
result = {'source': 'chiefpansancolt/clash-of-clans-data', 'revision': spec['sha'], 'checked': '2026-11-08', 'items': items}
target = ROOT / 'app/src/main/assets/planner-catalog.json'
target.write_text(json.dumps(result, separators=(',', ':'), ensure_ascii=False), encoding='utf-8')
print(f'{len(items)} catalog items, {target.stat().st_size} bytes')
