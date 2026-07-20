package dev.nitjsefnie.cultivated.client.render;

import dev.nitjsefnie.cultivated.data.display.Display;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Phase C §C.5 — renders a {@code textured_cube} display: a unit cube drawn from the given block-atlas
 * sprite and tint (translucent), with the {@link dev.nitjsefnie.cultivated.data.display.RenderOptions}
 * transforms applied by the pot renderer. The sprite is fetched from the block atlas by id
 * ({@link DisplayResolveContext#sprite}); an absent {@code color} renders untinted (white).
 */
@Environment(EnvType.CLIENT)
public final class TexturedCubeDisplayRenderer implements DisplayRenderer<Display> {
	@Override
	public void resolve(final Display display, final DisplayResolveContext context, final List<ResolvedDisplay> out) {
		if (!(display instanceof Display.TexturedCube cube)) {
			return;
		}
		final TextureAtlasSprite sprite = context.sprite(cube.texture());
		final int argb = PotRenderMath.renderColor(cube.options(), 0xFFFFFFFF);
		out.add(new ResolvedDisplay(CubeRenderer.geometry(sprite, argb, cube.options()), cube.options(), context.growthScale()));
	}
}
