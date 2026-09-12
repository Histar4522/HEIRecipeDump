package com.weienx.heirecipedump;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Walks HadEnoughItems and writes what it finds as JSON.
 * <pre>
 * jei_dump/index.json                     manifest written by dump_all
 * jei_dump/categories/&lt;uid&gt;.json          metainfo + every recipe of one category
 * jei_dump/items/&lt;modid&gt;.json             every item of one mod
 * </pre>
 */
public final class JeiDumper {

	public static final String DUMP_DIR = "jei_dump";
	private static final String CATEGORIES_DIR = "categories";
	private static final String ITEMS_DIR = "items";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final File root;
	private final IRecipeRegistry recipeRegistry;
	private final IIngredientRegistry ingredientRegistry;

	public JeiDumper(File gameDir, IRecipeRegistry recipeRegistry, IIngredientRegistry ingredientRegistry) {
		this.root = new File(gameDir, DUMP_DIR);
		this.recipeRegistry = recipeRegistry;
		this.ingredientRegistry = ingredientRegistry;
	}

	/** Everything HEI has: all categories, all their recipes, all items. */
	public void dumpAll(Consumer<String> out) {
		JsonArray categoryIndex = new JsonArray();
		int recipeCount = 0;
		for (IRecipeCategory category : categories()) {
			int count = dumpCategory(category, out);
			recipeCount += count;

			JsonObject entry = new JsonObject();
			entry.addProperty("uid", uid(category));
			entry.addProperty("title", title(category));
			entry.addProperty("mod_name", modName(category));
			entry.addProperty("recipe_count", count);
			entry.addProperty("file", relativePath(categoryFile(category)));
			categoryIndex.add(entry);
		}

		JsonArray modIndex = new JsonArray();
		int itemCount = 0;
		Map<String, List<ItemStack>> byMod = itemsByMod();
		for (Map.Entry<String, List<ItemStack>> entry : byMod.entrySet()) {
			dumpItemFile(entry.getKey(), entry.getValue(), out);
			itemCount += entry.getValue().size();

			JsonObject mod = new JsonObject();
			mod.addProperty("mod_id", entry.getKey());
			mod.addProperty("item_count", entry.getValue().size());
			mod.addProperty("file", relativePath(itemFile(entry.getKey())));
			modIndex.add(mod);
		}

		writeIndex(categoryIndex, modIndex);
		out.accept(String.format(Locale.ROOT, "dump_all: %d categories, %d recipes, %d items from %d mods -> %s",
			categoryIndex.size(), recipeCount, itemCount, byMod.size(), root));
	}

	/** Writes one JSON file per matching category (all categories when filter is null). */
	public void dumpRecipes(String filter, Consumer<String> out) {
		List<IRecipeCategory> matches = filter == null ? categories() : matchCategories(filter);
		if (matches.isEmpty()) {
			out.accept("No recipe category matches '" + filter + "', nothing dumped.");
			return;
		}

		int recipeCount = 0;
		for (IRecipeCategory category : matches) {
			recipeCount += dumpCategory(category, out);
		}
		out.accept(String.format(Locale.ROOT, "dump_recipes: %d categories, %d recipes -> %s",
			matches.size(), recipeCount, new File(root, CATEGORIES_DIR)));
	}

	/** Writes one JSON file per matching mod (all mods when filter is null). */
	public void dumpItems(String filter, Consumer<String> out) {
		int itemCount = 0;
		int modCount = 0;
		for (Map.Entry<String, List<ItemStack>> entry : itemsByMod().entrySet()) {
			if (filter != null && !entry.getKey().equalsIgnoreCase(filter)) {
				continue;
			}
			dumpItemFile(entry.getKey(), entry.getValue(), out);
			itemCount += entry.getValue().size();
			modCount++;
		}

		if (modCount == 0) {
			out.accept("No items registered for mod '" + filter + "'.");
			return;
		}
		out.accept(String.format(Locale.ROOT, "dump_items: %d items from %d mod(s) -> %s",
			itemCount, modCount, new File(root, ITEMS_DIR)));
	}

	private int dumpCategory(IRecipeCategory category, Consumer<String> out) {
		String uid = uid(category);
		try {
			List<IRecipeWrapper> wrappers = wrappersOf(category);

			JsonObject json = new JsonObject();
			json.add("metainfo", metainfo(category, wrappers));
			JsonArray recipes = new JsonArray();
			int index = 0;
			for (IRecipeWrapper wrapper : wrappers) {
				recipes.add(recipeJson(wrapper, index++));
			}
			json.add("recipes", recipes);
			writeJson(categoryFile(category), json);

			out.accept("  " + uid + ": " + wrappers.size() + " recipes");
			return wrappers.size();
		} catch (RuntimeException | LinkageError e) {
			HEIRecipeDump.LOGGER.error("Failed to dump recipe category {}", uid, e);
			out.accept("  " + uid + ": FAILED (" + e + ")");
			return 0;
		}
	}

	private JsonObject metainfo(IRecipeCategory category, List<IRecipeWrapper> wrappers) {
		JsonObject meta = new JsonObject();
		meta.addProperty("uid", uid(category));
		meta.addProperty("title", title(category));
		meta.addProperty("mod_name", modName(category));
		meta.addProperty("category_class", category.getClass().getName());

		IDrawable background = background(category);
		if (background != null) {
			JsonObject size = new JsonObject();
			size.addProperty("width", background.getWidth());
			size.addProperty("height", background.getHeight());
			meta.add("background", size);
		}

		meta.addProperty("recipe_count", wrappers.size());

		TreeSet<String> wrapperClasses = new TreeSet<>();
		for (IRecipeWrapper wrapper : wrappers) {
			wrapperClasses.add(wrapper.getClass().getName());
		}
		JsonArray classes = new JsonArray();
		for (String name : wrapperClasses) {
			classes.add(name);
		}
		meta.add("wrapper_classes", classes);

		JsonArray catalysts = new JsonArray();
		for (Object catalyst : recipeRegistry.getRecipeCatalysts(category)) {
			catalysts.add(IngredientCapture.ingredientJson(catalyst));
		}
		meta.add("catalysts", catalysts);

		return meta;
	}

