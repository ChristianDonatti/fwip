"""Builds sample CBZ files from Pepper&Carrot by David Revoy (CC BY 4.0) for screenshots."""
import io, re, sys, urllib.request, zipfile

OUT = sys.argv[1]
LANG = 'en'
BASE = 'https://www.peppercarrot.com/0_sources/'


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'fwip-screenshots'})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


index = get(f'https://www.peppercarrot.com/{LANG}/webcomics/index.html').decode('utf-8', 'replace')
episodes = sorted(set(re.findall(r'(ep\d\d_[A-Za-z0-9-]+?)(?:__|/|\.html)', index)))
episodes = [e for e in episodes if not e.endswith('-')][:8]
print('episodes', episodes)

for n, ep in enumerate(episodes, 1):
    title = ep.split('_', 1)[1].replace('-', ' ')
    pages = []
    for p in range(1, 13):
        name = f'{LANG}_Pepper-and-Carrot_by-David-Revoy_E{ep[2:4]}P{p:02d}.jpg'
        data = None
        for res in ('low-res', 'hi-res'):
            try:
                data = get(f'{BASE}{ep}/{res}/{name}')
                break
            except Exception:
                pass
        if data is None:
            break
        pages.append((name, data))
    if not pages:
        print('no pages for', ep)
        continue
    info = f'''<?xml version="1.0" encoding="utf-8"?>
<ComicInfo>
  <Series>Pepper&amp;Carrot</Series>
  <Number>{int(ep[2:4])}</Number>
  <Title>{title}</Title>
  <Writer>David Revoy</Writer>
  <Penciller>David Revoy</Penciller>
  <Publisher>peppercarrot.com</Publisher>
  <Summary>Pepper&amp;Carrot episode {int(ep[2:4])}: {title}. Art and story by David Revoy, CC BY 4.0, www.peppercarrot.com</Summary>
  <Web>https://www.peppercarrot.com</Web>
  <LanguageISO>{LANG}</LanguageISO>
</ComicInfo>'''
    path = f'{OUT}/Pepper&Carrot {int(ep[2:4]):02d} - {title}.cbz'
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_STORED) as z:
        z.writestr('ComicInfo.xml', info)
        for name, data in pages:
            z.writestr(name, data)
    print(path, len(pages), 'pages')
