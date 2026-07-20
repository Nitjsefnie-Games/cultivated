package dev.nitjsefnie.cultivated.item;

import dev.nitjsefnie.cultivated.block.Tier;
import net.minecraft.world.item.Item;

/**
 * Phase D §D — a botany-pot tier-upgrade item. Right-clicking a pot while holding one converts it in
 * place to {@link #targetTier} (see {@code BotanyPotBlock#useItemOn}), preserving the pot's contents
 * and growth. The item only carries its target tier; the strict next-tier gating and the world swap
 * live in {@link dev.nitjsefnie.cultivated.block.TierUpgrade} and the block interaction.
 */
public class UpgradeItem extends Item {
	private final Tier targetTier;

	public UpgradeItem(final Tier targetTier, final Properties properties) {
		super(properties);
		this.targetTier = targetTier;
	}

	/** The tier a pot is upgraded to when this item is used on it. */
	public Tier targetTier() {
		return this.targetTier;
	}
}
