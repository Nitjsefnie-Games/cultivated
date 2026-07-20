package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.block.BotanyPotBlock;
import dev.nitjsefnie.cultivated.block.PotMaterials;
import dev.nitjsefnie.cultivated.block.PotType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Phase B §B.1 — registers every botany pot block (Basic / Hopper / Waxed × each
 * {@link PotMaterials} material) plus its {@link BlockItem}, directly against the vanilla registries
 * (no generic registrar framework — mirrors {@link ModRecipes}/{@link ModComponents}). Each pot
 * inherits the map colour of its source material's vanilla block, falling back to orange.
 *
 * <p>{@link #POTS} is populated in registration order and consumed by {@link ModBlockEntities} (to
 * build the shared block-entity type) and {@link ModCreativeTab} (to fill the creative tab).
 */
public final class ModBlocks {
	/** Hardness (§B.1: ~1.25). */
	private static final float HARDNESS = 1.25f;
	/** Blast resistance (§B.1: ~4.2). */
	private static final float RESISTANCE = 4.2f;

	/** Every registered pot block, in registration order. Empty until {@link #register()} runs. */
	public static final List<Block> POTS = new ArrayList<>();

	private ModBlocks() {
	}

	public static void register() {
		if (!POTS.isEmpty()) {
			return; // idempotent guard
		}
		for (final String material : PotMaterials.ALL) {
			final MapColor mapColor = mapColorOf(material);
			for (final PotType type : PotType.values()) {
				POTS.add(registerPot(PotMaterials.potBlockName(material, type), type, mapColor));
			}
		}
		Cultivated.LOGGER.info("Registered {} botany pot blocks", POTS.size());
	}

	private static Block registerPot(final String name, final PotType type, final MapColor mapColor) {
		final Identifier id = Cultivated.id(name);
		final ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);

		final BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(mapColor)
			.strength(HARDNESS, RESISTANCE)
			.sound(SoundType.STONE)
			.noOcclusion()
			// light emission follows the block-entity-driven LEVEL state (§B.1)
			.lightLevel(state -> state.getValue(BotanyPotBlock.LEVEL))
			.setId(blockKey);

		final BotanyPotBlock block = new BotanyPotBlock(type, properties);
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

		final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		final BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey));
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);

		return block;
	}

	/** Map colour of the vanilla source block for {@code material}, or orange if it is absent. */
	private static MapColor mapColorOf(final String material) {
		final Block source = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(material));
		return source != Blocks.AIR ? source.defaultMapColor() : MapColor.COLOR_ORANGE;
	}
}
