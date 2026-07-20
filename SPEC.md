# "Cultivated" — Behavior & Data-Model Specification (Clean-Room)

A from-scratch, **Fabric-only, Minecraft 26.2, MIT-licensed** reimplementation of the
abandoned LGPL "BotanyPots" ecosystem (core + Tiers + Trees, and a planned Ores
extension), with **no dependency on the Bookshelf library** — all borrowed utilities
are reimplemented in-mod.

This document describes **what the system does** and **the data/JSON contract**, not how
the original was coded. JSON field names and datapack type-path segments are reproduced
verbatim because they are the data contract; Java symbols/layout from the original are not.

Throughout, `<ns>` denotes the mod's own namespace (choose your own, e.g. `cultivated`).
Where drop-in compatibility with existing BotanyPots datapacks is desired, keep the literal
`botanypots` namespace for recipe/type/tag ids; otherwise use `<ns>`. This is a launch
decision — see §A.9.

---

## 0. Concept Overview

A **botany pot** is a decorative planter block that grows a crop from a **soil + seed**
pair without farmland, water, or light rules. The player (or a datapack author) supplies:

- a **soil** item in the soil slot,
- a **seed** item in the seed slot.

If a registered **crop** recipe accepts that seed, and the crop's accepted-soil test accepts
the soil, the crop grows over time. When mature it can be **harvested** — manually (basic pot)
or automatically (hopper pot) — producing item drops.

Everything about which items are soils/seeds, how long crops take, what they drop, and how
they look while growing is **data-driven via recipe JSON files** loaded through the vanilla
recipe system. The mod ships a large default datapack plus cross-mod compatibility content.

Three physical pot variants exist:

- **Basic** — grows crops; the player right-clicks to harvest when mature.
- **Hopper** — grows crops; auto-harvests into an internal 12-slot buffer and pushes items
  into the inventory below; has an extra harvest-tool slot.
- **Waxed** — purely decorative; always renders the crop fully grown; never ticks or grows.

Add-ons extend this: **Tiers** adds faster/higher-yield pot tiers + upgrade items; **Trees**
is a pure datapack that grows saplings into tree-product drops; **Ores** (planned) grows ore
products.

---

## Phase A — Data / Recipe Model + Loading (the heart)

### A.1 Recipe kinds and how they are stored

Four data-driven "recipe" kinds drive the mechanic. Each is a distinct **custom recipe type**
registered into the vanilla recipe registry, so files live under
`data/<pack>/recipe/<...>/<name>.json` and are loaded/reloaded by the vanilla recipe manager.
Each JSON has a `"type"` field naming its serializer.

| Kind | `type` value(s) | Purpose |
|------|-----------------|---------|
| Soil | `<ns>:soil`, `<ns>:block_derived_soil` | Marks an item as a valid soil; provides growth/yield/light modifiers + display |
| Crop | `<ns>:crop`, `<ns>:block_derived_crop` | Marks an item as a seed; defines grow time, accepted soils, drops, display, light, yield |
| Fertilizer | `<ns>:fertilizer` | Held item that adds instant growth (bone-meal-like) |
| Pot interaction | `<ns>:pot_interaction` | Held item that transforms the pot's soil/seed (e.g. water bucket + lava → obsidian) |

All four share a common base behavior:
- They are "special" recipes that produce no crafting-grid result (assemble → empty).
- Their **input** is a *pot context* (see §B.4), not a crafting grid.
- Each exposes two matching tests:
  - **couldMatch(candidate item, context)** — a *cheap, intrinsic* test on a single item
    (ingredient membership only; no world/NBT/components). Used for cache indexing & lookup.
  - **matches(context)** — the *full* contextual test (soil present, pot predicate, etc.).
- Each may contribute a **hover tooltip** shown when its item is hovered in the pot GUI.

### A.2 The lookup cache (matching engine)

Recipe lookup must be fast (pots tick constantly, GUIs query per-frame). Implement a
per-recipe-kind cache, rebuilt whenever recipes reload, separately for client and server:

1. On reload, collect all recipes of the kind.
2. A recipe is **cacheable** if its match depends only on intrinsic item identity/tags
   (id, tag membership) — never on world, player, components, or the rest of the pot. All
   the built-in basic recipes are cacheable. (Design a recipe as non-cacheable only if it
   carries context-sensitive conditions.)
3. For every item in the item registry, take its default instance; for each cacheable recipe
   whose `couldMatch` accepts that item, index the recipe under that item. Non-cacheable
   recipes go into a separate "uncached" list.
4. **Lookup(item, context):** iterate the recipes indexed under `item.getItem()` and return
   the first whose `couldMatch` passes; if none, scan the uncached list. (Returns the recipe;
   the caller then confirms with the full `matches` when it needs the contextual guarantee.)

Also expose: `isCached(item)` (has ≥1 indexed recipe — used by the "missing" command to know
if an item is already a soil/crop), and access to all indexed values (used by debug commands).

The active crop/soil for a pot is memoized per-pot with a reloadable cache that **invalidates**
when the slot item changes or when `matches` fails, so a stale recipe never lingers.

### A.3 Soil recipe schema

**`<ns>:soil`** (explicit):

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `input` | Ingredient | — (required) | Item(s) accepted as this soil |
| `display` | Display object (§C) | — (required) | What is rendered in the pot's soil layer |
| `growth_modifier` | float | `0.0` | Added to the growth-rate divisor (positive = faster; see A.7) |
| `light_level` | int | `0` | Light the soil emits |
| `yield_modifier` | float ≥ 0 | `0.0` | Added to the crop's drop chance (see A.8) |

