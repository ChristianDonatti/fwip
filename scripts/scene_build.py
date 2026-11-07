"""Turns Poly Haven CC0 panoramas into flat scene photos for the book reader.

SPEC (env, JSON):
  {"preview": ["id", ...]}  -> one sheet per panorama with 8 views around (yaw 0..315) in PREVIEW_DIR
  {"scenes": {"fireplace": {"id": "...", "yaw": 0, "pitch": 0, "fov": 90}, ...},
   "textures": {"desk": "wood_table_001"}}  -> scenes/<name>.jpg in OUT_DIR
"""
import io, json, math, os, sys, urllib.request
import numpy as np
from PIL import Image, ImageDraw

OUT, PREVIEW = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
os.makedirs(PREVIEW, exist_ok=True)
SPEC = json.loads(os.environ['SPEC'])
UA = {'User-Agent': 'fwip-scenes'}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=180) as r:
        return r.read()


_cache = {}


def pano(aid):
    if aid not in _cache:
        files = json.loads(get(f'https://api.polyhaven.com/files/{aid}'))
        url = files['tonemapped']['url']
        img = Image.open(io.BytesIO(get(url))).convert('RGB')
        if img.width > 8192:
            img = img.resize((8192, 4096), Image.LANCZOS)
        _cache[aid] = np.asarray(img).astype(np.float32)
    return _cache[aid]


def view(src, yaw, pitch, fov, w, h):
    """Perspective view of an equirectangular panorama; yaw 0 = the middle of the image."""
    H, W, _ = src.shape
    f = (w / 2) / math.tan(math.radians(fov) / 2)
    xs = (np.arange(w) - w / 2 + 0.5)
    ys = (np.arange(h) - h / 2 + 0.5)
    x, y = np.meshgrid(xs, ys)
    z = np.full_like(x, f)
    # Pitch (up positive) around the x axis, then yaw around the y axis.
    p, q = math.radians(pitch), math.radians(yaw)
    y2 = y * math.cos(p) - z * math.sin(p)
    z2 = y * math.sin(p) + z * math.cos(p)
    x3 = x * math.cos(q) + z2 * math.sin(q)
    z3 = -x * math.sin(q) + z2 * math.cos(q)
    lon = np.arctan2(x3, z3)
    lat = np.arctan2(-y2, np.sqrt(x3 ** 2 + z3 ** 2))
    u = (lon / (2 * math.pi) + 0.5) * W - 0.5
    v = (0.5 - lat / math.pi) * H - 0.5
    u0 = np.floor(u).astype(int); v0 = np.floor(v).astype(int)
    du = (u - u0)[..., None]; dv = (v - v0)[..., None]
    u1 = (u0 + 1) % W; u0 %= W
    v0 = np.clip(v0, 0, H - 1); v1 = np.clip(v0 + 1, 0, H - 1)
    out = (src[v0, u0] * (1 - du) * (1 - dv) + src[v0, u1] * du * (1 - dv)
           + src[v1, u0] * (1 - du) * dv + src[v1, u1] * du * dv)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


if 'preview' in SPEC:
    for aid in SPEC['preview']:
        try:
            src = pano(aid)
        except Exception as e:
            print('failed', aid, e)
            continue
        tw, th = 480, 300
        sheet = Image.new('RGB', (tw * 4, (th + 20) * 2), (20, 20, 20))
        d = ImageDraw.Draw(sheet)
        for i, yaw in enumerate(range(0, 360, 45)):
            im = view(src, yaw, SPEC.get('pitch', 0), SPEC.get('fov', 90), tw, th)
            x, y = (i % 4) * tw, (i // 4) * (th + 20)
            sheet.paste(im, (x, y))
            d.text((x + 6, y + th + 3), f'{aid} yaw {yaw}', fill=(255, 255, 255))
        sheet.save(os.path.join(PREVIEW, aid + '.jpg'), quality=80)
        _cache.clear()

for name, s in SPEC.get('scenes', {}).items():
    src = pano(s['id'])
    im = view(src, s.get('yaw', 0), s.get('pitch', 0), s.get('fov', 90), 2400, 1500)
    im.save(os.path.join(OUT, name + '.jpg'), quality=82, optimize=True, progressive=True)
    im.resize((480, 300)).save(os.path.join(PREVIEW, name + '.jpg'), quality=80)
    _cache.clear()

for name, tid in SPEC.get('textures', {}).items():
    files = json.loads(get(f'https://api.polyhaven.com/files/{tid}'))
    url = files['Diffuse']['2k']['jpg']['url']
    im = Image.open(io.BytesIO(get(url))).convert('RGB')
    im.save(os.path.join(OUT, name + '.jpg'), quality=84, optimize=True, progressive=True)
    im.resize((480, 480)).save(os.path.join(PREVIEW, name + '.jpg'), quality=80)

credits = SPEC.get('credits')
if credits is not None:
    open(os.path.join(OUT, 'CREDITS.txt'), 'w').write(credits)
print(os.listdir(OUT), os.listdir(PREVIEW))
