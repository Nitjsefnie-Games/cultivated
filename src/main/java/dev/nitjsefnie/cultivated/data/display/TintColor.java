package dev.nitjsefnie.cultivated.data.display;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Phase C §C.6 — a tint colour parsed either from an ARGB object ({@code {alpha,red,green,blue}},
 * each 0–255, default 255) or from a hex string ({@code "RRGGBB"} / {@code "AARRGGBB"}, leading
 * {@code #} optional). Stored as a packed ARGB int.
 */
public record TintColor(int argb) {
	private static final Codec<Integer> COMPONENT = Codec.intRange(0, 255);

	private static final Codec<TintColor> OBJECT_CODEC = RecordCodecBuilder.create(
		i -> i.group(
				COMPONENT.optionalFieldOf("alpha", 255).forGetter(c -> c.alpha()),
				COMPONENT.optionalFieldOf("red", 255).forGetter(c -> c.red()),
				COMPONENT.optionalFieldOf("green", 255).forGetter(c -> c.green()),
				COMPONENT.optionalFieldOf("blue", 255).forGetter(c -> c.blue())
			)
			.apply(i, TintColor::fromComponents)
	);

	private static final Codec<TintColor> HEX_CODEC = Codec.STRING.comapFlatMap(TintColor::parseHex, TintColor::toHex);

	public static final Codec<TintColor> CODEC = Codec.either(HEX_CODEC, OBJECT_CODEC)
		.xmap(either -> either.map(c -> c, c -> c), Either::right);

	public static TintColor fromComponents(final int alpha, final int red, final int green, final int blue) {
		return new TintColor((alpha << 24) | (red << 16) | (green << 8) | blue);
	}

	public int alpha() {
		return (this.argb >> 24) & 0xFF;
	}

	public int red() {
		return (this.argb >> 16) & 0xFF;
	}

	public int green() {
		return (this.argb >> 8) & 0xFF;
	}

	public int blue() {
		return this.argb & 0xFF;
	}

	private static DataResult<TintColor> parseHex(final String raw) {
		String hex = raw.startsWith("#") ? raw.substring(1) : raw;
		try {
			long value = Long.parseLong(hex, 16);
			if (hex.length() <= 6) {
				value |= 0xFF000000L; // opaque when no alpha given
			}
			return DataResult.success(new TintColor((int)value));
		} catch (final NumberFormatException e) {
			return DataResult.error(() -> "Invalid hex colour: " + raw);
		}
	}

	private String toHex() {
		return String.format("%08X", this.argb);
	}
}
