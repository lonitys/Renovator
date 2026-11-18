"""Writes schematic test fixtures into src/main/resources/data/stilwerk/stilwerk_fixtures/.

Written straight from the format descriptions (Sponge Schematic v2/v3, Litematica v6, vanilla structure),
independent of the Java importers, so the GameTests check the importers against the formats.
"""
import gzip
import struct
from pathlib import Path

OUT = Path("src/main/resources/data/stilwerk/stilwerk_fixtures")
DV = 3955  # Minecraft 1.21.1

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Byte(int): pass
class Short(int): pass
class Long(int): pass
class ByteArray(bytes): pass
class IntArray(list): pass
class LongArray(list): pass
class List(list):
    def __init__(self, elem_type, items):
        super().__init__(items)
        self.elem_type = elem_type


def tag_type(v):
    if isinstance(v, Byte): return BYTE
    if isinstance(v, Short): return SHORT
    if isinstance(v, Long): return LONG
    if isinstance(v, bool): return BYTE
    if isinstance(v, int): return INT
    if isinstance(v, ByteArray): return BYTE_ARRAY
    if isinstance(v, str): return STRING
    if isinstance(v, IntArray): return INT_ARRAY
    if isinstance(v, LongArray): return LONG_ARRAY
    if isinstance(v, List): return LIST
    if isinstance(v, dict): return COMPOUND
    raise TypeError(type(v))


def s(text):
    b = text.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def payload(v):
    t = tag_type(v)
    if t == BYTE: return struct.pack(">b", int(v))
    if t == SHORT: return struct.pack(">h", int(v))
    if t == INT: return struct.pack(">i", int(v))
    if t == LONG: return struct.pack(">q", int(v))
    if t == BYTE_ARRAY: return struct.pack(">i", len(v)) + bytes(v)
    if t == STRING: return s(v)
    if t == INT_ARRAY: return struct.pack(">i", len(v)) + b"".join(struct.pack(">i", x) for x in v)
    if t == LONG_ARRAY: return struct.pack(">i", len(v)) + b"".join(struct.pack(">q", x) for x in v)
    if t == LIST:
        return struct.pack(">bi", v.elem_type if len(v) else END, len(v)) + b"".join(payload(x) for x in v)
    if t == COMPOUND:
        return b"".join(struct.pack(">b", tag_type(x)) + s(k) + payload(x) for k, x in v.items()) + struct.pack(">b", END)


def write(name, root, root_name=""):
    data = struct.pack(">b", COMPOUND) + s(root_name) + payload(root)
    OUT.mkdir(parents=True, exist_ok=True)
    with gzip.open(OUT / name, "wb") as f:
        f.write(data)
    print("wrote", OUT / name)


def varints(values):
    out = bytearray()
    for v in values:
        while True:
            b = v & 0x7F
            v >>= 7
            if v:
                out.append(b | 0x80)
            else:
                out.append(b)
                break
    return ByteArray(bytes(out))


def to_signed64(v):
    return v - (1 << 64) if v >= (1 << 63) else v


def pack_litematic(values, bits):
    """Tightly packed values that may span two longs (LitematicaBitArray)."""
    total = (len(values) * bits + 63) // 64
    longs = [0] * total
    for i, v in enumerate(values):
        start = i * bits
        a, off = start >> 6, start & 63
        longs[a] |= (v << off) & 0xFFFFFFFFFFFFFFFF
        end = (i + 1) * bits - 1
        b = end >> 6
        if b != a:
            longs[b] |= v >> (64 - off)
    return LongArray([to_signed64(x) for x in longs])


# ---------------------------------------------------------------- shared house content (W=4, H=3, L=5)

W, H, L = 4, 3, 5
STAIR_STATES = []
for wood in ("oak", "spruce"):
    for facing in ("north", "south", "west", "east"):
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                for wl in ("false", "true"):
                    STAIR_STATES.append(f"minecraft:{wood}_stairs[facing={facing},half={half},shape={shape},waterlogged={wl}]")
PALETTE = ["minecraft:air", "minecraft:oak_planks", "minecraft:stone_bricks", "minecraft:oak_sign[rotation=0,waterlogged=false]"]
PALETTE += STAIR_STATES[:130 - len(PALETTE)]  # 130 entries → ids >= 128 need two varint bytes
EAST_TOP = "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"
HIGH_ID = 129


def house_cells():
    cells = [0] * (W * H * L)

    def put(x, y, z, pid):
        cells[x + z * W + y * W * L] = pid

    for x in range(W):
        for z in range(L):
            put(x, 0, z, 2)  # stone brick floor
    put(1, 1, 0, PALETTE.index(EAST_TOP))
    put(0, 1, 0, 3)  # sign
    put(2, 1, 2, 1)  # planks
    put(3, 2, 4, HIGH_ID)
    return cells


