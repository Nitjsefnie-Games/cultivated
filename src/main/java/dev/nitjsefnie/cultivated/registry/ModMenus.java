package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.menu.BasicPotMenu;
import dev.nitjsefnie.cultivated.menu.HopperPotMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Phase B §B.7 — registers the two pot menu types ({@code cultivated:basic_pot_menu},
 * {@code cultivated:hopper_pot_menu}) directly against the vanilla {@code MENU} registry, mirroring
 * {@link ModRecipes}/{@link ModBlocks}. Both are Fabric {@link ExtendedMenuType}s carrying a
 * {@link BlockPos} payload (via {@link BlockPos#STREAM_CODEC}), so the client menu is created with the
 * live pot position; the client-side factory forwards to each menu's {@code (id, inventory, pos)}
 * constructor.
 */
public final class ModMenus {
	public static ExtendedMenuType<BasicPotMenu, BlockPos> BASIC_POT;
	public static ExtendedMenuType<HopperPotMenu, BlockPos> HOPPER_POT;

	private ModMenus() {
	}

	public static void register() {
		BASIC_POT = register("basic_pot_menu",
			new ExtendedMenuType<BasicPotMenu, BlockPos>(BasicPotMenu::new, BlockPos.STREAM_CODEC));
		HOPPER_POT = register("hopper_pot_menu",
			new ExtendedMenuType<HopperPotMenu, BlockPos>(HopperPotMenu::new, BlockPos.STREAM_CODEC));
	}

	private static <T extends AbstractContainerMenu> ExtendedMenuType<T, BlockPos> register(
		final String name, final ExtendedMenuType<T, BlockPos> type
	) {
		return Registry.register(BuiltInRegistries.MENU, Cultivated.id(name), type);
	}
}
