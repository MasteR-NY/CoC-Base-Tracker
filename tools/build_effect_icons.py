"""Bundle seven compact, hash-checked boost/event symbols from pinned game art."""
from pathlib import Path
import hashlib, io, json, urllib.request
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
REV='fdac291ae7fbc2901221ff945834be534d0344ae'
manifest=json.loads((ROOT/'.tools/identity-data/manifest.json').read_text(encoding='utf-8'))
files={row['path']:row for group in manifest['assets'].values() for row in group}
paths={
 'builder_potion':'magic_items/builder_potion.webp',
 'research_potion':'magic_items/research_potion.webp',
 'pet_potion':'magic_items/pet_potion.webp',
 'builder_bite':'magic_snacks/builder_bite.webp',
 'study_soup':'magic_snacks/study_soup.webp',
 'clock_tower':'buildings/builder-base/clock_tower/level_10.webp',
 'hammer_jam':'magic_items/hammer_of_building.webp'}
cache=ROOT/'.tools/effect-icons';cache.mkdir(exist_ok=True)
sources={}
for name,path in paths.items():
 target=cache/(name+'.webp');url=f'https://raw.githubusercontent.com/ClashKingInc/ClashKingAssets/{REV}/assets/{path}'
 if not target.exists():
  with urllib.request.urlopen(url,timeout=30) as response:data=response.read(2_000_001)
  if len(data)>2_000_000:raise ValueError('Oversized image')
  target.write_bytes(data)
 data=target.read_bytes();assert hashlib.sha256(data).hexdigest()==files[path]['sha']
 with Image.open(io.BytesIO(data)) as image:
  image=image.convert('RGBA');bounds=image.getbbox();assert bounds
  image=image.crop(bounds);image.thumbnail((80,80),Image.Resampling.LANCZOS)
  image.save(ROOT/f'app/src/main/res/drawable-nodpi/effect_{name}.webp','WEBP',quality=85,method=6)
 sources[name]={'url':url,'sha256':files[path]['sha']}
(ROOT/'docs/effect-art-sources.json').write_text(json.dumps(sources,indent=2)+'\n',encoding='utf-8')
print('Bundled seven verified boost/event icons.')
