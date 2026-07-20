package dev.nitjsefnie.cultivated.command.generator;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Phase F.2 (last para) — a soil generator that maps a fixed item tag to a fixed display, used for
 * the built-in fluid/special soils (water / lava / snow) whose display is a rendered block state
 * rather than the source block itself. {@link #supports} matches any block whose item form is in the
 * configured tag; {@link #generate} emits a {@code cultivated:soil} recipe keyed on that tag with the
 * fixed simple display.
 */
public final class TaggedSoilGenerator implements SoilGenerator {
	private final TagKey<Item> inputTag;
	private final String tagReference;
	private final String displayBlockName;
	private final boolean renderFluid;
	private final double growthModifier;
	private final int lightLevel;

	/**
	 * @param inputTag         the item tag whose members this generator claims (e.g. {@code soil/water})
	 * @param displayBlockName the block state Name to render (e.g. {@code minecraft:water})
	 * @param renderFluid      whether the display renders as a fluid
	 * @param growthModifier   soil growth modifier
	 * @param lightLevel       emitted light (e.g. 15 for lava)
	 */
	public TaggedSoilGenerator(
		final TagKey<Item> inputTag, final String displayBlockName,
		final boolean renderFluid, final double growthModifier, final int lightLevel
	) {
		this.inputTag = inputTag;
		this.tagReference = "#" + inputTag.location();
		this.displayBlockName = displayBlockName;
		this.renderFluid = renderFluid;
		this.growthModifier = growthModifier;
		this.lightLevel = lightLevel;
	}

	@Override
	public boolean supports(final Block block, final Identifier blockId) {
		return block.asItem().getDefaultInstance().is(this.inputTag);
	}

	@Override
	public JsonObject generate(final Block block, final Identifier blockId) {
		final JsonObject json = new JsonObject();
		json.addProperty("type", "cultivated:soil");
		json.addProperty("input", this.tagReference);
		if (this.growthModifier != 0.0) {
			json.addProperty("growth_modifier", this.growthModifier);
		}
		if (this.lightLevel != 0) {
			json.addProperty("light_level", this.lightLevel);
		}

		final JsonObject blockState = new JsonObject();
		blockState.addProperty("Name", this.displayBlockName);
		final JsonObject display = new JsonObject();
		display.addProperty("type", "cultivated:simple");
		display.add("block_state", blockState);
		if (this.renderFluid) {
			display.addProperty("render_fluid", true);
		}
		json.add("display", display);
		return json;
	}
}
