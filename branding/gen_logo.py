# Makeium logo: Minecraft's diamond pickaxe texture with the diamond head turned green (hue shift; the stick stays).
# It is vanilla art recoloured — not AI-generated — so the same icon is used on every store (no separate HAE edition).
#
# Usage: python branding/gen_logo.py
#   reads  assets/minecraft/textures/item/diamond_pickaxe.png from the 26.3 client jar
#          (~/.gradle/caches/neoformruntime/artifacts/minecraft_26.3_client.jar, override with MAKEIUM_MC_JAR)
#   writes common/src/main/resources/assets/makeium/icon.png            (128x128 mod icon on the UI background)
#          common/src/main/resources/assets/makeium/textures/gui/logo.png (16x16, drawn in the Makeium top bar)
#          branding/logo-512.png                                         (512x512, transparent, for store pages)
import colorsys, os, struct, zipfile, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.environ.get("MAKEIUM_MC_JAR", os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_26.3_client.jar"))
SOURCE = "assets/minecraft/textures/item/diamond_pickaxe.png"
GREEN_HUE = 105 / 360.0  # minecraft.net green


def read_png(data):
    """Minimal PNG decoder: greyscale/RGB/palette/grey+alpha/RGBA (palette and grey also at 1/2/4 bits), non-interlaced."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, palette, trns = 8, b"", None, None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0 and (depth == 8 or ctype in (0, 3)), "unsupported PNG"
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (w * channels * depth + 7) // 8
    bpp = max(1, channels * depth // 8)
    raw = zlib.decompress(idat)
    rows, prev = [], bytearray(stride)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        if depth < 8:
            per = 8 // depth
            vals = [(line[x // per] >> (8 - depth * (x % per + 1))) & ((1 << depth) - 1) for x in range(w)]
            if ctype == 0:
                vals = [v * 255 // ((1 << depth) - 1) for v in vals]
            unpacked = bytearray(vals)
        else:
            unpacked = line
        px = []
        for x in range(w):
            v = unpacked[x * channels:(x + 1) * channels]
            if ctype == 6:
                px.append(tuple(v))
            elif ctype == 2:
                px.append((v[0], v[1], v[2], 255))
            elif ctype == 3:
                r, g, bb = palette[v[0]]
                alpha = trns[v[0]] if trns is not None and v[0] < len(trns) else 255
                px.append((r, g, bb, alpha))
            elif ctype == 4:
                px.append((v[0], v[0], v[0], v[1]))
            else:
                px.append((v[0], v[0], v[0], 255))
        rows.append(px)
    return w, h, rows


def write_png(path, w, h, rows):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def greenify(p):
    r, g, b, a = p
    if a == 0:
        return p
    hue, light, sat = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    # diamond pixels are cyan/blue (hue ~150-230°); the wooden stick is orange-brown and stays
    if 0.40 <= hue <= 0.66 and sat > 0.15:
        nr, ng, nb = colorsys.hls_to_rgb(GREEN_HUE, light, min(1.0, sat * 1.05))
        return (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return p


def scaled(rows, scale, bg=None):
    out = []
    for row in rows:
        line = []
        for p in row:
            q = p if p[3] or bg is None else bg
            line += [q] * scale
        out += [line] * scale
    return out


def main():
    with zipfile.ZipFile(JAR) as z:
        w, h, rows = read_png(z.read(SOURCE))
    green = [[greenify(p) for p in row] for row in rows]
    res = os.path.join(ROOT, "common/src/main/resources/assets/makeium")
    write_png(os.path.join(res, "textures/gui/logo.png"), w, h, green)
    write_png(os.path.join(res, "icon.png"), w * 8, h * 8, scaled(green, 8, (0x12, 0x12, 0x12, 255)))
    write_png(os.path.join(ROOT, "branding/logo-512.png"), w * 32, h * 32, scaled(green, 32))
    print("logo: %dx%d from %s" % (w, h, SOURCE))


if __name__ == "__main__":
    main()
