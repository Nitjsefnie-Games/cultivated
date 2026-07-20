package dev.nitjsefnie.cultivated.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nitjsefnie.cultivated.block.BotanyPotBlock;
import dev.nitjsefnie.cultivated.block.BotanyPotBlockEntity;
import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.AxisRotation;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.Vec3f;
import dev.nitjsefnie.cultivated.recipe.CropRecipe;
import dev.nitjsefnie.cultivated.recipe.PotContext;
import dev.nitjsefnie.cultivated.recipe.SoilRecipe;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Phase C §C.1 — the botany pot block-entity renderer: draws the soil display at the bottom of the
 * planter and the crop display(s) stacked on top, scaled by growth progress, the whole render rotated
 * to the pot's {@code horizontal_facing}. Uses the 26.2 render-state pipeline: {@code
 * extractRenderState} resolves each {@link Display} into {@link ResolvedDisplay} block-model draws via
 * the {@link DisplayRendererRegistry} (§C.2, simple + phased in Task C1); {@code submit} replays them
 * with the {@link RenderOptions} transforms applied (§C.6). Honors the client visual config (§C.7):
 * {@code render_soil}/{@code render_crop} toggles, {@code use_growth_animation}, and {@code
 * pot_view_distance} (via {@link #getViewDistance()}).
 *
 * <p>Visual correctness (does it LOOK right) is deferred to in-game testing — it cannot be verified
 * headlessly. The face set, fluid layer and per-quad tint carried in {@link RenderOptions} are
 * resolved but not yet applied through the high-level block-model submit path (deferred to Task C2's
 * lower-level geometry path); the explicit {@code color} tint IS applied (see {@link DisplayResolveContext}).
 */
@Environment(EnvType.CLIENT)
public class BotanyPotBlockEntityRenderer implements BlockEntityRenderer<BotanyPotBlockEntity, PotRenderState> {
	private final BlockModelResolver blockModelResolver;
	private final DisplayRendererRegistry displayRenderers;

	public BotanyPotBlockEntityRenderer(final BlockEntityRendererProvider.Context context) {
		this.blockModelResolver = context.blockModelResolver();
		this.displayRenderers = DisplayRendererRegistry.createDefault();
	}

	@Override
	public PotRenderState createRenderState() {
		return new PotRenderState();
	}

	@Override
	public void extractRenderState(
		final BotanyPotBlockEntity pot,
		final PotRenderState state,
		final float partialTicks,
		final Vec3 cameraPosition,
		final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(pot, state, partialTicks, cameraPosition, breakProgress);
		state.soilDraws.clear();
		state.cropDraws.clear();
		state.facingYRot = facingYRot(pot);

		final float progress = pot.getGrowthProgress();
		final boolean animate = CultivatedConfig.useGrowthAnimation;

		if (CultivatedConfig.renderCrop) {
			this.extractCrop(pot, state, progress, animate);
		}
		if (CultivatedConfig.renderSoil) {
			this.extractSoil(pot, state, progress, cameraPosition);
		}
	}

	/** Crop displays render only while growth is sustained (§C.1), scaled by growth progress. */
	private void extractCrop(final BotanyPotBlockEntity pot, final PotRenderState state, final float progress, final boolean animate) {
		final CropRecipe crop = pot.resolveCrop();
		if (crop == null) {
			return;
		}
		if (!crop.acceptsSoil(pot.getItem(PotContext.SOIL))) {
			return; // growth not sustained → no crop drawn (§C.1)
		}
		final float growthScale = PotRenderMath.cropScale(progress, animate);
		final DisplayResolveContext context = new DisplayResolveContext(this.blockModelResolver, this.displayRenderers, progress, growthScale);
		for (final Display display : crop.displays()) {
			context.resolve(display, state.cropDraws);
		}
	}

	/** Soil renders only when a soil item is present and the camera is at/above the pot's Y (§C.1). */
	private void extractSoil(final BotanyPotBlockEntity pot, final PotRenderState state, final float progress, final Vec3 cameraPosition) {
		final ItemStack soilStack = pot.getItem(PotContext.SOIL);
		if (soilStack.isEmpty()) {
			return;
		}
		if (cameraPosition.y < pot.getBlockPos().getY()) {
			return; // soil is only visible from at/above the pot (§C.1)
		}
		final Display soilDisplay = soilDisplay(pot, soilStack);
		if (soilDisplay == null) {
			return;
		}
		final DisplayResolveContext context = new DisplayResolveContext(this.blockModelResolver, this.displayRenderers, progress, 1.0f);
		context.resolve(soilDisplay, state.soilDraws);
	}

	/**
	 * The soil display: the soil recipe's display, or — for a block item with no soil recipe — a
	 * fallback simple block-state display of that block, so any block "looks like" soil (§C.1).
	 */
	private static @Nullable Display soilDisplay(final BotanyPotBlockEntity pot, final ItemStack soilStack) {
		final SoilRecipe soil = pot.resolveSoil();
		if (soil != null) {
			return soil.soilDisplay();
		}
		final Block block = soilStack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
		if (block == null) {
			return null;
		}
		return new Display.Simple(block.defaultBlockState(), RenderOptions.DEFAULT);
	}

	private static float facingYRot(final BotanyPotBlockEntity pot) {
		final var blockState = pot.getBlockState();
		if (!blockState.hasProperty(BotanyPotBlock.FACING)) {
			return Direction.NORTH.toYRot();
		}
		return blockState.getValue(BotanyPotBlock.FACING).toYRot();
	}

	@Override
	public void submit(final PotRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
		if (state.soilDraws.isEmpty() && state.cropDraws.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		// Rotate the whole render about the pot's vertical centre axis to the pot facing (§C.1).
		poseStack.translate(0.5f, 0.0f, 0.5f);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingYRot));
		poseStack.translate(-0.5f, 0.0f, -0.5f);

		final float soilTop = submitStack(state.soilDraws, poseStack, collector, state.lightCoords, 0.0f);
		submitStack(state.cropDraws, poseStack, collector, state.lightCoords, soilTop);
		poseStack.popPose();
	}

	/** Render a bottom-up stack of displays, each lifted above the previous by its measured height (§C.6). */
	private static float submitStack(
		final List<ResolvedDisplay> draws, final PoseStack poseStack, final SubmitNodeCollector collector, final int lightCoords, final float startBase
	) {
		float base = startBase;
		for (final ResolvedDisplay draw : draws) {
			submitDisplay(draw, poseStack, collector, lightCoords, base);
			base = PotRenderMath.nextStackBase(base, draw.options(), draw.growthScale());
		}
		return base;
	}

	/** Apply scale (× growth), offset, then each re-centred axis rotation (§C.6), and submit the model. */
	private static void submitDisplay(
		final ResolvedDisplay draw, final PoseStack poseStack, final SubmitNodeCollector collector, final int lightCoords, final float base
	) {
		final RenderOptions options = draw.options();
		final Vec3f scale = options.scale();
		final Vec3f offset = options.offset();
		final float growth = draw.growthScale();

		poseStack.pushPose();
		poseStack.translate(0.5f, base, 0.5f);
		poseStack.scale(scale.x() * growth, scale.y() * growth, scale.z() * growth);
		poseStack.translate(offset.x(), offset.y(), offset.z());
		for (final AxisRotation rotation : options.rotation()) {
			poseStack.rotateAround(
				axisOf(rotation).rotationDegrees(rotation.degrees()),
				PotRenderMath.ROTATION_PIVOT, PotRenderMath.ROTATION_PIVOT, PotRenderMath.ROTATION_PIVOT
			);
		}
		// Centre the 0..1 block-model cube horizontally on the pivot the transforms were built around.
		poseStack.translate(-0.5f, 0.0f, -0.5f);
		draw.model().submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	private static Axis axisOf(final AxisRotation rotation) {
		return switch (rotation.axis()) {
			case X -> Axis.XP;
			case Y -> Axis.YP;
			case Z -> Axis.ZP;
		};
	}

	/** §C.7 {@code pot_view_distance}: soil/crop stop rendering beyond this distance (drives shouldRender). */
	@Override
	public int getViewDistance() {
		return Math.max(1, (int)CultivatedConfig.potViewDistance);
	}
}
