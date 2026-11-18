"""Writes the interior designer data: data/stilwerk/stilwerk_interior/modules.json and recipes.json.

Modules are authored in oak with the token COLOR for dye colours. Front faces south (+z), back at z=0 against
the wall. Every category has a vanilla module; Macaw's Furniture (mcwfurnitures) and Refurbished Furniture
(refurbished_furniture) modules are used instead when those mods are installed.

Facing conventions:
  vanilla stairs: facing = side of the backrest (a chair facing north has its back to the north)
  mod furniture:  facing = the side the front looks at (a chair at a desk to the north faces north)
  open trapdoor:  the panel lies on the side opposite to "facing"
"""
import json
from pathlib import Path

OUT = Path("src/main/resources/data/stilwerk/stilwerk_interior")
MCW = "mcwfurnitures"
RF = "refurbished_furniture"

modules = []


def mod(mid, category, placement, size, cells, requires=(), weight=10):
    xs, ys, zs = size
    for x, y, z, _ in cells:
        assert 0 <= x < xs and 0 <= y < ys and 0 <= z < zs, (mid, x, y, z)
    modules.append({
        "id": mid, "category": category, "placement": placement, "size": list(size),
        "requires": list(requires), "weight": weight,
        "blocks": [{"p": [x, y, z], "s": s} for x, y, z, s in cells],
    })


def v(name):
    return "minecraft:" + name


LANTERN = v("lantern[hanging=false]")
BARREL_UP = v("barrel[facing=up]")
BARREL_S = v("barrel[facing=south]")


def row(y, z, states, x0=0):
    return [(x0 + i, y, z, s) for i, s in enumerate(states)]


# ---------------------------------------------------------------- bedroom
bed = [(1, 0, 0, v("COLOR_bed[facing=north,part=head]")), (1, 0, 1, v("COLOR_bed[facing=north,part=foot]"))]
mod("bed_single", "bed", "wall", (3, 2, 2), bed + [
    (0, 0, 0, BARREL_UP), (0, 1, 0, LANTERN), (2, 0, 0, BARREL_UP), (2, 1, 0, v("potted_azure_bluet"))])
mod("bed_double", "bed", "wall", (4, 2, 2), [
    (0, 0, 0, BARREL_UP), (0, 1, 0, LANTERN),
    (1, 0, 0, v("COLOR_bed[facing=north,part=head]")), (1, 0, 1, v("COLOR_bed[facing=north,part=foot]")),
    (2, 0, 0, v("COLOR_bed[facing=north,part=head]")), (2, 0, 1, v("COLOR_bed[facing=north,part=foot]")),
    (3, 0, 0, BARREL_UP), (3, 1, 0, v("potted_red_tulip"))], weight=12)
mod("bed_plain", "bed", "wall", (1, 1, 2), bed and [(0, 0, 0, v("COLOR_bed[facing=north,part=head]")),
                                                     (0, 0, 1, v("COLOR_bed[facing=north,part=foot]"))], weight=2)
mod("bed_single_mcw", "bed", "wall", (3, 2, 2), bed + [
    (0, 0, 0, f"{MCW}:oak_drawer[facing=south]"), (0, 1, 0, LANTERN),
    (2, 0, 0, f"{MCW}:oak_drawer[facing=south]"), (2, 1, 0, v("potted_azure_bluet"))], requires=[MCW])
mod("bed_single_rf", "bed", "wall", (3, 2, 2), bed + [
    (0, 0, 0, f"{RF}:oak_drawer[facing=south]"), (0, 1, 0, f"{RF}:COLOR_lamp"),
    (2, 0, 0, f"{RF}:oak_drawer[facing=south]"), (2, 1, 0, v("potted_fern"))], requires=[RF])

mod("wardrobe_barrels", "wardrobe", "wall", (2, 2, 1), row(0, 0, [BARREL_S, BARREL_S]) + row(1, 0, [BARREL_S, BARREL_S]))
mod("wardrobe_mcw", "wardrobe", "wall", (1, 2, 1), [
    (0, 0, 0, f"{MCW}:oak_wardrobe[facing=south,connection=base,hinge=left]"),
    (0, 1, 0, f"{MCW}:oak_wardrobe[facing=south,connection=top,hinge=left]")], requires=[MCW])
