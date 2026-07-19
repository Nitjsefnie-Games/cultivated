package dev.nitjsefnie.cultivated.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Phase A §A.12 — a data-driven sound effect: sound event id, category (default MASTER), volume
 * and pitch.
 */
public record SoundEffect(Holder<SoundEvent> sound, SoundSource category, float volume, float pitch) {
	private static final Map<String, SoundSource> BY_NAME = Arrays.stream(SoundSource.values())
		.collect(Collectors.toMap(SoundSource::getName, Function.identity()));

	public static final Codec<SoundSource> SOURCE_CODEC = Codec.STRING.xmap(
		name -> BY_NAME.getOrDefault(name, SoundSource.MASTER), SoundSource::getName
	);

	public static final Codec<SoundEffect> CODEC = RecordCodecBuilder.create(
		i -> i.group(
				SoundEvent.CODEC.fieldOf("id").forGetter(SoundEffect::sound),
				SOURCE_CODEC.optionalFieldOf("category", SoundSource.MASTER).forGetter(SoundEffect::category),
				Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(SoundEffect::volume),
				Codec.FLOAT.optionalFieldOf("pitch", 1.0f).forGetter(SoundEffect::pitch)
			)
			.apply(i, SoundEffect::new)
	);
}
