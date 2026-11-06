"""Sample books for the emulator check: an EPUB (Project Gutenberg when reachable, else made here),
a TXT and an FB2 made here, plus the Kindle samples in scripts/samples."""
import io, os, shutil, sys, urllib.request, zipfile

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

WORDS = ("the light came through the window and fell across the table where the old letters lay "
         "she read them slowly one after another as the afternoon turned to evening and the street "
         "below grew quiet except for the sound of a bicycle and someone calling a name far away").split()


def paragraph(seed, n=70):
    w = [WORDS[(seed * 7 + i * 3) % len(WORDS)] for i in range(n)]
    out, sentence = [], []
    for i, x in enumerate(w):
        sentence.append(x)
        if len(sentence) > 9 and (i + seed) % 11 == 0:
            s = ' '.join(sentence)
            out.append(s[0].upper() + s[1:] + '.')
            sentence = []
    if sentence:
        s = ' '.join(sentence)
        out.append(s[0].upper() + s[1:] + '.')
    return ' '.join(out)


CHAPTERS = [(f'Chapter {i}', [paragraph(i * 13 + p) for p in range(14)]) for i in range(1, 7)]


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'fwip-check'})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def cover_png(title):
    try:
        from PIL import Image, ImageDraw
        img = Image.new('RGB', (600, 900), (38, 70, 83))
        d = ImageDraw.Draw(img)
        d.rectangle((40, 40, 560, 860), outline=(233, 196, 106), width=6)
        d.text((80, 400), title, fill=(244, 241, 222))
        b = io.BytesIO()
        img.save(b, 'PNG')
        return b.getvalue()
    except Exception:
        return None


def made_epub(path, title, author, series=None, index=None):
    cover = cover_png(title)
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr(zipfile.ZipInfo('mimetype'), 'application/epub+zip', compress_type=zipfile.ZIP_STORED)
        z.writestr('META-INF/container.xml', '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
        items, spine, nav = [], [], []
        for i, (t, ps) in enumerate(CHAPTERS, 1):
            body = ''.join(f'<p>{p}</p>' for p in ps)
            z.writestr(f'OEBPS/c{i}.xhtml', f'<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>{t}</title></head><body><h1>{t}</h1>{body}</body></html>')
            items.append(f'<item id="c{i}" href="c{i}.xhtml" media-type="application/xhtml+xml"/>')
            spine.append(f'<itemref idref="c{i}"/>')
            nav.append(f'<li><a href="c{i}.xhtml">{t}</a></li>')
        z.writestr('OEBPS/nav.xhtml', f'<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol>{"".join(nav)}</ol></nav></body></html>')
        meta = ''
        if cover:
            z.writestr('OEBPS/cover.png', cover)
            items.append('<item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>')
            meta += '<meta name="cover" content="cover"/>'
        if series:
            meta += f'<meta name="calibre:series" content="{series}"/><meta name="calibre:series_index" content="{index}"/>'
        z.writestr('OEBPS/content.opf', f'<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">fwip-{title}</dc:identifier><dc:title>{title}</dc:title><dc:creator>{author}</dc:creator><dc:language>en</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>{meta}</metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>{"".join(items)}</manifest><spine>{"".join(spine)}</spine></package>')


# A real book, when Project Gutenberg answers: Dom Casmurro (public domain).
try:
    data = get('https://www.gutenberg.org/ebooks/55752.epub3.images')
    if data[:2] == b'PK':
        open(os.path.join(OUT, 'Dom_Casmurro.epub'), 'wb').write(data)
        print('gutenberg ok')
except Exception as e:
    print('gutenberg failed', e)

made_epub(os.path.join(OUT, 'Quiet_Street_1.epub'), 'The Quiet Street', 'Ana Lima', 'Quiet Street', 1)
made_epub(os.path.join(OUT, 'Quiet_Street_2.epub'), 'Letters at Dusk', 'Ana Lima', 'Quiet Street', 2)

with open(os.path.join(OUT, 'Evening_Notes.txt'), 'w', encoding='utf-8') as f:
    f.write('Evening Notes\n\n')
    for t, ps in CHAPTERS[:4]:
        f.write(t.upper() + '\n\n' + '\n\n'.join(ps) + '\n\n')

with open(os.path.join(OUT, 'The_Bicycle.fb2'), 'w', encoding='utf-8') as f:
    secs = ''.join(f'<section><title><p>{t}</p></title>{"".join(f"<p>{p}</p>" for p in ps)}</section>' for t, ps in CHAPTERS[:3])
    f.write(f'<?xml version="1.0" encoding="utf-8"?><FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description><title-info><genre>prose</genre><author><first-name>Rui</first-name><last-name>Prado</last-name></author><book-title>The Bicycle</book-title><lang>en</lang></title-info></description><body>{secs}</body></FictionBook>')

here = os.path.dirname(os.path.abspath(__file__))
for name in os.listdir(os.path.join(here, 'samples')):
    if name.endswith(('.mobi', '.azw3')):
        shutil.copy(os.path.join(here, 'samples', name), os.path.join(OUT, name))
print(sorted(os.listdir(OUT)))
