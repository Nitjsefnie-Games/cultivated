# Contributing to Cultivated

Issues and pull requests are welcome — especially bug reports from actual
play. A pot that drops the wrong thing, a tier bonus that does not apply, a
recipe that fails to load: those are the reports worth the most, and they
are much easier to act on with the Minecraft version, the Fabric API
version, the mod list, and the log attached.

## LLM and agent contributions are welcome

You may use an LLM or a coding agent to write your contribution. There is
no penalty, no separate review queue, and no expectation that you rewrite
its output by hand. Much of this repo was built that way.

Two conditions, and they are about honesty rather than provenance:

1. **Disclose the model** with a trailer on each commit it authored:

   ```
   Co-Authored-By: <Model Name> <noreply@example.com>
   ```

   e.g. `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. One
   primary-author trailer per commit.

2. **Do not submit claims you have not verified.** Paste the output of
   `./gradlew test`, and for anything that changes in-world behaviour, say
   what you actually did in game and what happened. "Should work" is not
   evidence — a mod compiles fine and still crashes on world load.

If a maintainer's reply reads like it was drafted by an agent, it probably
was. That is fine in both directions.

## The rule that matters most: this is a clean-room reimplementation

Cultivated implements the *concept* of botany pots. It is **not** derived
from BotanyPots' source, and it must stay that way for the MIT licence on
this repo to mean anything.

So: **do not copy code, identifiers, asset files, or lang strings from
BotanyPots or any other mod into this repo**, and do not paste decompiled
Minecraft source. If you have read another mod's source recently and want
to contribute the same feature, say so in the PR — implementing from
observed behaviour is fine, transcribing is not. [`SPEC.md`](SPEC.md) is
the behavioural specification this mod is built from; extend that first if
you are adding a mechanic.

## Other constraints

- **Data-driven or it does not ship.** Soils, crops, trees, and ores are
  recipe JSON so datapacks can extend them. A hardcoded `if (item ==
  WHEAT_SEEDS)` in Java is the wrong answer.
- **Config-driven tuning.** Growth and yield numbers live in
  `config/cultivated.json`. Do not bake a new constant into the code.
- **Growth advances per game tick** so `/tick rate`, `/tick freeze`, and
  `/tick step` govern every pot. A change that ties growth to wall-clock
  time breaks that and will be declined.
- **Server-safe.** Client-only classes stay behind the client entrypoint.
  A dedicated server must load the mod without touching a rendering class.

## Building

Requires **JDK 25**.

```bash
./gradlew build          # compile + run tests + produce the jar
./gradlew runClient      # dev client
./gradlew runServer      # dev dedicated server
```

Fabric/Minecraft versions are pinned in `gradle.properties`
(Minecraft 26.2, loader 0.19.3, Fabric API 0.155.2+26.2). Bumping them is a
PR of its own, not a side effect of a feature PR.

## Tests

```bash
./gradlew test
```

There is a real JUnit 5 suite under `src/test/java` covering pot mechanics,
tier upgrades, menu routing, recipe parsing, and asset coverage. Some of it
is deliberately adversarial — `ShippedRecipesParseTest` parses every recipe
that ships, and `PotAssetCoverageTest` fails if a registered pot has no
model or texture. If you add a pot, a soil, or a crop type, the coverage
tests are what will tell you what you forgot.

Add a test with your change. A mechanic that can only be verified by
loading a world is a mechanic nobody can safely refactor later.

## House style

- Java 25, `dev.nitjsefnie.cultivated` package root.
- Registry objects registered in one place, not scattered across the class
  that happens to use them.
- Prefer a tag (`cultivated:harvest_items`) over an instance check.
- No linter or formatter config. Match the surrounding file.

## Pull requests

Small and single-purpose beats large and comprehensive. Include what
changed and why, the test output, and for gameplay changes a short
description of what you did in game to check it. Screenshots help for
anything visual.

If you are unsure whether something is a bug or intended, open an issue and
ask — a wrong premise caught early is cheaper than a correct fix to the
wrong problem.
