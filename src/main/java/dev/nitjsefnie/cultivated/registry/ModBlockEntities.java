package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import dev.nitjsefnie.cultivated.block.Tier;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Phase B §B.1 (B1 carry-over) / Phase D §D — registers one block-entity type per {@link Tier} over
 * that tier's pot blocks: {@code cultivated:botany_pot} over the base pots ({@link ModBlocks#POTS}) and
 * {@code cultivated:{elite,ultra,mega}_botany_pot} over each tier's 183 blocks
 * ({@link ModBlocks#TIERED_POTS}). All four share the core {@link BotanyPotBlockEntity} class + ticker;
 * each is recorded via {@link BotanyPotBlockEntity#registerType} so a pot reports the type of its own
 * tier (required for {@code isValid}/save-load, §D). Must run after {@link ModBlocks#register()}.
 */
public final class ModBlockEntities {
	/** The base ({@link Tier#BASE}) type, {@code cultivated:botany_pot}. */
	public static BlockEntityType<BotanyPotBlockEntity> BOTANY_POT;

	/** All four pot block-entity types keyed by {@link Tier} (base + elite/ultra/mega). */
	public static final Map<Tier, BlockEntityType<BotanyPotBlockEntity>> TYPES = new EnumMap<>(Tier.class);

	private ModBlockEntities() {
	}

	public static void register() {
		BOTANY_POT = registerType(Tier.BASE, "botany_pot", ModBlocks.POTS);
		for (final Map.Entry<Tier, List<Block>> entry : ModBlocks.TIERED_POTS.entrySet()) {
			final Tier tier = entry.getKey();
			registerType(tier, tier.idPrefix() + "_botany_pot", entry.getValue());
		}
	}

	private static BlockEntityType<BotanyPotBlockEntity> registerType(
		final Tier tier, final String name, final List<Block> blocks
	) {
		final BlockEntityType<BotanyPotBlockEntity> type = FabricBlockEntityTypeBuilder
			.<BotanyPotBlockEntity>create(BotanyPotBlockEntity::new, blocks.toArray(new Block[0]))
			.build();
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Cultivated.id(name), type);
		BotanyPotBlockEntity.registerType(tier, type);
		TYPES.put(tier, type);
		return type;
	}
}
