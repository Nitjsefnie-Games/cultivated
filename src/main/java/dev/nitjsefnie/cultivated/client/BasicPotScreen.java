package dev.nitjsefnie.cultivated.client;

import dev.nitjsefnie.cultivated.menu.BasicPotMenu;
import dev.nitjsefnie.cultivated.menu.PotMenuTextures;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase B §B.7/§B.8 — the client screen for the {@code cultivated:basic_pot_menu}. Draws the 176×166
 * container background (a Task B6 placeholder texture until authored) and the input slots with their
 * placeholder empty-slot icons (handled by {@link dev.nitjsefnie.cultivated.menu.PotSlot}). Tooltip
 * lines are Task B5.
 */
@Environment(EnvType.CLIENT)
public class BasicPotScreen extends AbstractContainerScreen<BasicPotMenu> {
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 166;

	public BasicPotScreen(final BasicPotMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		final int left = (this.width - this.imageWidth) / 2;
		final int top = (this.height - this.imageHeight) / 2;
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, PotMenuTextures.BASIC_BACKGROUND, left, top,
			0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256
		);
	}
}
