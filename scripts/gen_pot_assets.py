#!/usr/bin/env python3
"""Cultivated Task B6 - one-shot asset generator (blockstates, block/item models, lang, GUI textures).

Invoked once; its OUTPUT is committed. NOT a canonical runtime script - the generated JSON/PNG under
src/main/resources/assets/cultivated/ is the deliverable. Re-run is idempotent (overwrites in place).

The material list below MIRRORS dev.nitjsefnie.cultivated.block.PotMaterials.ALL (61 materials).
PotAssetCoverageTest cross-checks the generated files against PotMaterials.ALL at build time, so any
drift between this list and the Java source is caught by the test suite rather than shipping silently.

Asset schema was verified against the decompiled MC 26.2 client sources:
  - blockstates:  {"variants": {"": {"model": "..."}}}                (BlockStateModelDispatcher)
  - block model:  parent / textures / elements(from,to,faces)         (cuboid.CuboidModel*)
  - render_type:  Fabric-only per-model field (no vanilla equivalent)  (carry-over confirmed fact)
  - item model:   {"model": {"type": "minecraft:model", "model": ...}} loaded from assets/<ns>/items/
                  (ClientItem.CODEC + ClientItemInfoLoader "items" lister)

Requires Pillow for the GUI textures (confirmed available: PIL 12.2.0).
"""
from __future__ import annotations

import json
import os

MODID = "cultivated"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MODID)
DATA = os.path.join(ROOT, "src", "main", "resources", "data", MODID)
LOOT_BLOCKS = os.path.join(DATA, "loot_table", "blocks")
# Base-pot crafting recipes (config-gated). Only BASE pots are craftable; tiered pots come from the
# upgrade items. Kept in a subdir so the ~244 generated files do not clutter recipe/.
RECIPES_POT = os.path.join(DATA, "recipe", "pot")
# Fabric merges this into the vanilla minecraft:mineable/pickaxe block tag, so a pickaxe is the
# effective/fast tool and (with .requiresCorrectToolForDrops()) is required for pot drops.
MINEABLE_PICKAXE_TAG = os.path.join(
    ROOT, "src", "main", "resources", "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"
)

# --- material list: mirror of PotMaterials.ALL (registration order) ---------------------------------
DYE_COLORS = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
]
BRICK_MATERIALS = [
    "bricks", "stone_bricks", "mossy_stone_bricks", "deepslate_bricks", "tuff_bricks",
    "mud_bricks", "prismarine", "nether_bricks", "red_nether_bricks",
    "polished_blackstone_bricks", "end_stone_bricks", "quartz_bricks",
]


def build_materials() -> list[str]:
    materials = ["terracotta"]
    for color in DYE_COLORS:
        materials.append(f"{color}_terracotta")
        materials.append(f"{color}_glazed_terracotta")
        materials.append(f"{color}_concrete")
    materials.extend(BRICK_MATERIALS)
    return materials


MATERIALS = build_materials()

# The three pot variants. Waxed reuses the basic model via a thin parent-only model (SPEC appendix).
POT_TYPES = ["basic", "hopper", "waxed"]

# Phase D §D — upgrade tiers. "" is the implicit BASE tier (existing Phase B ids, no prefix); the three
# tiers prefix every id with elite_/ultra_/mega_. This mirrors dev.nitjsefnie.cultivated.block.Tier.
TIERS = ["", "elite", "ultra", "mega"]


def prefixed(tier: str, name: str) -> str:
    """Prefix a base id with a tier (elite/ultra/mega), or return it unchanged for the BASE tier."""
    return f"{tier}_{name}" if tier else name


def basic_name(material: str, tier: str = "") -> str:
    return prefixed(tier, f"{material}_botany_pot")


def variant_name(material: str, pot_type: str, tier: str = "") -> str:
    if pot_type == "basic":
        base = f"{material}_botany_pot"
    elif pot_type == "hopper":
        base = f"{material}_hopper_botany_pot"
    else:
        base = f"{material}_waxed_botany_pot"
    return prefixed(tier, base)


def write_json(path: str, obj: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, indent="\t", ensure_ascii=False)
        fh.write("\n")


def title_case(name: str) -> str:
    return " ".join(word.capitalize() for word in name.split("_"))


def gen_blockstate(material: str, pot_type: str, tier: str = "") -> None:
    name = variant_name(material, pot_type, tier)
    # Single default variant (SPEC: blockstate = single default variant; facing/level/waterlogged
    # do not change the rendered model).
    obj = {"variants": {"": {"model": f"{MODID}:block/{name}"}}}
    write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), obj)


