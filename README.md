# Cultivated

Grow crops, trees, and ores in pots. A **clean-room, Fabric-only** reimplementation of the
BotanyPots concept for **Minecraft 26.2**, **MIT-licensed**, with the "tiers" feature built in
and **no Bookshelf dependency** (utilities are reimplemented in-mod).

> Independent implementation, written from a behavior spec — **not** derived from the LGPL
> BotanyPots / Botany Pots Tiers / BotanyTrees source. Inspired by Darkhax's & starforcraft's
> mods; credit to them for the original concept.

## Status: foundation only (build in progress)

This repo currently contains the **project scaffold** (a green Fabric 26.2 build) and the
**clean-room behavior + data-model spec** the implementation is built from. The mod itself is
being implemented phase by phase against that spec.

- **[`SPEC.md`](SPEC.md)** — the complete clean-room behavior & data-model specification
  (recipe schemas, growth/yield formulas, block/BE/menu behavior, rendering, tiers, config).
  This is the source of truth for the build.

### Build plan (phases, from SPEC.md)

| Phase | Scope | Complexity |
|---|---|---|
| **A** | Data / recipe model + loading (soils, crops, fertilizers, interactions; growth/yield; drop providers; conditions) — *the heart* | Large / hardest |
| **B** | Block + block entity + menu (pot blocks, 15-slot BE, tick loop, hopper automation, comparator, GUIs) | Large |
| **C** | Rendering / display (soil + growth-staged crop, display types, render options) — client-only | Medium |
| **D** | Tiers (tiered pots: speed/output modifiers, upgrade items) | Small–Medium |
| **E** | Trees & Ores (mostly datapack content on top of the crop system) | Small |
| **F** | Config + commands (config loader, debug/datagen commands, optional JEI) | Small–Medium |
| **G** | In-mod utilities replacing Bookshelf (~18: caches, codecs, registration, load-conditions, etc.) — scaffold early | Medium, pervasive |

**Content note:** the vanilla crop/soil/fertilizer recipe set is **curated for 26.2** (covering
growables new since 1.21.1). Cross-mod compatibility recipes are **out of scope**.

## Building

Requires **JDK 25**.

```bash
./gradlew build
```

## License

MIT
