"""Tiny UI driver for screenshots: find a node by text or description and tap it."""
import re, subprocess, sys, time, xml.etree.ElementTree as ET


def adb(*args):
    try:
        return subprocess.run(['adb', *args], capture_output=True, timeout=30).stdout
    except subprocess.TimeoutExpired:
        return b''



def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
    try:
        return list(ET.fromstring(adb('exec-out', 'cat', '/sdcard/ui.xml')).iter('node'))
    except ET.ParseError:
        return []


def find(pattern):
    for n in nodes():
        label = (n.get('text') or '') + '|' + (n.get('content-desc') or '')
        if re.search(pattern, label):
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(pattern, tries=8):
    for _ in range(tries):
        p = find(pattern)
        if p:
            adb('shell', 'input', 'tap', str(p[0]), str(p[1]))
            return True
        time.sleep(2)
    labels = [((n.get('text') or '') + '|' + (n.get('content-desc') or '')) for n in nodes()]
    print('not found:', pattern, [l for l in labels if l != '|'][:60])
    return False


if __name__ == '__main__':
    ok = tap(sys.argv[1])
    sys.exit(0 if ok else 1)
