#!/usr/bin/env python3
"""SortBox icon: green box with white sort arrows."""
import struct, zlib, os

def chunk(tag, data):
    c = struct.pack('>I', len(data)) + tag + data
    return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

def png(path, w, h, pixel):
    raw = b''
    for y in range(h):
        raw += b'\x00'
        for x in range(w):
            raw += bytes(pixel(x, y))
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(raw)))
        f.write(chunk(b'IEND', b''))

BG    = (26, 48, 34, 255)
WHITE = (235, 245, 240, 255)
GREEN = (90, 232, 130, 255)

def tex(x, y):
    if x < 6 or x > 121 or y < 6 or y > 121:
        return (10, 16, 12, 255)
    base = BG
    # box outline
    if x in (20, 107) or y in (30, 97):
        base = WHITE
    if 24 <= x <= 103 and 36 <= y <= 91:
        base = GREEN
        # three sorting bars (short -> long)
        if y in (44, 58, 72):
            if y == 44 and 24 + 8 <= x <= 88:
                base = WHITE
            if y == 58 and 24 + 8 <= x <= 78:
                base = WHITE
            if y == 72 and 24 + 8 <= x <= 98:
                base = WHITE
    return base

base = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'sortbox')
png(os.path.join(base, 'icon.png'), 128, 128, tex)
print('icon written')
