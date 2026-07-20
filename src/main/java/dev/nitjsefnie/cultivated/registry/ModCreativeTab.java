package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Phase B §B.1 — the {@code cultivated:botany_pots} creative tab, populated with every registered
 * pot block ({@link ModBlocks#POTS}) in registration order. Must run after {@link ModBlocks#register()}.
 */
public final class ModCreativeTab {
	public static CreativeModeTab BOTANY_POTS;

	private ModCreativeTab() {
	}

	public static void register() {
		final CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.cultivated.botany_pots"))
			.icon(() -> new ItemStack(ModBlocks.POTS.isEmpty() ? net.minecraft.world.level.block.Blocks.FLOWER_POT : ModBlocks.POTS.get(0)))
			.displayItems((parameters, output) -> {
				for (final Block block : ModBlocks.POTS) {
					output.accept(block);
				}
			})
			.build();
		BOTANY_POTS = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Cultivated.id("botany_pots"), tab);
	}
}
