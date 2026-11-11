"""Makes the small tiling textures of the 3D book (cloth for boards, fibre for paper).
Black or white pixels with a little alpha, to lay over any colour. Run: python3 scripts/textures.py"""
import numpy as np
from PIL import Image

OUT = 'app/src/main/res/drawable-nodpi/'
rng = np.random.default_rng(7)

def tile_noise(n, octaves):
    """Smooth noise that tiles: random phases of whole-number waves."""
    y, x = np.mgrid[0:n, 0:n] / n * 2 * np.pi
    out = np.zeros((n, n))
    for fx, fy, amp in octaves:
        out += amp * np.sin(fx * x + rng.uniform(0, 6.28)) * np.sin(fy * y + rng.uniform(0, 6.28))
    return out

def save(name, value):
    """value in -1..1: negative darkens, positive lightens."""
    v = np.clip(value, -1, 1)
    rgba = np.zeros(v.shape + (4,), dtype=np.uint8)
    rgba[..., :3] = np.where(v[..., None] > 0, 255, 0)
    rgba[..., 3] = (np.abs(v) * 255).astype(np.uint8)
    Image.fromarray(rgba, 'RGBA').save(OUT + name, optimize=True)

# Book cloth: a plain weave, threads over and under, each a little uneven.
n = 192
threads = 48
y, x = np.mgrid[0:n, 0:n]
u = x / n * threads
v = y / n * threads
warp = np.sin(np.pi * u) ** 2
weft = np.sin(np.pi * v) ** 2
over = ((np.floor(u) + np.floor(v)) % 2)
weave = np.where(over > 0, warp, weft) - 0.5
col = rng.normal(0, 0.25, threads).repeat(n // threads)[None, :]
row = rng.normal(0, 0.25, threads).repeat(n // threads)[:, None]
uneven = np.where(over > 0, col, row)
cloth = 0.20 * weave + 0.06 * uneven + 0.04 * rng.normal(0, 1, (n, n))
save('tex_cloth.png', cloth)

# Paper: fine grain and short faint fibres.
n = 256
grain = rng.normal(0, 1, (n, n))
fibres = np.zeros((n, n))
for _ in range(260):
    cx, cy = rng.integers(0, n, 2)
    ang = rng.uniform(0, np.pi)
    length = rng.integers(8, 28)
    for k in range(length):
        px = int(cx + np.cos(ang) * k) % n
        py = int(cy + np.sin(ang) * k) % n
        fibres[py, px] += rng.choice([-1, 1]) * 0.6
paper = 0.035 * grain + 0.05 * fibres
save('tex_paper.png', paper)
