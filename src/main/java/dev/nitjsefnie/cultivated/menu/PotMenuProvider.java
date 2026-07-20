package dev.nitjsefnie.cultivated.menu;

import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Phase B §B.7 — opens a botany pot menu for a player. Implemented as Fabric's
 * {@link ExtendedMenuProvider} (fabric-menu-api-v1) so the pot's {@link BlockPos} is streamed to the
 * client alongside the open-screen packet (the {@code fabric-screen-handler-api} / vanilla
 * {@code ExtendedScreenHandlerType} is not present in this Fabric build). The server builds the menu
 * over the live {@link BotanyPotBlockEntity}; the client rebuilds it over a throwaway container using
 * the streamed position (see {@link BasicPotMenu}/{@link HopperPotMenu}).
 */
public record PotMenuProvider(BotanyPotBlockEntity pot) implements ExtendedMenuProvider<BlockPos> {
	@Override
	public Component getDisplayName() {
		return this.pot.getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
		final BlockPos pos = this.pot.getBlockPos();
		return this.pot.getPotType().isHopper()
			? new HopperPotMenu(containerId, inventory, this.pot, pos)
			: new BasicPotMenu(containerId, inventory, this.pot, pos);
	}

	@Override
	public BlockPos getScreenOpeningData(final ServerPlayer player) {
		return this.pot.getBlockPos();
	}
}
