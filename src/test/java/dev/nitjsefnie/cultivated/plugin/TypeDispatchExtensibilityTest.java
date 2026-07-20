package dev.nitjsefnie.cultivated.plugin;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.nitjsefnie.cultivated.CultivatedTestBootstrap;
import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.drop.DropProvider;
import dev.nitjsefnie.cultivated.data.growth.GrowthAmount;
import dev.nitjsefnie.cultivated.ingredient.CultivatedIngredient;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Task F3 (I2) — proves the {@code type}-dispatch is a real, live registry, not just a refactored
 * {@code if/else}: for each of the five value kinds it registers a brand-new {@code test:*} type
 * (through the same {@code register} hook add-ons use) and asserts a JSON referencing that new type
 * now parses through the public {@code CODEC}. Because the built-in registrations flow through the
 * plugin path (seeded by {@link CultivatedTestBootstrap}), the built-in types keep parsing too — that
 * is the {@code ShippedRecipesParseTest}'s job; here we only add and exercise new ones.
 *
 * <p>The sealed value interfaces cannot gain new subtypes from outside, so each dummy sub-codec is a
 * {@link MapCodec#unit} that decodes an existing variant under the new type id — enough to prove the
 * dispatch resolves a freshly-registered type.
 */
class TypeDispatchExtensibilityTest {
	@BeforeAll
	static void boot() {
		CultivatedTestBootstrap.bootstrap();
	}

	private static JsonElement json(final String raw) {
		return JsonParser.parseString(raw);
	}

	private static <T> T parse(final Codec<T> codec, final String raw) {
		final DataResult<T> result = codec.parse(JsonOps.INSTANCE, json(raw));
		assertTrue(result.error().isEmpty(),
			() -> "expected the freshly-registered type to parse, but got: " + result.error().map(Object::toString).orElse(""));
		return result.result().orElseThrow();
	}

	@Test
	void newDisplayTypeParses() {
		Display.register("test:dummy_display", MapCodec.unit(new Display.Transitional(List.of())));
		final Display parsed = parse(Display.CODEC, "{\"type\":\"test:dummy_display\"}");
		assertInstanceOf(Display.Transitional.class, parsed);
	}

	@Test
	void newDropProviderTypeParses() {
		DropProvider.register("test:dummy_drop", MapCodec.unit(new DropProvider.Items(List.of())));
		final DropProvider parsed = parse(DropProvider.CODEC, "{\"type\":\"test:dummy_drop\"}");
		assertInstanceOf(DropProvider.Items.class, parsed);
	}

	@Test
	void newGrowthAmountTypeParses() {
		GrowthAmount.register("test:dummy_growth", MapCodec.unit(new GrowthAmount.Constant(0)));
		final GrowthAmount parsed = parse(GrowthAmount.CODEC, "{\"type\":\"test:dummy_growth\"}");
		assertInstanceOf(GrowthAmount.Constant.class, parsed);
	}

	@Test
	void newIngredientTypeParses() {
		CultivatedIngredient.register("test:dummy_ingredient", MapCodec.unit(new CultivatedIngredient.Either_(List.of())));
		final CultivatedIngredient parsed = parse(CultivatedIngredient.CODEC, "{\"type\":\"test:dummy_ingredient\"}");
		assertInstanceOf(CultivatedIngredient.Either_.class, parsed);
	}

	@Test
	void newLoadConditionTypeParses() {
		LoadCondition.register("test:dummy_condition", MapCodec.unit(new LoadCondition.Config("dummy")));
		final LoadCondition parsed = parse(LoadCondition.CODEC, "{\"type\":\"test:dummy_condition\"}");
		assertInstanceOf(LoadCondition.Config.class, parsed);
	}

	@Test
	void builtinTypesStillResolveAlongsideNewOnes() {
		// A built-in type registered through the plugin path still parses (guards against the refactor
		// dropping a seeded type); parsed via the same public CODEC the shipped data uses.
		final GrowthAmount constant = parse(GrowthAmount.CODEC, "{\"type\":\"" + GrowthAmount.CONSTANT_TYPE + "\",\"amount\":5}");
		assertInstanceOf(GrowthAmount.Constant.class, constant);
	}
}
