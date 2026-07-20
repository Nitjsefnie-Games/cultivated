package dev.nitjsefnie.cultivated.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pure decisions behind {@code check_crops}'s per-tool harvest audit (§F.2): the tool-variety
 * composition and the "produced nothing with any tool" verdict. Needs the vanilla registry
 * bootstrapped so the {@link Items} constants resolve. The verdict itself is a per-list emptiness
 * check (the harvest collector never records empty stacks), so a non-empty list of drops is stood in
 * for with a size-1 list here — no bound {@link ItemStack} instance is required.
 */
class HarvestAuditTest {
	@BeforeAll
	static void bootstrap() {
		CultivatedTestBootstrap.bootstrap();
	}

	@Test
	void auditToolItemsCoverTheExpectedSixTools() {
		assertEquals(
			List.of(Items.SHEARS, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE,
				Items.DIAMOND_SWORD, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE),
			HarvestAudit.AUDIT_TOOL_ITEMS,
			"shears, pickaxe, axe, sword, shovel, hoe — empty hand is added at the call site");
	}

	@Test
	void producedNothing_whenEveryToolYieldsAnEmptyList() {
		assertTrue(HarvestAudit.producedNothing(List.of(List.of(), List.of(), List.of())));
		assertTrue(HarvestAudit.producedNothing(List.of()), "no tools tried at all");
	}

	@Test
	void notProducedNothing_whenAnySingleToolYieldsARealDrop() {
		// A non-empty per-tool list means that tool dropped something; its element is never inspected.
		final List<ItemStack> withDrop = Arrays.asList((ItemStack) null);
		assertFalse(HarvestAudit.producedNothing(List.of(List.of(), List.of(), withDrop)));
	}
}