def gen_block_model(material: str, pot_type: str, tier: str = "") -> None:
    name = variant_name(material, pot_type, tier)
    side_texture = f"minecraft:block/{material}"
    if pot_type == "waxed":
        # Reuse the tier's basic pot geometry + textures by parenting it, but declare render_type
        # explicitly (do not rely on it being inherited through the parent chain).
        obj = {
            "parent": f"{MODID}:block/{basic_name(material, tier)}",
            "render_type": "minecraft:cutout",
        }
    elif pot_type == "hopper":
        obj = {
            "parent": f"{MODID}:block/template/hopper_pot",
            "render_type": "minecraft:cutout",
            "textures": {
                "material": side_texture,
                "material_top": side_texture,
            },
        }
    else:  # basic
        obj = {
            "parent": f"{MODID}:block/template/pot",
            "render_type": "minecraft:cutout",
            "textures": {
                "material": side_texture,
                "material_top": side_texture,
            },
        }
    write_json(os.path.join(ASSETS, "models", "block", f"{name}.json"), obj)


def gen_item_model(material: str, pot_type: str, tier: str = "") -> None:
    name = variant_name(material, pot_type, tier)
    obj = {"model": {"type": "minecraft:model", "model": f"{MODID}:block/{name}"}}
    write_json(os.path.join(ASSETS, "items", f"{name}.json"), obj)


def gen_loot_table(material: str, pot_type: str, tier: str = "") -> None:
    # Self-drop block loot table (survives_explosion), matching the Phase B base-pot schema exactly so
    # regenerating the base tier is byte-identical to the committed files.
    name = variant_name(material, pot_type, tier)
    obj = {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1.0,
                "entries": [
                    {
                        "type": "minecraft:item",
                        "name": f"{MODID}:{name}",
                    }
                ],
                "conditions": [
                    {
                        "condition": "minecraft:survives_explosion",
                    }
                ],
            }
        ],
    }
    write_json(os.path.join(LOOT_BLOCKS, f"{name}.json"), obj)


def gen_mineable_pickaxe_tag() -> list[str]:
    """Write the minecraft:mineable/pickaxe block tag listing every pot variant id.

    Enumerated from the same tier/material/pot-type loops as every other asset, so it can never
    drift from the registered block list. Fabric merges these values into the vanilla tag.
    """
    ids = [
        f"{MODID}:{variant_name(material, pot_type, tier)}"
        for tier in TIERS
        for material in MATERIALS
        for pot_type in POT_TYPES
    ]
    write_json(MINEABLE_PICKAXE_TAG, {"values": ids})
    return ids


# Hand-authored lang keys the generator must preserve on every re-run. These name things that are
# not enumerated by the tier/material/pot-type loops below (the shared container title, the upgrade
# items, tier tooltips). Keeping them here makes gen_lang() idempotent: regenerating en_us.json can
# never silently drop them. Any spawner-soil / spawn-egg display names would be added here too.
LANG_CONTAINER = {
    f"container.{MODID}.botany_pot": "Botany Pot",
}
LANG_ITEMS = {
    f"item.{MODID}.elite_upgrade": "Elite Pot Upgrade",
    f"item.{MODID}.ultra_upgrade": "Ultra Pot Upgrade",
    f"item.{MODID}.mega_upgrade": "Mega Pot Upgrade",
    f"item.{MODID}.hopper_upgrade": "Hopper Pot Upgrade",
}
LANG_TOOLTIPS = {
    f"tooltip.{MODID}.tier.hold_shift": "Hold Shift for tier bonuses",
    f"tooltip.{MODID}.tier.speed": "Growth Speed: %s",
    f"tooltip.{MODID}.tier.output": "Yield Bonus: %s",
}
# Recipe-viewer (REI/EMI) category + tooltip strings — hand-authored, not enumerated by the loops,
# so they are preserved here to keep gen_lang() idempotent (a re-run must not drop them).
LANG_VIEWER = {
    f"category.{MODID}.crop": "Growing",
    f"category.{MODID}.interaction": "Pot Interaction",
    f"gui.{MODID}.viewer.grow_time": "Grow time: %s",
    f"gui.{MODID}.viewer.spawn_egg_drops": "Drops the planted mob's loot",
    f"gui.{MODID}.viewer.consumes_held": "Consumes the held item",
    f"gui.{MODID}.viewer.damages_held": "Damages the held item",
    f"gui.{MODID}.viewer.keeps_held": "Keeps the held item",
}


