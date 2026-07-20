package dev.nitjsefnie.cultivated.data.drop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nitjsefnie.cultivated.Cultivated;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * §G#10 — enumerate the unique possible item outputs of a loot table for the recipe viewers
 * (REI/JEI). The referenced table is read from the datapack JSON on the classpath
 * ({@code data/<namespace>/loot_table/<path>.json}) — the mod's own tables (e.g.
 * {@code data/cultivated/loot_table/tree/*}) are bundled in the jar and available on both the client
 * and server sides without needing a running-server loot reload, so the client viewer can enumerate
 * a tree crop's drops. Pools → entries are walked recursively, collecting the item of every
 * {@code minecraft:item} entry (recursing into {@code alternatives}/{@code group}/{@code sequence}
 * children); {@code tag}/{@code loot_table}/{@code dynamic} entries and loot functions are skipped.
 * Any table that cannot be resolved or parsed degrades to an empty list — never a crash.
 */
public final class LootTableDisplayItems {
	/** Resolved item lists are immutable and cached per table id; the viewer re-copies to fresh stacks. */
	private static final Map<Identifier, List<Item>> CACHE = new ConcurrentHashMap<>();

	private LootTableDisplayItems() {
	}

	/** The unique possible item outputs of {@code tableId} as fresh display stacks (empty if unresolvable). */
	public static List<ItemStack> resolve(final Identifier tableId) {
		final List<Item> items = CACHE.computeIfAbsent(tableId, LootTableDisplayItems::load);
		if (items.isEmpty()) {
			return List.of();
		}
		final List<ItemStack> stacks = new ArrayList<>(items.size());
		for (final Item item : items) {
			final ItemStack stack = new ItemStack(item);
			if (!stack.isEmpty()) {
				stacks.add(stack);
			}
		}
		return stacks;
	}

	private static List<Item> load(final Identifier tableId) {
		final String resourcePath = "/data/" + tableId.getNamespace() + "/loot_table/" + tableId.getPath() + ".json";
		try (InputStream stream = LootTableDisplayItems.class.getResourceAsStream(resourcePath)) {
			if (stream == null) {
				return List.of();
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				final JsonElement root = JsonParser.parseReader(reader);
				if (!root.isJsonObject()) {
					return List.of();
				}
				final Set<Item> items = new LinkedHashSet<>();
				collectPools(root.getAsJsonObject(), items);
				return List.copyOf(items);
			}
		} catch (final RuntimeException | java.io.IOException failure) {
			Cultivated.LOGGER.warn("Cultivated: could not enumerate loot table {} for display: {}", tableId, failure.toString());
			return List.of();
		}
	}

	private static void collectPools(final JsonObject table, final Set<Item> out) {
		final JsonElement pools = table.get("pools");
		if (pools == null || !pools.isJsonArray()) {
			return;
		}
		for (final JsonElement pool : pools.getAsJsonArray()) {
			if (!pool.isJsonObject()) {
				continue;
			}
			final JsonElement entries = pool.getAsJsonObject().get("entries");
			if (entries != null && entries.isJsonArray()) {
				collectEntries(entries.getAsJsonArray(), out);
			}
		}
	}

	private static void collectEntries(final JsonArray entries, final Set<Item> out) {
		for (final JsonElement entry : entries) {
			if (entry.isJsonObject()) {
				collectEntry(entry.getAsJsonObject(), out);
			}
		}
	}

	private static void collectEntry(final JsonObject entry, final Set<Item> out) {
		final JsonElement children = entry.get("children");
		if (children != null && children.isJsonArray()) {
			collectEntries(children.getAsJsonArray(), out);
		}
		final JsonElement type = entry.get("type");
		if (type == null || !type.isJsonPrimitive()) {
			return;
		}
		if (!isItemEntry(type.getAsString())) {
			return;
		}
		final JsonElement name = entry.get("name");
		if (name == null || !name.isJsonPrimitive()) {
			return;
		}
		final Identifier itemId = Identifier.tryParse(name.getAsString());
		if (itemId == null) {
			return;
		}
		BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(out::add);
	}

	/** True for an {@code item} loot entry (namespaced or bare), the only entry kind that names an item. */
	private static boolean isItemEntry(final String type) {
		return "minecraft:item".equals(type) || "item".equals(type);
	}
}
