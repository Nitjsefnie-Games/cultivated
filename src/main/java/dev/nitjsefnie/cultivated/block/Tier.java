package dev.nitjsefnie.cultivated.block;

import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Phase D §D — the botany-pot upgrade tiers. {@link #BASE} is the implicit tier of every Phase B
 * pot (no speed/output bonus); {@code ELITE}/{@code ULTRA}/{@code MEGA} form an ordered upgrade
 * chain ({@link #next()}) and carry config-backed, <em>additive</em> modifiers: {@link #speed()}
 * feeds the growth divisor (§A.7) and {@link #output()} feeds {@code totalYield} (§A.8). The values
 * are read live from {@link CultivatedConfig} so a Phase-F config reload retunes tiers without new
 * block instances.
 *
 * <p>The block carries the tier ({@link Provider}); {@link BotanyPotBlockEntity} derives its tier
 * from its block, and returns these values from its public growth/yield modifier accessors.
 */
public enum Tier {
	BASE {
		@Override
		public int speed() {
			return 0;
		}

		@Override
		public int output() {
			return 0;
		}
	},
	ELITE {
		@Override
		public int speed() {
			return CultivatedConfig.eliteSpeed;
		}

		@Override
		public int output() {
			return CultivatedConfig.eliteOutput;
		}
	},
	ULTRA {
		@Override
		public int speed() {
			return CultivatedConfig.ultraSpeed;
		}

		@Override
		public int output() {
			return CultivatedConfig.ultraOutput;
		}
	},
	MEGA {
		@Override
		public int speed() {
			return CultivatedConfig.megaSpeed;
		}

		@Override
		public int output() {
			return CultivatedConfig.megaOutput;
		}
	};

	/** Additive growth-speed contribution into the growth divisor (§A.7); 0 for {@link #BASE}. */
	public abstract int speed();

	/** Additive output contribution into {@code totalYield} (§A.8); 0 for {@link #BASE}. */
	public abstract int output();

	/** The next tier in the upgrade chain (base→elite→ultra→mega), or {@code null} at the top. */
	public @Nullable Tier next() {
		final Tier[] chain = values();
		final int nextOrdinal = this.ordinal() + 1;
		return nextOrdinal < chain.length ? chain[nextOrdinal] : null;
	}

	public boolean isBase() {
		return this == BASE;
	}

	/** Id prefix for tiered block/item names: {@code ""} for {@link #BASE}, else {@code "elite"} etc. */
	public String idPrefix() {
		return this == BASE ? "" : this.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The {@code cultivated:config} crafting-gate property name (§A.9) for this tier + pot type — the
	 * key {@link CultivatedConfig#booleanProperty(String)} resolves. {@link #BASE} maps to the
	 * existing Phase B gates ({@code can_craft_basic_pots}, {@code can_craft_hopper_pots},
	 * {@code can_wax_pots}); tiers use the {@code can_craft_<tier>_<type>_pots} grid.
	 */
	public String craftGate(final PotType type) {
		if (this == BASE) {
			return switch (type) {
				case BASIC -> "can_craft_basic_pots";
				case HOPPER -> "can_craft_hopper_pots";
				case WAXED -> "can_wax_pots";
			};
		}
		final String tier = this.idPrefix();
		return switch (type) {
			case BASIC -> "can_craft_" + tier + "_basic_pots";
			case HOPPER -> "can_craft_" + tier + "_hopper_pots";
			case WAXED -> "can_craft_" + tier + "_waxed_pots";
		};
	}

	/** A block that carries a {@link Tier}; the block entity derives its tier from its block. */
	public interface Provider {
		Tier tier();
	}
}