**`<ns>:block_derived_soil`** (convenience — derive fields from a block):

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `block` | block id | — (required) | Block to derive from |
| `input` | Ingredient | *block's item* | Override the accepted item |
| `display` | Display | *simple state of block's default state, top face only* | Override display |
| `growth_modifier` | float | `0.0` | as above |
| `light_level` | int | `0` | as above |
| `yield_modifier` | float ≥ 0 | `0.0` | as above |
| `render_options` | Options (§C.6) | default (up face) | Options for the derived display |

Semantics: soil provides growth modifier, light level, display, yield modifier, and an
optional per-tick hook. Example (`farmland`): `{"type":"<ns>:block_derived_soil",
"block":"minecraft:farmland","growth_modifier":0.10}` → 10% faster growth. A fluid soil is a
simple display of `minecraft:water`/`minecraft:lava` with `render_fluid:true`. A minimal soil
is just `{"type":"<ns>:block_derived_soil","block":"minecraft:dirt"}`.

### A.4 Crop recipe schema

**`<ns>:crop`** (explicit):

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `input` | Ingredient | — (required) | The seed item(s) that plant this crop |
| `soil` | Ingredient | tag `<ns>:soil/dirt` | Which soils **sustain** growth (crop only grows when the pot's soil item matches) |
| `grow_time` | int ≥ 1 | `1200` | Base ticks to mature under neutral modifiers |
| `display` | list of Display (§C) | — (required) | Bottom-up stack of displays rendered as the crop |
| `light_level` | int 0–15 | `0` | Light the crop emits |
| `drops` | list of Drop Providers (§A.6) | `[]` | Produced on harvest |
| `function` | resource id | absent | mcfunction run server-side on harvest |
| `pot_predicate` | Block Predicate | absent | Restricts which pot block the crop may grow in |
| `yield` | float ≥ 0 | `1.0` | Base drop chance (1.0 = 100%; >1 enables multiple rolls) |
| `yield_scale` | float ≥ 0 | `1.0` | Multiplier applied to *external* yield modifiers (soil/pot/tool) before they add to `yield` |

**`<ns>:block_derived_crop`** (convenience — derive from a growable block):

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `block` | block id | — (required) | Block to derive seed/display/drops from |
| `input` | Ingredient | *derived seed* | Override the seed (see derivation below) |
| `soil` | Ingredient | tag `<ns>:soil/dirt` | as above |
| `grow_time` | int ≥ 1 | `1200` | as above |
| `display` | list of Display | *auto (see below)* | Override display |
| `light_level` | int 0–15 | `0` | as above |
| `drops` | list of Drop Providers | *auto: block_state drops of harvest state* | Override drops |
| `render_options` | Options | default | Options for auto display |
| `function`, `pot_predicate`, `yield`, `yield_scale` | | as above | |

**Derivation rules for `block_derived_crop`:**
- **Seed:** if the block is a vanilla-style crop block with a seed item, use it; else use the
  block's own item; else error.
- **Display (auto):** if the block state has a `half` (top/bottom) or `double_block_half`
  (upper/lower) property → two stacked simple displays (lower then upper). Otherwise an
  **aging** display built from the block (see §C.3).
- **Drops (auto):** a **block_state** drop provider using the block's **harvest state** =
  its max-age state, additionally set `berries=true` if present and the down face on a
  multiface block, so the loot matches a fully-grown, harvestable plant.

**Sustained growth:** a crop keeps growing only while the pot's soil item matches the crop's
`soil` ingredient (`isGrowthSustained`). If soil is wrong, growth pauses (and the GUI tooltip
warns "wrong soil"). A crop that has reached max age still counts as sustained.

Examples (from the shipped datapack):
```json
// simplest possible crop
{"type":"<ns>:block_derived_crop","block":"actuallyadditions:canola"}
// mushroom: custom soil test + loot-table drops
{"type":"<ns>:block_derived_crop","block":"minecraft:red_mushroom",
 "soil":{"type":"<ns>:either","ingredients":[
   {"type":"<ns>:block_tag","tag":"minecraft:mushroom_grow_block"},
   {"tag":"<ns>:soil/mushroom"}]},
 "drops":[{"type":"<ns>:loot_table","table_id":"<pack>:.../crop/red_mushroom"}]}
// sculk: slow, tag soil, loot table
{"type":"<ns>:block_derived_crop","block":"minecraft:sculk",
 "soil":{"tag":"<ns>:soil/sculk"},"grow_time":2400,
 "drops":[{"type":"<ns>:loot_table","table_id":"<pack>:.../crop/sculk_bloom"}]}
// multi-input seed + explicit transitional display (see §C)
{"type":"<ns>:block_derived_crop","block":"allthemodium:ancient_cavevines",
 "display":{"type":"<ns>:transitional","phases":[ ...simple states by age... ]},
 "input":[{"item":"...soulberries"},{"item":"...cavevines_plant"}]}
```

### A.5 Fertilizer recipe schema (`<ns>:fertilizer`)

Represents a held item that, when right-clicked on a pot, instantly advances growth.

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `held_item` | Ingredient | — (required) | Item used in hand |
| `soil_item` | Ingredient | absent | Optional: only if pot's soil matches |
| `seed_item` | Ingredient | absent | Optional: only if pot's seed matches |
| `growth` | Growth Amount (§A.10) | — (required) | How much growth to add |
| `cooldown` | int | `20` | Ticks before the pot can be fertilized again |
| `spawn_particles` | bool | `true` | Show bee-growth particles |
| `notify_sculk` | bool | `true` | Emit a sculk-detectable block-change game event |
| `sound_effect` | Sound Effect (§A.12) | absent | Sound played |

**Behavior:** applies only when the pot's own bone-meal cooldown has elapsed. Adds `growth`
ticks to the pot's accumulated growth, but **clamped so it can never exceed
`requiredGrowthTicks − 20`** — i.e. fertilizer can bring a crop close to done but never
finishes the final 20 ticks, and does nothing if the crop needs ≤ 20 ticks total or is
already within 20 of done. Sets the pot's bone-meal cooldown to `cooldown`, plays effects,
and consumes one held item unless the player is in creative.
Example (`bone_meal`): `growth` = ranged 400–600, sound `item.bone_meal.use`.

### A.6 Item drop providers (crop outputs)

`drops` is a list of drop providers; on harvest, **each roll** runs **every** provider in
order. Each provider `type`:

| `type` | Fields | Behavior |
|--------|--------|----------|
| `<ns>:items` | `items`: list of `{result: ItemStack, chance: 0..1 = 1.0}` | Each entry drops its stack if an independent `chance` roll succeeds |
| `<ns>:loot_table` | `table_id`: resource id | Rolls the loot table with the pot's loot context |
| `<ns>:block` | `block`: block id | Uses the block's own loot table (harvest state = block default); falls back to seed/block item if the table is empty/missing |
| `<ns>:block_state` | `block_state`: block state | Like `block` but with a specific state (e.g. max-age + berries) so loot conditions on age/berries pass |
| `<ns>:entity` | `entity`: NBT, `damage_source`: DamageType id (optional) | Derives a living entity from NBT and rolls its death loot table |

Providers also expose a **display item list** (unique possible outputs) for recipe viewers
(JEI). Loot-table-based providers compute this by enumerating the table's possible item
entries. The loot context supplies the pot's block state, origin, and the harvest tool
(pot's tool slot, or the config default tool if empty) — so silk-touch/fortune-style loot
works via the tool.

### A.7 Growth-time formula (required ticks)

```
divisor = global_growth_modifier            // config, default 1.0
        + soil.growth_modifier               // 0 if no soil
        + tool_efficiency_modifier           // see below
        + pot.growth_modifier                // 0 for base pots; tier speed multiplier for tiered pots
requiredGrowthTicks = floor(crop.grow_time / divisor)

tool_efficiency_modifier =
      highestEnchantLevel(tag <ns>:increase_pot_growth, harvestTool) * efficiency_growth_modifier   // config, default 0.05
    + attributeValue("<ns>:growth", harvestTool)   // sum of the tool's "growth" item-attribute modifiers
```
Higher divisor → fewer required ticks → faster. Note tiers contribute their **speed
multiplier** additively into the divisor (so an "elite ×2" pot yields divisor ≈ 1 + 2 = 3,
i.e. ~3× base speed — see §D).

### A.8 Yield formula (drop rolls)

```
totalYield = crop.yield                                   // base, default 1.0
           + yield_scale * pot.yield_modifier             // 0 for base pots; tier output multiplier for tiered
           + yield_scale * soil.yield_modifier
           + yield_scale * attributeValue("<ns>:yield", harvestTool)

rolls: guaranteed = floor(totalYield); then +1 roll with probability = frac(totalYield); min 0
```
So 5.5 → 5 guaranteed rolls + 50% chance of a 6th. Each roll runs all drop providers.

**Important asymmetry (describe as-is):** the multi-roll yield system is applied **only by the
hopper pot's automatic harvest**. A **basic pot's manual harvest runs the drop providers
exactly once** regardless of `totalYield`. The reimplementation should preserve this unless a
deliberate design change is chosen (recommend documenting it as a known quirk).

### A.9 Load conditions (data-gating)

Any recipe (and other data JSON) may carry a **load-conditions** array (original key
`bookshelf:load_conditions`; generalize to a `<ns>:load_conditions` key + support the legacy
key for compatibility). If any condition fails, the file is skipped at load. Condition
`type`s used by the datapack:

- `<ns>:block_exists` — `{"values":["<block id>", ...]}` load only if block(s) exist.
- `<ns>:item_exists` — same for items.
- `<ns>:either` — `{"ingredients":[...]}` — also used as an **Ingredient type** (matches any
  of several sub-ingredients); paired with `<ns>:block_tag` ingredient (matches an item whose
  block is in a block tag).
- `<ns>:config` — `{"property":"<name>"}` load only if a named boolean config property is
  true. Built-in property names: `can_craft_basic_pots`, `can_craft_hopper_pots`,
  `can_wax_pots` (Tiers adds per-tier `can_craft_<tier>_basic_pots`, etc.). This gates the
  pot **crafting** recipes so config can disable crafting a variant.

You must reimplement: a condition-type registry, JSON parsing of the conditions key on recipe
load, and the custom `either`/`block_tag` ingredient types and the `config` condition.

### A.10 Growth-amount types (used by fertilizer `growth`)

Registered, dispatchable-by-`type` values:

| `type` | Fields | Result |
|--------|--------|--------|
| `<ns>:constant` | `amount`: int ≥ 0 | Fixed ticks |
| `<ns>:ranged` | `min`, `max`: int ≥ 0 | Random int in `[min,max]` |
| `<ns>:percent` | `amount`: float 0..1 | `floor(requiredGrowthTicks * amount)` ticks |

### A.11 Item overrides (data components)

Two item **data components** let a *specific* item stack override its recipe-based behavior:

- **Crop override** — attaches a full crop definition to a stack; that stack grows that crop
  regardless of any `<ns>:crop` recipe (e.g. "a special dirt that grows diamonds").
- **Soil override** — attaches a full soil definition to a stack.

Component ids: `<ns>:crop`, `<ns>:soil`. When a pot resolves its crop/soil it checks the slot
item's override component first, falling back to the recipe cache. Serialized (persistent +
networked) as an embedded recipe of the matching kind.

### A.12 Shared value types

- **Sound Effect:** `{id: sound event, category: sound source = "MASTER", volume: float = 1.0,
  pitch: float = 1.0}`.
- **Ingredient:** vanilla ingredient plus the custom `either` / `block_tag` types (§A.9).
- All schemas are serialized with map-codecs (JSON) **and** stream-codecs (network sync), so
  clients receive the full recipe/soil/crop data for tooltips, JEI, and rendering.

### A.13 Datapack scope note

The shipped datapack contains the vanilla crop/soil/fertilizer/interaction JSONs plus **~2000
cross-mod compatibility files** under `recipe/<othermod>/…`, each guarded by `block_exists`/
`item_exists` load conditions. Treat these as bulk datapack content — the engine must load
them, but they need not be enumerated in the spec.

---

## Phase B — Block + Block Entity + Menu

### B.1 The pot block

- **Variants:** three pot **types** — Basic, Hopper, Waxed — each generated for many
  materials: `terracotta`; for every dye color: `<color>_terracotta`, `<color>_glazed_terracotta`,
  `<color>_concrete`; and brick materials: `bricks, stone_bricks, mossy_stone_bricks,
  deepslate_bricks, tuff_bricks, mud_bricks, prismarine, nether_bricks, red_nether_bricks,
  polished_blackstone_bricks, end_stone_bricks, quartz_bricks`. Block ids follow
  `<material>_botany_pot`, `<material>_hopper_botany_pot`, `<material>_waxed_botany_pot`.
  Map color follows the source material's block (fallback orange).
- **Block states:** `waterlogged` (bool), `level` (0–15, drives emitted light), and
  `horizontal_facing` (placement-derived; opposite of the nearest looking direction).
- **Shape / collision:** a single box from (2,0,2) to (14,8,14) — an 8-tall planter occupying
  the middle of the block. Not full-cube: `noOcclusion`, cutout render layer.
- **Hardness / resistance:** ~1.25 / ~4.2. No special tool required.
- **Waterloggable:** provides water fluid state when waterlogged; propagates skylight only
  when not water-filled.
- **Render shape:** MODEL (a real block model, not a BE-only render); block-entity renderer
  draws the soil/crop on top (§C).
- **Light:** emitted light comes from the `level` block-state property. The active crop and
  soil each declare a `light_level`; the reimplementation should drive the `level` property
  from the max of the current crop/soil light (the exact wiring was not visible in the shared
  common code — implement it to emit max(crop light, soil light), 0 when empty).
- **Comparator:** analog output = the pot's stored comparator level (§B.3): 0 while empty/early,
  scaling up while growing, 15 when mature.
- **On break:** drops its container contents; self-drops via a simple loot table.

### B.2 Right-click interaction order (Basic & Hopper; Waxed ignores all)

On use-item-on (server authority), resolve in this order and stop at the first that applies:
1. **Harvest (Basic only):** if the pot is a Basic pot and the crop is mature/harvestable →
   reset growth, run the crop's drops **once**, pop them into the world, emit a block-change
   game event.
2. **Fertilizer:** if the held item matches a fertilizer recipe (and it `matches` the context)
   → apply it (§A.5).
3. **Pot interaction:** if the held item matches a pot-interaction recipe → apply it (§B.6).
4. **Open menu:** otherwise open the pot's container GUI.

### B.3 Block entity — stored state

A container block entity (loot-table-capable, world-aware container):

- **Slots (15 total):** `SOIL=0`, `SEED=1`, `TOOL=2`, and `3..14` = 12 output/storage slots.
- **Persistent fields:** the 15-slot item list; `growth_time` (float accumulator);
  `comparator_level` (int, server-only — stripped from client sync); `export_delay`,
  `grow_cooldown` (float accumulators); `bonemeal_cooldown` (int).
- **Accumulators:** growth/cooldown counters advance by a **fixed step per game tick**,
  independent of the world's tick rate (see §G #4), so **growth advances per game tick; `/tick
  rate` affects growth speed** — a higher rate grows crops faster, a lower rate slower, and
  `/tick freeze` pauses growth (block entities do not tick while frozen). *User decision
  2026-07-20, supersedes the original real-time-stable design.*
- **Change hooks:** setting the soil or seed slot **resets** the pot (clear growth, invalidate
  cached crop/soil, reset comparator, clear bone-meal cooldown) and marks it updated; setting
  the tool slot just marks updated.
- **Automation faces:** only the storage slots (3..14) are extractable, and only through the
  **down** face, and only on **hopper** pots. Nothing can be inserted through any face by
  automation.
- **Sync:** the update tag sends only slots ≤ TOOL (soil/seed/tool) to clients (output buffer
  is not needed for rendering) and omits the comparator level.
- **MCFunction:** the crop's optional `function` is executed server-side with a command source
  positioned at the pot (used by advanced crop authors).

### B.4 Pot context (the recipe input)

Recipes receive a **context** object, not a container. Two implementations:
- **Live context** — wraps a real pot BE (+ optional player + hand). Exposes soil/seed/tool
  items, the held interaction item, the current crop/soil, required growth ticks, whether
  we're on the server thread, and a way to build loot params / run functions.
- **Display context** — a client-only, immutable simulated inventory used by JEI/GUI tooltips
  (no world writes; loot/functions throw).

### B.5 Growth tick loop (server + client both tick; writes are server-guarded)

Each tick, for a non-waxed pot (waxed pots force growth to "max" and return immediately):
1. Decrement bone-meal cooldown if > 0.
2. Resolve soil; run its optional per-tick hook.
3. Resolve crop; run its per-tick hook. Then:
   - Tick down `grow_cooldown` if > 0.
   - If `grow_cooldown ≤ 0` **and** the crop's growth is sustained (correct soil):
     advance `growth_time` by one fixed per-game-tick step (see §G #4); run the crop's growth-tick hook;
     compute `requiredGrowthTicks` (§A.7).
     - If `growth_time ≥ required` → set comparator to 15; set `grow_cooldown = 5` (re-check
       maturity every 5 ticks). If **hopper** and harvestable: on the server, compute yield
       rolls (§A.8) and for each roll run the crop's drops **into the storage slots**; damage
       the harvest tool by 1 (if `damage_harvest_tool` config and the tool lacks the
       `<ns>:negate_harvest_damage` enchantment); emit a block-change event; **reset** growth.
       (A basic pot simply holds at mature with comparator 15 until manually harvested.)
     - Else (still growing) → comparator = `ceil(14 * growth_time / required)`.
4. **Hopper export:** every `export_delay`, if the block below is not air, push each storage
   slot's items into the inventory below (through its up face), writing back the remainder.

### B.6 Pot-interaction recipe (`<ns>:pot_interaction`) behavior

Transforms the pot in place when the held item (and optional soil/seed constraints) match.

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `held_item` | Ingredient | — (required) | Item used in hand |
| `damage_held` | bool | `true` | Damage the held item by 1 |
| `consume_held` | bool | `false` | Consume 1 held item (only if not damaging) |
| `soil_item` | Ingredient | absent | Optional constraint on current soil |
| `seed_item` | Ingredient | absent | Optional constraint on current seed |
| `new_soil` | ItemStack | absent | Replace the pot's soil with this (drops old soil remainder) |
| `new_seed` | ItemStack | absent | Replace the pot's seed with this |
| `extra_drops` | loot table id | absent | Roll this table and pop the items |
| `sound_effect` | Sound Effect | absent | Sound |
| `notify_sculk` | bool | `true` | Emit block-change game event |

Examples: water bucket on a lava-bucket soil → obsidian soil; hoe on coarse dirt → dirt;
shovel on grass/dirt/podzol/mycelium/rooted dirt → dirt path (soil_item may be a list).

### B.7 Container menu (GUI) and slot rules

Two menu layouts (registered menu types `<ns>:basic_pot_menu`, `<ns>:hopper_pot_menu`):

- **Basic menu:** soil slot + seed slot only (soil at x=80,y=48; seed at x=80,y=22).
- **Hopper menu:** soil slot (x=44,y=48) + seed slot (x=44,y=22) + **tool slot** (x=18,y=35,
  accepts only items in tag `<ns>:harvest_items`) + a **4×3 grid of 12 output slots**
  (extract-only) starting at (80,17), stepping 18px.
- Plus the standard 3×9 inventory + 9 hotbar.
- **Input slots** show a placeholder empty-slot icon (soil/seed/hoe) and may enforce a
  predicate (tool slot). **Output slots** are extract-only (no manual insertion).
- **Shift-click routing (`quickMoveStack`)** is bespoke: from player inventory, a shift-clicked
  item is routed into the tool slot (if it's a harvest tool and the slot is empty), else the
  soil slot (if it resolves to a soil), else the seed slot (if it resolves to a crop), else
  normal inventory↔hotbar movement; outputs and soil/seed/tool shift back into the inventory.
- The block position is synced to the client menu via container data so the client menu can
  reach the live BE for tooltips.

### B.8 GUI tooltips

When hovering an item in the pot GUI, the mod appends recipe-derived tooltip lines:
- **Soil:** growth-modifier % and yield-modifier % (colored + for buff, − for nerf).
- **Crop hovered as the planted seed:** effective growth time (formatted, tick-rate aware);
  total yield % with a breakdown of contributing sources (base, soil, pot, tool) each scaled
  by `yield_scale`; a red "wrong soil" warning if the current soil is not accepted; a red
  "wrong pot" warning if a `pot_predicate` excludes this pot.
- **Crop hovered elsewhere:** its base grow time, base yield %, and yield scale %.
- **Fertilizer / interaction / harvest tool:** their own summary lines (tool shows its growth
  modifier from enchantments/attributes).
- Item **override** components short-circuit to the override's own tooltip.

---

## Phase C — Rendering / Display

### C.1 What is drawn

The pot's block-entity renderer draws, inside the planter:
1. The **crop** display(s) on top — only while the crop's growth is sustained — scaled by
   growth progress.
2. The **soil** display at the bottom (scaled to ~0.6375 height), only if a soil item is
   present and the camera is at/above the pot's Y (so you see soil from above).

Growth **progress** = `clamp(growth_time / requiredGrowthTicks, 0..1)`. Crop render scale =
`0.40 + 0.60 * progress` (so a young crop is 40% size, mature is 100% of the display's own
scale). Multiple crop displays render bottom-up, each offset above the previous by its
measured height so multi-block crops stack cleanly. The whole render is rotated to face the
pot's `horizontal_facing`.

If a soil has no soil recipe but the soil item is a block item, the renderer falls back to a
simple block-state display of that block (so any block item "looks like" soil).

### C.2 Display object model (data-driven, dispatch by `type`)

A **display** is a registered, `type`-dispatched value with its own JSON/stream codec and a
bound **renderer**. Built-in types:

| `type` | Fields | What it draws |
|--------|--------|---------------|
| `<ns>:simple` | `block_state`, `options` | One block state |
| `<ns>:aging` | `block`, `options` | Auto phase list from a block's age/flower_amount property; picks phase by progress |
| `<ns>:transitional` | `phases`: list of displays | Explicit phase list; picks phase by progress |
| `<ns>:entity` | `entity` NBT, `should_tick`=true, `spin_speed`=0, `scale`={0.5,0.5,0.5}, `offset`? | Renders a (optionally spinning/ticking) entity |
| `<ns>:textured_cube` | `texture`, `options` | A colored textured cube (translucent) |

`display` fields accept either a single object or a list where a list is expected (flexible
list codec).

### C.3 Phased displays

`aging` and `transitional` are "phased": they hold an ordered list of sub-displays and select
one by growth progress. Phase index = `clamp(floor(progress * (count-1)), 0, count-1)`.
`aging` auto-builds its phases from the block:
- a crop block → one simple display per age (0..maxAge);
- a `flower_amount` property → one per possible flower count;
- else an `age` integer property → one per age value;
- else a single default-state display.

### C.4 Simple display rendering

Renders the block state's baked model (cutout), honoring the `options` face set (which faces
to include), optional fluid layer (if `render_fluid`), and an optional tint color (else uses
the block's world tint if the quad is tinted).

### C.5 Entity / textured-cube rendering

- **Entity:** builds a display entity from NBT (cached, reload-aware); optionally advances its
  animation each frame; scales/offsets it; spins it around Y by `spin_speed * 360° * progress`;
  renders it and its passengers.
- **Textured cube:** draws a unit cube with the given block-atlas sprite and tint.

### C.6 Render options (`options` on most displays)

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `scale` | vec3 | `{0.625, 0.625, 0.625}` | Per-axis scale (looked right at 100% growth) |
| `offset` | vec3 | `{0,0,0}` | Per-axis positional offset |
| `rotation` | list of axis-aligned rotations | `[]` | Each is an enum `X_0..X_270, Y_0..Y_270, Z_0..Z_270` (axis + 0/90/180/270°); each rotation also carries a re-centering offset so the render stays axis-aligned |
| `render_fluid` | bool | `false` | Include the block's fluid layer |
| `color` | Tint Color | absent | Tint applied to the render |
| `faces` | set of directions | all 6 | Which faces of the model to draw |

The renderer applies scale (× growth scale), offset, then each rotation (rotate + re-center),
and accumulates a height offset so the next stacked display sits directly on top.

**Tint Color** parses **either** an ARGB object `{alpha,red,green,blue}` (each 0–255, default
255) **or** a hex string `"RRGGBB"` / `"AARRGGBB"` (leading `#` optional).

### C.7 Client visual config

- `pot_view_distance` (default 48): pots beyond this distance stop rendering soil/crop.
- `use_growth_animation` (default true): if false, crops always render at full size.
- `render_soil` / `render_crop` (default true): toggle each layer for performance.

---

## Phase D — Tiers Add-on

Adds three faster/higher-yield pot **tiers**: `elite`, `ultra`, `mega`. Purely additive on
top of the core; no new growth engine.

- **Blocks:** a full parallel set of pot blocks for every material × pot type (basic/hopper/
  waxed), id-prefixed by tier: `<tier>_<material>_botany_pot`, etc. Each tier has its own block
  entity type but reuses the core BE behavior and renderer.
- **Modifiers:** a tiered pot overrides the pot growth/yield hooks to return its tier's
  **speed multiplier** (into the growth divisor, §A.7) and **output multiplier** (into
  totalYield, §A.8), both **additive** and read from config.
- **Config (per tier, integers):** default speed/output — elite 2/2, ultra 6/3, mega 10/4.
  (Because they add into the divisor/yield, an "elite 2" pot is ~3× base speed and ~3 yield
  rolls — preserve this additive semantics.)
- **Upgrade items:** `<tier>_upgrade` items. Right-clicking a pot with an upgrade converts it
  **in place to the next tier**, preserving block-entity data (contents/growth): base→elite→
  ultra→mega, strictly in order and only to the immediately next tier (else fail); the old BE
  contents are moved (not dropped/duped), then restored into the new block; one upgrade item
  is consumed. Crafted via shaped recipes (e.g. elite = iron blocks + ender pearl), gated by
  per-tier `can_craft_*` config conditions.
- **Tooltip:** shows the pot's multiplier and speed (with a "hold shift" summary).
- A tiered GUI/menu existed only as commented-out scaffolding; tiered pots reuse the core menu.

---

## Phase E — Trees & Ores

### E.1 Trees (pure datapack)

Trees is essentially a **datapack only** (its lone code file just declares a mod id). "Growing
a tree in a pot" is nothing more than a `block_derived_crop` whose `block` is a **sapling**,
with a longer `grow_time` and drops routed to a custom **loot table** that yields the tree's
products (logs, leaves, saplings, fruit). There is **no** structure generation, no
sapling→tree block transformation — the pot renders the sapling display and, on harvest, rolls
the tree's loot table. Example:
```json
{"type":"<ns>:block_derived_crop","block":"aether:skyroot_sapling","grow_time":2400,
 "drops":[{"type":"<ns>:loot_table","table_id":"<treepack>:tree_drops/aether/skyroot"}]}
```
The tree loot tables use standard vanilla loot mechanics (uniform counts, random-chance
bonus entries for saplings/fruit) and are guarded by `block_exists` load conditions for
cross-mod content. Reimplementation = ship these as datapack files; no engine changes.

### E.2 Ores (planned — not in the cloned source; infer)

Follow the Trees pattern: an ore-growing extension needs only **data + a few items**, no new
engine mechanics:
- Define a set of **"ore seed" items** (crafted from the corresponding resources) and, if
  desired, dedicated **soils** (e.g. a stone/deepslate-like soil).
- Define `<ns>:crop` (or `block_derived_crop`) recipes whose `input` is the ore seed, tuned
  with a long `grow_time` and a yield curve, dropping ores / raw metals via `<ns>:items`,
  `<ns>:loot_table`, or — to require silk-touch-style state — `<ns>:block_state` drops of the
  ore block.
- Use `light_level` for glowing ores. Everything else (rendering, harvest, hopper automation,
  tiers) comes for free from the core.
Treat Ores as a future data/item pack; only add engine code if a genuinely new drop behavior
is required (the existing drop-provider types likely suffice).

---

## Phase F — Config & Commands

### F.1 Config (annotation-driven, commented JSON; reimplement a small loader)

**Gameplay:**
- `global_growth_modifier` (float, 1.0) — global growth divisor base (§A.7).
- `damage_harvest_tool` (bool, true) — hopper auto-harvest damages the tool.
- `efficiency_growth_modifier` (float, 0.05) — growth added per level of the
  `<ns>:increase_pot_growth` enchantment on the tool.
- `default_harvest_stack` (JSON item, empty) — item used as the loot-context tool when the
  pot's tool slot is empty (e.g. set to silk-touch shears so pots yield silk-touch drops).

**Recipes:** `craft_basic_pots`, `craft_hopper_pots`, `craft_wax_pots` (bools, true) — gate
the pot crafting recipes via the `<ns>:config` load condition.

**Visuals:** `pot_view_distance` (48), `use_growth_animation` (true), `render_soil` (true),
`render_crop` (true) — see §C.7.

The original used an external annotation-config lib (distinct from Bookshelf). Reimplement a
minimal annotation- or record-based config that reads/writes a commented JSON file, or adopt
an existing MIT config library.

### F.2 Commands (root `/<modid>`, owner permission, under a `debug` subtree)

- **`missing seeds [include_saplings] [generate]`** — scans the item registry for items that
  *could* be crops (crop/growing/bonemealable/sapling/bush/coral/flower/seed blocks) but have
  no crop recipe yet; lists their ids (click-to-copy) and, with `generate true`, writes
  suggested `block_derived_crop` JSON files (auto-assigning soils: water for coral/lily,
  mushroom tag for mushrooms, sand for cactus, sculk for sculk).
- **`missing soils [generate]`** — same idea for potential soils (block items accepted by some
  crop's soil test, or referenced by pot interactions); generates `block_derived_soil` JSON.
- **`check_crops`** — places a test hopper pot, iterates every cached crop, forces it to
  maturity with a variety of tools (empty, silk-touch shears/pickaxe/axe/sword/shovel/hoe),
  and reports any crop that produces **no** drops (or has no valid soil) — a datapack QA tool.
- **`place_seeds [pos] [all_soils]`** — fills a grid of waxed pots with every crop (and, with
  `all_soils`, every accepted soil), all at full growth, for a visual catalog.

Generators are pluggable (soil/crop generator interfaces) so add-ons can register their own;
a fallback generator handles the general case, and a "tagged soil" generator maps an item tag
to a fixed display (used for the built-in water/lava/snow soils).

### F.3 Plugin API

A service-loaded **plugin** interface lets add-ons register: soil/crop generators, display
types, drop-provider types, growth-amount types, and (client) bind display renderers. The core
registers its own built-ins through one such plugin. Reimplement as a Fabric entrypoint or
service-loader list.

### F.4 Optional JEI integration

If JEI is present, show a **Crop** category (seed + accepted soil + possible drops + grow time)
and an **Interaction** category. This is an optional compile/runtime dependency; keep it
isolated so the mod runs without JEI.

---

## Phase G — Bookshelf-Replacement Utilities (build in-mod)

The original leaned on the Bookshelf library (and an external config lib). Reimplement the
following **behaviors** in-mod (MIT), no Bookshelf dependency:

1. **Cached supplier** — a supplier that resolves once and memoizes; convenience constructors
   to look up a registry object by id lazily; map/cast helpers. Used pervasively for registry
   handles (blocks, items, block-entity types, menu types, attributes, components, sounds).
2. **Reloadable cache** — a value derived from the current level that rebuilds when the world/
   recipes reload and can be explicitly invalidated; helper constructors to build: a map of
   recipes-by-type for a level, and a (living) entity from NBT. Includes `ifPresent`/`map`
   conveniences.
3. **Sided reloadable cache** — keeps independent client and server instances of a reloadable
   cache (recipe caches, soil/crop caches must not bleed across sides).
4. **Tick accumulator** — tracks elapsed **game ticks** as a float, advancing/retreating by a
   fixed step per game tick, **independent of the world's tick rate**; supports tickUp / tickDown
   / set / get / reset. Drives growth and all pot cooldowns, so **growth advances per game tick
   and `/tick rate` affects growth speed** (higher = faster, `/tick freeze` pauses). *User
   decision 2026-07-20, supersedes the original real-time-stable design.*
5. **Codec/data helpers:**
   - build a recipe serializer from a map-codec + stream-codec pair;
   - optional-value stream codec wrapper;
   - **flexible list / flexible set** codecs (accept a single element or an array);
   - map-codec helpers: boolean/enum/`xor`/`either`, a block-state map codec, an enumerable
     enum codec, an enum stream codec, a "list on a map key" helper;
   - a non-empty ingredient stream codec;
   - block / item / entity-type / block-state ↔ id (stream) codecs;
   - a container-item-list sublist helper (trim the synced item NBT to the first N slots).
6. **Loader-agnostic registration framework** — a "content provider" with typed adapters for:
   blocks (+ placeable/block-item), block entities, items, menu types, menu screens, recipe
   types, recipe serializers, data components, attributes, creative tabs, commands,
   block-entity renderers, block render-type (cutout) assignment, and load-condition types.
   Reimplement directly against Fabric registries.
7. **Gameplay/loader services** — drop item remainders at a position; add an item into a
   specific set of container slots; insert an item into a neighboring inventory through a face
   (returning remainder); build a block-entity type for a set of blocks; a service/plugin
   loader; a physical-side concept (client/server) with an "only for side" annotation.
8. **Menu helpers** — an input slot (optional accept-predicate + empty-slot placeholder icon),
   an extract-only output slot, and a container-data wrapper that syncs a BlockPos through the
   menu's data slots.
9. **Enchantment level helper** — get the highest / first enchantment level among the
   enchantments in a given enchantment **tag** on a stack (used for growth-boost and
   damage-negation enchantments).
10. **Loot-table enumeration** — list the unique possible item outputs of a loot table (for
    JEI display of drops).
11. **Render helper (client)** — render a block model / a textured box / a fluid box; fetch a
    block-atlas sprite by id.
12. **Block hooks** — a small interface for extra block behavior hooks the pot block used.
13. **Command helpers** — permission-level predicates (e.g. "owner"), and argument getters that
    return a default when an optional argument is absent.
14. **Load-conditions framework** — a registry of condition types, a JSON key parsed on recipe
    load that skips a file when a condition fails, plus the built-in `block_exists`,
    `item_exists`, `either`, `block_tag`, and the mod's own `config` condition (§A.9).
15. **Maths helper** — percent-chance roll, random int in an inclusive range, decimal
    formatting for tooltips.
16. **Function helper** — null-safe "test an optional predicate" utility.
17. **Vanilla accessors (mixins)** — read a crop block's seed item; read an integer block
    property's max value; obtain the active registry access when recipes reload (to resolve
    loot tables for drop enumeration). Reimplement as small Fabric mixins/accessors.
18. **Config library** — annotation- or record-driven config with per-field comments and a
    "write defaults" toggle, backed by a commented JSON file.

---

## Appendix — Registered ids & tags to define

- **Recipe types:** `<ns>:soil`, `<ns>:crop`, `<ns>:fertilizer`, `<ns>:pot_interaction`.
- **Recipe serializers:** `soil`, `block_derived_soil`, `crop`, `block_derived_crop`,
  `fertilizer`, `pot_interaction`.
- **Menu types:** `<ns>:basic_pot_menu`, `<ns>:hopper_pot_menu`.
- **Block entity type:** `<ns>:botany_pot` (Tiers: one per tier).
- **Data components:** `<ns>:crop`, `<ns>:soil` (item overrides).
- **Item attributes:** `<ns>:growth`, `<ns>:yield` (tools carry modifiers).
- **Enchantment tags:** `<ns>:increase_pot_growth`, `<ns>:negate_harvest_damage`.
- **Item tags:** `<ns>:harvest_items` (hopper tool slot), `<ns>:soil/dirt` (default accepted
  soil), plus soil category tags used by the datapack: `soil/water`, `soil/lava`, `soil/snow`,
  `soil/mushroom`, `soil/sand`, `soil/sculk`; generator-ignore tags
  `crop_generator_ignores`, `soil_generator_ignores`.
- **Display types:** `simple`, `aging`, `transitional`, `entity`, `textured_cube`.
- **Drop-provider types:** `items`, `loot_table`, `block`, `block_state`, `entity`.
- **Growth-amount types:** `constant`, `ranged`, `percent`.
- **Load-condition / ingredient types:** `config`, `block_exists`, `item_exists`, `either`,
  `block_tag`.
- **Empty-slot GUI icons:** soil / seed placeholders; vanilla hoe placeholder for the tool
  slot.

## Appendix — Pot crafting recipe shapes (datapack, config-gated)

- **Basic:** shaped ring of material `M` around a flower pot `P`: `["M M","MPM"," M "]` —
  gated `can_craft_basic_pots`.
- **Hopper:** shapeless `hopper + basic pot`; **and** a "quick" shaped variant with a hopper on
  top: `["MHM","MPM"," M "]` — gated `can_craft_hopper_pots`.
- **Waxed:** shapeless `honeycomb + basic pot` — gated `can_wax_pots`.
- Each pot self-drops via a trivial block loot table.

## Appendix — Block model / asset conventions

- Pot block models parent a shared template (`block/template/pot`, `block/template/hopper_pot`),
  cutout render type, textured with the material's side texture (`material`) and a generated
  "pot top" texture (`material_top`) — a trimmed 12-wide top ring derived from the material's
  top texture. Waxed pots reuse the basic model/item. Blockstate = single default variant.
- GUI textures: a basic and a hopper container background.
