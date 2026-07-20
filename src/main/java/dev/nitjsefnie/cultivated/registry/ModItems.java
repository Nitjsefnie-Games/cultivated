package dev.nitjsefnie.cultivated.registry;

import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.block.Tier;
import dev.nitjsefnie.cultivated.condition.ConfigResourceCondition;
import dev.nitjsefnie.cultivated.item.UpgradeItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Phase D §D — registers the three tier-upgrade items ({@code elite_upgrade}, {@code ultra_upgrade},
 * {@code mega_upgrade}) directly against the vanilla item registry (mirrors {@link ModBlocks}), and
 * registers the {@code cultivated:config} Fabric resource condition that gates their crafting recipes.
 * {@link #UPGRADES} is consumed by {@link ModCreativeTab} to add the items to the pots creative tab.
 */
public final class ModItems {
	public static UpgradeItem ELITE_UPGRADE;
	public static UpgradeItem ULTRA_UPGRADE;
	public static UpgradeItem MEGA_UPGRADE;

	/** Every registered upgrade item, in registration order. Empty until {@link #register()} runs. */
	public static final List<Item> UPGRADES = new ArrayList<>();

	private ModItems() {
	}

	public static void register() {
		if (!UPGRADES.isEmpty()) {
			return; // idempotent guard
		}
		ELITE_UPGRADE = registerUpgrade("elite_upgrade", Tier.ELITE);
		ULTRA_UPGRADE = registerUpgrade("ultra_upgrade", Tier.ULTRA);
		MEGA_UPGRADE = registerUpgrade("mega_upgrade", Tier.MEGA);
		// Gate the vanilla shaped upgrade recipes on the per-tier config flags (§A.9).
		ConfigResourceCondition.register();
		Cultivated.LOGGER.info("Registered {} pot upgrade items", UPGRADES.size());
	}

	private static UpgradeItem registerUpgrade(final String name, final Tier targetTier) {
		final Identifier id = Cultivated.id(name);
		final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		final UpgradeItem item = new UpgradeItem(targetTier, new Item.Properties().setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		UPGRADES.add(item);
		return item;
	}
}