	private JsonObject recipeJson(IRecipeWrapper wrapper, int index) {
		JsonObject json = new JsonObject();
		json.addProperty("index", index);
		json.addProperty("wrapper_class", wrapper.getClass().getName());

		if (wrapper instanceof ICraftingRecipeWrapper) {
			try {
				ResourceLocation recipeId = ((ICraftingRecipeWrapper) wrapper).getRegistryName();
				if (recipeId != null) {
					json.addProperty("recipe_id", recipeId.toString());
				}
			} catch (RuntimeException ignored) {
				// not every crafting wrapper can give its id
			}
		}

		IngredientCapture capture = new IngredientCapture();
		try {
			wrapper.getIngredients(capture);
		} catch (RuntimeException | LinkageError e) {
			json.addProperty("error", e.toString());
		}
		json.add("inputs", capture.toJson(false));
		json.add("outputs", capture.toJson(true));
		return json;
	}

	private void dumpItemFile(String modId, List<ItemStack> stacks, Consumer<String> out) {
		JsonObject json = new JsonObject();
		json.addProperty("mod_id", modId);
		json.addProperty("item_count", stacks.size());
		JsonArray items = new JsonArray();
		for (ItemStack stack : stacks) {
			items.add(IngredientCapture.itemJson(stack));
		}
		json.add("items", items);
		writeJson(itemFile(modId), json);
		out.accept("  " + modId + ": " + stacks.size() + " items");
	}

	private Map<String, List<ItemStack>> itemsByMod() {
		Map<String, List<ItemStack>> byMod = new TreeMap<>();
		for (ItemStack stack : ingredientRegistry.getAllIngredients(VanillaTypes.ITEM)) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			String modId = modIdOf(stack);
			List<ItemStack> stacks = byMod.get(modId);
			if (stacks == null) {
				stacks = new ArrayList<>();
				byMod.put(modId, stacks);
			}
			stacks.add(stack);
		}
		return byMod;
	}

	private List<IRecipeCategory> categories() {
		// HEI hands out its own cached list here, copy it before iterating
		return new ArrayList<>(recipeRegistry.getRecipeCategories());
	}

	private List<IRecipeCategory> matchCategories(String filter) {
		List<IRecipeCategory> matches = new ArrayList<>();
		for (IRecipeCategory category : categories()) {
			String uid = uid(category);
			if (uid.equalsIgnoreCase(filter)) {
				return Collections.singletonList(category);
			}
			if (containsIgnoreCase(uid, filter) || containsIgnoreCase(title(category), filter)) {
				matches.add(category);
			}
		}
		return matches;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private List<IRecipeWrapper> wrappersOf(IRecipeCategory category) {
		List raw = recipeRegistry.getRecipeWrappers(category);
		return new ArrayList<IRecipeWrapper>(raw);
	}

	private void writeIndex(JsonArray categoryIndex, JsonArray modIndex) {
		JsonObject index = new JsonObject();
		index.addProperty("minecraft_version", "1.12.2");
		index.addProperty("hei_version", heiVersion());
		index.addProperty("dump_time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
		index.add("categories", categoryIndex);
		index.add("mods", modIndex);
		writeJson(new File(root, "index.json"), index);
	}

	private void writeJson(File file, JsonElement json) {
		try {
			File dir = file.getParentFile();
			if (dir != null) {
				dir.mkdirs();
			}
			Files.write(file.toPath(), GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write " + file, e);
		}
	}

	private File categoryFile(IRecipeCategory category) {
		return new File(new File(root, CATEGORIES_DIR), fileName(uid(category)) + ".json");
	}

	private File itemFile(String modId) {
		return new File(new File(root, ITEMS_DIR), fileName(modId) + ".json");
	}

	private String relativePath(File file) {
		File parent = file.getParentFile();
		return (parent == null ? "" : parent.getName() + "/") + file.getName();
	}

	private static String heiVersion() {
		try {
			ModContainer jei = Loader.instance().getIndexedModList().get("jei");
			return jei == null ? "unknown" : jei.getVersion();
		} catch (RuntimeException e) {
			return "unknown";
		}
	}

	private static String modIdOf(ItemStack stack) {
		ResourceLocation name = stack.getItem() == null ? null : stack.getItem().getRegistryName();
		return name == null ? "unknown" : name.getNamespace();
	}

	/** Category uids go into file names, so strip anything a file system might not like. */
	private static String fileName(String name) {
		return name.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static boolean containsIgnoreCase(String haystack, String needle) {
		return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
	}

	private static String uid(IRecipeCategory category) {
		try {
			return String.valueOf(category.getUid());
		} catch (RuntimeException e) {
			return "unknown";
		}
	}

	private static String title(IRecipeCategory category) {
		try {
			return category.getTitle();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String modName(IRecipeCategory category) {
		try {
			return category.getModName();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static IDrawable background(IRecipeCategory category) {
		try {
			return category.getBackground();
		} catch (RuntimeException e) {
			return null;
		}
	}
}
