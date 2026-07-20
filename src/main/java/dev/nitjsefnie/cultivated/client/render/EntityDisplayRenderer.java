package dev.nitjsefnie.cultivated.client.render;

import com.mojang.math.Axis;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.Vec3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Phase C §C.5 — renders an {@code entity} display: builds the entity from NBT (cached via
 * {@link DisplayEntityCache}), optionally advances its animation once per game tick ({@code should_tick}),
 * scales/offsets it (via the resolved {@link RenderOptions}), spins it around Y by {@code spin_speed *
 * 360° * progress} ({@link PotRenderMath#spinDegrees}), and submits it and its passengers through the
 * 26.2 {@link EntityRenderDispatcher} render-state pipeline ({@code extractEntity} at extract time,
 * {@code submit} at submit time).
 */
@Environment(EnvType.CLIENT)
public final class EntityDisplayRenderer implements DisplayRenderer<Display> {
	@Override
	public void resolve(final Display display, final DisplayResolveContext context, final List<ResolvedDisplay> out) {
		if (!(display instanceof Display.Entity entityDisplay)) {
			return;
		}
		final Entity entity = context.displayEntity(entityDisplay);
		if (entity == null) {
			return;
		}
		// Advance the shared cached entity at most once per game tick (not once per pot per frame) so
		// animation speed is independent of pot count and framerate (§C.5).
		if (entityDisplay.shouldTick() && context.shouldTickDisplayEntity(entityDisplay)) {
			tick(entity);
		}

		final EntityRenderDispatcher dispatcher = context.entityRenderDispatcher();
		final List<EntityRenderState> states = new ArrayList<>();
		extractStates(entity, dispatcher, context.partialTicks(), states);
		if (states.isEmpty()) {
			return;
		}

		final float spin = PotRenderMath.spinDegrees(entityDisplay.spinSpeed(), context.progress());
		final DisplayGeometry geometry = (poseStack, collector, lightCoords, camera) -> {
			poseStack.pushPose();
			// Cancel the block-model centring the pot renderer applies (§C.6) — an entity's own origin
			// is already its base centre — then spin about that vertical axis.
			poseStack.translate(0.5f, 0.0f, 0.5f);
			if (spin != 0.0f) {
				poseStack.mulPose(Axis.YP.rotationDegrees(spin));
			}
			for (final EntityRenderState state : states) {
				dispatcher.submit(state, camera, 0.0, 0.0, 0.0, poseStack, collector);
			}
			poseStack.popPose();
		};

		final RenderOptions options = optionsFor(entityDisplay);
		out.add(new ResolvedDisplay(geometry, options, context.growthScale()));
	}

	/** Extract render states for {@code entity} and its whole passenger stack, recursing into nested riders. */
	private static void extractStates(
		final Entity entity, final EntityRenderDispatcher dispatcher, final float partialTicks, final List<EntityRenderState> out
	) {
		out.add(dispatcher.extractEntity(entity, partialTicks));
		for (final Entity passenger : entity.getPassengers()) {
			extractStates(passenger, dispatcher, partialTicks, out);
		}
	}

	/** Render transform for an entity display: its own {@code scale}/{@code offset}, no faces/fluid/tint. */
	private static RenderOptions optionsFor(final Display.Entity entityDisplay) {
		final Vec3f offset = entityDisplay.offset().orElse(RenderOptions.ZERO);
		return new RenderOptions(entityDisplay.scale(), offset, List.of(), false, Optional.empty(), RenderOptions.ALL_FACES);
	}

	/** Best-effort once-per-game-tick animation advance for a detached display entity ({@code should_tick}). */
	private static void tick(final Entity entity) {
		try {
			entity.setNoGravity(true);
			entity.tick();
		} catch (final Throwable t) {
			Cultivated.LOGGER.warn("Display entity {} threw while ticking; leaving it static", entity.getType(), t);
		}
	}
}
