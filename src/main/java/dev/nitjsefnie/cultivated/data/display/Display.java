package dev.nitjsefnie.cultivated.data.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.data.display.RenderOptions.Vec3f;
import dev.nitjsefnie.cultivated.plugin.TypeDispatchRegistry;
import dev.nitjsefnie.cultivated.util.CodecHelper;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
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

	/**
	 * Task F3 (I2) — the mutable {@code type} → sub-codec registry. Built-ins are seeded through the
	 * plugin path ({@link #registerBuiltins}); add-ons add new display types with {@link #register}.
	 */
	TypeDispatchRegistry<Display> DISPATCH = TypeDispatchRegistry.create(Display::typeId, "Unknown cultivated display type: ");

	Codec<Display> CODEC = DISPATCH.codec();

	/** A {@code display} field accepts either a single display or a list of them (§C.2). */
	Codec<List<Display>> LIST_CODEC = CodecHelper.flexibleList(CODEC);

	/** Register (or override) a display {@code type} → sub-codec mapping (add-on hook). */
	static void register(final String typeId, final MapCodec<? extends Display> mapCodec) {
		DISPATCH.register(typeId, mapCodec);
	}

	/** Feed the built-in display types through {@code out} (used by the core plugin, §F.3). */
	static void registerBuiltins(final BiConsumer<String, MapCodec<? extends Display>> out) {
		out.accept(SIMPLE_TYPE, Simple.MAP_CODEC);
		out.accept(AGING_TYPE, Aging.MAP_CODEC);
		out.accept(TRANSITIONAL_TYPE, Transitional.MAP_CODEC);
		out.accept(ENTITY_TYPE, Entity.MAP_CODEC);
		out.accept(TEXTURED_CUBE_TYPE, TexturedCube.MAP_CODEC);
	}

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
