#!/usr/bin/env python3
"""Generates the simple placeholder textures used by the mod. Re-run after editing: python3 tools/generate_textures.py"""
import os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/towerdefense/textures")
random.seed(7)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

def brick_side(accent):
    img = Image.new("RGBA", (16, 16))
    base = (110, 110, 118, 255)
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 0 if row % 2 == 0 else 4
            mortar = (y % 4 == 3) or ((x + off) % 8 == 7)
            c = shade(base, 0.6) if mortar else shade(base, random.uniform(0.9, 1.1))
            img.putpixel((x, y), c)
    # coloured band + arrow-slit style window
    for x in range(16):
        for y in (1, 2, 13, 14):
            img.putpixel((x, y), shade(accent, 1.0 if y in (1, 13) else 0.7))
    for y in range(5, 11):
        for x in range(6, 10):
            img.putpixel((x, y), (25, 25, 30, 255))
    for y in range(6, 10):
        for x in (7, 8):
            img.putpixel((x, y), shade(accent, 1.1))
    return img

def tower_top(accent):
    img = Image.new("RGBA", (16, 16), shade((90, 90, 98), 1.0))
    for i in range(16):
        for c in (0, 15):
            img.putpixel((i, c), shade(accent, 0.6))
            img.putpixel((c, i), shade(accent, 0.6))
    for y in range(4, 12):
        for x in range(4, 12):
            d = max(abs(x - 7.5), abs(y - 7.5))
            img.putpixel((x, y), shade(accent, 1.0) if d < 2.5 else shade(accent, 0.55))
    return img

def core():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                c = (60, 20, 100, 255)
            elif d > 4.5:
                c = (130, 60, 200, 255)
            elif d > 2.5:
                c = (80, 220, 255, 255)
            else:
                c = (230, 255, 255, 255)
            f = random.uniform(0.93, 1.07)
            img.putpixel((x, y), shade(c, f))
    return img

def coin():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.5:
                if d > 5.2:
                    c = (176, 118, 20, 255)
                else:
                    c = (255, 207, 40, 255) if (x + y) % 7 else (255, 235, 120, 255)
                img.putpixel((x, y), c)
    for y in range(4, 12):  # simple "T" stamp
        img.putpixel((7, y), (176, 118, 20, 255))
        img.putpixel((8, y), (176, 118, 20, 255))
    for x in range(5, 11):
        img.putpixel((x, 4), (176, 118, 20, 255))
        img.putpixel((x, 5), (176, 118, 20, 255))
    return img

def blaster():
    """Siege Rifle: long diagonal barrel, wooden stock, scope on top, glowing muzzle."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dark, mid, light = (45, 47, 55, 255), (100, 104, 116, 255), (165, 170, 185, 255)
    wood, wood_d = (140, 85, 40, 255), (95, 55, 25, 255)
    for i in range(12):  # barrel + receiver along the diagonal
        x, y = 2 + i, 13 - i
        img.putpixel((x, y), mid)
        img.putpixel((x + 1, y), dark)
        if y - 1 >= 0:
            img.putpixel((x, y - 1), light)
    for x, y in ((1, 14), (2, 14), (1, 15), (2, 15), (3, 14), (2, 13), (3, 15)):  # stock
        img.putpixel((x, y), wood)
    for x, y in ((3, 15), (1, 15)):
        img.putpixel((x, y), wood_d)
    for x, y in ((5, 11), (6, 11), (7, 10), (6, 10)):  # scope on top of the receiver
        img.putpixel((x, y), (30, 32, 40, 255))
    img.putpixel((5, 10), (90, 230, 255, 255))   # lens
    img.putpixel((7, 9), (90, 230, 255, 255))
    for x, y in ((14, 2), (14, 1), (15, 1), (15, 2), (13, 1)):  # muzzle glow
        img.putpixel((x, y), (255, 215, 90, 255))
    return img

def save(img, *path):
    full = os.path.join(OUT, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)

towers = {
    "arrow_tower": (70, 200, 70), "frost_tower": (110, 200, 255), "flame_tower": (230, 60, 40),
    "cannon_tower": (255, 140, 30), "storm_tower": (255, 230, 60), "sniper_tower": (190, 120, 255),
    "wither_tower": (90, 30, 110),
}
for name, accent in towers.items():
    save(brick_side(accent), "block", name + "_side.png")
    save(tower_top(accent), "block", name + "_top.png")
save(core(), "block", "defense_core.png")
save(coin(), "item", "coin.png")
save(blaster(), "item", "siege_blaster.png")
print("textures written to", os.path.normpath(OUT))
