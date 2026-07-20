package dev.nitjsefnie.cultivated.client;

import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import dev.nitjsefnie.cultivated.block.PotMechanics;
import dev.nitjsefnie.cultivated.cache.PotRecipeCaches;
import dev.nitjsefnie.cultivated.client.PotTooltipFormatting.YieldBreakdown;
import dev.nitjsefnie.cultivated.data.formula.GrowthFormula;
import dev.nitjsefnie.cultivated.data.growth.GrowthAmount;
import dev.nitjsefnie.cultivated.menu.AbstractPotMenu;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.FertilizerRecipe;
import dev.nitjsefnie.cultivated.recipe.PotInteractionRecipe;
import dev.nitjsefnie.cultivated.recipe.SimplePotContext;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import dev.nitjsefnie.cultivated.registry.ModAttributes;
import dev.nitjsefnie.cultivated.registry.ModComponents;
import dev.nitjsefnie.cultivated.registry.ModTags;
import dev.nitjsefnie.cultivated.util.MathHelper;
import dev.nitjsefnie.cultivated.util.ToolAttributes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.jspecify.annotations.Nullable;

/**
 * Phase B §B.8 — appends the mod's recipe-derived lines to the tooltip of an item hovered inside a
 * pot GUI. Client-only display text: it resolves the hovered stack against the item override
 * components (§A.11) first, then the client-synced recipe caches, and reads the live pot block
 * entity (via the menu's synced {@link AbstractPotMenu#getPos()}) so the planted-seed numbers match
 * exactly what the growth engine computes. The pure arithmetic/formatting lives in
 * {@link PotTooltipFormatting}; this class only turns it into coloured {@link Component}s.
 */
@Environment(EnvType.CLIENT)
public final class PotTooltip {
	private static final String INDENT = "  ";

	private PotTooltip() {
	}

	/**
	 * Append the pot tooltip lines for {@code stack} (the item in {@code hoveredSlot}) to
	 * {@code lines}. A no-op for an empty stack, a missing hovered slot, or an item that resolves to
	 * no pot recipe. When anything is appended it is preceded by a blank separator line.
	 */
	public static void appendLines(
		final List<Component> lines, final AbstractPotMenu menu, final @Nullable Slot hoveredSlot, final ItemStack stack
	) {
		if (stack.isEmpty() || hoveredSlot == null) {
			return;
		}

		final Minecraft minecraft = Minecraft.getInstance();
		final Level level = minecraft.level;

		// §A.11: an override component short-circuits to its own embedded recipe; else the client cache.
		CropRecipe crop = stack.get(ModComponents.CROP_OVERRIDE);
		if (crop == null) {
			crop = PotRecipeCaches.crops(true).lookup(stack, SimplePotContext.ofSeed(stack));
		}
		SoilRecipe soil = stack.get(ModComponents.SOIL_OVERRIDE);
		if (soil == null) {
			soil = PotRecipeCaches.soils(true).lookup(stack, SimplePotContext.ofSoil(stack));
		}
		final FertilizerRecipe fertilizer = PotRecipeCaches.fertilizers(true).lookup(stack, SimplePotContext.ofHeld(stack));
		final PotInteractionRecipe interaction = PotRecipeCaches.interactions(true).lookup(stack, SimplePotContext.ofHeld(stack));

		final boolean potSlot = !(hoveredSlot.container instanceof Inventory);
		final int containerSlot = hoveredSlot.getContainerSlot();
		final boolean seedSlot = potSlot && containerSlot == PotMechanics.SEED;
		final boolean toolSlot = potSlot && containerSlot == PotMechanics.TOOL;

		final BotanyPotBlockEntity pot = level != null && level.getBlockEntity(menu.getPos()) instanceof BotanyPotBlockEntity be
			? be
			: null;

		final List<Component> extra = new ArrayList<>();
		if (seedSlot && crop != null) {
			// The crop hovered as the planted seed: the full effective, live-pot breakdown.
			appendPlantedCrop(extra, crop, pot, level, menu);
		} else {
			if (crop != null) {
				appendCropElsewhere(extra, crop);
			}
			if (soil != null) {
				appendSoil(extra, soil);
			}
			if (fertilizer != null) {
				appendFertilizer(extra, fertilizer);
			}
			if (interaction != null) {
				appendInteraction(extra, interaction);
			}
			if (toolSlot || stack.is(ModTags.HARVEST_ITEMS)) {
				appendTool(extra, stack);
			}
		}

		if (!extra.isEmpty()) {
			lines.add(Component.empty());
			lines.addAll(extra);
		}
	}

