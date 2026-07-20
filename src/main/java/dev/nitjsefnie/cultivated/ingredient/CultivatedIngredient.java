package dev.nitjsefnie.cultivated.ingredient;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.plugin.TypeDispatchRegistry;
import dev.nitjsefnie.cultivated.util.CodecHelper;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * Phase A §A.9/§A.12 — an item predicate used by soil/crop/fertilizer/interaction recipes. Wraps
 * a vanilla {@link Ingredient} and adds the two custom types the datapack uses:
 * <ul>
 *   <li>{@code cultivated:either} — matches any of several sub-ingredients;</li>
 *   <li>{@code cultivated:block_tag} — matches an item whose block form is in a block tag.</li>
 * </ul>
 *
 * <p>Vanilla {@link Ingredient} is {@code final}, so this is a parallel predicate abstraction
 * rather than a subclass. The codec accepts the plain vanilla ingredient form (item id, {@code
 * #tag}, or list) as well as the two typed objects above.
 */
public sealed interface CultivatedIngredient extends Predicate<ItemStack> {
	String EITHER_TYPE = Cultivated.id("either").toString();
	String BLOCK_TAG_TYPE = Cultivated.id("block_tag").toString();
	String SPAWN_EGG_TYPE = Cultivated.id("spawn_egg").toString();

	@Override
	boolean test(ItemStack stack);

	/**
	 * Task F3 (I2) — the mutable {@code type} → sub-codec registry for the typed custom ingredients
	 * (either/block_tag). Built-ins are seeded through the plugin path ({@link #registerBuiltins});
	 * add-ons add new typed ingredients with {@link #register}. The plain vanilla ingredient form is
	 * handled separately by {@link #CODEC}'s {@code either(...)} wrapper below, not via this registry.
	 */
	TypeDispatchRegistry<CultivatedIngredient> DISPATCH = TypeDispatchRegistry.create(CultivatedIngredient::typeId, "Unknown cultivated ingredient type: ");

	/** The typed codecs (either/block_tag), dispatched by the {@code type} field. */
	Codec<CultivatedIngredient> CUSTOM_CODEC = DISPATCH.codec();

	/** Register (or override) a typed-ingredient {@code type} → sub-codec mapping (add-on hook). */
	static void register(final String typeId, final MapCodec<? extends CultivatedIngredient> mapCodec) {
		DISPATCH.register(typeId, mapCodec);
	}

	/** Feed the built-in typed-ingredient types through {@code out} (used by the core plugin, §F.3). */
	static void registerBuiltins(final BiConsumer<String, MapCodec<? extends CultivatedIngredient>> out) {
		out.accept(EITHER_TYPE, Either_.MAP_CODEC);
		out.accept(BLOCK_TAG_TYPE, BlockTag.MAP_CODEC);
		out.accept(SPAWN_EGG_TYPE, SpawnEgg.MAP_CODEC);
	}

	Codec<CultivatedIngredient> CODEC = Codec.either(CUSTOM_CODEC, Ingredient.CODEC)
		.xmap(
			either -> either.map(custom -> custom, Vanilla::new),
			ingredient -> ingredient instanceof Vanilla v ? Either.right(v.ingredient()) : Either.left(ingredient)
		);

	/** Type id used to encode a custom ingredient; null for the plain vanilla form. */
	default String typeId() {
		return null;
	}

	/** Wraps a vanilla ingredient (item id / #tag / list). */
	record Vanilla(Ingredient ingredient) implements CultivatedIngredient {
		@Override
		public boolean test(final ItemStack stack) {
			return this.ingredient.test(stack);
		}
	}

	/** {@code cultivated:either} — matches any of several sub-ingredients. */
	record Either_(List<CultivatedIngredient> ingredients) implements CultivatedIngredient {
		static final MapCodec<Either_> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(CodecHelper.flexibleList(CODEC).fieldOf("ingredients").forGetter(Either_::ingredients))
				.apply(i, Either_::new)
		);

		@Override
		public boolean test(final ItemStack stack) {
			for (final CultivatedIngredient ingredient : this.ingredients) {
				if (ingredient.test(stack)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public String typeId() {
			return EITHER_TYPE;
		}
	}

	/** {@code cultivated:block_tag} — matches an item whose block form is in a block tag. */
	record BlockTag(TagKey<Block> tag) implements CultivatedIngredient {
		static final MapCodec<BlockTag> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(TagKey.codec(Registries.BLOCK).fieldOf("tag").forGetter(BlockTag::tag))
				.apply(i, BlockTag::new)
		);

		@Override
		public boolean test(final ItemStack stack) {
			final Block block = Block.byItem(stack.getItem());
			return block != net.minecraft.world.level.block.Blocks.AIR && block.builtInRegistryHolder().is(this.tag);
		}

		@Override
		public String typeId() {
			return BLOCK_TAG_TYPE;
		}
	}

	/**
	 * {@code cultivated:spawn_egg} — matches any spawn egg item ({@code stack.getItem() instanceof
	 * SpawnEggItem}). Because {@link dev.nitjsefnie.cultivated.cache.RecipeLookupCache} indexes a recipe
	 * under every registered item whose default instance this accepts, a single recipe carrying this
	 * ingredient is indexed under EVERY spawn egg — vanilla and modded alike — so the growable-mob
	 * mechanism is generic without enumerating any entity id.
	 */
	record SpawnEgg() implements CultivatedIngredient {
		public static final SpawnEgg INSTANCE = new SpawnEgg();
		static final MapCodec<SpawnEgg> MAP_CODEC = MapCodec.unit(INSTANCE);

		@Override
		public boolean test(final ItemStack stack) {
			return stack.getItem() instanceof SpawnEggItem;
		}

		@Override
		public String typeId() {
			return SPAWN_EGG_TYPE;
		}
	}
}