def gen_lang() -> dict:
    lang: dict[str, str] = {}
    lang[f"itemGroup.{MODID}.botany_pots"] = "Botany Pots"
    lang.update(LANG_CONTAINER)
    for tier in TIERS:
        for material in MATERIALS:
            for pot_type in POT_TYPES:
                name = variant_name(material, pot_type, tier)
                lang[f"block.{MODID}.{name}"] = title_case(name)
    lang.update(LANG_ITEMS)
    lang.update(LANG_TOOLTIPS)
    lang.update(LANG_VIEWER)
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), lang)
    return lang


# --- base-pot crafting recipes (config-gated, vanilla crafting_shaped/shapeless) -------------------
# Only BASE pots are craftable; tiered pots are produced by applying the upgrade items in-world, so
# no tiered crafting recipes are generated. Ingredient is always the vanilla block item
# minecraft:<material> (verified to exist for all 61 materials in MC 26.2). The three config gates
# are resolved by name through the cultivated:config load condition; the names below are the exact
# BOOLEAN_PROPERTIES keys registered in CultivatedConfig.

GATE_BASIC = "can_craft_basic_pots"
GATE_HOPPER = "can_craft_hopper_pots"
GATE_WAXED = "can_wax_pots"


def _config_conditions(gate: str) -> list:
    return [{"condition": f"{MODID}:config", "property": gate}]


def _result(name: str) -> dict:
    return {"id": f"{MODID}:{name}", "count": 1}


def gen_pot_recipes(material: str) -> list[str]:
    """Emit the base-pot crafting recipes for one material; return the recipe ids written."""
    mat_item = f"minecraft:{material}"
    basic_id = basic_name(material)
    hopper_id = variant_name(material, "hopper")
    waxed_id = variant_name(material, "waxed")
    written: list[str] = []

    # Basic: shaped ring of material around a flower pot.
    basic = {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"M": mat_item, "P": "minecraft:flower_pot"},
        "pattern": ["M M", "MPM", " M "],
        "result": _result(basic_id),
        "fabric:load_conditions": _config_conditions(GATE_BASIC),
    }
    write_json(os.path.join(RECIPES_POT, f"{basic_id}.json"), basic)
    written.append(f"{MODID}:pot/{basic_id}")

    # Hopper (a): shapeless hopper + basic pot.
    hopper_shapeless = {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:hopper", f"{MODID}:{basic_id}"],
        "result": _result(hopper_id),
        "fabric:load_conditions": _config_conditions(GATE_HOPPER),
    }
    write_json(os.path.join(RECIPES_POT, f"{hopper_id}.json"), hopper_shapeless)
    written.append(f"{MODID}:pot/{hopper_id}")

    # Hopper (b): "quick" shaped variant with a hopper on top of the material ring.
    hopper_quick = {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"M": mat_item, "H": "minecraft:hopper", "P": "minecraft:flower_pot"},
        "pattern": ["MHM", "MPM", " M "],
        "result": _result(hopper_id),
        "fabric:load_conditions": _config_conditions(GATE_HOPPER),
    }
    write_json(os.path.join(RECIPES_POT, f"{hopper_id}_quick.json"), hopper_quick)
    written.append(f"{MODID}:pot/{hopper_id}_quick")

    # Waxed: shapeless honeycomb + basic pot.
    waxed_shapeless = {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:honeycomb", f"{MODID}:{basic_id}"],
        "result": _result(waxed_id),
        "fabric:load_conditions": _config_conditions(GATE_WAXED),
    }
    write_json(os.path.join(RECIPES_POT, f"{waxed_id}.json"), waxed_shapeless)
    written.append(f"{MODID}:pot/{waxed_id}")

    return written


# --- GUI textures (functional placeholders via Pillow) ---------------------------------------------
PANEL_BG = (198, 198, 198, 255)
PANEL_HL = (255, 255, 255, 255)
PANEL_SH = (85, 85, 85, 255)
SLOT_BG = (139, 139, 139, 255)
LABEL = (64, 64, 64, 255)


def _bevel(draw, x0, y0, x1, y1, light, dark):
    # top + left highlight, bottom + right shadow (vanilla-style 1px bevel)
    draw.line([(x0, y0), (x1 - 1, y0)], fill=light)
    draw.line([(x0, y0), (x0, y1 - 1)], fill=light)
    draw.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=dark)
    draw.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=dark)