mod("wardrobe_rf", "wardrobe", "wall", (1, 2, 1), [
    (0, 0, 0, f"{RF}:oak_storage_cabinet[facing=south]"), (0, 1, 0, f"{RF}:oak_storage_cabinet[facing=south]")], requires=[RF])

mod("desk_stairs", "desk", "window", (2, 2, 2), [
    (0, 0, 0, v("oak_stairs[facing=north,half=top]")), (1, 0, 0, v("oak_stairs[facing=north,half=top]")),
    (1, 1, 0, v("candle[candles=2,lit=false]")), (0, 0, 1, v("oak_stairs[facing=south]"))])
mod("desk_mcw", "desk", "window", (2, 2, 2), [
    (0, 0, 0, f"{MCW}:oak_desk[facing=north]"), (1, 0, 0, f"{MCW}:oak_desk[facing=north]"),
    (1, 1, 0, LANTERN), (0, 0, 1, f"{MCW}:oak_chair[facing=north]")], requires=[MCW])
mod("desk_rf", "desk", "window", (2, 2, 2), [
    (0, 0, 0, f"{RF}:oak_desk[facing=south]"), (1, 0, 0, f"{RF}:oak_desk[facing=south]"),
    (0, 1, 0, f"{RF}:computer[facing=south]"), (0, 0, 1, f"{RF}:oak_chair[facing=north]")], requires=[RF])

# ---------------------------------------------------------------- living room
mod("sofa_stairs", "sofa", "wall", (5, 1, 3), [
    (0, 0, 0, v("oak_trapdoor[facing=west,open=true,half=bottom]")),
    (1, 0, 0, v("oak_stairs[facing=north]")), (2, 0, 0, v("oak_stairs[facing=north]")), (3, 0, 0, v("oak_stairs[facing=north]")),
    (4, 0, 0, v("oak_trapdoor[facing=east,open=true,half=bottom]")),
    (1, 0, 2, v("oak_trapdoor[facing=north,half=top]")), (2, 0, 2, v("oak_trapdoor[facing=north,half=top]")),
    (3, 0, 2, v("oak_trapdoor[facing=north,half=top]"))])
mod("sofa_mcw", "sofa", "wall", (3, 1, 3), [
    (0, 0, 0, f"{MCW}:COLOR_couch[facing=south]"), (1, 0, 0, f"{MCW}:COLOR_couch[facing=south]"),
    (2, 0, 0, f"{MCW}:COLOR_couch[facing=south]"), (1, 0, 2, f"{MCW}:oak_coffee_table")], requires=[MCW])
mod("sofa_rf", "sofa", "wall", (3, 1, 3), [
    (0, 0, 0, f"{RF}:COLOR_sofa[facing=south]"), (1, 0, 0, f"{RF}:COLOR_sofa[facing=south]"),
    (2, 0, 0, f"{RF}:COLOR_sofa[facing=south]"), (1, 0, 2, f"{RF}:oak_table")], requires=[RF])

mod("media_shelf", "media", "wall", (3, 2, 1), [
    (0, 0, 0, BARREL_UP), (1, 0, 0, v("chiseled_bookshelf[facing=south]")), (2, 0, 0, BARREL_UP),
    (0, 1, 0, v("potted_bamboo")), (2, 1, 0, LANTERN)])
mod("media_rf", "media", "wall", (2, 2, 1), [
    (0, 0, 0, f"{RF}:oak_drawer[facing=south]"), (1, 0, 0, f"{RF}:oak_drawer[facing=south]"),
    (0, 1, 0, f"{RF}:television[facing=south]")], requires=[RF])

