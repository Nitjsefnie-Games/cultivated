package dev.nitjsefnie.cultivated.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase B §B.1 — the pure list of pot cosmetic materials and the id-mapping rules for the block
 * variants generated from them. Each material's name is also the path of the vanilla source block
 * whose map colour the pot inherits (namespace {@code minecraft}); registration
 * ({@link dev.nitjsefnie.cultivated.registry.ModBlocks}) resolves that block for its map colour,
 * falling back to orange when absent.
 *
 * <p>Kept free of any Minecraft registry access so the enumeration and id math are unit-testable
 * without a game runtime.
 */
public final class PotMaterials {
	/** The 16 vanilla dye colours, each contributing terracotta / glazed-terracotta / concrete pots. */
	private static final List<String> DYE_COLORS = List.of(
		"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	);

	/** Brick-family materials (each is a vanilla block path in the {@code minecraft} namespace). */
	private static final List<String> BRICK_MATERIALS = List.of(
		"bricks", "stone_bricks", "mossy_stone_bricks", "deepslate_bricks", "tuff_bricks",
		"mud_bricks", "prismarine", "nether_bricks", "red_nether_bricks",
		"polished_blackstone_bricks", "end_stone_bricks", "quartz_bricks"
	);

	/** Every pot material, in registration order (terracotta, dyed families, then bricks). */
	public static final List<String> ALL = buildMaterials();

	private PotMaterials() {
	}

	private static List<String> buildMaterials() {
		final List<String> materials = new ArrayList<>();
		materials.add("terracotta");
		for (final String color : DYE_COLORS) {
			materials.add(color + "_terracotta");
			materials.add(color + "_glazed_terracotta");
			materials.add(color + "_concrete");
		}
		materials.addAll(BRICK_MATERIALS);
		return List.copyOf(materials);
	}

	/** The registry path (mod namespace) of the pot block for {@code material} and {@code type}. */
	public static String potBlockName(final String material, final PotType type) {
		return switch (type) {
			case BASIC -> material + "_botany_pot";
			case HOPPER -> material + "_hopper_botany_pot";
			case WAXED -> material + "_waxed_botany_pot";
		};
	}
}
