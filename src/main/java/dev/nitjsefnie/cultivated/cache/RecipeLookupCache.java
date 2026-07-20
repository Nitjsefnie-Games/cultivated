package dev.nitjsefnie.cultivated.cache;

import dev.nitjsefnie.cultivated.condition.LoadCondition;
import dev.nitjsefnie.cultivated.recipe.BotanyRecipe;
import dev.nitjsefnie.cultivated.recipe.PotContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Phase A §A.2 — the per-recipe-kind lookup cache (matching engine). Built on reload from all
 * recipes of one kind. Cacheable recipes (intrinsic-only match) are indexed per item by scanning
 * every registered item's default instance through each recipe's {@link BotanyRecipe#couldMatch};
 * non-cacheable recipes go into an uncached list. Recipes whose load conditions (§A.9) fail are
 * excluded from the cache entirely.
 */
public final class RecipeLookupCache<T extends BotanyRecipe> {
	private final Map<Item, List<T>> byItem;
	private final List<T> uncached;
	private final List<T> all;

	private RecipeLookupCache(final Map<Item, List<T>> byItem, final List<T> uncached, final List<T> all) {
		this.byItem = byItem;
		this.uncached = uncached;
		this.all = all;
	}

	public static <T extends BotanyRecipe> RecipeLookupCache<T> empty() {
		return new RecipeLookupCache<>(new IdentityHashMap<>(), List.of(), List.of());
	}

	/** Build the cache from the active recipes of a kind (already collected from the recipe map). */
	public static <T extends BotanyRecipe> RecipeLookupCache<T> build(final List<T> recipes) {
		final List<T> active = new ArrayList<>(recipes.size());
		for (final T recipe : recipes) {
			if (LoadCondition.testAll(recipe.conditions())) {
				active.add(recipe);
			}
		}

		final Map<Item, List<T>> byItem = new IdentityHashMap<>();
		final List<T> uncached = new ArrayList<>();
		final List<T> cacheable = new ArrayList<>();
		for (final T recipe : active) {
			if (recipe.isCacheable()) {
				cacheable.add(recipe);
			} else {
				uncached.add(recipe);
			}
		}

		for (final Item item : BuiltInRegistries.ITEM) {
			final ItemStack stack = item.getDefaultInstance();
			List<T> matches = null;
			for (final T recipe : cacheable) {
				if (recipe.couldMatch(stack)) {
					if (matches == null) {
						matches = new ArrayList<>(2);
					}
					matches.add(recipe);
				}
			}
			if (matches != null) {
				byItem.put(item, matches);
			}
		}

		return new RecipeLookupCache<>(byItem, uncached, active);
	}

	/**
	 * Return the first recipe whose {@link BotanyRecipe#couldMatch} accepts {@code stack}, scanning
	 * the item's indexed recipes then the uncached list. The caller confirms with the full
	 * {@link BotanyRecipe#matches} when it needs the contextual guarantee.
	 */
	@Nullable
	public T lookup(final ItemStack stack, final PotContext context) {
		final List<T> indexed = this.byItem.get(stack.getItem());
		if (indexed != null) {
			for (final T recipe : indexed) {
				if (recipe.couldMatch(stack)) {
					return recipe;
				}
			}
		}
		for (final T recipe : this.uncached) {
			if (recipe.couldMatch(stack)) {
				return recipe;
			}
		}
		return null;
	}

	/**
	 * The first recipe that BOTH cheap-matches {@code stack} ({@link BotanyRecipe#couldMatch}) AND fully
	 * {@link BotanyRecipe#matches} the live {@code context} — scanning the item's indexed recipes then the
	 * uncached list. Unlike {@link #lookup}, this does not stop at the first cheap-match: when several
	 * recipes share a primary ingredient (e.g. every {@code #minecraft:hoes} pot-interaction), each is
	 * confirmed against the full context, so a later matching recipe is never shadowed by an earlier one
	 * that cheap-matches the held item but fails on soil/seed. Returns {@code null} when none fully matches.
	 */
	@Nullable
	public T firstMatching(final ItemStack stack, final PotContext context, final Level level) {
		final List<T> indexed = this.byItem.get(stack.getItem());
		if (indexed != null) {
			for (final T recipe : indexed) {
				if (recipe.couldMatch(stack) && recipe.matches(context, level)) {
					return recipe;
				}
			}
		}
		for (final T recipe : this.uncached) {
			if (recipe.couldMatch(stack) && recipe.matches(context, level)) {
				return recipe;
			}
		}
		return null;
	}

	/** True if the item has at least one indexed recipe (used by the "missing" command). */
	public boolean isCached(final Item item) {
		final List<T> indexed = this.byItem.get(item);
		return indexed != null && !indexed.isEmpty();
	}

	/** All active recipes of this kind (used by debug commands). */
	public List<T> values() {
		return Collections.unmodifiableList(this.all);
	}
}
