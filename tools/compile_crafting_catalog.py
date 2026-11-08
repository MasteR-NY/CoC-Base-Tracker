"""Review pinned official module tables against the pinned community ID catalog.

Only data is fetched. Android builds use the generated offline asset, never this
network step. A changed checksum or baseline value requires a deliberate review.
"""
import csv
import hashlib
import io
import json
import lzma
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = 'https://game-assets.clashofclans.com/443f20eb3aa749c454b1da26259eac44f145a40b/'
SOURCES = {
    'seasonal_defense_modules': '6d7b2b0b60f1e250dda6e110d3390fa5379069d807bfa01df237a45ce189fd9e',
    'seasonal_defense_archetypes': '875c805702710b513206599e8082a3a3948c2ecaf6b526e129e6472420adbc9b',
    'seasonal_defense': '785ef77326a772816a40edf7d82fbe9c53ee72a12f25fa782eab651d62393a48',
    'buildings': '8844122102cea914beaf178a6383cba34b7b3912453303b459205e6fadb86b37',
}
DEFENSES = [
    ('hot-candle', 103000011, 'Inferno Candle', 'InfernoCandle', 102000033),
    ('hero-hunter', 103000012, 'Headhunter Tower', 'HeadhunterTower', 102000036),
    ('cake-a-pult', 103000013, 'Cake Thrower', 'CakeThrower', 102000039),
]


def table(name):
    folder = ROOT / '.tools/crafting-review'
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f'logic-{name}.csv'
    if not path.exists():
        with urllib.request.urlopen(BASE + f'logic/{name}.csv', timeout=30) as response:
            payload = response.read(8 * 1024 * 1024 + 1)
        assert len(payload) <= 8 * 1024 * 1024, 'Oversized official table'
        path.write_bytes(payload)
    payload = path.read_bytes()
    assert hashlib.sha256(payload).hexdigest() == SOURCES[name], f'Review changed {name} table'
    assert payload.startswith(b'Sig:'), 'Unexpected Supercell file format'
    payload = payload[68:]
    decoder = lzma.LZMADecompressor()
    plain = decoder.decompress(payload[:9] + bytes(4) + payload[9:], max_length=8 * 1024 * 1024)
    assert decoder.eof, 'Truncated or oversized official CSV'
    groups = {}
    for row in list(csv.DictReader(io.StringIO(plain.decode('utf-8'))))[1:]:
        if row['Name']:
            current = row['Name']
            assert current not in groups, 'Duplicate official record'
            groups[current] = []
        groups[current].append(row)
    return groups


def compile_modules():
    modules, archetypes, phases, buildings = [table(name) for name in SOURCES]
    assert [r['Archetypes'] for r in phases['Season4']] == [d[2] for d in DEFENSES]
    station = buildings['Crafting Station'][0]
    assert int(station['GlobalID']) == 1000097 and int(station['BuildingLevel']) == 1
    result = []
    for slug, parent_id, archetype, prefix, first_id in DEFENSES:
        source = f'data/home/crafted-defenses/{slug}.json'
        defense = json.loads((ROOT / '.tools/planner-data' / source.replace('/', '__')).read_text(encoding='utf-8'))
        assert defense['dataId'] == parent_id and defense['craftingPhase'] == 4 and defense['isCurrent']
        keys = [prefix + suffix + 'Module' for suffix in ('HP', 'Attack', 'Effect')]
        assert archetypes[archetype][0]['Modules'].split(';') == keys
        assert len(defense['modules']) == 3
        for index, module in enumerate(defense['modules']):
            assert module['dataId'] == first_id + index
            official = modules[keys[index]]
            assert len(official) == len(module['upgrades']) == 10
            levels = []
            resource = official[0]['BuildResource'].replace('DarkElixir', 'Dark Elixir')
            for level, (entry, row) in enumerate(zip(module['upgrades'], official), 1):
                assert entry['level'] == level and entry['buildCost'] == int(row['BuildCost'])
                assert entry['buildCostResource'] == resource
                duration = {unit: int(row['BuildTime' + suffix] or 0) for unit, suffix in [('days', 'D'), ('hours', 'H'), ('minutes', 'M'), ('seconds', 'S')]}
                seconds = lambda time: sum(time.get(unit, 0) * factor for unit, factor in [('days', 86400), ('hours', 3600), ('minutes', 60), ('seconds', 1)])
                assert seconds(entry['buildTime']) == seconds(duration), f'Review changed {keys[index]} level {level}'
                hall = int(row['TownHallLevel'])  # No guessed MAX or phase-dependent hall.
                levels.append(dict(level=level, buildCost=entry['buildCost'], buildCostResource=resource, buildTime=duration, townHallRequired=hall))
            result.append(dict(id=f'{slug}-module-{index + 1}', dataId=module['dataId'], name=f"{defense['name']} · {module['name']}", base='home', category='crafted-module', parentDataId=parent_id, craftingPhase=4, requirements={'craftingStationRequired': 1}, levels=levels, source=source, officialModule=keys[index]))
    return result


def provenance():
    return {'checked': '2026-11-09', 'gameVersion': '18.647.6', 'phase': 4,
            'sources': [{'url': BASE + f'logic/{name}.csv', 'sha256': digest} for name, digest in SOURCES.items()],
            'seasonalUnlock': 'https://supercell.com/en/games/clashofclans/blog/news/the-awesome-quest-is-here/',
            'scope': 'Phase 4 only. TH11 station availability is seasonal; future phase eligibility and dates are not inferred.'}