mod("shelf_wide", "shelf", "wall", (3, 2, 1), row(0, 0, [v("bookshelf")] * 3) + row(1, 0, [v("bookshelf")] * 3))
mod("shelf_chiseled", "shelf", "wall", (3, 2, 1), row(0, 0, [v("bookshelf"), v("chiseled_bookshelf[facing=south]"), v("bookshelf")])
    + row(1, 0, [v("bookshelf")] * 3))
mod("shelf_tall", "shelf", "wall", (2, 3, 1), [(x, y, 0, v("bookshelf")) for x in range(2) for y in range(3)], weight=8)
mod("shelf_mcw", "shelf", "wall", (3, 2, 1), [(x, y, 0, f"{MCW}:oak_bookshelf[facing=south]") for x in range(3) for y in range(2)],
    requires=[MCW])

mod("lamp_post", "lamp", "corner", (1, 2, 1), [(0, 0, 0, v("oak_fence")), (0, 1, 0, LANTERN)])
mod("lamp_rf", "lamp", "corner", (1, 1, 1), [(0, 0, 0, f"{RF}:COLOR_lamp")], requires=[RF])

for i, plant in enumerate(["potted_azalea_bush", "potted_flowering_azalea_bush", "potted_fern", "potted_bamboo", "potted_red_tulip"]):
    mod(f"plant_{i}", "plant", "corner", (1, 1, 1), [(0, 0, 0, v(plant))])

# ---------------------------------------------------------------- kitchen and dining
mod("counter_barrels", "kitchen_counter", "wall", (2, 1, 1), row(0, 0, [BARREL_UP, BARREL_UP]))
mod("counter_crafting", "kitchen_counter", "wall", (3, 1, 1), row(0, 0, [BARREL_UP, v("crafting_table"), BARREL_UP]), weight=6)
mod("counter_mcw", "kitchen_counter", "wall", (2, 1, 1), row(0, 0, [f"{MCW}:oak_counter[facing=south]"] * 2), requires=[MCW])
mod("counter_mcw_cabinets", "kitchen_counter", "wall", (2, 3, 1), row(0, 0, [f"{MCW}:oak_counter[facing=south]"] * 2)
    + row(2, 0, [f"{MCW}:oak_kitchen_cabinet[facing=south]"] * 2), requires=[MCW], weight=14)
mod("counter_rf", "kitchen_counter", "wall", (3, 1, 1), row(0, 0, [f"{RF}:oak_kitchen_cabinetry[facing=south]",
                                                                    f"{RF}:oak_kitchen_drawer[facing=south]",
                                                                    f"{RF}:oak_kitchen_cabinetry[facing=south]"]), requires=[RF])
mod("stove_smoker", "stove", "wall", (1, 1, 1), [(0, 0, 0, v("smoker[facing=south]"))])
mod("stove_double", "stove", "wall", (2, 1, 1), [(0, 0, 0, v("smoker[facing=south]")), (1, 0, 0, v("furnace[facing=south]"))])
mod("stove_rf", "stove", "wall", (1, 1, 1), [(0, 0, 0, f"{RF}:light_stove[facing=south]")], requires=[RF])
mod("stove_rf_hood", "stove", "wall", (1, 3, 1), [(0, 0, 0, f"{RF}:light_stove[facing=south]"), (0, 2, 0, f"{RF}:light_range_hood[facing=south]")],
    requires=[RF], weight=14)
mod("sink_cauldron", "kitchen_sink", "window", (1, 1, 1), [(0, 0, 0, v("water_cauldron[level=3]"))])
mod("sink_mcw", "kitchen_sink", "window", (1, 1, 1), [(0, 0, 0, f"{MCW}:oak_kitchen_sink[facing=south]")], requires=[MCW])
mod("sink_rf", "kitchen_sink", "window", (1, 1, 1), [(0, 0, 0, f"{RF}:oak_kitchen_sink[facing=south]")], requires=[RF])
mod("fridge_rf", "fridge", "wall", (1, 2, 1), [(0, 0, 0, f"{RF}:light_freezer[facing=south]"), (0, 1, 0, f"{RF}:light_fridge[facing=south]")],
    requires=[RF])

