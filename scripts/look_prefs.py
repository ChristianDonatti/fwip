"""Prints a SharedPreferences XML file from key=value arguments (used by ui_check.sh).

set:a,b string sets, true/false become booleans, i:N ints, numbers with a dot floats, keys starting with accent_ ints, the rest strings.
"""
import sys
from xml.sax.saxutils import escape, quoteattr

lines = ["<?xml version='1.0' encoding='utf-8' standalone='yes' ?>", '<map>']
for arg in sys.argv[1:]:
    key, value = arg.split('=', 1)
    if value.startswith('set:'):
        items = [v for v in value[4:].split(',') if v]
        if items:
            lines.append(f'    <set name={quoteattr(key)}>' + ''.join(f'<string>{escape(v)}</string>' for v in items) + '</set>')
        else:
            lines.append(f'    <set name={quoteattr(key)} />')
    elif value in ('true', 'false'):
        lines.append(f'    <boolean name={quoteattr(key)} value="{value}" />')
    elif value.startswith('i:'):
        lines.append(f'    <int name={quoteattr(key)} value="{int(value[2:])}" />')
    elif key.startswith('accent_'):
        lines.append(f'    <int name={quoteattr(key)} value="{int(value)}" />')
    elif '.' in value and value.replace('.', '', 1).isdigit():
        lines.append(f'    <float name={quoteattr(key)} value="{value}" />')
    else:
        lines.append(f'    <string name={quoteattr(key)}>{escape(value)}</string>')
lines.append('</map>')
print('\n'.join(lines))
