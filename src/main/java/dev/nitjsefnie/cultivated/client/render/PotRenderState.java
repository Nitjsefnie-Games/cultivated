package dev.nitjsefnie.cultivated.client.render;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * Phase C — the per-frame render state extracted from a botany pot: the resolved soil and crop
 * block-model draws (bottom-up) and the pot's facing yaw. Populated in {@code extractRenderState} and
 * consumed in {@code submit} (the 26.2 pipeline decouples the two passes).
 */
@Environment(EnvType.CLIENT)
public class PotRenderState extends BlockEntityRenderState {
	/** Soil draws, rendered at the bottom of the planter (§C.1). */
	public final List<ResolvedDisplay> soilDraws = new ArrayList<>();
	/** Crop draws, stacked bottom-up above the soil (§C.1). */
	public final List<ResolvedDisplay> cropDraws = new ArrayList<>();
	/** The pot's {@code horizontal_facing} yaw in degrees; the whole render is rotated to it (§C.1). */
	public float facingYRot;
}
