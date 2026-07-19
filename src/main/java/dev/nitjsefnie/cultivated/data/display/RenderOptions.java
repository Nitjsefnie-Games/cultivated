package dev.nitjsefnie.cultivated.data.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.util.CodecHelper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * Phase C §C.6 — render options carried by most displays. This is the parsed data model; the
 * renderer that consumes it is Phase C (client) and is not built here.
 */
public record RenderOptions(
	Vec3f scale,
	Vec3f offset,
	List<AxisRotation> rotation,
	boolean renderFluid,
	Optional<TintColor> color,
	Set<Direction> faces
) {
	public static final Vec3f DEFAULT_SCALE = new Vec3f(0.625f, 0.625f, 0.625f);
	public static final Vec3f ZERO = new Vec3f(0.0f, 0.0f, 0.0f);
	public static final Set<Direction> ALL_FACES = Set.of(Direction.values());

	public static final RenderOptions DEFAULT = new RenderOptions(
		DEFAULT_SCALE, ZERO, List.of(), false, Optional.empty(), ALL_FACES
	);

	public static final MapCodec<RenderOptions> MAP_CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
				Vec3f.CODEC.optionalFieldOf("scale", DEFAULT_SCALE).forGetter(RenderOptions::scale),
				Vec3f.CODEC.optionalFieldOf("offset", ZERO).forGetter(RenderOptions::offset),
				AxisRotation.CODEC.listOf().optionalFieldOf("rotation", List.of()).forGetter(RenderOptions::rotation),
				Codec.BOOL.optionalFieldOf("render_fluid", false).forGetter(RenderOptions::renderFluid),
				TintColor.CODEC.optionalFieldOf("color").forGetter(RenderOptions::color),
				CodecHelper.flexibleSet(Direction.CODEC).optionalFieldOf("faces", ALL_FACES).forGetter(RenderOptions::faces)
			)
			.apply(i, RenderOptions::new)
	);

	public static final Codec<RenderOptions> CODEC = MAP_CODEC.codec();

	/** A per-axis float triple, parsed from a 3-element array. */
	public record Vec3f(float x, float y, float z) {
		public static final Codec<Vec3f> CODEC = Codec.FLOAT.listOf(3, 3).xmap(
			list -> new Vec3f(list.get(0), list.get(1), list.get(2)),
			v -> List.of(v.x(), v.y(), v.z())
		);
	}

	/** An axis-aligned rotation: an axis (X/Y/Z) and an angle of 0/90/180/270 degrees (§C.6). */
	public enum AxisRotation {
		X_0(Axis.X, 0), X_90(Axis.X, 90), X_180(Axis.X, 180), X_270(Axis.X, 270),
		Y_0(Axis.Y, 0), Y_90(Axis.Y, 90), Y_180(Axis.Y, 180), Y_270(Axis.Y, 270),
		Z_0(Axis.Z, 0), Z_90(Axis.Z, 90), Z_180(Axis.Z, 180), Z_270(Axis.Z, 270);

		public static final Codec<AxisRotation> CODEC = Codec.STRING.xmap(AxisRotation::valueOf, Enum::name);

		private final Axis axis;
		private final int degrees;

		AxisRotation(final Axis axis, final int degrees) {
			this.axis = axis;
			this.degrees = degrees;
		}

		public Axis axis() {
			return this.axis;
		}

		public int degrees() {
			return this.degrees;
		}

		public enum Axis {
			X, Y, Z
		}
	}
}
