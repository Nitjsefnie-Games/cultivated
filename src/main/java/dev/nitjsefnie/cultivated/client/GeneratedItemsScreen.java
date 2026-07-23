package dev.nitjsefnie.cultivated.client;

import dev.nitjsefnie.cultivated.network.GeneratedItemsSyncPayload;
import dev.nitjsefnie.cultivated.network.ToggleGeneratedItemPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Chunk 2 — the scrollable "Generated Items" screen opened from the hopper GUI's cogwheel button.
 * One row per generated entry of the pot at {@link #pos}: the item icon, its display name and a
 * checkbox where CHECKED means the item keeps generating (unsuppressed) and UNCHECKED means it is
 * suppressed. Clicking a checkbox sends {@link ToggleGeneratedItemPayload}; the server answers with
 * a fresh {@link GeneratedItemsSyncPayload}, which rebuilds the rows from the authoritative state.
 * Rows beyond the visible window scroll with the mouse wheel (clamped). An empty snapshot shows
 * "Nothing generated yet". Esc closes the screen (vanilla {@link Screen#keyPressed} handling).
 */
@Environment(EnvType.CLIENT)
public class GeneratedItemsScreen extends Screen {
	private static final Component TITLE = Component.literal("Generated Items");
	private static final Component EMPTY_MESSAGE = Component.literal("Nothing generated yet");
	private static final int ROW_HEIGHT = 20;
	private static final int LIST_WIDTH = 240;
	private static final int TOP_MARGIN = 36;
	private static final int BOTTOM_MARGIN = 32;
	/** Horizontal space between the item icon and the checkbox's left edge. */
	private static final int ICON_WIDTH = 22;

	private final BlockPos pos;
	private final List<Checkbox> checkboxes = new ArrayList<>();
	private List<GeneratedItemsSyncPayload.Entry> entries = List.of();
	private int scrollOffset;
	private int listLeft;
	private int listTop;
	private int visibleRows = 1;

	public GeneratedItemsScreen(final BlockPos pos) {
		super(TITLE);
		this.pos = pos;
	}

	@Override
	protected void init() {
		this.listLeft = (this.width - LIST_WIDTH) / 2;
		this.listTop = TOP_MARGIN;
		this.visibleRows = Math.max(1, (this.height - TOP_MARGIN - BOTTOM_MARGIN) / ROW_HEIGHT);
		GeneratedItemsClientData.setSyncListener(this::onSync);
		this.rebuildRows();
	}

	/** Refresh from the latest client-side snapshot, recreating the row checkboxes. */
	private void rebuildRows() {
		this.entries = GeneratedItemsClientData.entries(this.pos);
		for (final Checkbox checkbox : this.checkboxes) {
			this.removeWidget(checkbox);
		}
		this.checkboxes.clear();
		for (int index = 0; index < this.entries.size(); index++) {
			final GeneratedItemsSyncPayload.Entry entry = this.entries.get(index);
			final int entryIndex = index;
			final Checkbox checkbox = Checkbox.builder(entry.stack().getHoverName(), this.font)
				.pos(this.listLeft + ICON_WIDTH, 0)
				.maxWidth(LIST_WIDTH - ICON_WIDTH)
				.selected(!entry.suppressed())
				.onValueChange((cb, selected) ->
					ClientPlayNetworking.send(new ToggleGeneratedItemPayload(this.pos, entryIndex)))
				.build();
			this.checkboxes.add(checkbox);
			this.addRenderableWidget(checkbox);
		}
		this.scrollOffset = this.clampedOffset(this.scrollOffset);
		this.layoutRows();
	}

	/** Reposition the row checkboxes for the current scroll offset and hide the scrolled-off ones. */
	private void layoutRows() {
		for (int index = 0; index < this.checkboxes.size(); index++) {
			final Checkbox checkbox = this.checkboxes.get(index);
			final int row = index - this.scrollOffset;
			final boolean visible = row >= 0 && row < this.visibleRows;
			checkbox.visible = visible;
			checkbox.active = visible;
			checkbox.setX(this.listLeft + ICON_WIDTH);
			checkbox.setY(this.listTop + row * ROW_HEIGHT + (ROW_HEIGHT - checkbox.getHeight()) / 2);
		}
	}

	private int clampedOffset(final int offset) {
		return Math.max(0, Math.min(offset, Math.max(0, this.entries.size() - this.visibleRows)));
	}

	/** A sync arrived for this pot while the screen is open: adopt the authoritative state. */
	private void onSync(final BlockPos syncedPos) {
		if (syncedPos.equals(this.pos) && this.minecraft != null && this.minecraft.gui.screen() == this) {
			this.rebuildRows();
		}
	}

	@Override
	public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
		final int newOffset = this.clampedOffset(this.scrollOffset - (int) Math.signum(scrollY));
		if (newOffset != this.scrollOffset) {
			this.scrollOffset = newOffset;
			this.layoutRows();
		}
		return true;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
		if (this.entries.isEmpty()) {
			graphics.centeredText(this.font, EMPTY_MESSAGE, this.width / 2, this.height / 2, 0xFFA0A0A0);
			return;
		}
		// Item icons for the visible rows (the checkboxes draw their own box + label).
		for (int row = 0; row < this.visibleRows; row++) {
			final int index = this.scrollOffset + row;
			if (index >= this.entries.size()) {
				break;
			}
			graphics.item(this.entries.get(index).stack(), this.listLeft, this.listTop + row * ROW_HEIGHT + (ROW_HEIGHT - 16) / 2);
		}
	}

	@Override
	public void removed() {
		GeneratedItemsClientData.setSyncListener(null);
		GeneratedItemsClientData.forget(this.pos);
		super.removed();
	}
}
