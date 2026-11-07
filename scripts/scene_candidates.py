"""Lists CC0 panoramas and textures from Poly Haven that could serve as reading scenes,
and draws contact sheets (one per scene) so the right ones can be picked by eye."""
import io, json, os, sys, urllib.request
from PIL import Image, ImageDraw

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {'User-Agent': 'fwip-scenes'}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read()


hdris = json.loads(get('https://api.polyhaven.com/assets?t=hdris'))
textures = json.loads(get('https://api.polyhaven.com/assets?t=textures'))
json.dump({'hdris': hdris, 'textures': textures}, open(os.path.join(OUT, 'index.json'), 'w'))

SCENES = {
    'fireplace': ['fireplace', 'cozy', 'cabin', 'living room', 'lounge', 'interior', 'room'],
    'library': ['library', 'book', 'study', 'hall', 'museum', 'church', 'castle', 'office'],
    'mountain': ['mountain', 'alpine', 'alps', 'peak', 'snow', 'valley', 'hill', 'cliff'],
    'park': ['park', 'garden', 'forest', 'tree', 'meadow', 'field', 'woods'],
    'beach': ['beach', 'sea', 'coast', 'ocean', 'shore', 'bay', 'lake'],
    'night': ['night', 'star', 'moon', 'dusk'],
    'rain': ['overcast', 'cloudy', 'street', 'city', 'urban', 'rain', 'window'],
}


def text(a):
    return ' '.join([a.get('name', '')] + a.get('categories', []) + a.get('tags', [])).lower()


def sheet(name, ids, thumb):
    ids = ids[:40]
    cols = 4
    tw, th = 480, 240
    rows = (len(ids) + cols - 1) // cols
    img = Image.new('RGB', (cols * tw, max(1, rows) * (th + 24)), (20, 20, 20))
    d = ImageDraw.Draw(img)
    for i, aid in enumerate(ids):
        x, y = (i % cols) * tw, (i // cols) * (th + 24)
        try:
            t = Image.open(io.BytesIO(get(thumb(aid)))).convert('RGB').resize((tw - 8, th - 8))
            img.paste(t, (x + 4, y + 4))
        except Exception as e:
            d.text((x + 10, y + 100), 'no thumb', fill=(255, 0, 0))
        d.text((x + 6, y + th + 4), aid, fill=(255, 255, 255))
    img.save(os.path.join(OUT, name + '.jpg'), quality=80)


picked = {}
for scene, words in SCENES.items():
    ids = [aid for aid, a in hdris.items() if any(w in text(a) for w in words)]
    # Prefer the most downloaded: the best looking ones usually.
    ids.sort(key=lambda i: -hdris[i].get('download_count', 0))
    picked[scene] = ids[:40]
    sheet(scene, ids, lambda aid: f'https://cdn.polyhaven.com/asset_img/thumbs/{aid}.png?width=480&height=240')

wood = [aid for aid, a in textures.items() if any(w in text(a) for w in ['wood', 'table', 'desk', 'plank', 'floor'])]
wood.sort(key=lambda i: -textures[i].get('download_count', 0))
picked['desk'] = wood[:40]
sheet('desk', wood, lambda aid: f'https://cdn.polyhaven.com/asset_img/thumbs/{aid}.png?width=480&height=240')
json.dump(picked, open(os.path.join(OUT, 'picked.json'), 'w'), indent=1)
print({k: len(v) for k, v in picked.items()})
