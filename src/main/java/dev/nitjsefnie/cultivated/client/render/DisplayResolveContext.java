package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Phase C — the inputs a {@link DisplayRenderer} needs while resolving a {@link Display} into
 * {@link ResolvedDisplay} draws during {@code extractRenderState}: the model/sprite/entity resolvers,
 * the dispatch registry (so phased displays can recurse), the current growth progress (phase
 * selection, §C.3) and growth scale (§C.1), plus the live client {@link Level} and {@code
 * partialTicks} the entity path needs (§C.5). It also builds the {@link DisplayGeometry} each renderer
 * appends: the high-level block-model draw, the low-level face-filtered / fluid geometry (§C.4), and
 * the block-atlas sprite lookup for {@code textured_cube} (§C.5).
 */
@Environment(EnvType.CLIENT)
public final class DisplayResolveContext {
	private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

	private final BlockModelResolver blockModelResolver;
	private final DisplayRendererRegistry registry;
	private final SpriteGetter spriteGetter;
	private final EntityRenderDispatcher entityRenderDispatcher;
	private final DisplayEntityCache entityCache;
	private final @Nullable Level level;
	private final float partialTicks;
	private final float progress;
	private final float growthScale;

	public DisplayResolveContext(
		final BlockModelResolver blockModelResolver,
		final DisplayRendererRegistry registry,
		final SpriteGetter spriteGetter,
		final EntityRenderDispatcher entityRenderDispatcher,
		final DisplayEntityCache entityCache,
		final @Nullable Level level,
		final float partialTicks,
		final float progress,
		final float growthScale
	) {
		this.blockModelResolver = blockModelResolver;
		this.registry = registry;
		this.spriteGetter = spriteGetter;
		this.entityRenderDispatcher = entityRenderDispatcher;
		this.entityCache = entityCache;
		this.level = level;
		this.partialTicks = partialTicks;
		this.progress = progress;
		this.growthScale = growthScale;
	}

	/** Growth progress in {@code [0,1]} used to pick a phased display's sub-display (§C.3). */
	public float progress() {
		return this.progress;
	}

	/** The scale applied on top of a display's own {@link RenderOptions#scale()} (§C.1). */
	public float growthScale() {
		return this.growthScale;
	}

	/** The frame's partial tick, for entity render-state extraction (§C.5). */
	public float partialTicks() {
		return this.partialTicks;
	}

	/** The 26.2 entity render dispatcher used to extract + submit entity displays (§C.5). */
	public EntityRenderDispatcher entityRenderDispatcher() {
		return this.entityRenderDispatcher;
	}

	/** Dispatch a (sub-)display back through the registry — used by phased displays to delegate (§C.3). */
	public void resolve(final Display display, final List<ResolvedDisplay> out) {
		this.registry.resolve(display, this, out);
	}

	/** The block-atlas sprite for {@code texture} (§C.5); the atlas's missing sprite if unknown. */
	public TextureAtlasSprite sprite(final Identifier texture) {
		return this.spriteGetter.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture));
	}

	/** The cached display entity for {@code display}, built from NBT against the client level (§C.5). */
	public @Nullable Entity displayEntity(final Display.Entity display) {
		if (this.level == null) {
			return null;
		}
		return this.entityCache.get(display, this.level);
	}

	/**
	 * Whether {@code display}'s cached entity should advance its animation this frame — {@code true} at
	 * most once per game tick per cached entity, so animation speed is decoupled from framerate and from
	 * how many pots share the entity (§C.5). Returns {@code false} when there is no level.
	 */
	public boolean shouldTickDisplayEntity(final Display.Entity display) {
		if (this.level == null) {
			return false;
		}
		return this.entityCache.markTickedIfDue(display, this.level.getGameTime());
	}

	/**
	 * Resolve a {@code simple} display (§C.4) into geometry: the block-state model honoring the option
	 * {@code faces} (high-level fast path when all faces are drawn, low-level VertexConsumer path when
	 * restricted) and its {@code render_fluid} layer if requested. Tint (explicit {@code color} or the
	 * quad's world tint) is applied on both paths.
	 */
	public void resolveSimple(final BlockState blockState, final RenderOptions options, final List<ResolvedDisplay> out) {
		if (options.faces().equals(RenderOptions.ALL_FACES)) {
			final BlockModelRenderState model = this.bakeBlockModel(blockState, options);
			if (!model.isEmpty()) {
				out.add(new ResolvedDisplay(highLevelGeometry(model), options, this.growthScale));
			}
		} else {
			final DisplayGeometry geometry = LowLevelBlockRenderer.geometry(blockState, options);
			if (geometry != null) {
				out.add(new ResolvedDisplay(geometry, options, this.growthScale));
			}
		}
		if (options.renderFluid()) {
			this.addFluidLayer(blockState, options, out);
		}
	}

	/** Append the block's fluid layer as a tinted sprite cube (§C.4) — used by water/lava-style soils. */
	private void addFluidLayer(final BlockState blockState, final RenderOptions options, final List<ResolvedDisplay> out) {
		final FluidState fluidState = blockState.getFluidState();
		if (fluidState.isEmpty()) {
			return;
		}
		final FluidModel fluidModel = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluidState);
		final TextureAtlasSprite sprite = fluidModel.stillMaterial().sprite();
		final int argb;
		if (options.color().isPresent()) {
			argb = options.color().get().argb();
		} else if (fluidModel.tintSource() != null) {
			argb = 0xFF000000 | fluidModel.tintSource().color(blockState);
		} else {
			argb = 0xFFFFFFFF;
		}
		out.add(new ResolvedDisplay(CubeRenderer.geometry(sprite, argb, options), options, this.growthScale));
	}

	private static DisplayGeometry highLevelGeometry(final BlockModelRenderState model) {
		return (poseStack, collector, lightCoords, camera) -> model.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
	}

	/**
	 * Bake a block state's model into a fresh {@link BlockModelRenderState} on the cutout/translucent
	 * sheet (§C.4), then apply the option's tint to the model's tint layers if one is set — otherwise
	 * the block's own quad tints stand (§C.4 "else uses the block's world tint if the quad is tinted").
	 */
	public BlockModelRenderState bakeBlockModel(final BlockState blockState, final RenderOptions options) {
		final BlockModelRenderState model = new BlockModelRenderState();
		this.blockModelResolver.update(model, blockState, BLOCK_DISPLAY_CONTEXT);
		options.color().ifPresent(color -> {
			final IntList tints = model.tintLayers();
			for (int i = 0; i < tints.size(); i++) {
				tints.set(i, color.argb());
			}
		});
		return model;
	}
}
