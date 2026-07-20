package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure material enumeration and pot-block id mapping (§B.1). No game runtime required. */
class PotMaterialsTest {

	// 1 (terracotta) + 16 dye colours * 3 (terracotta / glazed / concrete) + 12 brick families.
	private static final int EXPECTED_MATERIALS = 1 + 16 * 3 + 12;

	@Test
	void materialCountMatchesSpec() {
		assertEquals(61, EXPECTED_MATERIALS);
		assertEquals(EXPECTED_MATERIALS, PotMaterials.ALL.size());
	}

	@Test
	void materialsAreUnique() {
		final Set<String> seen = new HashSet<>(PotMaterials.ALL);
		assertEquals(PotMaterials.ALL.size(), seen.size(), "material names must be unique");
	}

	@Test
	void includesRepresentativeMaterials() {
		assertTrue(PotMaterials.ALL.contains("terracotta"));
		assertTrue(PotMaterials.ALL.contains("white_terracotta"));
		assertTrue(PotMaterials.ALL.contains("black_glazed_terracotta"));
		assertTrue(PotMaterials.ALL.contains("lime_concrete"));
		assertTrue(PotMaterials.ALL.contains("bricks"));
		assertTrue(PotMaterials.ALL.contains("quartz_bricks"));
		assertTrue(PotMaterials.ALL.contains("tuff_bricks"));
	}

	@Test
	void potBlockNameFollowsTypePattern() {
		assertEquals("terracotta_botany_pot", PotMaterials.potBlockName("terracotta", PotType.BASIC));
		assertEquals("terracotta_hopper_botany_pot", PotMaterials.potBlockName("terracotta", PotType.HOPPER));
		assertEquals("terracotta_waxed_botany_pot", PotMaterials.potBlockName("terracotta", PotType.WAXED));
		assertEquals("white_concrete_hopper_botany_pot", PotMaterials.potBlockName("white_concrete", PotType.HOPPER));
	}

	@Test
	void everyVariantIdIsUnique() {
		// 61 materials * 3 pot types = 183 blocks, all with distinct registry ids (no collisions).
		final Set<String> ids = new HashSet<>();
		for (final String material : PotMaterials.ALL) {
			for (final PotType type : PotType.values()) {
				assertTrue(ids.add(PotMaterials.potBlockName(material, type)),
					"duplicate id: " + PotMaterials.potBlockName(material, type));
			}
		}
		assertEquals(61 * 3, ids.size());
	}
}
