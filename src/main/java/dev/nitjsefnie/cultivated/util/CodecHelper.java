package dev.nitjsefnie.cultivated.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Phase G #5 — codec/data helpers: flexible list/set codecs (accept a single element or an
 * array), a recipe-serializer builder from a map-codec, and stream-codec conveniences. Stream
 * codecs are derived from the JSON codec via {@link ByteBufCodecs#fromCodecWithRegistries} so
 * that every data type is network-syncable (as an NBT payload) without a hand-written
 * stream-codec per type — clients receive the full recipe data for tooltips/JEI/rendering.
 */
public final class CodecHelper {
	private CodecHelper() {
	}

	/** A codec that accepts either a single element or an array, always producing a list. */
	public static <T> Codec<List<T>> flexibleList(final Codec<T> element) {
		return Codec.either(element.listOf(), element)
			.xmap(
				either -> either.map(list -> list, List::of),
				list -> list.size() == 1 ? Either.right(list.get(0)) : Either.left(list)
			);
	}

	/** A codec that accepts either a single element or an array, always producing a set. */
	public static <T> Codec<Set<T>> flexibleSet(final Codec<T> element) {
		return flexibleList(element).xmap(Set::copyOf, List::copyOf);
	}

	/** Build a network stream-codec from a JSON codec (NBT-over-network). */
	public static <T> StreamCodec<RegistryFriendlyByteBuf, T> streamOf(final Codec<T> codec) {
		return ByteBufCodecs.fromCodecWithRegistries(codec);
	}

	/** Build a recipe serializer from a map-codec, deriving the stream-codec from the codec. */
	public static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeSerializer<T> recipeSerializer(final MapCodec<T> mapCodec) {
		return new RecipeSerializer<>(mapCodec, streamOf(mapCodec.codec()));
	}
}
