package dev.nitjsefnie.cultivated.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * A recipe-embedded {@link ItemStack} whose decode is DEFERRED past recipe-parse time.
 *
 * <p>Recipes load during datapack reload, <em>before</em> item data components are bound to their
 * registry holders. {@link ItemStack#CODEC} resolves its {@code id} through
 * {@code Item.CODEC_WITH_BOUND_COMPONENTS}, which rejects an item whose components are not yet bound
 * with {@code "Item <id> does not have components yet"}; and even constructing an {@code ItemStack}
 * eagerly reads the item's (unbound) prototype components ({@code Holder#components()} throws
 * "Components not bound yet"). Either way a recipe carrying an inline stack fails to load in-game.
 *
 * <p>This wrapper captures the raw serialized form at parse time and materialises the real stack
 * only on first {@link #get()} — by harvest / interaction-apply / display time, item components are
 * bound. Because the raw snapshot keeps the full {@code components} patch, component-carrying stacks
 * are preserved (only the resolution <em>timing</em> changes): a {@code new_soil} may still carry a
 * {@code cultivated:soil} override component. This mirrors how
 * {@link dev.nitjsefnie.cultivated.config.CultivatedConfig#defaultHarvestStack()} defers its
 * fallback-stack decode for the same reason.
 */
public final class LazyItemStack {
	/**
	 * A pass-through codec: on decode it snapshots the raw stack form (never touching item
	 * components); on encode it re-emits that snapshot converted to the target ops. Used the same way
	 * on the datapack (JSON) and network (NBT) paths — both of which round-trip the recipe through the
	 * serializer — with materialisation always deferred to {@link #get()}.
	 */
	public static final Codec<LazyItemStack> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<LazyItemStack, T>> decode(final DynamicOps<T> ops, final T input) {
			return DataResult.success(Pair.of(new LazyItemStack(new Dynamic<>(ops, input)), ops.empty()));
		}

		@Override
		public <T> DataResult<T> encode(final LazyItemStack input, final DynamicOps<T> ops, final T prefix) {
			return DataResult.success(input.raw.convert(ops).getValue());
		}
	};

	private final Dynamic<?> raw;
	private ItemStack resolved;

	private LazyItemStack(final Dynamic<?> raw) {
		this.raw = raw;
	}

	/**
	 * Wrap an already-materialised {@link ItemStack} as a {@link LazyItemStack} for runtime-constructed
	 * drops (e.g. the spawn-egg harvest drop). The stack is serialised through {@link ItemStack#CODEC}
	 * into the same raw snapshot form the {@link #CODEC decode path} captures — so it must be called when
	 * item components are already bound (harvest time), not during recipe parse. The full {@code
	 * components} patch is preserved, and {@link #get()} materialises it back on first use.
	 */
	public static LazyItemStack of(final ItemStack stack) {
		final Tag encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).getOrThrow();
		return new LazyItemStack(new Dynamic<>(NbtOps.INSTANCE, encoded));
	}

	/**
	 * The materialised stack, decoded from the captured raw form on first use and cached. Returns
	 * {@link ItemStack#EMPTY} when the raw form does not (yet) decode; a failed decode is not cached,
	 * so a stack referencing an item whose components are not bound yet resolves once binding happens.
	 */
	public ItemStack get() {
		if (this.resolved != null) {
			return this.resolved;
		}
		final ItemStack decoded = this.raw.read(ItemStack.CODEC).result().orElse(null);
		if (decoded != null) {
			this.resolved = decoded;
			return decoded;
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean equals(final Object other) {
		return other instanceof LazyItemStack lazy && this.raw.equals(lazy.raw);
	}

	@Override
	public int hashCode() {
		return this.raw.hashCode();
	}

	@Override
	public String toString() {
		return "LazyItemStack" + this.raw.getValue();
	}
}
