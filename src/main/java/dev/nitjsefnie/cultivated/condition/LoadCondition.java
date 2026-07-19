package dev.nitjsefnie.cultivated.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Phase A §A.9 / Phase G #14 — a data-gating condition attached to a recipe (or other data JSON)
 * under the {@code cultivated:load_conditions} key. If any condition fails, the file is treated
 * as absent. In this data engine the conditions are stored on the parsed recipe and evaluated at
 * cache-build time (when the registries are populated); a recipe whose conditions fail is
 * excluded from the active lookup cache.
 *
 * <p>Built-in types: {@code block_exists}, {@code item_exists}, and the mod's own {@code config}.
 */
public sealed interface LoadCondition {
	String BLOCK_EXISTS_TYPE = Cultivated.id("block_exists").toString();
	String ITEM_EXISTS_TYPE = Cultivated.id("item_exists").toString();
	String CONFIG_TYPE = Cultivated.id("config").toString();

	boolean test();

	String typeId();

	Codec<LoadCondition> CODEC = Codec.STRING.dispatch(
		"type",
		LoadCondition::typeId,
		type -> {
			if (BLOCK_EXISTS_TYPE.equals(type)) {
				return BlockExists.MAP_CODEC;
			} else if (ITEM_EXISTS_TYPE.equals(type)) {
				return ItemExists.MAP_CODEC;
			} else if (CONFIG_TYPE.equals(type)) {
				return Config.MAP_CODEC;
			}
			throw new IllegalArgumentException("Unknown cultivated load condition type: " + type);
		}
	);

	Codec<List<LoadCondition>> LIST_CODEC = CODEC.listOf();

	/** True when every condition passes (an empty/absent list always passes). */
	static boolean testAll(final List<LoadCondition> conditions) {
		for (final LoadCondition condition : conditions) {
			if (!condition.test()) {
				return false;
			}
		}
		return true;
	}

	/** {@code cultivated:block_exists} — passes only if all named blocks are registered. */
	record BlockExists(List<Identifier> values) implements LoadCondition {
		static final MapCodec<BlockExists> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(Identifier.CODEC.listOf().fieldOf("values").forGetter(BlockExists::values))
				.apply(i, BlockExists::new)
		);

		@Override
		public boolean test() {
			return this.values.stream().allMatch(BuiltInRegistries.BLOCK::containsKey);
		}

		@Override
		public String typeId() {
			return BLOCK_EXISTS_TYPE;
		}
	}

	/** {@code cultivated:item_exists} — passes only if all named items are registered. */
	record ItemExists(List<Identifier> values) implements LoadCondition {
		static final MapCodec<ItemExists> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(Identifier.CODEC.listOf().fieldOf("values").forGetter(ItemExists::values))
				.apply(i, ItemExists::new)
		);

		@Override
		public boolean test() {
			return this.values.stream().allMatch(BuiltInRegistries.ITEM::containsKey);
		}

		@Override
		public String typeId() {
			return ITEM_EXISTS_TYPE;
		}
	}

	/** {@code cultivated:config} — passes only if a named boolean config property is true. */
	record Config(String property) implements LoadCondition {
		static final MapCodec<Config> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(Codec.STRING.fieldOf("property").forGetter(Config::property))
				.apply(i, Config::new)
		);

		@Override
		public boolean test() {
			return CultivatedConfig.booleanProperty(this.property);
		}

		@Override
		public String typeId() {
			return CONFIG_TYPE;
		}
	}
}
