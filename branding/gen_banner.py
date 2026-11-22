# Makeium README banner and "Available for <loader>" badges, in the same pixel-plate style as BlitzLink's
# (HY-Project/BlitzLinkMC branding/gen_logo.py), recoloured to the Makeium UI theme (MkTheme: dark + minecraft.net green).
#
# Usage: python branding/gen_banner.py
#   reads  the 26.3 client jar like gen_logo.py (diamond pickaxe -> green logo; fox and white banner for the loader badges)
#   writes branding/makeium-banner-{1,4}x.png and branding/badge-{neoforge,fabric}-{1,2,3}x.png
# Everything drawn here is either vanilla Minecraft texture pixels or hand-placed pixel glyphs; no AI art.
import os, sys, zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_logo import JAR, ROOT, SOURCE, greenify, read_png, write_png  # noqa: E402

PAL = {
    'bg':  (0x12, 0x12, 0x12),  # MkTheme.BG
    'K':   (0x07, 0x07, 0x07),  # outline
    'ln':  (0x3A, 0x3A, 0x3D),  # MkTheme.LINE (frame, dividers)
    'ln2': (0x1E, 0x1E, 0x1F),  # MkTheme.PANEL (inner bevel)
    # green ramp ("MAKE"): highlight .. MkTheme.GREEN_TEXT / GREEN_HOVER / GREEN / GREEN_DARK
    'g4':  (0xB4, 0xEE, 0x8C),
    'g3':  (0x6C, 0xC3, 0x49),
    'g2':  (0x52, 0xA5, 0x35),
    'g1':  (0x3C, 0x85, 0x27),
    'g0':  (0x2A, 0x5E, 0x1C),
    'gd':  (0x1A, 0x3B, 0x11),
    # light grey ramp ("IUM", badge names): MkTheme.TEXT .. FAINT
    'p':   (0xF0, 0xF0, 0xF0),
    's4':  (0xD2, 0xD2, 0xD4),
    's3':  (0xB4, 0xB4, 0xB8),
    's2':  (0x8C, 0x8C, 0x91),
    's1':  (0x55, 0x55, 0x5A),
    's0':  (0x3A, 0x3A, 0x3D),
    'mu':  (0xA0, 0xA0, 0xA0),  # MkTheme.MUTED
}


class Canvas:
    def __init__(s, w, h):
        s.w, s.h = w, h
        s.col = [[None] * w for _ in range(h)]
        s.own = [[None] * w for _ in range(h)]

    def inb(s, x, y):
        return 0 <= x < s.w and 0 <= y < s.h

    def layer(s, pix, owner):
        for (x, y), c in pix.items():
            if s.inb(x, y):
                s.col[y][x] = c
                s.own[y][x] = owner

    def save(s, path, scale=1):
        rows = []
        for y in range(s.h):
            row = []
            for x in range(s.w):
                k = s.col[y][x]
                px = (0, 0, 0, 0) if k is None else (PAL[k] if isinstance(k, str) else k[:3]) + (255,)
                row += [px] * scale
            rows += [row] * scale
        write_png(path, s.w * scale, s.h * scale, rows)


def plate(cv):
    for y in range(cv.h):
        for x in range(cv.w):
            edge = min(x, y, cv.w - 1 - x, cv.h - 1 - y)
            cv.col[y][x] = 'ln' if edge == 0 else 'ln2' if edge == 1 else 'bg'
            cv.own[y][x] = 'bgdeco'


def finish(cv):
    """Outline everything drawn on the plate against the plate background."""
    add = []
    for y in range(cv.h):
        for x in range(cv.w):
            if cv.own[y][x] == 'bgdeco':
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if cv.inb(nx, ny) and cv.own[ny][nx] not in (None, 'bgdeco', 'outline'):
                        add.append((x, y))
                        break
    for x, y in add:
        cv.col[y][x] = 'K'
        cv.own[y][x] = 'outline'


def stamp(cv, x0, y0, rows, owner, scale=1):
    cv.layer({(x0 + x * scale + sx, y0 + y * scale + sy): p
              for y, r in enumerate(rows) for x, p in enumerate(r) if p is not None and p[3] >= 128
              for sy in range(scale) for sx in range(scale)}, owner)