	private static void appendPlantedCrop(
		final List<Component> lines, final CropRecipe crop, final @Nullable BotanyPotBlockEntity pot,
		final @Nullable Level level, final AbstractPotMenu menu
	) {
		final int required;
		final double potYield;
		final double soilYield;
		final double toolYield;
		final boolean acceptsSoil;
		if (pot != null) {
			required = pot.requiredGrowthTicks();
			final SoilRecipe resolvedSoil = pot.resolveSoil();
			potYield = pot.yieldModifier();
			soilYield = resolvedSoil != null ? resolvedSoil.yieldModifier() : 0.0;
			toolYield = ToolAttributes.sum(pot.getHarvestTool(), ModAttributes.YIELD);
			acceptsSoil = crop.acceptsSoil(pot.getItem(PotMechanics.SOIL));
		} else {
			required = GrowthFormula.requiredGrowthTicks(crop.growTime(), 0.0, 0.0, 0.0);
			potYield = 0.0;
			soilYield = 0.0;
			toolYield = 0.0;
			acceptsSoil = true;
		}
		final float tickRate = level != null ? level.tickRateManager().tickrate() : 20.0f;

		lines.add(header("Planted crop"));
		lines.add(plainLine("Grow time",
			PotTooltipFormatting.formatDuration(required)
				+ " (" + PotTooltipFormatting.effectiveGameTicks(required, tickRate) + " ticks)"));

		// Pot yield modifier is the live pot's additive tier output (§D); 0 for base pots.
		final YieldBreakdown breakdown = PotTooltipFormatting.yieldBreakdown(
			crop.yield(), crop.yieldScale(), potYield, soilYield, toolYield
		);
		lines.add(plainLine("Total yield", PotTooltipFormatting.percent(breakdown.total())));
		lines.add(neutralSubLine("Base", breakdown.base()));
		lines.add(signedSubLine("Soil", breakdown.soil()));
		lines.add(signedSubLine("Pot", breakdown.pot()));
		lines.add(signedSubLine("Tool", breakdown.tool()));

		if (PotTooltipFormatting.wrongSoil(acceptsSoil)) {
			lines.add(warning("Wrong soil"));
		}
		if (crop.potPredicate().isPresent() && level != null) {
			final BlockPredicate predicate = crop.potPredicate().get();
			final boolean matches = predicate.matches(new BlockInWorld(level, menu.getPos(), false));
			if (PotTooltipFormatting.wrongPot(true, matches)) {
				lines.add(warning("Wrong pot"));
			}
		}
	}

	private static void appendCropElsewhere(final List<Component> lines, final CropRecipe crop) {
		lines.add(header("Crop"));
		lines.add(plainLine("Base grow time", PotTooltipFormatting.formatDuration(crop.growTime())));
		lines.add(plainLine("Base yield", PotTooltipFormatting.percent(crop.yield())));
		lines.add(plainLine("Yield scale", PotTooltipFormatting.percent(crop.yieldScale())));
	}

	private static void appendSoil(final List<Component> lines, final SoilRecipe soil) {
		lines.add(header("Soil"));
		lines.add(signedLine("Growth", soil.growthModifier()));
		lines.add(signedLine("Yield", soil.yieldModifier()));
	}

	private static void appendFertilizer(final List<Component> lines, final FertilizerRecipe fertilizer) {
		lines.add(header("Fertilizer"));
		lines.add(plainLine("Growth", describeGrowth(fertilizer.growth())));
	}

	private static void appendInteraction(final List<Component> lines, final PotInteractionRecipe interaction) {
		lines.add(header("Pot interaction"));
		interaction.newSoil().ifPresent(newSoil -> lines.add(
			Component.literal("Sets soil: ").withStyle(ChatFormatting.GRAY).append(newSoil.getHoverName())));
		interaction.newSeed().ifPresent(newSeed -> lines.add(
			Component.literal("Sets seed: ").withStyle(ChatFormatting.GRAY).append(newSeed.getHoverName())));
	}

	private static void appendTool(final List<Component> lines, final ItemStack tool) {
		final double growth = GrowthFormula.toolEfficiencyModifier(tool, ToolAttributes.sum(tool, ModAttributes.GROWTH));
		lines.add(header("Harvest tool"));
		lines.add(signedLine("Growth", growth));
	}

	private static String describeGrowth(final GrowthAmount growth) {
		if (growth instanceof GrowthAmount.Constant constant) {
			return "+" + constant.amount() + " ticks";
		}
		if (growth instanceof GrowthAmount.Ranged ranged) {
			return "+" + ranged.min() + "–" + ranged.max() + " ticks";
		}
		if (growth instanceof GrowthAmount.Percent percent) {
			return "+" + MathHelper.formatDecimal(percent.amount() * 100.0) + "% of grow time";
		}
		return "";
	}

	// ---- component builders ----

	private static Component header(final String text) {
		return Component.literal(text).withStyle(ChatFormatting.GOLD);
	}

	private static Component plainLine(final String label, final String value) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	private static Component signedLine(final String label, final double fraction) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(PotTooltipFormatting.signedPercent(fraction)).withStyle(trendColor(fraction)));
	}

	private static Component signedSubLine(final String label, final double fraction) {
		return Component.literal(INDENT + label + ": ").withStyle(ChatFormatting.DARK_GRAY)
			.append(Component.literal(PotTooltipFormatting.signedPercent(fraction)).withStyle(trendColor(fraction)));
	}

	private static Component neutralSubLine(final String label, final double fraction) {
		return Component.literal(INDENT + label + ": ").withStyle(ChatFormatting.DARK_GRAY)
			.append(Component.literal(PotTooltipFormatting.percent(fraction)).withStyle(ChatFormatting.GRAY));
	}

	private static Component warning(final String text) {
		return Component.literal(text).withStyle(ChatFormatting.RED);
	}

	private static ChatFormatting trendColor(final double value) {
		return switch (PotTooltipFormatting.trend(value)) {
			case BUFF -> ChatFormatting.GREEN;
			case NERF -> ChatFormatting.RED;
			case NEUTRAL -> ChatFormatting.GRAY;
		};
	}
}
