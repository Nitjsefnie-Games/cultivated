package dev.nitjsefnie.cultivated.data.growth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.util.MathHelper;
import net.minecraft.util.RandomSource;

/**
 * Phase A §A.10 — how much growth a fertilizer adds, dispatched by {@code type}.
 * <ul>
 *   <li>{@code cultivated:constant} — a fixed number of ticks;</li>
 *   <li>{@code cultivated:ranged} — a random int in {@code [min, max]};</li>
 *   <li>{@code cultivated:percent} — {@code floor(requiredGrowthTicks * amount)} ticks.</li>
 * </ul>
 */
public sealed interface GrowthAmount {
	String CONSTANT_TYPE = Cultivated.id("constant").toString();
	String RANGED_TYPE = Cultivated.id("ranged").toString();
	String PERCENT_TYPE = Cultivated.id("percent").toString();

	/** Resolve a concrete number of growth ticks to add. */
	int resolve(int requiredGrowthTicks, RandomSource random);

	String typeId();

	Codec<GrowthAmount> CODEC = Codec.STRING.dispatch(
		"type",
		GrowthAmount::typeId,
		type -> {
			if (CONSTANT_TYPE.equals(type)) {
				return Constant.MAP_CODEC;
			} else if (RANGED_TYPE.equals(type)) {
				return Ranged.MAP_CODEC;
			} else if (PERCENT_TYPE.equals(type)) {
				return Percent.MAP_CODEC;
			}
			throw new IllegalArgumentException("Unknown cultivated growth amount type: " + type);
		}
	);

	record Constant(int amount) implements GrowthAmount {
		static final MapCodec<Constant> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(Codec.intRange(0, Integer.MAX_VALUE).fieldOf("amount").forGetter(Constant::amount))
				.apply(i, Constant::new)
		);

		@Override
		public int resolve(final int requiredGrowthTicks, final RandomSource random) {
			return this.amount;
		}

		@Override
		public String typeId() {
			return CONSTANT_TYPE;
		}
	}

	record Ranged(int min, int max) implements GrowthAmount {
		static final MapCodec<Ranged> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					Codec.intRange(0, Integer.MAX_VALUE).fieldOf("min").forGetter(Ranged::min),
					Codec.intRange(0, Integer.MAX_VALUE).fieldOf("max").forGetter(Ranged::max)
				)
				.apply(i, Ranged::new)
		);

		@Override
		public int resolve(final int requiredGrowthTicks, final RandomSource random) {
			return MathHelper.nextIntInclusive(random, this.min, this.max);
		}

		@Override
		public String typeId() {
			return RANGED_TYPE;
		}
	}

	record Percent(float amount) implements GrowthAmount {
		static final MapCodec<Percent> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(Codec.floatRange(0.0f, 1.0f).fieldOf("amount").forGetter(Percent::amount))
				.apply(i, Percent::new)
		);

		@Override
		public int resolve(final int requiredGrowthTicks, final RandomSource random) {
			return (int)Math.floor(requiredGrowthTicks * this.amount);
		}

		@Override
		public String typeId() {
			return PERCENT_TYPE;
		}
	}
}
