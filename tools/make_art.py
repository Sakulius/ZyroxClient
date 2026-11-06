import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/zyrox"
CAPE_W = 10
CAPE_H = 16
SMOOTH = {"texture": {"blur": True, "clamp": True}}
Z_SHAPE = ["11111111", "00000110", "00001100", "00011000", "00110000", "01100000", "11111111"]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(stops, t):
    t = min(max(t, 0.0), 0.999) * (len(stops) - 1)
    index = int(t)
    return lerp(stops[index], stops[index + 1], t - index)


def zyrox():
    art = [[lerp((10, 14, 34), (22, 44, 96), y / (CAPE_H - 1)) for _ in range(CAPE_W)] for y in range(CAPE_H)]
    for y, row in enumerate(Z_SHAPE):
        for x, bit in enumerate(row):
            if bit == "1":
                art[y + 4][x + 1] = (86, 170, 255)
    for x in range(CAPE_W):
        art[1][x] = (86, 170, 255)
        art[14][x] = (86, 170, 255)
    return art


def nebula():
    rng = random.Random(4)
    stops = [(12, 8, 38), (58, 24, 110), (150, 52, 170), (60, 90, 200)]
    art = [[ramp(stops, 0.5 + 0.28 * math.sin(x * 0.9 + y * 0.45) + 0.22 * math.sin(y * 0.8 - x * 0.5)) for x in range(CAPE_W)] for y in range(CAPE_H)]
    for _ in range(9):
        art[rng.randrange(CAPE_H)][rng.randrange(CAPE_W)] = (240, 235, 255)
    return art


def inferno():
    rng = random.Random(9)
    stops = [(255, 226, 92), (255, 132, 28), (190, 34, 16), (70, 12, 12), (22, 8, 10)]
    heights = [rng.uniform(-0.12, 0.12) for _ in range(CAPE_W)]
    return [[ramp(stops, 1 - y / (CAPE_H - 1) + heights[x]) for x in range(CAPE_W)] for y in range(CAPE_H)]


