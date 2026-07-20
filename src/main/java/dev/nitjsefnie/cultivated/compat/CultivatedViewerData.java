package dev.nitjsefnie.cultivated.compat;

import dev.nitjsefnie.cultivated.cache.PotRecipeCaches;
import dev.nitjsefnie.cultivated.client.PotTooltipFormatting;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.PotInteractionRecipe;
import dev.nitjsefnie.cultivated.recipe.SpawnEggCropRecipe;
import dev.nitjsefnie.cultivated.registry.ModBlocks;
import dev.nitjsefnie.cultivated.registry.ModTags;
import dev.nitjsefnie.cultivated.util.LazyItemStack;
import dev.nitjsefnie.cultivated.util.TickAccumulator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The single bridge between the mod's recipe data and the optional recipe-viewer plugins. Turns the
 * client-synced pot recipe caches ({@link PotRecipeCaches}) into viewer-agnostic {@link CropView} /
 * {@link InteractionView} snapshots. All viewer-referencing code lives under {@code compat/jei} and
 * {@code compat/rei}; this class references only the mod's own recipe types and vanilla Minecraft, so
 * it never forces a viewer class to load — the JEI/REI plugins call into it, not the other way round.
 *
 * <p>Ingredient stacks are enumerated by scanning every registered item's default instance through the
 * recipe predicate (mirroring how {@link dev.nitjsefnie.cultivated.cache.RecipeLookupCache} indexes
 * recipes), so both custom and vanilla-tag ingredients resolve to concrete display stacks a viewer can
 * cycle through.
 */
public final class CultivatedViewerData {
	private CultivatedViewerData() {
	}

	/** All growing recipes to show, drawn from the client-synced caches (crops + the generic mob crop). */
	public static List<CropView> crops() {
		final List<CropView> out = new ArrayList<>();
		for (final CropRecipe recipe : PotRecipeCaches.crops(true).values()) {
			out.add(cropView(recipe));
		}
		for (final SpawnEggCropRecipe recipe : PotRecipeCaches.spawnEggCrops(true).values()) {
			out.add(spawnEggView(recipe));
		}
		return out;
	}

	/** All {@code pot_interaction} recipes to show, drawn from the client-synced cache. */
	public static List<InteractionView> interactions() {
		final List<InteractionView> out = new ArrayList<>();
		for (final PotInteractionRecipe recipe : PotRecipeCaches.interactions(true).values()) {
			out.add(interactionView(recipe));
		}
		return out;
	}

	/** A representative icon stack for the viewer categories: the first registered botany pot. */
	public static ItemStack iconStack() {
		if (!ModBlocks.POTS.isEmpty()) {
			final ItemStack pot = new ItemStack(ModBlocks.POTS.get(0));
			if (!pot.isEmpty()) {
				return pot;
			}
		}
		return new ItemStack(Items.FLOWER_POT);
	}

	/**
	 * The grow-time line for a crop, e.g. {@code "Grow time: 1m (1200 ticks)"} — a base-rate readable
	 * duration (20 t/s) plus the exact game-tick count, both via {@link PotTooltipFormatting} so the
	 * numbers match the in-game pot tooltips.
	 */
	public static Component growTimeLine(final int growTicks) {
		final int ticks = PotTooltipFormatting.effectiveGameTicks(growTicks, TickAccumulator.NORMAL_TICK_RATE);
		final String duration = PotTooltipFormatting.formatDuration(growTicks, TickAccumulator.NORMAL_TICK_RATE);
		return Component.translatable("gui.cultivated.viewer.grow_time", duration + " (" + ticks + " ticks)");
	}

	private static CropView cropView(final CropRecipe recipe) {
		final List<ItemStack> seeds = ingredientStacks(recipe.input());
		final List<ItemStack> soils = new ArrayList<>();
		for (final Item item : BuiltInRegistries.ITEM) {
			final ItemStack stack = item.getDefaultInstance();
			if (!stack.isEmpty() && recipe.acceptsSoil(stack)) {
				soils.add(stack);
			}
		}
		final List<ItemStack> drops = new ArrayList<>();
		for (final DropProvider provider : recipe.drops()) {
			for (final ItemStack drop : provider.getDisplayItems()) {
				if (!drop.isEmpty()) {
					drops.add(drop);
				}
			}
		}
		return new CropView(seeds, soils, drops, recipe.growTime(), false);
	}

	private static CropView spawnEggView(final SpawnEggCropRecipe recipe) {
		final List<ItemStack> seeds = spawnEggStacks();
		final List<ItemStack> soils = soilStacks(recipe.soil());
		// Per-mob drops depend on the planted egg's entity, so they cannot be enumerated statically here;
		// the category renders a note for this generic entry instead of a drop slot.
		return new CropView(seeds, soils, List.of(), recipe.growTime(), true);
	}

	private static InteractionView interactionView(final PotInteractionRecipe recipe) {
		final List<ItemStack> held = ingredientStacks(recipe.heldItem());
		final List<ItemStack> requiredSoil = recipe.soilItem().map(CultivatedViewerData::ingredientStacks).orElseGet(List::of);
		final List<ItemStack> requiredSeed = recipe.seedItem().map(CultivatedViewerData::ingredientStacks).orElseGet(List::of);
		final List<ItemStack> resultSoil = lazyStack(recipe.newSoil());
		final List<ItemStack> resultSeed = lazyStack(recipe.newSeed());
		return new InteractionView(held, requiredSoil, requiredSeed, resultSoil, resultSeed, recipe.consumeHeld(), recipe.damageHeld());
	}

	private static List<ItemStack> lazyStack(final Optional<LazyItemStack> lazy) {
		if (lazy.isEmpty()) {
			return List.of();
		}
		final ItemStack stack = lazy.get().get();
		return stack.isEmpty() ? List.of() : List.of(stack);
	}

	private static List<ItemStack> soilStacks(final Optional<CultivatedIngredient> soil) {
		if (soil.isPresent()) {
			return ingredientStacks(soil.get());
		}
		final List<ItemStack> out = new ArrayList<>();
		for (final Item item : BuiltInRegistries.ITEM) {
			final ItemStack stack = item.getDefaultInstance();
			if (!stack.isEmpty() && stack.is(ModTags.SOIL_DIRT)) {
				out.add(stack);
			}
		}
		return out;
	}

	private static List<ItemStack> spawnEggStacks() {
		final List<ItemStack> out = new ArrayList<>();
		for (final Item item : BuiltInRegistries.ITEM) {
			if (item instanceof SpawnEggItem) {
				out.add(item.getDefaultInstance());
			}
		}
		return out;
	}

	private static List<ItemStack> ingredientStacks(final CultivatedIngredient ingredient) {
		final List<ItemStack> out = new ArrayList<>();
		for (final Item item : BuiltInRegistries.ITEM) {
			final ItemStack stack = item.getDefaultInstance();
			if (!stack.isEmpty() && ingredient.test(stack)) {
				out.add(stack);
			}
		}
		return out;
	}
}
