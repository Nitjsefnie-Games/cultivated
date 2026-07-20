package dev.nitjsefnie.cultivated.menu;

import dev.nitjsefnie.cultivated.Cultivated;
import net.minecraft.resources.Identifier;

/**
 * Phase B §B.7/§B.8 — the texture ids the pot menus and screens reference. The empty-slot placeholder
 * icons are GUI atlas sprites ({@code assets/cultivated/textures/gui/sprites/&lt;path&gt;.png}); the
 * backgrounds are full container textures ({@code assets/cultivated/textures/gui/container/…png}).
 *
 * <p>The PNGs themselves are asset work owned by Task B6 — until then these ids render as the
 * missing-texture placeholder at runtime (no crash). The exact ids are fixed here so B6 knows what to
 * author.
 */
public final class PotMenuTextures {
	/** Empty-slot sprite for the soil input slot. */
	public static final Identifier SOIL_SLOT = Cultivated.id("container/slot/soil");
	/** Empty-slot sprite for the seed input slot. */
	public static final Identifier SEED_SLOT = Cultivated.id("container/slot/seed");
	/** Empty-slot sprite for the harvest-tool input slot (hoe icon). */
	public static final Identifier HOE_SLOT = Cultivated.id("container/slot/hoe");

	/** Basic pot container background (176×166). */
	public static final Identifier BASIC_BACKGROUND = Cultivated.id("textures/gui/container/basic_pot.png");
	/** Hopper pot container background (176×166). */
	public static final Identifier HOPPER_BACKGROUND = Cultivated.id("textures/gui/container/hopper_pot.png");

	private PotMenuTextures() {
	}
}