def glacier():
    stops = [(230, 250, 255), (136, 214, 240), (52, 134, 196), (22, 62, 120)]
    art = []
    for y in range(CAPE_H):
        row = []
        for x in range(CAPE_W):
            facet = ((x + y) // 4 % 2) * 0.16 + ((x - y) // 5 % 2) * 0.1
            row.append(ramp(stops, y / (CAPE_H - 1) * 0.75 + facet))
        art.append(row)
    return art


def circuit():
    art = [[(10, 18, 16) if (x + y) % 2 else (13, 23, 20) for x in range(CAPE_W)] for y in range(CAPE_H)]
    trace = (46, 214, 134)
    for x, top, bottom in [(2, 0, 7), (7, 4, 15), (4, 9, 15)]:
        for y in range(top, bottom + 1):
            art[y][x] = trace
    for y, left, right in [(7, 2, 5), (4, 5, 7), (9, 4, 7)]:
        for x in range(left, right + 1):
            art[y][x] = trace
    for x, y in [(2, 7), (7, 4), (4, 9), (7, 9), (7, 15), (2, 0)]:
        art[y][x] = (190, 255, 220)
    return art


def write_cape(name, art):
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    shade = lambda color, amount: tuple(round(c * amount) for c in color) + (255,)
    for y in range(CAPE_H):
        for x in range(CAPE_W):
            image.putpixel((1 + x, 1 + y), art[y][x] + (255,))
            image.putpixel((12 + x, 1 + y), shade(art[y][CAPE_W - 1 - x], 0.45))
        image.putpixel((0, 1 + y), shade(art[y][0], 0.7))
        image.putpixel((11, 1 + y), shade(art[y][CAPE_W - 1], 0.7))
    for x in range(CAPE_W):
        image.putpixel((1 + x, 0), shade(art[0][x], 0.7))
        image.putpixel((11 + x, 0), shade(art[CAPE_H - 1][x], 0.7))
    path = ASSETS / "textures/cape" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def write_badge():
    size = 18
    scale = 8
    big = Image.new("RGBA", (size * scale, size * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(big)
    draw.rounded_rectangle((0, 0, size * scale - 1, size * scale - 1), 5 * scale, fill=(66, 150, 255, 255))
    pad = 4 * scale
    far = size * scale - pad
    stroke = 2 * scale + 4
    draw.line([(pad, pad + stroke // 2), (far, pad + stroke // 2), (pad, far - stroke // 2), (far, far - stroke // 2)], fill=(255, 255, 255, 255), width=stroke, joint="curve")
    big.resize((size, size), Image.LANCZOS).save(ASSETS / "textures/gui/badge.png")
    font = {"providers": [{"type": "bitmap", "file": "zyrox:gui/badge.png", "height": 9, "ascent": 8, "chars": [""]}]}
    (ASSETS / "font/badge.json").write_text(json.dumps(font), encoding="ascii")


def write_logo():
    font = ImageFont.truetype(str(ASSETS / "font/inter-semibold.ttf"), 148)
    letters = "ZYROX"
    spacing = 14
    widths = [font.getlength(letter) for letter in letters]
    width = round(sum(widths) + spacing * (len(letters) - 1))
    image = Image.new("RGBA", (width + 8, 150), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    x = 4.0
    for letter, advance in zip(letters, widths):
        draw.text((x, -18), letter, font=font, fill=(255, 255, 255, 255))
        x += advance + spacing
    image = image.crop(image.getbbox())
    image.save(ASSETS / "textures/gui/logo.png")
    (ASSETS / "textures/gui/logo.png.mcmeta").write_text(json.dumps(SMOOTH), encoding="ascii")
    print("logo", image.size)


def write_icon(name, shape):
    scale = 4
    big = Image.new("RGBA", (128 * scale, 128 * scale), (0, 0, 0, 0))
    shape(ImageDraw.Draw(big), scale)
    big.resize((128, 128), Image.LANCZOS).save(ASSETS / "textures/icon" / f"{name}.png")
    (ASSETS / "textures/icon" / f"{name}.png.mcmeta").write_text(json.dumps(SMOOTH), encoding="ascii")


def sword_shape(draw, s):
    white = (255, 255, 255, 255)
    for mirror in (False, True):
        place = lambda x, y: ((128 - x if mirror else x) * s, y * s)
        draw.polygon([place(*p) for p in [(112, 10), (118, 16), (114, 34), (50, 94), (34, 78), (94, 14)]], fill=white)
        draw.line([place(22, 70), place(58, 106)], fill=white, width=10 * s)
        draw.line([place(36, 92), place(14, 114)], fill=white, width=12 * s)


def pin_shape(draw, s):
    white = (255, 255, 255, 255)
    draw.ellipse((28 * s, 10 * s, 100 * s, 82 * s), fill=white)
    draw.polygon([(34 * s, 64 * s), (94 * s, 64 * s), (64 * s, 118 * s)], fill=white)
    draw.ellipse((50 * s, 32 * s, 78 * s, 60 * s), fill=(0, 0, 0, 0))


WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)


def box(s, *points):
    return [value * s for value in points]


def fps_shape(draw, s):
    draw.arc(box(s, 14, 22, 114, 122), 180, 360, fill=WHITE, width=12 * s)
    draw.line(box(s, 64, 72, 90, 40), fill=WHITE, width=9 * s)
    draw.ellipse(box(s, 54, 62, 74, 82), fill=WHITE)
    draw.rounded_rectangle(box(s, 14, 88, 114, 100), 6 * s, fill=WHITE)


def coordinates_shape(draw, s):
    draw.ellipse(box(s, 26, 26, 102, 102), outline=WHITE, width=10 * s)
    draw.ellipse(box(s, 54, 54, 74, 74), fill=WHITE)
    for line in [(64, 8, 64, 38), (64, 90, 64, 120), (8, 64, 38, 64), (90, 64, 120, 64)]:
        draw.line(box(s, *line), fill=WHITE, width=10 * s)


def cps_shape(draw, s):
    draw.rounded_rectangle(box(s, 34, 12, 94, 116), 30 * s, outline=WHITE, width=10 * s)
    draw.line(box(s, 64, 14, 64, 52), fill=WHITE, width=8 * s)
    draw.line(box(s, 36, 52, 92, 52), fill=WHITE, width=8 * s)
    draw.pieslice(box(s, 39, 17, 89, 67), 180, 270, fill=WHITE)
    draw.rectangle(box(s, 39, 42, 60, 48), fill=WHITE)


def keystrokes_shape(draw, s):
    for x, y in [(44, 18), (10, 66), (44, 66), (78, 66)]:
        draw.rounded_rectangle(box(s, x, y, x + 40, y + 40), 8 * s, fill=WHITE)


def ping_shape(draw, s):
    for index in range(4):
        left = 14 + index * 26
        draw.rounded_rectangle(box(s, left, 92 - index * 24, left + 18, 112), 4 * s, fill=WHITE)


def compass_shape(draw, s):
    draw.ellipse(box(s, 12, 12, 116, 116), outline=WHITE, width=10 * s)
    draw.polygon([(84 * s, 44 * s), (72 * s, 72 * s), (44 * s, 84 * s), (56 * s, 56 * s)], fill=WHITE)


def speed_shape(draw, s):
    for left in (16, 58):
        draw.line([(left * s, 26 * s), ((left + 34) * s, 64 * s), (left * s, 102 * s)], fill=WHITE, width=14 * s, joint="curve")


def clock_shape(draw, s):
    draw.ellipse(box(s, 12, 12, 116, 116), outline=WHITE, width=10 * s)
    draw.line(box(s, 64, 34, 64, 66), fill=WHITE, width=9 * s)
    draw.line(box(s, 64, 66, 86, 80), fill=WHITE, width=9 * s)


def memory_shape(draw, s):
    draw.rounded_rectangle(box(s, 30, 30, 98, 98), 8 * s, fill=WHITE)
    draw.rounded_rectangle(box(s, 48, 48, 80, 80), 4 * s, fill=CLEAR)
    for offset in (42, 64, 86):
        draw.line(box(s, offset, 12, offset, 116), fill=WHITE, width=8 * s)
        draw.line(box(s, 12, offset, 116, offset), fill=WHITE, width=8 * s)
    draw.rounded_rectangle(box(s, 48, 48, 80, 80), 4 * s, fill=CLEAR)
    draw.rectangle(box(s, 56, 56, 72, 72), fill=WHITE)


def server_shape(draw, s):
    for top in (18, 50, 82):
        draw.rounded_rectangle(box(s, 14, top, 114, top + 26), 6 * s, fill=WHITE)
        draw.ellipse(box(s, 24, top + 8, 34, top + 18), fill=CLEAR)
        draw.rectangle(box(s, 74, top + 10, 104, top + 16), fill=CLEAR)


def armor_shape(draw, s):
    draw.polygon([(s * x, s * y) for x, y in [(30, 14), (50, 14), (64, 28), (78, 14), (98, 14), (118, 40), (98, 54), (92, 44), (92, 114), (36, 114), (36, 44), (30, 54), (10, 40)]], fill=WHITE)


def effects_shape(draw, s):
    draw.rounded_rectangle(box(s, 46, 10, 82, 22), 4 * s, fill=WHITE)
    draw.rectangle(box(s, 52, 22, 76, 50), fill=WHITE)
    draw.ellipse(box(s, 20, 42, 108, 122), fill=WHITE)
    draw.ellipse(box(s, 42, 70, 58, 86), fill=CLEAR)
    draw.ellipse(box(s, 68, 88, 80, 100), fill=CLEAR)


def combo_shape(draw, s):
    for top in (18, 50, 82):
        draw.line([(22 * s, (top + 26) * s), (64 * s, top * s), (106 * s, (top + 26) * s)], fill=WHITE, width=14 * s, joint="curve")


def reach_shape(draw, s):
    draw.line(box(s, 20, 64, 108, 64), fill=WHITE, width=10 * s)
    draw.polygon([(8 * s, 64 * s), (34 * s, 42 * s), (34 * s, 86 * s)], fill=WHITE)
    draw.polygon([(120 * s, 64 * s), (94 * s, 42 * s), (94 * s, 86 * s)], fill=WHITE)
    for x in (44, 64, 84):
        draw.line(box(s, x, 28, x, 44), fill=WHITE, width=7 * s)


def items_shape(draw, s):
    draw.rounded_rectangle(box(s, 18, 40, 110, 118), 12 * s, fill=WHITE)
    draw.arc(box(s, 42, 10, 86, 66), 180, 360, fill=WHITE, width=10 * s)
    draw.rounded_rectangle(box(s, 36, 62, 92, 74), 5 * s, fill=CLEAR)


def blocks_shape(draw, s):
    top = [(64, 12), (114, 38), (64, 64), (14, 38)]
    left = [(14, 46), (60, 70), (60, 118), (14, 92)]
    right = [(114, 46), (68, 70), (68, 118), (114, 92)]
    for face in (top, left, right):
        draw.polygon([(x * s, y * s) for x, y in face], fill=WHITE)


def armor_warning_shape(draw, s):
    draw.polygon([(x * s, y * s) for x, y in [(64, 8), (112, 26), (108, 72), (64, 120), (20, 72), (16, 26)]], fill=WHITE)
    draw.rounded_rectangle(box(s, 57, 30, 71, 76), 6 * s, fill=CLEAR)
    draw.ellipse(box(s, 56, 84, 72, 100), fill=CLEAR)


def low_health_shape(draw, s):
    draw.ellipse(box(s, 12, 18, 68, 74), fill=WHITE)
    draw.ellipse(box(s, 60, 18, 116, 74), fill=WHITE)
    draw.polygon([(16 * s, 62 * s), (112 * s, 62 * s), (64 * s, 118 * s)], fill=WHITE)
    draw.rectangle(box(s, 40, 46, 88, 64), fill=WHITE)
    draw.line([(64 * s, 26 * s), (52 * s, 56 * s), (74 * s, 66 * s), (60 * s, 112 * s)], fill=CLEAR, width=7 * s)


(ASSETS / "textures/gui").mkdir(parents=True, exist_ok=True)
(ASSETS / "textures/icon/pvp").mkdir(parents=True, exist_ok=True)
for pvp, shape in {"combo": combo_shape, "reach": reach_shape, "item_counter": items_shape, "block_counter": blocks_shape, "armor_warning": armor_warning_shape, "low_health": low_health_shape}.items():
    write_icon("pvp/" + pvp, shape)
(ASSETS / "textures/icon/hud").mkdir(parents=True, exist_ok=True)
for hud, shape in {"fps": fps_shape, "coordinates": coordinates_shape, "cps": cps_shape, "keystrokes": keystrokes_shape, "ping": ping_shape, "compass": compass_shape, "speed": speed_shape, "clock": clock_shape, "memory": memory_shape, "server": server_shape, "armor": armor_shape, "effects": effects_shape}.items():
    write_icon("hud/" + hud, shape)
for cape, art in {"zyrox": zyrox(), "nebula": nebula(), "inferno": inferno(), "glacier": glacier(), "circuit": circuit()}.items():
    write_cape(cape, art)
write_badge()
write_logo()
write_icon("pvp", sword_shape)
write_icon("waypoints", pin_shape)
