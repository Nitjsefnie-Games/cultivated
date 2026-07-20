package dev.nitjsefnie.cultivated.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nitjsefnie.cultivated.block.PotMechanics.EmptyHandBranch;
import dev.nitjsefnie.cultivated.block.PotMechanics.HeldConsumption;
import dev.nitjsefnie.cultivated.block.PotMechanics.HeldItemBranch;
import org.junit.jupiter.api.Test;

/**
 * Pure right-click interaction-order decisions (§B.2) and the pot-interaction held-item consumption
 * rule (§B.6). No game runtime required.
 */
class PotInteractionOrderTest {

	// ---- empty-hand branch (§B.2 step 1 / step 4): harvest a mature Basic pot, else open menu ----

	@Test
	void emptyHand_waxedIgnoresEverything() {
		assertEquals(EmptyHandBranch.IGNORE, PotMechanics.emptyHandBranch(true, false, false));
		// waxed wins even if the (irrelevant) basic/harvestable flags are set
		assertEquals(EmptyHandBranch.IGNORE, PotMechanics.emptyHandBranch(true, true, true));
	}

	@Test
	void emptyHand_basicMatureHarvests() {
		assertEquals(EmptyHandBranch.HARVEST, PotMechanics.emptyHandBranch(false, true, true));
	}

	@Test
	void emptyHand_basicNotMatureOpensMenu() {
		assertEquals(EmptyHandBranch.OPEN_MENU, PotMechanics.emptyHandBranch(false, true, false));
	}

	@Test
	void emptyHand_hopperNeverHarvestsOnUse() {
		// Hopper (basic=false): even a mature crop opens the menu (auto-harvest is the tick loop's job).
		assertEquals(EmptyHandBranch.OPEN_MENU, PotMechanics.emptyHandBranch(false, false, true));
		assertEquals(EmptyHandBranch.OPEN_MENU, PotMechanics.emptyHandBranch(false, false, false));
	}

	// ---- held-item branch (§B.2 steps 2–3): fertilizer before pot-interaction, else defer ----

	@Test
	void heldItem_waxedIgnoresEverything() {
		assertEquals(HeldItemBranch.IGNORE, PotMechanics.heldItemBranch(true, true, true));
		assertEquals(HeldItemBranch.IGNORE, PotMechanics.heldItemBranch(true, false, false));
	}

	@Test
	void heldItem_fertilizerBeatsInteraction() {
		// When both match, fertilizer (step 2) is chosen ahead of pot-interaction (step 3).
		assertEquals(HeldItemBranch.FERTILIZE, PotMechanics.heldItemBranch(false, true, true));
		assertEquals(HeldItemBranch.FERTILIZE, PotMechanics.heldItemBranch(false, true, false));
	}

	@Test
	void heldItem_interactionWhenOnlyInteractionMatches() {
		assertEquals(HeldItemBranch.INTERACT, PotMechanics.heldItemBranch(false, false, true));
	}

	@Test
	void heldItem_deferWhenNothingMatches() {
		assertEquals(HeldItemBranch.DEFER, PotMechanics.heldItemBranch(false, false, false));
	}

	// ---- pot-interaction consumption (§B.6): damage wins over consume ----

	@Test
	void consumption_damageWinsOverConsume() {
		// damage_held true → damage, regardless of consume_held
		assertEquals(HeldConsumption.DAMAGE, PotMechanics.heldConsumption(true, false));
		assertEquals(HeldConsumption.DAMAGE, PotMechanics.heldConsumption(true, true));
	}

	@Test
	void consumption_consumeOnlyWhenNotDamaging() {
		assertEquals(HeldConsumption.CONSUME, PotMechanics.heldConsumption(false, true));
	}

	@Test
	void consumption_noneWhenNeither() {
		assertEquals(HeldConsumption.NONE, PotMechanics.heldConsumption(false, false));
	}
}
