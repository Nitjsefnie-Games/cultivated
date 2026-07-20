package dev.nitjsefnie.cultivated.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.nitjsefnie.cultivated.block.BotanyPotBlock;
import dev.nitjsefnie.cultivated.block.Tier;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;

/**
 * Phase D §D / §B.8 — the item tooltip for tiered pot block items. Base pots get no extra lines; a
 * tiered pot shows a "hold Shift" hint, and while Shift is held the tier's additive growth-speed and
 * yield modifiers (§A.7/§A.8) alongside the derived approximate growth multiplier (divisor ≈ 1 +
 * speed under default config, so the pot's own contribution is roughly {@code (1 + speed)×}).
 *
 * <p>Client-only: registered from {@code CultivatedClient} via Fabric's {@link ItemTooltipCallback}
 * (MC 26.2 {@code BlockItem}s carry no per-instance {@code appendHoverText}), and Shift is read
 * live through {@link InputConstants#isKeyDown} since 26.2 dropped {@code Screen.hasShiftDown()}.
 */
public final class TierPotTooltip {
	private TierPotTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!(stack.getItem() instanceof BlockItem blockItem)
				|| !(blockItem.getBlock() instanceof BotanyPotBlock pot)) {
				return;
			}
			final Tier tier = pot.tier();
			if (tier.isBase()) {
				return;
			}
			if (shiftDown()) {
				lines.add(Component.translatable("tooltip.cultivated.tier.speed", "×" + growthMultiplier(tier))
					.withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("tooltip.cultivated.tier.output", "+" + tier.output())
					.withStyle(ChatFormatting.GRAY));
			} else {
				lines.add(Component.translatable("tooltip.cultivated.tier.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
			}
		});
	}

	/**
	 * The tier's approximate self-contributed growth multiplier for the tooltip: the additive speed
	 * modifier feeds the growth divisor (§A.7), which is {@code 1 + speed} for a pot with no soil/tool
	 * bonus under the default global modifier — so the pot alone is roughly {@code (1 + speed)×} the
	 * base pot's speed.
	 */
	public static int growthMultiplier(final Tier tier) {
		return 1 + tier.speed();
	}

	private static boolean shiftDown() {
		final Window window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
			|| InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
	}
}
