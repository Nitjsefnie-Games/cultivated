package dev.nitjsefnie.cultivated.data.drop;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.plugin.TypeDispatchRegistry;
import dev.nitjsefnie.cultivated.recipe.PotContext;
import dev.nitjsefnie.cultivated.util.CodecHelper;
import dev.nitjsefnie.cultivated.util.MathHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase A §A.6 — crop output providers, dispatched by {@code type}. On harvest, each yield roll
 * runs every provider in order.
 *
 * <p>The {@code cultivated:items} provider is fully data-resolvable here. The loot/entity-based
 * providers ({@code loot_table}, {@code block}, {@code block_state}, {@code entity}) require the
 * pot's live loot context (world, origin, harvest tool) to roll — that resolution is Phase B;
 * this class carries their parsed data plus a Phase-B-facing {@link #generateDrops} seam and a
 * best-effort display-item fallback for recipe viewers.
 */
public sealed interface DropProvider {
	String ITEMS_TYPE = Cultivated.id("items").toString();
	String LOOT_TABLE_TYPE = Cultivated.id("loot_table").toString();
	String BLOCK_TYPE = Cultivated.id("block").toString();
	String BLOCK_STATE_TYPE = Cultivated.id("block_state").toString();
	String ENTITY_TYPE = Cultivated.id("entity").toString();

	/**
	 * Emit this provider's drops for one roll into {@code out}. Loot/entity-based providers are
	 * resolved by Phase B's live context; until then they emit their static fallback.
	 */
	void generateDrops(PotContext context, RandomSource random, Consumer<ItemStack> out);

	/** Unique possible outputs for recipe viewers (JEI); best-effort for loot-based providers. */
	List<ItemStack> getDisplayItems();

	String typeId();

	/**
	 * Task F3 (I2) — the mutable {@code type} → sub-codec registry. Built-ins are seeded through the
	 * plugin path ({@link #registerBuiltins}); add-ons add new provider types with {@link #register}.
	 */
	TypeDispatchRegistry<DropProvider> DISPATCH = TypeDispatchRegistry.create(DropProvider::typeId, "Unknown cultivated drop provider type: ");

	Codec<DropProvider> CODEC = DISPATCH.codec();

	Codec<List<DropProvider>> LIST_CODEC = DropProvider.CODEC.listOf();

	/** Register (or override) a drop-provider {@code type} → sub-codec mapping (add-on hook). */
	static void register(final String typeId, final MapCodec<? extends DropProvider> mapCodec) {
		DISPATCH.register(typeId, mapCodec);
	}

	/** Feed the built-in drop-provider types through {@code out} (used by the core plugin, §F.3). */
	static void registerBuiltins(final BiConsumer<String, MapCodec<? extends DropProvider>> out) {
		out.accept(ITEMS_TYPE, Items.MAP_CODEC);
		out.accept(LOOT_TABLE_TYPE, LootTable.MAP_CODEC);
		out.accept(BLOCK_TYPE, BlockDrop.MAP_CODEC);
		out.accept(BLOCK_STATE_TYPE, BlockStateDrop.MAP_CODEC);
		out.accept(ENTITY_TYPE, EntityDrop.MAP_CODEC);
	}

	/**
	 * A tolerant {@code block} reference (C1 / §A.9): a missing block id resolves to air rather than
	 * raising a decode error, so a guarded recipe referencing an absent block still parses (and is
	 * then dropped by its load condition). An air {@link BlockDrop}/{@link BlockStateDrop} is inert —
	 * its {@code ItemStack} is empty, so it emits and displays nothing.
	 */
	Codec<Block> LENIENT_BLOCK = Identifier.CODEC.xmap(
		id -> BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR),
		BuiltInRegistries.BLOCK::getKey
	);

	/**
	 * A tolerant {@code block_state} reference that falls back to inert air ONLY when the referenced
	 * block id is absent from the registry (so a guarded cross-mod drop still parses and is dropped by
	 * its load condition), while a genuinely malformed block-state (bad property / bad format) still
	 * surfaces a decode error. {@link BlockState#CODEC} dispatches on the {@code "Name"} field via
	 * {@code BLOCK.byNameCodec()}; we re-read that field as an {@link Identifier} and treat it as
	 * "absent block" only when it is a well-formed id missing from the block registry — every other
	 * failure (missing/malformed {@code Name}, bad {@code Properties}) propagates the original error.
	 */
	Codec<BlockState> LENIENT_BLOCK_STATE = new Codec<>() {
		@Override
		public <T> DataResult<Pair<BlockState, T>> decode(final DynamicOps<T> ops, final T input) {
			final DataResult<Pair<BlockState, T>> result = BlockState.CODEC.decode(ops, input);
			if (result.result().isPresent()) {
				return result;
			}
			final Optional<Identifier> name = Identifier.CODEC.fieldOf("Name").codec().parse(ops, input).result();
			if (name.isPresent() && BuiltInRegistries.BLOCK.getOptional(name.get()).isEmpty()) {
				return DataResult.success(Pair.of(Blocks.AIR.defaultBlockState(), ops.empty()));
			}
			return result;
		}

		@Override
		public <T> DataResult<T> encode(final BlockState input, final DynamicOps<T> ops, final T prefix) {
			return BlockState.CODEC.encode(input, ops, prefix);
		}
	};

	/** {@code cultivated:items} — each entry drops its stack on an independent chance roll. */
	record Items(List<Entry> items) implements DropProvider {
		static final MapCodec<Items> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(CodecHelper.flexibleList(Entry.CODEC).fieldOf("items").forGetter(Items::items))
				.apply(i, Items::new)
		);

		@Override
		public void generateDrops(final PotContext context, final RandomSource random, final Consumer<ItemStack> out) {
			for (final Entry entry : this.items) {
				if (MathHelper.rollChance(random, entry.chance())) {
					out.accept(entry.result().copy());
				}
			}
		}

		@Override
		public List<ItemStack> getDisplayItems() {
			final List<ItemStack> display = new ArrayList<>(this.items.size());
			for (final Entry entry : this.items) {
				display.add(entry.result().copy());
			}
			return display;
		}

		@Override
		public String typeId() {
			return ITEMS_TYPE;
		}

		public record Entry(ItemStack result, float chance) {
			static final Codec<Entry> CODEC = RecordCodecBuilder.create(
				i -> i.group(
						ItemStack.CODEC.fieldOf("result").forGetter(Entry::result),
						Codec.floatRange(0.0f, 1.0f).optionalFieldOf("chance", 1.0f).forGetter(Entry::chance)
					)
					.apply(i, Entry::new)
			);
		}
	}

	/** {@code cultivated:loot_table} — rolls a loot table with the pot's loot context (Phase B). */
	record LootTable(Identifier tableId) implements DropProvider {
		static final MapCodec<LootTable> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(Identifier.CODEC.fieldOf("table_id").forGetter(LootTable::tableId))
				.apply(i, LootTable::new)
		);

		@Override
		public void generateDrops(final PotContext context, final RandomSource random, final Consumer<ItemStack> out) {
			for (final ItemStack stack : context.rollLootTable(this.tableId, random)) {
				out.accept(stack);
			}
		}

		@Override
		public List<ItemStack> getDisplayItems() {
			return List.of();
		}

		@Override
		public String typeId() {
			return LOOT_TABLE_TYPE;
		}
	}

	/** {@code cultivated:block} — uses the block's own loot table (Phase B). */
	record BlockDrop(Block block) implements DropProvider {
		static final MapCodec<BlockDrop> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(LENIENT_BLOCK.fieldOf("block").forGetter(BlockDrop::block))
				.apply(i, BlockDrop::new)
		);

		@Override
		public void generateDrops(final PotContext context, final RandomSource random, final Consumer<ItemStack> out) {
			// Roll the block's own loot table via the pot's live context; fall back to the plain block
			// item when the table is missing/empty or there is no live world to roll against (§A.6).
			final List<ItemStack> rolled = context.rollBlockDrops(this.block.defaultBlockState(), random);
			if (!rolled.isEmpty()) {
				rolled.forEach(out);
				return;
			}
			final ItemStack fallback = new ItemStack(this.block);
			if (!fallback.isEmpty()) {
				out.accept(fallback);
			}
		}

		@Override
		public List<ItemStack> getDisplayItems() {
			final ItemStack fallback = new ItemStack(this.block);
			return fallback.isEmpty() ? List.of() : List.of(fallback);
		}

		@Override
		public String typeId() {
			return BLOCK_TYPE;
		}
	}

	/** {@code cultivated:block_state} — like {@code block} but for a specific state (Phase B). */
	record BlockStateDrop(BlockState blockState) implements DropProvider {
		static final MapCodec<BlockStateDrop> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(LENIENT_BLOCK_STATE.fieldOf("block_state").forGetter(BlockStateDrop::blockState))
				.apply(i, BlockStateDrop::new)
		);

		@Override
		public void generateDrops(final PotContext context, final RandomSource random, final Consumer<ItemStack> out) {
			// Roll against the specific state (so age/berry loot conditions pass); fall back as above.
			final List<ItemStack> rolled = context.rollBlockDrops(this.blockState, random);
			if (!rolled.isEmpty()) {
				rolled.forEach(out);
				return;
			}
			final ItemStack fallback = new ItemStack(this.blockState.getBlock());
			if (!fallback.isEmpty()) {
				out.accept(fallback);
			}
		}

		@Override
		public List<ItemStack> getDisplayItems() {
			final ItemStack fallback = new ItemStack(this.blockState.getBlock());
			return fallback.isEmpty() ? List.of() : List.of(fallback);
		}

		@Override
		public String typeId() {
			return BLOCK_STATE_TYPE;
		}
	}

	/** {@code cultivated:entity} — rolls a living entity's death loot table (Phase B). */
	record EntityDrop(CompoundTag entity, Optional<Identifier> damageSource) implements DropProvider {
		static final MapCodec<EntityDrop> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					CompoundTag.CODEC.fieldOf("entity").forGetter(EntityDrop::entity),
					Identifier.CODEC.optionalFieldOf("damage_source").forGetter(EntityDrop::damageSource)
				)
				.apply(i, EntityDrop::new)
		);

		@Override
		public void generateDrops(final PotContext context, final RandomSource random, final Consumer<ItemStack> out) {
			for (final ItemStack stack : context.rollEntityDrops(this.entity, this.damageSource, random)) {
				out.accept(stack);
			}
		}

		@Override
		public List<ItemStack> getDisplayItems() {
			return List.of();
		}

		@Override
		public String typeId() {
			return ENTITY_TYPE;
		}
	}
}