table4 = [(1, 0, 1), (2, 0, 1), (1, 0, 2), (2, 0, 2)]
chairs_v = [(0, 0, 1, "west"), (0, 0, 2, "west"), (3, 0, 1, "east"), (3, 0, 2, "east"), (1, 0, 0, "north"), (2, 0, 0, "north"),
            (1, 0, 3, "south"), (2, 0, 3, "south")]
OPP = {"west": "east", "east": "west", "north": "south", "south": "north"}
mod("dining_table", "dining", "centre", (4, 2, 4), [(x, y, z, v("oak_fence")) for x, y, z in table4]
    + [(x, 1, z, v("oak_pressure_plate")) for x, _, z in table4]
    + [(x, y, z, v(f"oak_stairs[facing={f}]")) for x, y, z, f in chairs_v])
mod("dining_mcw", "dining", "centre", (4, 1, 4), [(x, y, z, f"{MCW}:oak_table") for x, y, z in table4]
    + [(x, y, z, f"{MCW}:oak_chair[facing={OPP[f]}]") for x, y, z, f in chairs_v], requires=[MCW])
mod("dining_rf", "dining", "centre", (4, 1, 4), [(x, y, z, f"{RF}:oak_table") for x, y, z in table4]
    + [(x, y, z, f"{RF}:oak_chair[facing={OPP[f]},tucked=false]") for x, y, z, f in chairs_v], requires=[RF])
mod("dining_small", "dining_small", "centre", (3, 2, 1), [
    (0, 0, 0, v("oak_stairs[facing=west]")), (1, 0, 0, v("oak_fence")), (1, 1, 0, v("oak_pressure_plate")),
    (2, 0, 0, v("oak_stairs[facing=east]"))])
mod("dining_small_mcw", "dining_small", "centre", (3, 1, 1), [
    (0, 0, 0, f"{MCW}:oak_chair[facing=east]"), (1, 0, 0, f"{MCW}:oak_table"), (2, 0, 0, f"{MCW}:oak_chair[facing=west]")],
    requires=[MCW])

# ---------------------------------------------------------------- library, bath, workshop, storage
mod("lectern", "lectern", "window", (1, 1, 1), [(0, 0, 0, v("lectern[facing=south]"))])

mod("bath_cauldrons", "bath", "wall", (2, 1, 1), row(0, 0, [v("water_cauldron[level=3]")] * 2))
mod("bath_rf", "bath", "wall", (2, 1, 1), [(0, 0, 0, f"{RF}:COLOR_bath[facing=east,type=bottom]"),
                                           (1, 0, 0, f"{RF}:COLOR_bath[facing=east,type=head]")], requires=[RF])
mod("toilet_quartz", "toilet", "corner", (1, 1, 1), [(0, 0, 0, v("quartz_stairs[facing=north]"))])
mod("toilet_rf", "toilet", "corner", (1, 1, 1), [(0, 0, 0, f"{RF}:white_toilet[facing=south]")], requires=[RF])
mod("basin_cauldron", "basin", "window", (1, 1, 1), [(0, 0, 0, v("water_cauldron[level=1]"))])
mod("basin_rf", "basin", "window", (1, 1, 1), [(0, 0, 0, f"{RF}:white_basin[facing=south]")], requires=[RF])

mod("workbench", "workshop", "wall", (3, 1, 1), row(0, 0, [v("crafting_table"), v("smithing_table"), v("stonecutter[facing=south]")]))
mod("workbench_loom", "workshop", "wall", (3, 1, 1), row(0, 0, [v("crafting_table"), v("loom[facing=south]"), v("cartography_table")]))
mod("workbench_rf", "workshop", "wall", (2, 1, 1), row(0, 0, [f"{RF}:workbench[facing=south]", v("crafting_table")]), requires=[RF])
mod("forge", "forge", "wall", (3, 1, 1), row(0, 0, [v("blast_furnace[facing=south]"), v("anvil[facing=east]"),
                                                    v("grindstone[face=floor,facing=south]")]))

