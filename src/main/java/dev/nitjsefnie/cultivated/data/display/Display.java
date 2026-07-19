package dev.nitjsefnie.cultivated.data.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.Vec3f;
import dev.nitjsefnie.cultivated.util.CodecHelper;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase C §C.2 — a data-driven display value dispatched by {@code type}. This is the parsed data
 * model (with JSON + derived network codecs); the bound client renderers are Phase C and are not
 * built here. Provided so the Phase-A soil/crop schemas that reference {@code display} can load.
 */
public sealed interface Display {
	String SIMPLE_TYPE = Cultivated.id("simple").toString();
	String AGING_TYPE = Cultivated.id("aging").toString();
	String TRANSITIONAL_TYPE = Cultivated.id("transitional").toString();
	String ENTITY_TYPE = Cultivated.id("entity").toString();
	String TEXTURED_CUBE_TYPE = Cultivated.id("textured_cube").toString();

	String typeId();

	Codec<Display> CODEC = Codec.STRING.dispatch(
		"type",
		Display::typeId,
		type -> {
			if (SIMPLE_TYPE.equals(type)) {
				return Simple.MAP_CODEC;
			} else if (AGING_TYPE.equals(type)) {
				return Aging.MAP_CODEC;
			} else if (TRANSITIONAL_TYPE.equals(type)) {
				return Transitional.MAP_CODEC;
			} else if (ENTITY_TYPE.equals(type)) {
				return Entity.MAP_CODEC;
			} else if (TEXTURED_CUBE_TYPE.equals(type)) {
				return TexturedCube.MAP_CODEC;
			}
			throw new IllegalArgumentException("Unknown cultivated display type: " + type);
		}
	);

	/** A {@code display} field accepts either a single display or a list of them (§C.2). */
	Codec<List<Display>> LIST_CODEC = CodecHelper.flexibleList(CODEC);

	record Simple(BlockState blockState, RenderOptions options) implements Display {
		static final MapCodec<Simple> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					BlockState.CODEC.fieldOf("block_state").forGetter(Simple::blockState),
					RenderOptions.MAP_CODEC.forGetter(Simple::options)
				)
				.apply(i, Simple::new)
		);

		@Override
		public String typeId() {
			return SIMPLE_TYPE;
		}
	}

	record Aging(Block block, RenderOptions options) implements Display {
		static final MapCodec<Aging> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(Aging::block),
					RenderOptions.MAP_CODEC.forGetter(Aging::options)
				)
				.apply(i, Aging::new)
		);

		@Override
		public String typeId() {
			return AGING_TYPE;
		}
	}

	record Transitional(List<Display> phases) implements Display {
		static final MapCodec<Transitional> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					Codec.lazyInitialized(() -> CodecHelper.flexibleList(Display.CODEC)).fieldOf("phases").forGetter(Transitional::phases)
				)
				.apply(i, Transitional::new)
		);

		@Override
		public String typeId() {
			return TRANSITIONAL_TYPE;
		}
	}

	record Entity(CompoundTag entity, boolean shouldTick, float spinSpeed, Vec3f scale, Optional<Vec3f> offset) implements Display {
		static final Vec3f DEFAULT_SCALE = new Vec3f(0.5f, 0.5f, 0.5f);
		static final MapCodec<Entity> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					CompoundTag.CODEC.fieldOf("entity").forGetter(Entity::entity),
					Codec.BOOL.optionalFieldOf("should_tick", true).forGetter(Entity::shouldTick),
					Codec.FLOAT.optionalFieldOf("spin_speed", 0.0f).forGetter(Entity::spinSpeed),
					Vec3f.CODEC.optionalFieldOf("scale", DEFAULT_SCALE).forGetter(Entity::scale),
					Vec3f.CODEC.optionalFieldOf("offset").forGetter(Entity::offset)
				)
				.apply(i, Entity::new)
		);

		@Override
		public String typeId() {
			return ENTITY_TYPE;
		}
	}

	record TexturedCube(Identifier texture, RenderOptions options) implements Display {
		static final MapCodec<TexturedCube> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					Identifier.CODEC.fieldOf("texture").forGetter(TexturedCube::texture),
					RenderOptions.MAP_CODEC.forGetter(TexturedCube::options)
				)
				.apply(i, TexturedCube::new)
		);

		@Override
		public String typeId() {
			return TEXTURED_CUBE_TYPE;
		}
	}
}
