package dev.nitjsefnie.cultivated.data.drop;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.recipe.PotContext;
import dev.nitjsefnie.cultivated.util.CodecHelper;
import dev.nitjsefnie.cultivated.util.MathHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

	Codec<DropProvider> CODEC = Codec.STRING.dispatch(
		"type",
		DropProvider::typeId,
		type -> {
			if (ITEMS_TYPE.equals(type)) {
				return Items.MAP_CODEC;
			} else if (LOOT_TABLE_TYPE.equals(type)) {
				return LootTable.MAP_CODEC;
			} else if (BLOCK_TYPE.equals(type)) {
				return BlockDrop.MAP_CODEC;
			} else if (BLOCK_STATE_TYPE.equals(type)) {
				return BlockStateDrop.MAP_CODEC;
			} else if (ENTITY_TYPE.equals(type)) {
				return EntityDrop.MAP_CODEC;
			}
			throw new IllegalArgumentException("Unknown cultivated drop provider type: " + type);
		}
	);

	Codec<List<DropProvider>> LIST_CODEC = DropProvider.CODEC.listOf();

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

	/** A tolerant {@code block_state} reference: any un-decodable state (e.g. absent block) → air. */
	Codec<BlockState> LENIENT_BLOCK_STATE = new Codec<>() {
		@Override
		public <T> DataResult<Pair<BlockState, T>> decode(final DynamicOps<T> ops, final T input) {
			final DataResult<Pair<BlockState, T>> result = BlockState.CODEC.decode(ops, input);
			if (result.result().isPresent()) {
				return result;
			}
			return DataResult.success(Pair.of(Blocks.AIR.defaultBlockState(), ops.empty()));
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
			// Requires the pot's live LootParams (world, origin, tool) — resolved in Phase B.
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
			// Phase B rolls the block loot table; fall back to the block item so a bare data engine
			// still yields something sensible.
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
			// Requires deriving a living entity and rolling its death loot table — Phase B.
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