def _slot(draw, x, y):
    # 18x18 sunken slot at (x-1,y-1); the 16x16 item area starts at (x,y)
    draw.rectangle([x - 1, y - 1, x + 16, y + 16], fill=SLOT_BG)
    _bevel(draw, x - 1, y - 1, x + 17, y + 17, PANEL_SH, PANEL_HL)


def gen_container_bg(path: str, input_slots: list[tuple[int, int]], output_grid: bool) -> None:
    from PIL import Image, ImageDraw

    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # 176x166 panel
    draw.rectangle([0, 0, 175, 165], fill=PANEL_BG)
    _bevel(draw, 0, 0, 176, 166, PANEL_HL, PANEL_SH)

    for (sx, sy) in input_slots:
        _slot(draw, sx, sy)

    if output_grid:
        for row in range(3):
            for col in range(4):
                _slot(draw, 80 + col * 18, 17 + row * 18)

    # player inventory (3x9) + hotbar (1x9) at (8,84) / (8,142)
    for row in range(3):
        for col in range(9):
            _slot(draw, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        _slot(draw, 8 + col * 18, 142)

    img.save(path)


def gen_slot_sprite(path: str, glyph: str) -> None:
    from PIL import Image, ImageDraw

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # faint icon so the empty-slot hint is visible but unobtrusive
    if glyph == "soil":
        draw.rectangle([2, 9, 13, 13], fill=(110, 74, 44, 160))  # dirt strip
        draw.rectangle([2, 7, 13, 9], fill=(90, 140, 60, 160))   # grass top
    elif glyph == "seed":
        draw.ellipse([5, 4, 10, 12], fill=(150, 130, 70, 160))   # seed
        draw.line([(8, 4), (8, 1)], fill=(90, 140, 60, 160))     # sprout
    elif glyph == "hoe":
        draw.line([(4, 12), (11, 3)], fill=(120, 80, 40, 180), width=2)  # handle
        draw.rectangle([9, 2, 13, 4], fill=(160, 160, 170, 180))         # head
    img.save(path)


def gen_gui_textures() -> list[str]:
    made = []
    container = os.path.join(ASSETS, "textures", "gui", "container")
    slots = os.path.join(ASSETS, "textures", "gui", "sprites", "container", "slot")
    os.makedirs(container, exist_ok=True)
    os.makedirs(slots, exist_ok=True)

    # Basic: soil(80,48) seed(80,22). Hopper: soil(44,48) seed(44,22) tool(18,35) + 4x3 outputs.
    basic_bg = os.path.join(container, "basic_pot.png")
    hopper_bg = os.path.join(container, "hopper_pot.png")
    gen_container_bg(basic_bg, [(80, 48), (80, 22)], output_grid=False)
    gen_container_bg(hopper_bg, [(44, 48), (44, 22), (18, 35)], output_grid=True)
    made += [basic_bg, hopper_bg]

    for glyph in ("soil", "seed", "hoe"):
        p = os.path.join(slots, f"{glyph}.png")
        gen_slot_sprite(p, glyph)
        made.append(p)
    return made


def main() -> None:
    for tier in TIERS:
        for material in MATERIALS:
            for pot_type in POT_TYPES:
                gen_blockstate(material, pot_type, tier)
                gen_block_model(material, pot_type, tier)
                gen_item_model(material, pot_type, tier)
                gen_loot_table(material, pot_type, tier)

    recipes: list[str] = []
    for material in MATERIALS:
        recipes.extend(gen_pot_recipes(material))

    lang = gen_lang()
    gui = gen_gui_textures()
    mineable = gen_mineable_pickaxe_tag()

    per_tier = len(MATERIALS) * len(POT_TYPES)
    n = per_tier * len(TIERS)
    tiered = per_tier * (len(TIERS) - 1)
    print(f"materials={len(MATERIALS)} tiers={len(TIERS)} (base + {len(TIERS) - 1}) variants={n} (base {per_tier} + tiered {tiered})")
    print(f"blockstates={n} block_models={n} item_models={n} loot_tables={n}")
    print(f"mineable/pickaxe tag ids={len(mineable)}")
    print(f"pot_crafting_recipes={len(recipes)} (basic {len(MATERIALS)} + hopper {2 * len(MATERIALS)} + waxed {len(MATERIALS)})")
    print(f"lang_entries={len(lang)} (1 item group + {n} blocks)")
    print(f"gui_textures={len(gui)}: " + ", ".join(os.path.relpath(p, ROOT) for p in gui))


if __name__ == "__main__":
    main()
