"""Writes the GameTest structures:
- empty.nbt: 16x16x16, a stone floor at y=0 and air above;
- village.nbt: 40x16x40 terrain, stone y=0, dirt y=1..2, grass y=3 (relative test y 4) and air above."""
import gzip
import struct
import sys
from pathlib import Path

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10
DATA_VERSION = 3955  # Minecraft 1.21.1


def name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def int_payload(v):
    return struct.pack(">i", v)


def list_payload(elem_type, payloads):
    return struct.pack(">bi", elem_type, len(payloads)) + b"".join(payloads)


def compound_payload(entries):
    """entries: list of (tag_type, key, payload_bytes)."""
    out = b"".join(struct.pack(">b", t) + name(k) + p for t, k, p in entries)
    return out + struct.pack(">b", TAG_END)


def write(out_path, sx, sy, sz, layers):
    """layers: block names from y=0 upwards; everything above is air."""
    blocks = []
    for y, _ in enumerate(layers):
        for x in range(sx):
            for z in range(sz):
                pos = list_payload(TAG_INT, [int_payload(x), int_payload(y), int_payload(z)])
                blocks.append(compound_payload([(TAG_LIST, "pos", pos), (TAG_INT, "state", int_payload(y))]))
    palette = [compound_payload([(TAG_STRING, "Name", name(b))]) for b in layers]
    root = compound_payload([
        (TAG_INT, "DataVersion", int_payload(DATA_VERSION)),
        (TAG_LIST, "size", list_payload(TAG_INT, [int_payload(sx), int_payload(sy), int_payload(sz)])),
        (TAG_LIST, "palette", list_payload(TAG_COMPOUND, palette)),
        (TAG_LIST, "blocks", list_payload(TAG_COMPOUND, blocks)),
        (TAG_LIST, "entities", list_payload(TAG_END, [])),
    ])
    data = struct.pack(">b", TAG_COMPOUND) + name("") + root
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(out_path, "wb") as f:
        f.write(data)
    print(f"wrote {out_path} ({len(data)} bytes uncompressed)")


def main(out_dir):
    write(f"{out_dir}/empty.nbt", 16, 16, 16, ["minecraft:stone"])
    write(f"{out_dir}/village.nbt", 40, 16, 40, ["minecraft:stone", "minecraft:dirt", "minecraft:dirt", "minecraft:grass_block"])


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/data/renovator/structure")
