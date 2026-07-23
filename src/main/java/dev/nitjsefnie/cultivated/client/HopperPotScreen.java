package dev.nitjsefnie.cultivated.client;

import dev.nitjsefnie.cultivated.menu.HopperPotMenu;
import dev.nitjsefnie.cultivated.menu.PotMenuTextures;
import dev.nitjsefnie.cultivated.network.RequestGeneratedItemsPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Phase B §B.7/§B.8 — the client screen for the {@code cultivated:hopper_pot_menu}. Draws the 176×215
 * container background, the soil/seed/tool input slots with their placeholder icons, the 4×3 grid of
 * output slots, and the 4×3 grid of fertilizer input slots above the player inventory. Tooltip lines
 * are Task B5.
 */
@Environment(EnvType.CLIENT)
public class HopperPotScreen extends AbstractContainerScreen<HopperPotMenu> {
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 215;
	/** Cogwheel button edge length; sits at the GUI's top-right corner (the title is top-left). */
	private static final int COG_SIZE = 18;

	public HopperPotScreen(final HopperPotMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		// Chunk 2 — cogwheel: request the generated-items snapshot and open the tracking screen.
		// No gear sprite exists in the assets, so this is a plain vanilla button with a gear glyph.
		this.addRenderableWidget(Button.builder(Component.literal("⚙"), button -> {
				ClientPlayNetworking.send(new RequestGeneratedItemsPayload(this.menu.getPos()));
				Minecraft.getInstance().gui.setScreen(new GeneratedItemsScreen(this.menu.getPos()));
			})
			.bounds(this.leftPos + this.imageWidth - COG_SIZE - 2, this.topPos + 2, COG_SIZE, COG_SIZE)
			.build());
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		final int left = (this.width - this.imageWidth) / 2;
		final int top = (this.height - this.imageHeight) / 2;
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, PotMenuTextures.HOPPER_BACKGROUND, left, top,
			0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256
		);
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(final ItemStack itemStack) {
		final List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(itemStack));
		PotTooltip.appendLines(lines, this.menu, this.hoveredSlot, itemStack);
		return lines;
	}
}
