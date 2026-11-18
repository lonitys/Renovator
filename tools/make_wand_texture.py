"""Writes the 16x16 architect wand item texture: wooden handle, gold band, amethyst crystal tip."""
import sys
from pathlib import Path

from PIL import Image

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/renovator/textures/item/architect_wand.png")

WOOD_D, WOOD_M, WOOD_L = (73, 54, 28), (104, 78, 47), (137, 103, 60)
GOLD_D, GOLD_M, GOLD_L = (168, 116, 18), (230, 180, 40), (255, 236, 120)
AME_D, AME_M, AME_L, AME_W = (84, 50, 146), (138, 92, 210), (190, 150, 245), (240, 225, 255)
OUTLINE = (32, 22, 40)


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # handle: diagonal from (2,13) to (8,7)
    for i in range(7):
        x, y = 2 + i, 13 - i
        px[x, y] = WOOD_M + (255,)
        px[x + 1, y] = WOOD_D + (255,)
        px[x, y - 1] = WOOD_L + (255,) if i % 2 == 0 else WOOD_M + (255,)
    px[1, 14] = WOOD_D + (255,)
    px[2, 14] = WOOD_D + (255,)
    # gold band around (9,6)
    for (x, y), c in {(8, 6): GOLD_M, (9, 6): GOLD_D, (9, 7): GOLD_D, (8, 5): GOLD_L, (10, 6): GOLD_D, (9, 5): GOLD_M,
                      (10, 5): GOLD_M, (10, 4): GOLD_L}.items():
        px[x, y] = c + (255,)
    # amethyst crystal tip, a small diamond at (12,3)
    crystal = {
        (11, 4): AME_D, (11, 3): AME_M, (12, 4): AME_M, (12, 3): AME_L, (12, 2): AME_M, (13, 3): AME_M,
        (13, 2): AME_L, (13, 1): AME_W, (14, 2): AME_M, (12, 1): AME_L, (11, 2): AME_D, (14, 1): AME_D,
        (13, 4): AME_D, (12, 5): AME_D,
    }
    for (x, y), c in crystal.items():
        px[x, y] = c + (255,)
    # dark outline around every opaque pixel for readability
    base = img.copy()
    bp = base.load()
    for y in range(16):
        for x in range(16):
            if bp[x, y][3] == 0 and any(
                    0 <= x + dx < 16 and 0 <= y + dy < 16 and bp[x + dx, y + dy][3] > 0
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUTLINE + (255,)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