SIGN_DATA = {"front_text": {"messages": List(STRING, ['"Hallo"', '""', '""', '""']), "color": "black", "has_glowing_text": Byte(0)},
             "back_text": {"messages": List(STRING, ['""', '""', '""', '""']), "color": "black", "has_glowing_text": Byte(0)},
             "is_waxed": Byte(0)}


def sponge_v2(name, palette, cells, data_version, bes):
    write(name, {
        "Version": 2, "DataVersion": data_version,
        "Width": Short(W), "Height": Short(H), "Length": Short(L),
        "Offset": IntArray([0, 0, 0]),
        "PaletteMax": len(palette),
        "Palette": {st: i for i, st in enumerate(palette)},
        "BlockData": varints(cells),
        "BlockEntities": List(COMPOUND, bes),
    }, root_name="Schematic")


def main():
    cells = house_cells()
    v2_be = dict(SIGN_DATA)
    v2_be.update({"Pos": IntArray([0, 1, 0]), "Id": "minecraft:sign"})
    sponge_v2("house_v2.schem", PALETTE, cells, DV, [v2_be])

    write("house_v3.schem", {"Schematic": {
        "Version": 3, "DataVersion": DV,
        "Width": Short(W), "Height": Short(H), "Length": Short(L),
        "Offset": IntArray([0, 0, 0]),
        "Blocks": {
            "Palette": {st: i for i, st in enumerate(PALETTE)},
            "Data": varints(cells),
            "BlockEntities": List(COMPOUND, [{"Pos": IntArray([0, 1, 0]), "Id": "minecraft:sign", "Data": SIGN_DATA}]),
        },
    }})

    grass = [0] * (W * H * L)
    grass[0] = 1
    sponge_v2("old_grass.schem", ["minecraft:air", "minecraft:grass"], grass, 3465, [])

    unknown = [0] * (W * H * L)
    unknown[0] = 1
    unknown[1] = 2
    sponge_v2("unknown.schem", ["minecraft:air", "nosuchmod:block", "minecraft:stone"], unknown, DV, [])

    # Litematica: region A 4x3x3 at origin (3-bit palette, values span longs), region B with negative x size
    pal_a = [{"Name": "minecraft:air"}, {"Name": "minecraft:stone"}, {"Name": "minecraft:oak_planks"}, {"Name": "minecraft:glass"},
             {"Name": "minecraft:oak_stairs", "Properties": {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": "false"}}]
    ax, ay, az = 4, 3, 3
    vals_a = [((x + 2 * y + 3 * z) % 4) + 1 for y in range(ay) for z in range(az) for x in range(ax)]  # index (y*az+z)*ax+x
    vals_a[(1 * az + 1) * ax + 2] = 0  # one explicit air cell at (2,1,1)
    pal_b = [{"Name": "minecraft:air"}, {"Name": "minecraft:gold_block"}]
    write("multi.litematic", {
        "MinecraftDataVersion": DV, "Version": 6,
        "Metadata": {"Name": "Zwei Regionen", "Author": "test", "RegionCount": 2,
                     "EnclosingSize": {"x": 6, "y": 3, "z": 3}, "TotalBlocks": 0, "TotalVolume": 0},
        "Regions": {
            "A": {"Position": {"x": 0, "y": 0, "z": 0}, "Size": {"x": ax, "y": ay, "z": az},
                  "BlockStatePalette": List(COMPOUND, pal_a), "BlockStates": pack_litematic(vals_a, 3),
                  "TileEntities": List(COMPOUND, []), "Entities": List(COMPOUND, []),
                  "PendingBlockTicks": List(COMPOUND, []), "PendingFluidTicks": List(COMPOUND, [])},
            "B": {"Position": {"x": 5, "y": 0, "z": 0}, "Size": {"x": -2, "y": 1, "z": 1},
                  "BlockStatePalette": List(COMPOUND, pal_b), "BlockStates": pack_litematic([1, 1], 2),
                  "TileEntities": List(COMPOUND, []), "Entities": List(COMPOUND, []),
                  "PendingBlockTicks": List(COMPOUND, []), "PendingFluidTicks": List(COMPOUND, [])},
        },
    })
    with open(OUT / "multi_expected.txt", "w") as f:
        f.write(" ".join(str(v) for v in vals_a))

    # vanilla structure: stone, explicit air, omitted (VOID)
    write("plain.nbt", {
        "DataVersion": DV,
        "size": List(INT, [3, 1, 1]),
        "palette": List(COMPOUND, [{"Name": "minecraft:stone"}, {"Name": "minecraft:air"}]),
        "blocks": List(COMPOUND, [{"pos": List(INT, [0, 0, 0]), "state": 0}, {"pos": List(INT, [1, 0, 0]), "state": 1}]),
        "entities": List(COMPOUND, []),
    })


if __name__ == "__main__":
    main()
