# Cultivated

A Fabric mod for **Minecraft 26.2** that adds **Botany Pots** — decorative planters that
grow crops, trees, ores, and even mobs from a soil and a seed. No farmland, no water, and no
light level required: drop in the right soil and seed, and the pot grows it on its own.

## Features

### Pots

Three kinds of pot, each crafted in **~61 materials** (terracotta, glazed terracotta, and
concrete in every dye color, plus brick/stone/quartz-style variants):

- **Basic** — right-click the pot to harvest it once the crop is mature.
- **Hopper** — auto-harvests into a 12-slot internal buffer and pushes the drops into the
  inventory or container directly below (chest, barrel, hopper, …). Also has a **tool slot**.
- **Waxed** — purely decorative: it always displays its crop full-grown and never harvests.

Pots are mined with a **pickaxe** and drop themselves when broken.

### Tiers & upgrades

Pots come in three optional tiers — **elite**, **ultra**, and **mega** — that grow faster and
yield more (the bonuses are additive and fully config-driven). You can upgrade a pot in place
without losing its contents:

- Right-click a pot with an **`elite_upgrade`**, **`ultra_upgrade`**, or **`mega_upgrade`**
  item to raise its tier.
- Right-click a basic pot with a **`hopper_upgrade`** to convert it into a hopper pot.

### The tool slot

A hopper pot's tool slot accepts a harvest tool — hoes, axes, pickaxes, shovels, swords, and
shears (anything in the `cultivated:harvest_items` tag). The tool acts as the loot-context
tool for harvests, so enchantments like **Silk Touch** and **Fortune** change what the pot
drops. A tool's growth/yield attributes and the **`increase_pot_growth`** enchantment further
speed up growth and boost yield.

### Content

Cultivated ships a large default set of:

- **Soils** — with per-soil growth-speed and yield modifiers.
- **Crops** — the full range of vanilla growables.
- **Trees** — plant a sapling and harvest the tree's products.
- **Ores** — grow raw metals and ores.

Everything is **data-driven via recipe JSON**, so datapacks can add more soils, crops, trees,
and ores (including content from other mods).

### Interactions

Soils react to some items in-world. For example:

- Use a **hoe** on a pot holding dirt or grass soil to till it into farmland for faster growth.
- Use a **water bucket** on a lava-bucket soil to turn it into obsidian.

### Growing mobs

Put a **spawner** in the soil slot and any **spawn egg** (vanilla or modded) in the seed slot,
and the pot grows that mob. When harvested it drops the mob's loot — armored variants have the
usual small chance to drop their gear, and there's a **0.1% chance** to drop the spawn egg back.

### Growth & `/tick rate`

Growth advances **per game tick**, so vanilla's `/tick rate` (and `/tick freeze`/`/tick step`)
speeds up, slows down, or pauses every pot in the world.

## Usage

1. Craft a botany pot in the material you like.
2. Place it, then put a **soil** in the soil slot and a matching **seed** in the seed slot.
3. Wait for it to grow. Right-click a basic pot to harvest, or let a hopper pot harvest into
   the container below.
4. Optionally slot a tool into a hopper pot's tool slot, or apply a tier upgrade.

## Config

Settings live in a commented JSON file at **`config/cultivated.json`**. It covers global
growth/yield multipliers, per-tier speed and output, crafting gates, render distance and
animation toggles, the default harvest tool, and more.

## Commands

Owner-only debug commands under **`/cultivated debug`**:

- **`missing seeds`** / **`missing soils`** — list registry items that grow / act as soil but
  have no recipe yet (optionally generate the JSON).
- **`check_crops`** — live-harvest every cached crop through a temporary pot to sanity-check drops.
- **`place_seeds`** — fill a grid of display pots with every crop and soil.
- **`perftest [count] [tiered]`** — spawn a field of active pots for FPS/tick profiling.

## Building

Requires **JDK 25**.

```bash
./gradlew build
```

## License

MIT — see [`LICENSE`](LICENSE).
