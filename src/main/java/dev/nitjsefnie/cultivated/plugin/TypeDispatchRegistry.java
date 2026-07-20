package dev.nitjsefnie.cultivated.plugin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Task F3 (I2) — a mutable {@code type}-string → sub-{@link MapCodec} registry backing a dispatched
 * value kind ({@link dev.nitjsefnie.cultivated.data.display.Display}, {@code DropProvider}, etc.).
 *
 * <p>Replaces the previous hardcoded {@code if/else} dispatch: the built-in types are seeded into the
 * registry (through the plugin path, see {@link CultivatedCorePlugin}) and add-ons register new types
 * with {@link #register}, all without editing core. The {@link #codec()} still dispatches on the
 * {@code type} string exactly as before — it resolves the sub-codec from this map at parse time — so
 * the serialized JSON is unchanged.
 *
 * @param <T> the dispatched value kind
 */
public final class TypeDispatchRegistry<T> {
	private final Map<String, MapCodec<? extends T>> byType = new HashMap<>();
	private final Codec<T> codec;

	private TypeDispatchRegistry(final Function<T, String> typeGetter, final String unknownTypeMessage) {
		// The lambda reads byType at parse time (not codec-construction time), so a type registered
		// after this codec is built still resolves — that is what makes the dispatch extensible.
		this.codec = Codec.STRING.dispatch(
			"type",
			typeGetter,
			type -> {
				final MapCodec<? extends T> mapCodec = this.byType.get(type);
				if (mapCodec == null) {
					throw new IllegalArgumentException(unknownTypeMessage + type);
				}
				return mapCodec;
			}
		);
	}

	/**
	 * @param typeGetter maps a value to its {@code type} string (for encoding)
	 * @param unknownTypeMessage prefix prepended to the type string when an unknown type is decoded
	 */
	public static <T> TypeDispatchRegistry<T> create(final Function<T, String> typeGetter, final String unknownTypeMessage) {
		return new TypeDispatchRegistry<>(typeGetter, unknownTypeMessage);
	}

	/** Register (or override) the sub-codec for a {@code type} string. */
	public void register(final String typeId, final MapCodec<? extends T> mapCodec) {
		this.byType.put(typeId, mapCodec);
	}

	/** The dispatching codec that resolves each {@code type} string from this registry. */
	public Codec<T> codec() {
		return this.codec;
	}
}
