package dev.nitjsefnie.cultivated.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import dev.nitjsefnie.cultivated.block.PotMechanics;
import dev.nitjsefnie.cultivated.block.PotType;
import dev.nitjsefnie.cultivated.cache.PotRecipeCaches;
import dev.nitjsefnie.cultivated.cache.RecipeLookupCache;
import dev.nitjsefnie.cultivated.command.generator.Generators;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.PotContext;
import dev.nitjsefnie.cultivated.recipe.SimplePotContext;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import dev.nitjsefnie.cultivated.registry.ModBlocks;
import dev.nitjsefnie.cultivated.registry.ModTags;
import dev.nitjsefnie.cultivated.util.CommandArguments;
import dev.nitjsefnie.cultivated.util.CommandPermissions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Phase F.2 — the owner-only {@code /cultivated debug} subtree: datapack QA tooling. Four commands:
 * {@code missing seeds}/{@code missing soils} scan the item registry for growables/soils that lack a
 * recipe (optionally writing suggested {@code block_derived_*} JSON via the pluggable
 * {@link Generators}); {@code check_crops} places a scratch hopper pot and live-harvests every cached
 * crop with a variety of tools, reporting any that drop nothing or have no accepted soil; and
 * {@code place_seeds} fills a grid of waxed display pots with every crop (and every accepted soil).
 *
 * <p>The {@code missing …} scans are read-only, fully data-driven off {@link PotRecipeCaches}.
 * {@code place_seeds} and {@code check_crops} mutate the world: {@code place_seeds} leaves the display
 * pots in place (forced to full growth); {@code check_crops} places one temporary pot, forces each
 * crop to maturity through {@link BotanyPotBlockEntity#forceFullGrowth()}, collects its per-tool drops
 * via {@link BotanyPotBlockEntity#collectHarvestDrops(ItemStack)}, then restores the original block.
 */
public final class DebugCommands {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Side length of the {@code place_seeds} pot grid before wrapping to the next row. */
	private static final int GRID_WIDTH = 16;

	private DebugCommands() {
	}

	/** Build the {@code debug} literal subtree (owner-gated) for attachment under {@code /cultivated}. */
	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("debug")
			.requires(CommandPermissions.owner())
			.then(Commands.literal("missing")
				.then(Commands.literal("seeds")
					.executes(ctx -> missingSeeds(ctx, false, false))
					.then(Commands.argument("include_saplings", BoolArgumentType.bool())
						.executes(ctx -> missingSeeds(ctx, BoolArgumentType.getBool(ctx, "include_saplings"), false))
						.then(Commands.argument("generate", BoolArgumentType.bool())
							.executes(ctx -> missingSeeds(
								ctx,
								BoolArgumentType.getBool(ctx, "include_saplings"),
								BoolArgumentType.getBool(ctx, "generate"))))))
				.then(Commands.literal("soils")
					.executes(ctx -> missingSoils(ctx, false))
					.then(Commands.argument("generate", BoolArgumentType.bool())
						.executes(ctx -> missingSoils(ctx, BoolArgumentType.getBool(ctx, "generate"))))))
			.then(Commands.literal("check_crops")
				.executes(DebugCommands::checkCrops))
			.then(Commands.literal("place_seeds")
				.executes(ctx -> placeSeeds(ctx, sourcePos(ctx), false))
				.then(Commands.argument("pos", BlockPosArgument.blockPos())
					.executes(ctx -> placeSeeds(ctx, blockPosArg(ctx, sourcePos(ctx)), false))
					.then(Commands.argument("all_soils", BoolArgumentType.bool())
						.executes(ctx -> placeSeeds(
							ctx,
							blockPosArg(ctx, sourcePos(ctx)),
							BoolArgumentType.getBool(ctx, "all_soils"))))));
	}

	// ---- missing seeds ----

	private static int missingSeeds(final CommandContext<CommandSourceStack> ctx, final boolean includeSaplings, final boolean generate) {
		final CommandSourceStack source = ctx.getSource();
		final RecipeLookupCache<CropRecipe> crops = PotRecipeCaches.crops();
		final List<Identifier> missing = new ArrayList<>();

		for (final Item item : BuiltInRegistries.ITEM) {
			if (!(item instanceof BlockItem blockItem)) {
				continue;
			}
			if (item.getDefaultInstance().is(ModTags.CROP_GENERATOR_IGNORES)) {
				continue;
			}
			if (crops.isCached(item)) {
				continue; // already a crop
			}
			if (CropCandidates.couldBeCrop(blockItem.getBlock(), includeSaplings)) {
				missing.add(BuiltInRegistries.ITEM.getKey(item));
			}
		}
		missing.sort(Comparator.comparing(Identifier::toString));

		source.sendSuccess(() -> Component.literal("Missing crop candidates: " + missing.size())
			.withStyle(ChatFormatting.GOLD), false);
		for (final Identifier id : missing) {
			source.sendSuccess(() -> clickableId(id), false);
		}

		if (generate) {
			final int written = generateFiles(source, "crop", missing,
				id -> Generators.generateCrop(BuiltInRegistries.ITEM.getValue(id) instanceof BlockItem bi ? bi.getBlock() : null, id));
			source.sendSuccess(() -> Component.literal("Wrote " + written + " block_derived_crop JSON files")
				.withStyle(ChatFormatting.GREEN), false);
		}
		return missing.size();
	}

	// ---- missing soils ----

	private static int missingSoils(final CommandContext<CommandSourceStack> ctx, final boolean generate) {
		final CommandSourceStack source = ctx.getSource();
		final RecipeLookupCache<SoilRecipe> soils = PotRecipeCaches.soils();
		final List<CropRecipe> crops = PotRecipeCaches.crops().values();
		final List<Identifier> missing = new ArrayList<>();

		for (final Item item : BuiltInRegistries.ITEM) {
			if (!(item instanceof BlockItem)) {
				continue;
			}
			if (item.getDefaultInstance().is(ModTags.SOIL_GENERATOR_IGNORES)) {
				continue;
			}
			if (soils.isCached(item)) {
				continue; // already a soil
			}
			final ItemStack stack = item.getDefaultInstance();
			if (acceptedBySomeCrop(crops, stack)) {
				missing.add(BuiltInRegistries.ITEM.getKey(item));
			}
		}
		missing.sort(Comparator.comparing(Identifier::toString));

		source.sendSuccess(() -> Component.literal("Missing soil candidates: " + missing.size())
			.withStyle(ChatFormatting.GOLD), false);
		for (final Identifier id : missing) {
			source.sendSuccess(() -> clickableId(id), false);
		}

		if (generate) {
			final int written = generateFiles(source, "soil", missing,
				id -> Generators.generateSoil(BuiltInRegistries.ITEM.getValue(id) instanceof BlockItem bi ? bi.getBlock() : null, id));
			source.sendSuccess(() -> Component.literal("Wrote " + written + " block_derived_soil JSON files")
				.withStyle(ChatFormatting.GREEN), false);
		}
		return missing.size();
	}

	private static boolean acceptedBySomeCrop(final List<CropRecipe> crops, final ItemStack stack) {
		for (final CropRecipe crop : crops) {
			if (crop.acceptsSoil(stack)) {
				return true;
			}
		}
		return false;
	}

	// ---- check_crops ----

	private static int checkCrops(final CommandContext<CommandSourceStack> ctx) {
		final CommandSourceStack source = ctx.getSource();
		final ServerLevel level = source.getLevel();
		final RecipeLookupCache<CropRecipe> crops = PotRecipeCaches.crops();
		final RecipeLookupCache<SoilRecipe> soils = PotRecipeCaches.soils();

		final Block hopperPot = firstHopperPot();
		if (hopperPot == null) {
			source.sendFailure(Component.literal("No hopper botany pot block is registered"));
			return 0;
		}

		// A scratch position above the command source, restored afterwards. Placed in what is usually
		// air above the caller's head to minimise collateral; the original block + any block-entity
		// metadata are captured and re-applied in the finally block regardless.
		final BlockPos testPos = sourcePos(ctx).above(2);
		final BlockState originalState = level.getBlockState(testPos);
		final BlockEntity originalBe = level.getBlockEntity(testPos);
		final CompoundTag originalBeTag = originalBe != null ? originalBe.saveWithFullMetadata(level.registryAccess()) : null;
		final List<ItemStack> tools = harvestTools(level);

		int checked = 0;
		int problems = 0;
		try {
			level.setBlockAndUpdate(testPos, hopperPot.defaultBlockState());
			if (!(level.getBlockEntity(testPos) instanceof BotanyPotBlockEntity pot)) {
				source.sendFailure(Component.literal("Failed to place a test botany pot"));
				return 0;
			}
			for (final Item item : BuiltInRegistries.ITEM) {
				if (!crops.isCached(item)) {
					continue;
				}
				final ItemStack seed = item.getDefaultInstance();
				final CropRecipe crop = crops.lookup(seed, matchContext());
				if (crop == null) {
					continue;
				}
				checked++;
				final Identifier id = BuiltInRegistries.ITEM.getKey(item);
				final ItemStack soil = pickAcceptedSoil(soils, crop);
				final boolean hasSoil = soil != null;

				pot.setItem(PotMechanics.SEED, seed.copy());
				pot.setItem(PotMechanics.SOIL, hasSoil ? soil.copy() : ItemStack.EMPTY);
				pot.forceFullGrowth();

				final List<List<ItemStack>> perToolDrops = new ArrayList<>(tools.size());
				for (final ItemStack tool : tools) {
					perToolDrops.add(pot.collectHarvestDrops(tool.copy()));
				}
				// A crop whose only output is an mcfunction (no drop providers) is not a "no drops" fault.
				final boolean hasDrops = !HarvestAudit.producedNothing(perToolDrops) || crop.function().isPresent();

				if (!hasDrops || !hasSoil) {
					problems++;
					final String reason = (!hasDrops ? "no drops" : "")
						+ (!hasDrops && !hasSoil ? ", " : "")
						+ (!hasSoil ? "no valid soil" : "");
					source.sendSuccess(() -> Component.literal(id + " — " + reason).withStyle(ChatFormatting.RED), false);
				}

				pot.setItem(PotMechanics.SEED, ItemStack.EMPTY);
				pot.setItem(PotMechanics.SOIL, ItemStack.EMPTY);
			}
		} finally {
			level.setBlockAndUpdate(testPos, originalState);
			if (originalBeTag != null) {
				final BlockEntity restored = level.getBlockEntity(testPos);
				if (restored != null) {
					restored.loadWithComponents(
						TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), originalBeTag));
					restored.setChanged();
				}
			}
		}

		final int checkedFinal = checked;
		final int problemsFinal = problems;
		source.sendSuccess(() -> Component.literal(
			"Checked " + checkedFinal + " crops; " + problemsFinal + " with problems")
			.withStyle(problemsFinal == 0 ? ChatFormatting.GREEN : ChatFormatting.GOLD), false);
		return problems;
	}

	/**
	 * The first cached soil the crop accepts, else the vanilla dirt item when the crop's default
	 * (dirt-tag) soil accepts it, else {@code null} — the crop has no accepted soil and is reported.
	 */
	private static @Nullable ItemStack pickAcceptedSoil(final RecipeLookupCache<SoilRecipe> soils, final CropRecipe crop) {
		for (final Item item : BuiltInRegistries.ITEM) {
			if (!soils.isCached(item)) {
				continue;
			}
			final ItemStack stack = item.getDefaultInstance();
			if (crop.acceptsSoil(stack)) {
				return stack;
			}
		}
		final ItemStack dirt = Items.DIRT.getDefaultInstance();
		return crop.acceptsSoil(dirt) ? dirt : null;
	}

	/**
	 * The variety of harvest tools tried per crop (§F.2): empty hand, silk-touch shears, silk-touch
	 * pickaxe, then a plain axe, sword, shovel and hoe. Silk touch is resolved from the level's
	 * enchantment registry so silk-touch-sensitive crop loot (grass, mushrooms, ferns…) can drop.
	 */
	private static List<ItemStack> harvestTools(final ServerLevel level) {
		final Holder<Enchantment> silkTouch = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
		final List<ItemStack> tools = new ArrayList<>();
		tools.add(ItemStack.EMPTY); // empty hand
		for (final Item item : HarvestAudit.AUDIT_TOOL_ITEMS) {
			final ItemStack stack = new ItemStack(item);
			if (item == Items.SHEARS || item == Items.DIAMOND_PICKAXE) {
				stack.enchant(silkTouch, 1);
			}
			tools.add(stack);
		}
		return tools;
	}

	private static @Nullable Block firstHopperPot() {
		for (final Block block : ModBlocks.POTS) {
			if (block instanceof PotType.Provider provider && provider.potType().isHopper()) {
				return block;
			}
		}
		return null;
	}

	// ---- place_seeds ----

	private static int placeSeeds(final CommandContext<CommandSourceStack> ctx, final BlockPos origin, final boolean allSoils) {
		final CommandSourceStack source = ctx.getSource();
		final ServerLevel level = source.getLevel();
		final Block waxedPot = firstWaxedPot();
		if (waxedPot == null) {
			source.sendFailure(Component.literal("No waxed botany pot block is registered"));
			return 0;
		}
		final RecipeLookupCache<CropRecipe> crops = PotRecipeCaches.crops();
		final RecipeLookupCache<SoilRecipe> soils = PotRecipeCaches.soils();

		final List<ItemStack[]> pots = new ArrayList<>(); // [0]=seed, [1]=soil
		for (final Item item : BuiltInRegistries.ITEM) {
			if (!crops.isCached(item)) {
				continue;
			}
			final ItemStack seed = item.getDefaultInstance();
			final CropRecipe crop = crops.lookup(seed, matchContext());
			if (crop == null) {
				continue;
			}
			final List<ItemStack> acceptedSoils = acceptedSoils(soils, crop, allSoils);
			for (final ItemStack soil : acceptedSoils) {
				pots.add(new ItemStack[] {seed.copy(), soil});
			}
		}

		int placed = 0;
		for (final ItemStack[] pot : pots) {
			final BlockPos pos = origin.offset(placed % GRID_WIDTH, 0, placed / GRID_WIDTH);
			level.setBlockAndUpdate(pos, waxedPot.defaultBlockState());
			if (level.getBlockEntity(pos) instanceof BotanyPotBlockEntity be) {
				be.setItem(PotMechanics.SEED, pot[0]);
				be.setItem(PotMechanics.SOIL, pot[1]);
				// §F.2 "at full growth": force the pot's growth to mature so both waxed display pots and
				// any non-waxed test pots (and their saved data) show a fully-grown crop.
				be.forceFullGrowth();
			}
			placed++;
		}

		final int placedFinal = placed;
		source.sendSuccess(() -> Component.literal("Placed " + placedFinal + " display pots")
			.withStyle(ChatFormatting.GREEN), false);
		return placed;
	}

	private static List<ItemStack> acceptedSoils(final RecipeLookupCache<SoilRecipe> soils, final CropRecipe crop, final boolean all) {
		final List<ItemStack> accepted = new ArrayList<>();
		for (final Item item : BuiltInRegistries.ITEM) {
			if (!soils.isCached(item)) {
				continue;
			}
			final ItemStack stack = item.getDefaultInstance();
			if (crop.acceptsSoil(stack)) {
				accepted.add(stack);
				if (!all) {
					return accepted;
				}
			}
		}
		if (accepted.isEmpty()) {
			accepted.add(Items.DIRT.getDefaultInstance()); // default dirt-tag soil
		}
		return accepted;
	}

	private static Block firstWaxedPot() {
		for (final Block block : ModBlocks.POTS) {
			if (block instanceof PotType.Provider provider && provider.potType().isWaxed()) {
				return block;
			}
		}
		return null;
	}

	// ---- shared helpers ----

	private static PotContext matchContext() {
		return SimplePotContext.empty();
	}

	private static BlockPos sourcePos(final CommandContext<CommandSourceStack> ctx) {
		final Vec3 pos = ctx.getSource().getPosition();
		return BlockPos.containing(pos);
	}

	private static BlockPos blockPosArg(final CommandContext<CommandSourceStack> ctx, final BlockPos fallback) {
		return CommandArguments.orDefault(() -> BlockPosArgument.getBlockPos(ctx, "pos"), fallback);
	}

	/** A click-to-copy identifier component (Phase F.2 — list ids the user can copy into a datapack). */
	private static MutableComponent clickableId(final Identifier id) {
		final String text = id.toString();
		return Component.literal(text).withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent.CopyToClipboard(text))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy"))));
	}

	private static int generateFiles(
		final CommandSourceStack source, final String category, final List<Identifier> ids,
		final java.util.function.Function<Identifier, JsonObject> generator
	) {
		final Path base = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
			.resolve("cultivated_generated").resolve("data").resolve("cultivated").resolve("recipe").resolve(category);
		int written = 0;
		try {
			Files.createDirectories(base);
			for (final Identifier id : ids) {
				final JsonObject json = generator.apply(id);
				if (json == null) {
					continue;
				}
				final String fileName = id.getNamespace() + "__" + id.getPath().replace('/', '_') + ".json";
				Files.writeString(base.resolve(fileName), GSON.toJson(json), StandardCharsets.UTF_8);
				written++;
			}
		} catch (final IOException e) {
			source.sendFailure(Component.literal("Failed to write generated files: " + e.getMessage()));
			Cultivated.LOGGER.error("Failed to write generated {} files", category, e);
		}
		return written;
	}
}