# ---- glyphs: heavy 7-row pixel caps (BlitzLink's set plus M) and a 5-row small set ----
GLYPHS = {
    'M': ["XX....XX", "XXX..XXX", "XXXXXXXX", "XX.XX.XX", "XX....XX", "XX....XX", "XX....XX"],
    'A': [".XXXX.", "XX..XX", "XX..XX", "XXXXXX", "XX..XX", "XX..XX", "XX..XX"],
    'K': ["XX..XX", "XX.XX.", "XXXX..", "XXX...", "XXXX..", "XX.XX.", "XX..XX"],
    'E': ["XXXXXX", "XX....", "XX....", "XXXXX.", "XX....", "XX....", "XXXXXX"],
    'I': ["XX", "XX", "XX", "XX", "XX", "XX", "XX"],
    'U': ["XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", ".XXXX."],
    'N': ["XX..XX", "XXX.XX", "XXXXXX", "XX.XXX", "XX..XX", "XX..XX", "XX..XX"],
    'O': [".XXXX.", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", ".XXXX."],
    'F': ["XXXXXX", "XX....", "XX....", "XXXXX.", "XX....", "XX....", "XX...."],
    'R': ["XXXXX.", "XX..XX", "XX..XX", "XXXXX.", "XXXX..", "XX.XX.", "XX..XX"],
    'G': [".XXXXX", "XX....", "XX....", "XX.XXX", "XX..XX", "XX..XX", ".XXXXX"],
    'B': ["XXXXX.", "XX..XX", "XX..XX", "XXXXX.", "XX..XX", "XX..XX", "XXXXX."],
    'C': [".XXXXX", "XX....", "XX....", "XX....", "XX....", "XX....", ".XXXXX"],
}
SMALL = {
    'A': [".X.", "X.X", "XXX", "X.X", "X.X"],
    'V': ["X.X", "X.X", "X.X", "X.X", ".X."],
    'I': ["X", "X", "X", "X", "X"],
    'L': ["X..", "X..", "X..", "X..", "XXX"],
    'B': ["XX.", "X.X", "XX.", "X.X", "XX."],
    'E': ["XXX", "X..", "XX.", "X..", "XXX"],
    'F': ["XXX", "X..", "XX.", "X..", "X.."],
    'O': ["XXX", "X.X", "X.X", "X.X", "XXX"],
    'R': ["XX.", "X.X", "XX.", "X.X", "X.X"],
    ' ': ["..", "..", "..", "..", ".."],
}
GREEN_RAMP = ['g4', 'g3', 'g3', 'g3', 'g2', 'g2', 'g2', 'g2', 'g2', 'g1', 'g1', 'g1', 'g0', 'g0']
GREY_RAMP = ['p', 'p', 's4', 's4', 's4', 's4', 's3', 's3', 's3', 's3', 's2', 's2', 's2', 's1']
NAME_RAMP = ['p', 'p', 'p', 's4', 's4', 's4', 's4']


def word(cv, text, x0, y0, ramp, owner, accent, scale=2, gap=3, slant=4):
    """Stepped-italic wordmark with a 2px diagonal extrusion and a dark outline (BlitzLink style)."""
    H = 7 * scale
    x, pix = x0, {}
    for ch in text:
        g = GLYPHS[ch]
        for gy, row in enumerate(g):
            for gx, c in enumerate(row):
                if c == 'X':
                    for sy in range(scale):
                        for sx in range(scale):
                            yy = gy * scale + sy
                            pix[(x + gx * scale + sx + (H - 1 - yy) // slant, y0 + yy)] = ramp[yy]
        x += len(g[0]) * scale + gap
    ext = {}
    for (px, py) in pix:
        for d in (1, 2):
            if (px + d, py + d) not in pix:
                ext[(px + d, py + d)] = accent[d - 1]
    m = set(pix) | set(ext)
    out = {}
    for (px, py) in m:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (px + dx, py + dy) not in m:
                out[(px + dx, py + dy)] = 'K'
    m |= set(out)
    # fill enclosed counters solid dark
    xs = [p[0] for p in m]; ys = [p[1] for p in m]
    x0b, x1b, y0b, y1b = min(xs) - 1, max(xs) + 1, min(ys) - 1, max(ys) + 1
    seen, stack = {(x0b, y0b)}, [(x0b, y0b)]
    while stack:
        cx, cy = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (cx + dx, cy + dy)
            if x0b <= q[0] <= x1b and y0b <= q[1] <= y1b and q not in m and q not in seen:
                seen.add(q); stack.append(q)
    for yy in range(y0b, y1b + 1):
        for xx in range(x0b, x1b + 1):
            if (xx, yy) not in m and (xx, yy) not in seen:
                out[(xx, yy)] = 'K'
    cv.layer(out, owner + '_ol')
    cv.layer(ext, owner + '_ex')
    cv.layer(pix, owner)
    return x - gap


def text_width(text, scale=2, gap=3):
    return sum(len(GLYPHS[c][0]) * scale + gap for c in text) - gap


def mc_texture(name):
    if not os.path.exists(JAR):
        sys.exit(f'Minecraft client jar not found: {JAR} (set MAKEIUM_MC_JAR)')
    with zipfile.ZipFile(JAR) as z:
        return read_png(z.read(f'assets/minecraft/textures/{name}.png'))[2]


def banner():
    logo = [[greenify(p) for p in row] for row in mc_texture(SOURCE[len('assets/minecraft/textures/'):-len('.png')])]
    H = 38
    mark = 3 + 32 + 3                         # 2x pickaxe with a 3px margin
    W = mark + 5 + text_width('MAKE') + 4 + text_width('IUM') + 8 + 6
    cv = Canvas(W, H)
    plate(cv)
    stamp(cv, 3, 3, logo, 'logo', scale=2)
    for y in range(7, H - 7):                 # divider between the mark and the wordmark
        cv.col[y][mark] = 'ln'
    x = word(cv, 'MAKE', mark + 5, 11, GREEN_RAMP, 'w1', accent=('g0', 'gd'))
    word(cv, 'IUM', x + 4, 11, GREY_RAMP, 'w2', accent=('s1', 's0'))
    finish(cv)
    return cv


# ---- loader badges: vanilla fox face (NeoForge) and white banner (Fabric), as on BlitzLink's badges ----
def fox_face():
    t = mc_texture('entity/fox/fox')
    grid = [[None] * 8 for _ in range(8)]
    def blit(u, v, w, h, x, y):
        for dy in range(h):
            for dx in range(w):
                grid[y + dy][x + dx] = t[v + dy][u + dx]
    blit(9, 2, 2, 2, 0, 0)    # ears
    blit(16, 2, 2, 2, 6, 0)
    blit(7, 11, 8, 6, 0, 2)   # head front
    blit(9, 21, 4, 2, 2, 6)   # snout front
    return grid


def banner_icon(w=10, flag_h=12, pole_h=2):
    wood = mc_texture('entity/banner/banner_base')
    cloth = mc_texture('entity/banner/base')
    tint = (0xF9, 0xFF, 0xFE)  # DyeColor.WHITE
    rows = [[wood[v][2 + x * 20 // w] for x in range(w)] for v in (44, 45)]
    for y in range(flag_h):
        row = []
        for x in range(w):
            r, g, b, a = cloth[1 + y * 40 // flag_h][1 + x * 20 // w]
            row.append((r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, 255) if a else None)
        rows.append(row)
    for y in range(pole_h):
        row = [None] * w
        row[w // 2 - 1] = wood[40 + y][46]
        row[w // 2] = wood[40 + y][47]
        rows.append(row)
    return rows


def loader_icon(key):
    if key == 'neoforge':
        return fox_face(), 2
    return [[None] * 3 + r + [None] * 3 for r in banner_icon()], 1


LOADERS = [('neoforge', 'NEOFORGE'), ('fabric', 'FABRIC')]


def text(cv, glyphs, s, x0, y0, colour, owner):
    pix, x = {}, x0
    for ch in s:
        g = glyphs[ch]
        for y, r in enumerate(g):
            for gx, c in enumerate(r):
                if c == 'X':
                    pix[(x + gx, y0 + y)] = colour[y] if isinstance(colour, list) else colour
        x += len(g[0]) + 1
    cv.layer(pix, owner)


def badge(key, name):
    widest = max(sum(len(GLYPHS[c][0]) + 1 for c in n) - 1 for _, n in LOADERS)
    cv = Canvas(25 + widest + 4, 22)
    plate(cv)
    icon, scale = loader_icon(key)
    stamp(cv, 3, 3, icon, 'icon', scale)
    for y in range(4, 18):
        cv.col[y][22] = 'ln'
    text(cv, SMALL, 'AVAILABLE FOR', 25, 5, 'mu', 'small')
    text(cv, GLYPHS, name, 25, 11, NAME_RAMP, 'name')
    finish(cv)
    return cv


if __name__ == '__main__':
    out = os.path.join(ROOT, 'branding')
    bn = banner()
    for s in (1, 4):
        bn.save(os.path.join(out, f'makeium-banner-{s}x.png'), s)
    print('banner grid', bn.w, 'x', bn.h)
    for key, name in LOADERS:
        bd = badge(key, name)
        for s in (1, 2, 3):
            bd.save(os.path.join(out, f'badge-{key}-{s}x.png'), s)
    print('badge grid', bd.w, 'x', bd.h)
