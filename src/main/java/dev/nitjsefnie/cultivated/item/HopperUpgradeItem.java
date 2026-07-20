package dev.nitjsefnie.cultivated.item;

import net.minecraft.world.item.Item;

/**
 * Playtest PF4c — the Basic→Hopper pot upgrade item. Right-clicking a {@code BASIC} pot while holding
 * one converts it in place to the {@code HOPPER} pot of the same material and tier (see
 * {@code BotanyPotBlock#useItemOn}), preserving the pot's contents and growth. The item is a plain
 * marker; the strict "only basic upgrades" rule and the world swap live in
 * {@link dev.nitjsefnie.cultivated.block.HopperUpgrade} and the block interaction.
 */
public class HopperUpgradeItem extends Item {
	public HopperUpgradeItem(final Properties properties) {
		super(properties);
	}
}
