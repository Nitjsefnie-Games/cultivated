package dev.nitjsefnie.cultivated.block;

/**
 * Phase B — the three botany-pot variants. Basic pots grow and hold at mature until manually
 * harvested; hopper pots additionally auto-harvest into their storage slots and export downward;
 * waxed pots are decorative (they force growth to max and never tick).
 */
public enum PotType {
	BASIC,
	HOPPER,
	WAXED;

	public boolean isHopper() {
		return this == HOPPER;
	}

	public boolean isWaxed() {
		return this == WAXED;
	}

	/** A block that carries a {@link PotType}; the block entity derives its type from its block. */
	public interface Provider {
		PotType potType();
	}
}
