package dev.nitjsefnie.cultivated.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * I1 + §A.9 — the dual-key load-conditions field ({@code cultivated:load_conditions} /
 * legacy {@code bookshelf:load_conditions}) and condition evaluation (decided keep vs drop).
 */
class LoadConditionTest {
	private static final String ABSENT = "zzznonexistent:block";

	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
	}

	private static List<LoadCondition> decode(final String json) {
		return LoadCondition.CONDITIONS_CODEC.codec()
			.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
			.getOrThrow();
	}

	@Test
	void modernKey_failingBlockExists_isDropped() {
		final List<LoadCondition> conditions = decode(
			"{\"cultivated:load_conditions\":[{\"type\":\"cultivated:block_exists\",\"values\":[\"" + ABSENT + "\"]}]}");
		assertEquals(1, conditions.size());
		assertFalse(LoadCondition.testAll(conditions), "an absent block_exists must fail so the file is dropped");
	}

	@Test
	void legacyBookshelfKey_isHonored() {
		final List<LoadCondition> conditions = decode(
			"{\"bookshelf:load_conditions\":[{\"type\":\"cultivated:block_exists\",\"values\":[\"" + ABSENT + "\"]}]}");
		assertEquals(1, conditions.size(), "legacy bookshelf:load_conditions must be read");
		assertFalse(LoadCondition.testAll(conditions));
	}

	@Test
	void presentBlockExists_isKept() {
		final List<LoadCondition> conditions = decode(
			"{\"cultivated:load_conditions\":[{\"type\":\"cultivated:block_exists\",\"values\":[\"minecraft:stone\"]}]}");
		assertEquals(1, conditions.size());
		assertTrue(LoadCondition.testAll(conditions), "a present block keeps the file");
	}

	@Test
	void absentField_isKept() {
		final List<LoadCondition> conditions = decode("{}");
		assertTrue(conditions.isEmpty());
		assertTrue(LoadCondition.testAll(conditions), "no conditions -> always keep");
	}

	@Test
	void modernKeyWinsWhenBothPresent() {
		final List<LoadCondition> conditions = decode(
			"{\"cultivated:load_conditions\":[{\"type\":\"cultivated:block_exists\",\"values\":[\"minecraft:stone\"]}],"
			+ "\"bookshelf:load_conditions\":[{\"type\":\"cultivated:block_exists\",\"values\":[\"" + ABSENT + "\"]}]}");
		assertTrue(LoadCondition.testAll(conditions),
			"cultivated:load_conditions must take precedence -> stone present -> kept");
	}
}
