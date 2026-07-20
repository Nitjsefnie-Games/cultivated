package dev.nitjsefnie.cultivated.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nitjsefnie.cultivated.Cultivated;
import dev.nitjsefnie.cultivated.config.CultivatedConfig;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.RegistryOps;

/**
 * Phase D §D / §A.9 — the {@code cultivated:config} Fabric resource condition. Where
 * {@link LoadCondition.Config} gates the mod's own data-driven recipes at cache-build time, plain
 * vanilla data files (the shaped upgrade-item crafting recipes) are loaded by the vanilla recipe
 * manager and can only be gated through Fabric's {@code fabric:load_conditions} mechanism. This type
 * bridges that mechanism to the same {@link CultivatedConfig#booleanProperty(String)} boolean gates,
 * so a recipe carrying {@code {"condition":"cultivated:config","property":"can_craft_elite_basic_pots"}}
 * is dropped when the gate is off.
 */
public record ConfigResourceCondition(String property) implements ResourceCondition {
	public static final MapCodec<ConfigResourceCondition> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(Codec.STRING.fieldOf("property").forGetter(ConfigResourceCondition::property))
			.apply(i, ConfigResourceCondition::new)
	);

	public static final ResourceConditionType<ConfigResourceCondition> TYPE =
		ResourceConditionType.create(Cultivated.id("config"), CODEC);

	/** Register the condition type; called once during common init (before datapacks load). */
	public static void register() {
		ResourceConditions.register(TYPE);
	}

	@Override
	public ResourceConditionType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean test(final RegistryOps.RegistryInfoLookup registryLookup) {
		return CultivatedConfig.booleanProperty(this.property);
	}
}
