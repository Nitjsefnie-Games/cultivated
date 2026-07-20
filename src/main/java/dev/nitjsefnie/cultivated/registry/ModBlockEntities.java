package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Phase B §B.1 (B1 carry-over) — registers the {@code cultivated:botany_pot} block-entity type over
 * every registered pot block ({@link ModBlocks#POTS}) and assigns it into the settable
 * {@link BotanyPotBlockEntity#TYPE} holder that B1 exposed, so pot block entities can be constructed
 * in-world. Must run after {@link ModBlocks#register()}.
 */
public final class ModBlockEntities {
	public static BlockEntityType<BotanyPotBlockEntity> BOTANY_POT;

	private ModBlockEntities() {
	}

	public static void register() {
		final BlockEntityType<BotanyPotBlockEntity> type = FabricBlockEntityTypeBuilder
			.<BotanyPotBlockEntity>create(BotanyPotBlockEntity::new, ModBlocks.POTS.toArray(new Block[0]))
			.build();
		BOTANY_POT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Cultivated.id("botany_pot"), type);
		BotanyPotBlockEntity.TYPE = BOTANY_POT;
	}
}
