package dev.nitjsefnie.cultivated.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import dev.nitjsefnie.cultivated.plugin.TypeDispatchRegistry;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
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

	/** The mod's own load-conditions field key ({@code cultivated:load_conditions}). */
	String CONDITIONS_KEY = Cultivated.id("load_conditions").toString();

	/**
	 * The legacy Bookshelf field key ({@code bookshelf:load_conditions}), accepted for drop-in
	 * compatibility with imported legacy datapacks (§A.9).
	 */
	String LEGACY_CONDITIONS_KEY = "bookshelf:load_conditions";

	boolean test();

	String typeId();

	/**
	 * Task F3 (I2) — the mutable {@code type} → sub-codec registry. Built-ins are seeded through the
	 * plugin path ({@link #registerBuiltins}); add-ons add new condition types with {@link #register}.
	 */
	TypeDispatchRegistry<LoadCondition> DISPATCH = TypeDispatchRegistry.create(LoadCondition::typeId, "Unknown cultivated load condition type: ");

	Codec<LoadCondition> CODEC = DISPATCH.codec();

	Codec<List<LoadCondition>> LIST_CODEC = CODEC.listOf();

	/** Register (or override) a load-condition {@code type} → sub-codec mapping (add-on hook). */
	static void register(final String typeId, final MapCodec<? extends LoadCondition> mapCodec) {
		DISPATCH.register(typeId, mapCodec);
	}

	/** Feed the built-in load-condition types through {@code out} (used by the core plugin, §F.3). */
	static void registerBuiltins(final BiConsumer<String, MapCodec<? extends LoadCondition>> out) {
		out.accept(BLOCK_EXISTS_TYPE, BlockExists.MAP_CODEC);
		out.accept(ITEM_EXISTS_TYPE, ItemExists.MAP_CODEC);
		out.accept(CONFIG_TYPE, Config.MAP_CODEC);
	}

	/**
	 * Map-codec for the optional load-conditions field on a data file (§A.9). Decodes from either
	 * {@link #CONDITIONS_KEY} ({@code cultivated:load_conditions}) or the legacy
	 * {@link #LEGACY_CONDITIONS_KEY} ({@code bookshelf:load_conditions}); when both are present the
	 * mod's own key wins. An absent field decodes to an empty (always-passing) list, and encoding
	 * always writes the mod's own key (nothing at all when the list is empty).
	 */
	MapCodec<List<LoadCondition>> CONDITIONS_CODEC = new MapCodec<>() {
		@Override
		public <T> Stream<T> keys(final DynamicOps<T> ops) {
			return Stream.of(ops.createString(CONDITIONS_KEY), ops.createString(LEGACY_CONDITIONS_KEY));
		}

		@Override
		public <T> DataResult<List<LoadCondition>> decode(final DynamicOps<T> ops, final MapLike<T> input) {
			T value = input.get(CONDITIONS_KEY);
			if (value == null) {
				value = input.get(LEGACY_CONDITIONS_KEY);
			}
			if (value == null) {
				return DataResult.success(List.of());
			}
			return LIST_CODEC.parse(ops, value);
		}

		@Override
		public <T> RecordBuilder<T> encode(final List<LoadCondition> input, final DynamicOps<T> ops, final RecordBuilder<T> prefix) {
			if (input.isEmpty()) {
				return prefix;
			}
			return prefix.add(CONDITIONS_KEY, LIST_CODEC.encodeStart(ops, input));
		}
	};

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