mod("storage_chests", "storage", "wall", (2, 1, 1), row(0, 0, [v("chest[facing=south,type=single]")] * 2))
mod("storage_barrels", "storage", "wall", (2, 2, 1), row(0, 0, [BARREL_S] * 2) + row(1, 0, [BARREL_S] * 2))
mod("storage_mcw", "storage", "wall", (2, 1, 1), row(0, 0, [f"{MCW}:oak_drawer[facing=south]", f"{MCW}:oak_triple_drawer[facing=south]"]),
    requires=[MCW])
mod("storage_rf", "storage", "wall", (2, 2, 1), row(0, 0, [f"{RF}:oak_crate", f"{RF}:oak_crate"]) + row(1, 0, [f"{RF}:oak_crate", BARREL_UP]),
    requires=[RF])


def slot(category, lo, hi, prio, prefer):
    return {"category": category, "min": lo, "max": hi, "priority": prio, "prefer": prefer}


recipes = [
    {"id": "bedroom", "rug": True, "light": True, "slots": [
        slot("bed", 1, 1, 100, "far_from_door"), slot("wardrobe", 1, 1, 80, "near_wall"), slot("desk", 0, 1, 50, "near_window"),
        slot("storage", 0, 1, 30, "near_wall"), slot("plant", 1, 2, 20, "corner")]},
    {"id": "living", "rug": True, "light": True, "slots": [
        slot("sofa", 1, 1, 100, "near_wall"), slot("media", 0, 1, 70, "near_wall"), slot("shelf", 0, 2, 60, "near_wall"),
        slot("lamp", 0, 1, 40, "corner"), slot("plant", 1, 3, 30, "corner")]},
    {"id": "kitchen", "rug": False, "light": True, "slots": [
        slot("stove", 1, 1, 100, "near_wall"), slot("kitchen_sink", 1, 1, 95, "near_window"), slot("fridge", 0, 1, 90, "near_wall"),
        slot("kitchen_counter", 2, 4, 85, "near_wall"), slot("storage", 0, 1, 50, "near_wall"), slot("dining_small", 0, 1, 40, "centre"),
        slot("plant", 0, 1, 10, "corner")]},
    {"id": "dining", "rug": False, "light": True, "slots": [
        slot("dining", 1, 1, 100, "centre"), slot("storage", 0, 2, 50, "near_wall"), slot("shelf", 0, 1, 40, "near_wall"),
        slot("plant", 1, 3, 20, "corner")]},
    {"id": "library", "rug": True, "light": True, "slots": [
        slot("shelf", 2, 6, 100, "near_wall"), slot("lectern", 1, 1, 80, "near_window"), slot("desk", 0, 1, 70, "near_window"),
        slot("lamp", 0, 2, 40, "corner"), slot("plant", 0, 2, 20, "corner")]},
    {"id": "bath", "rug": False, "light": True, "slots": [
        slot("bath", 1, 1, 100, "near_wall"), slot("toilet", 1, 1, 90, "corner"), slot("basin", 1, 1, 80, "near_window"),
        slot("plant", 0, 1, 20, "corner")]},
    {"id": "workshop", "rug": False, "light": True, "slots": [
        slot("workshop", 1, 1, 100, "near_wall"), slot("forge", 1, 1, 95, "near_wall"), slot("storage", 1, 2, 60, "near_wall"),
        slot("lamp", 0, 1, 20, "corner")]},
    {"id": "storage", "rug": False, "light": True, "slots": [
        slot("storage", 2, 8, 100, "near_wall"), slot("lamp", 0, 1, 10, "corner")]},
]

OUT.mkdir(parents=True, exist_ok=True)
(OUT / "modules.json").write_text(json.dumps(modules, indent=1), encoding="utf-8")
(OUT / "recipes.json").write_text(json.dumps(recipes, indent=1), encoding="utf-8")
cats = sorted({m["category"] for m in modules})
vanilla = {m["category"] for m in modules if not m["requires"]}
missing = [c for c in cats if c not in vanilla and c != "fridge"]
print(f"wrote {len(modules)} modules in {len(cats)} categories, {len(recipes)} recipes; categories without vanilla: {missing}")
