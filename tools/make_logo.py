"""Draws the Renovator logo as pixel art: a blueprint grid, a house whose lower half is built and whose upper half is
still a blue projection, and the Architect's Wand in front. Writes the mod-list logo into the jar resources and the
400x400 CurseForge logo into release/curseforge/.

Run from the project root: python tools/make_logo.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

N = 100           # canvas in "pixels"
SCALE = 4         # 400 x 400 output
WAND = Path("src/main/resources/assets/renovator/textures/item/architect_wand.png")


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def draw():
    img = Image.new("RGBA", (N, N))
    px = img.load()
    top, bottom = (30, 82, 148), (18, 52, 100)
    for y in range(N):
        for x in range(N):
            c = lerp(top, bottom, y / (N - 1))
            if x % 10 == 0 or y % 10 == 0:
                c = lerp(c, (90, 150, 215), 0.35)
            if x % 50 == 0 or y % 50 == 0:
                c = lerp(c, (120, 180, 235), 0.45)
            px[x, y] = c + (255,)
    d = ImageDraw.Draw(img)
    # house: walls x 12..60, ground y 80, eaves y 54, roof apex y 30
    left, right, ground, eaves, apex_x, apex_y = 12, 60, 80, 54, 36, 30
    plank, plank_dark, log = (176, 132, 82), (140, 102, 60), (105, 78, 48)
    ghost, ghost_line = (110, 170, 240, 120), (190, 225, 255, 255)
    built_top = 66
    # built part: planks with darker seams, log corners, a door
    for y in range(built_top, ground):
        for x in range(left, right + 1):
            px[x, y] = (plank_dark if (y - built_top) % 4 == 3 else plank) + (255,)
    for y in range(built_top, ground):
        for x in (left, left + 1, right - 1, right):
            px[x, y] = log + (255,)
    d.rectangle([33, 68, 39, ground - 1], fill=(96, 64, 36, 255))
    d.point([(38, 74)], fill=(220, 190, 90, 255))
    # projected part: translucent blue walls and roof with light outline
    overlay = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rectangle([left, eaves, right, built_top - 1], fill=ghost)
    od.polygon([(left - 4, eaves), (apex_x, apex_y), (right + 4, eaves)], fill=ghost)
    img.alpha_composite(overlay)
    d.line([(left, eaves), (left, built_top - 1)], fill=ghost_line, width=1)
    d.line([(right, eaves), (right, built_top - 1)], fill=ghost_line, width=1)
    d.line([(left - 4, eaves), (apex_x, apex_y), (right + 4, eaves), (left - 4, eaves)], fill=ghost_line, width=1)
    # window in the projected wall
    d.rectangle([20, 57, 27, 63], outline=ghost_line)
    d.line([(23, 57), (23, 63)], fill=ghost_line)
    # ground line
    d.line([(4, ground), (N - 5, ground)], fill=(120, 180, 235, 255), width=1)
    # the wand, 3x, crystal up right
    wand = Image.open(WAND).convert("RGBA").resize((48, 48), Image.NEAREST)
    img.alpha_composite(wand, (48, 36))
    # sparkles around the crystal
    for (x, y) in [(95, 33), (89, 28), (97, 42), (84, 31)]:
        for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
            if 0 <= x + dx < N and 0 <= y + dy < N:
                px[x + dx, y + dy] = (235, 220, 255, 255) if (dx, dy) == (0, 0) else (190, 160, 255, 255)
    return img.resize((N * SCALE, N * SCALE), Image.NEAREST)


def main():
    logo = draw()
    Path("release/curseforge").mkdir(parents=True, exist_ok=True)
    logo.save("release/curseforge/logo.png")
    logo.save("src/main/resources/renovator_logo.png")
    print("wrote release/curseforge/logo.png and src/main/resources/renovator_logo.png")


if __name__ == "__main__":
    main()
