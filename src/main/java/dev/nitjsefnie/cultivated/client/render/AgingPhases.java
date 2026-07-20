package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import dev.nitjsefnie.cultivated.data.display.RenderOptions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Phase C §C.3 — builds an {@code aging} display's ordered phase list from a block's growth property:
 * one {@link Display.Simple} per {@code age} value (a crop block / generic age integer property), or
 * per {@code flower_amount} value, or a single default-state display when the block has neither.
 * Phases are ordered ascending so phase index 0 is the youngest.
 *
 * <p>The built list is memoized per {@link Display.Aging} — i.e. keyed by {@code (block, options)}, both
 * immutable and both a record's only components, so equal displays share one list. Rebuilding it on every
 * frame in the render hot path (only to discard all but one phase) is wasteful; the cache is a
 * {@link ConcurrentHashMap} so it is safe for concurrent client render use.
 */
@Environment(EnvType.CLIENT)
public final class AgingPhases {
	private static final String AGE = "age";
	private static final String FLOWER_AMOUNT = "flower_amount";

	/** Memoized phase lists keyed by {@code (block, options)} — the {@link Display.Aging} record itself. */
	private static final Map<Display.Aging, List<Display>> CACHE = new ConcurrentHashMap<>();

	private AgingPhases() {
	}

	public static List<Display> build(final Display.Aging aging) {
		return CACHE.computeIfAbsent(aging, AgingPhases::buildPhases);
	}

	private static List<Display> buildPhases(final Display.Aging aging) {
		final Block block = aging.block();
		final RenderOptions options = aging.options();
		final StateDefinition<Block, BlockState> definition = block.getStateDefinition();

		IntegerProperty property = intProperty(definition, AGE);
		if (property == null) {
			property = intProperty(definition, FLOWER_AMOUNT);
		}

		if (property == null) {
			return List.of(new Display.Simple(block.defaultBlockState(), options));
		}

		final IntegerProperty phaseProperty = property;
		final List<Integer> values = new ArrayList<>(phaseProperty.getPossibleValues());
		values.sort(Comparator.naturalOrder());

		final List<Display> phases = new ArrayList<>(values.size());
		for (final Integer value : values) {
			final BlockState state = block.defaultBlockState().setValue(phaseProperty, value);
			phases.add(new Display.Simple(state, options));
		}
		return List.copyOf(phases);
	}

	private static @Nullable IntegerProperty intProperty(final StateDefinition<Block, BlockState> definition, final String name) {
		final Property<?> property = definition.getProperty(name);
		return property instanceof IntegerProperty integerProperty ? integerProperty : null;
	}
}
