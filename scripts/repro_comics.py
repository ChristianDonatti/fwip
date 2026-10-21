"""Test comics for the reader: Pepper&Carrot pages (CC BY 4.0) with a double-page image in the middle."""
import io, os, re, subprocess, sys, urllib.request, zipfile
from PIL import Image

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
BASE = 'https://www.peppercarrot.com/0_sources/'

def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'fwip-repro'})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()

index = get('https://www.peppercarrot.com/en/webcomics/index.html').decode('utf-8', 'replace')
episodes = sorted(set(re.findall(r'(ep\d\d_[A-Za-z0-9-]+?)(?:__|/|\.html)', index)))
pages = []
for ep in episodes[:4]:
    for p in range(1, 7):
        name = f'en_Pepper-and-Carrot_by-David-Revoy_E{ep[2:4]}P{p:02d}.jpg'
        try:
            pages.append(Image.open(io.BytesIO(get(f'{BASE}{ep}/low-res/{name}'))).convert('RGB'))
        except Exception:
            break
print(len(pages), 'pages')
# Number every page in big type so screenshots show which is where.
from PIL import ImageDraw
def label(im, text):
    im = im.copy()
    d = ImageDraw.Draw(im)
    w = im.width
    d.rectangle([0, 0, w // 3, w // 7], fill='white')
    d.text((10, 0), text, fill='red', font_size=w // 9)
    return im

out = []
for i, im in enumerate(pages[:16]):
    out.append(im)
# A double-page spread at position 9 (two pages side by side), another at 13.
for at in (9, 13):
    a, b = out[at], out[at + 1]
    h = min(a.height, b.height)
    wide = Image.new('RGB', (a.width + b.width, h), 'white')
    wide.paste(a, (0, 0)); wide.paste(b, (a.width, 0))
    out[at:at + 2] = [wide]
files = []
for i, im in enumerate(out):
    buf = io.BytesIO()
    label(im, f'P{i + 1}').save(buf, 'JPEG', quality=85)
    files.append((f'page{i + 1:03d}.jpg', buf.getvalue()))
with zipfile.ZipFile(f'{OUT}/Repro Double.cbz', 'w', zipfile.ZIP_STORED) as z:
    for n, data in files:
        z.writestr(n, data)
tmp = f'{OUT}/tmp'
os.makedirs(tmp, exist_ok=True)
for n, data in files:
    open(f'{tmp}/{n}', 'wb').write(data)
subprocess.run(['rar', 'a', '-idq', '-ep1', f'{OUT}/Repro Double.cbr'] + [f'{tmp}/{n}' for n, _ in files], check=True)
subprocess.run(['rar', 'a', '-idq', '-s', '-ep1', f'{OUT}/Repro Solid.cbr'] + [f'{tmp}/{n}' for n, _ in reversed(files)], check=True)
for n, _ in files:
    os.remove(f'{tmp}/{n}')
os.rmdir(tmp)
print(os.listdir(OUT))
