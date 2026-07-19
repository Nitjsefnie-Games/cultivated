package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

/** Phase A appendix — tag keys the data engine references. */
public final class ModTags {
	// Enchantment tags (§A.7 growth boost, §B.5 harvest-damage negation).
	public static final TagKey<Enchantment> INCREASE_POT_GROWTH = enchantment("increase_pot_growth");
	public static final TagKey<Enchantment> NEGATE_HARVEST_DAMAGE = enchantment("negate_harvest_damage");

	// Item tags.
	public static final TagKey<Item> HARVEST_ITEMS = item("harvest_items");
	public static final TagKey<Item> SOIL_DIRT = item("soil/dirt");
	public static final TagKey<Item> SOIL_WATER = item("soil/water");
	public static final TagKey<Item> SOIL_LAVA = item("soil/lava");
	public static final TagKey<Item> SOIL_SNOW = item("soil/snow");
	public static final TagKey<Item> SOIL_MUSHROOM = item("soil/mushroom");
	public static final TagKey<Item> SOIL_SAND = item("soil/sand");
	public static final TagKey<Item> SOIL_SCULK = item("soil/sculk");
	public static final TagKey<Item> CROP_GENERATOR_IGNORES = item("crop_generator_ignores");
	public static final TagKey<Item> SOIL_GENERATOR_IGNORES = item("soil_generator_ignores");

	private ModTags() {
	}

	private static TagKey<Item> item(final String path) {
		return TagKey.create(Registries.ITEM, Cultivated.id(path));
	}

	private static TagKey<Enchantment> enchantment(final String path) {
		return TagKey.create(Registries.ENCHANTMENT, Cultivated.id(path));
	}
}
