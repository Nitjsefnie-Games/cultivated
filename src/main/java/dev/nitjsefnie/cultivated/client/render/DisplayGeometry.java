package dev.nitjsefnie.cultivated.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Phase C §C.4/§C.5/§C.6 — the geometry of a single resolved display, captured during {@code
 * extractRenderState} and replayed during {@code submit} with the {@link
 * dev.nitjsefnie.cultivated.data.display.RenderOptions} transforms already applied to {@code
 * poseStack} by the {@link BotanyPotBlockEntityRenderer}.
 *
 * <p>Task C1 only produced high-level block-model draws ({@code BlockModelRenderState.submit}). Task
 * C2 generalises the resolved draw to this closure so the pot renderer can replay <em>either</em> a
 * high-level block model, a low-level face-filtered / fluid / textured-cube quad batch (§C.4), or an
 * entity submission (§C.5) through one uniform submit pass.
 */
@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface DisplayGeometry {
	/** Replay this geometry into {@code collector}; {@code poseStack} is already fully transformed. */
	void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CameraRenderState camera);
}
