package dev.nitjsefnie.cultivated.mixin;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Phase G #17 — vanilla accessor: expose {@link CropBlock}'s protected seed-item getter so that
 * {@code block_derived_crop} can derive the seed for a vanilla crop block (§A.4).
 */
@Mixin(CropBlock.class)
public interface CropBlockAccessor {
	@Invoker("getBaseSeedId")
	ItemLike cultivated$getBaseSeedId();
}
