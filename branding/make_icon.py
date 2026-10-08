"""Throughput mod icon.

Authored as a 32x32 pixel grid and exported with nearest-neighbour scaling only,
so every output stays crisp pixel art.

Run from the project root:  python3 -P branding/make_icon.py
(-P keeps the script directory off sys.path but still sees user-site Pillow.)
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
N = 32

PALETTE = {
    ".": (0, 0, 0, 0),            # transparent (outside rounded corners)
    "K": (8, 10, 14, 255),        # tile border
    "b": (22, 27, 36, 255),       # tile fill
    "g": (30, 37, 49, 255),       # tile grid lines (graph paper)
    "h": (40, 48, 63, 255),       # tile top bevel
    "s": (14, 17, 23, 255),       # tile bottom bevel
    "O": (12, 13, 17, 255),       # object outline
    "L": (178, 183, 192, 255),    # iron light
    "M": (122, 127, 137, 255),    # iron mid
    "D": (78, 82, 91, 255),       # iron dark
    "I": (36, 38, 45, 255),       # hopper bowl interior
    "i": (50, 53, 61, 255),       # bowl back wall
    "A": (255, 176, 46, 255),     # amber signal
    "Y": (255, 226, 140, 255),    # amber highlight
    "a": (196, 112, 18, 255),     # amber shade
}

# Hopper sprite, 22 x 14: wide rim with a visible bowl, tapered body, spout.
HOPPER = [
    "OOOOOOOOOOOOOOOOOOOOOO",
    "OLLLLLLLLLLLLLLLLLLLLO",
    "OLiiiiiiiiiiiiiiiiiiDO",
    "OLIIIIIIIIIIIIIIIIIIDO",
    "OLMMMMMMMMMMMMMMMMMMDO",
    "ODDDDDDDDDDDDDDDDDDDDO",
    "OOOOOLMMMMMMMMMMDOOOOO",
    "....OLMMMMMMMMMMDO....",
    "....OLMMMMMMMMMMDO....",
    "....ODDDDDDDDDDDDO....",
    "....OOOOLMMMMDOOOO....",
    ".......OOLMMDOO.......",
    "........OLMMDO........",
    "........OOOOOO........",
]
HOPPER_X, HOPPER_Y = 5, 16

# Rising sparkline: 45-degree segments only, so it stays clean pixel art.
# Each vertex is (x, y) on the 32 grid; the stroke is 2px tall.
SPARK = [(3, 13), (7, 9), (10, 12), (14, 8), (17, 11), (25, 3)]
# "Trending up" chevron: corner at the top right, two 2px arms.
ARROW_CORNER = (26, 2)
ARROW_ARM = 6


def blank():
    return [["." for _ in range(N)] for _ in range(N)]


def tile(grid):
    """Rounded dark tile with a faint graph-paper grid and bevel."""
    corner = {(0, 0), (1, 0), (0, 1), (N - 1, 0), (N - 2, 0), (N - 1, 1),
              (0, N - 1), (1, N - 1), (0, N - 2), (N - 1, N - 1),
              (N - 2, N - 1), (N - 1, N - 2)}
    border = {(1, 1), (N - 2, 1), (1, N - 2), (N - 2, N - 2)}
    for y in range(N):
        for x in range(N):
            if (x, y) in corner:
                continue
            if x in (0, N - 1) or y in (0, N - 1) or (x, y) in border:
                grid[y][x] = "K"
            elif y == 1 or (y == 2 and x not in (1, N - 2)):
                grid[y][x] = "h" if y == 1 else "b"
            elif y == N - 2:
                grid[y][x] = "s"
            elif (x - 1) % 6 == 0 or (y - 1) % 6 == 0:
                grid[y][x] = "g"
            else:
                grid[y][x] = "b"
    for x in range(2, N - 2):
        grid[1][x] = "h"


def stamp(grid, sprite, ox, oy):
    for dy, row in enumerate(sprite):
        for dx, ch in enumerate(row):
            if ch != ".":
                grid[oy + dy][ox + dx] = ch


def line_pixels(pts):
    out = []
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for t in range(steps + 1):
            x = x0 + round((x1 - x0) * t / steps)
            y = y0 + round((y1 - y0) * t / steps)
            if (x, y) not in out:
                out.append((x, y))
    return out


def sparkline(grid):
    core = line_pixels(SPARK)
    body = set()
    for x, y in core:
        body.add((x, y))
        body.add((x, y + 1))
    cx, cy = ARROW_CORNER
    arrow = set()
    for k in range(ARROW_ARM):
        for t in (0, 1):
            arrow.add((cx - k, cy + t))      # horizontal arm
            arrow.add((cx - 1 + t, cy + k))  # vertical arm
    shape = body | arrow
    # dark outline around everything, drawn first
    for x, y in shape:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                p = (x + dx, y + dy)
                if p not in shape and 1 < p[0] < N - 1 and 1 < p[1] < N - 2:
                    grid[p[1]][p[0]] = "O"
    for x, y in shape:
        grid[y][x] = "A"
    # top edge catches light, bottom edge is shaded
    for x, y in shape:
        if (x, y - 1) not in shape:
            grid[y][x] = "Y"
        elif (x, y + 1) not in shape:
            grid[y][x] = "a"


def render(grid):
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            img.putpixel((x, y), PALETTE[grid[y][x]])
    return img


def main():
    grid = blank()
    tile(grid)
    stamp(grid, HOPPER, HOPPER_X, HOPPER_Y)
    sparkline(grid)
    img = render(grid)

    outputs = {
        ROOT / "src/main/resources/assets/throughput/icon.png": 128,
        ROOT / "branding/icon-512.png": 512,
        ROOT / "branding/icon-32.png": 32,
        ROOT / "site/favicon.png": 64,
    }
    for path, size in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        img.resize((size, size), Image.NEAREST).save(path, optimize=True)
        print(f"wrote {path.relative_to(ROOT)} ({size}x{size})")


if __name__ == "__main__":
    main()
