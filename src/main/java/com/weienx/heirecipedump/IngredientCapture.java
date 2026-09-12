package com.weienx.heirecipedump;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IIngredientType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A sink for {@link IIngredients} that just records what a recipe wrapper reports, keyed by
 * ingredient class name (works for both the {@link IIngredientType} API and the deprecated
 * {@code Class} API). This is how recipes are dumped without knowing every ingredient type
 * a mod might have registered.
 */
public class IngredientCapture implements IIngredients {

	private final Map<String, List<List<Object>>> inputs = new LinkedHashMap<>();
	private final Map<String, List<List<Object>>> outputs = new LinkedHashMap<>();

	@Override
	public <T> void setInput(IIngredientType<T> ingredientType, T input) {
		setInputs(ingredientType, Collections.singletonList(input));
	}

	@Override
	public <T> void setInputs(IIngredientType<T> ingredientType, List<T> input) {
		set(inputs, ingredientType.getIngredientClass().getName(), slotsOf(input));
	}

	@Override
	public <T> void setInputLists(IIngredientType<T> ingredientType, List<List<T>> input) {
		set(inputs, ingredientType.getIngredientClass().getName(), copy(input));
	}

	@Override
	public <T> void setOutput(IIngredientType<T> ingredientType, T output) {
		setOutputs(ingredientType, Collections.singletonList(output));
	}

	@Override
	public <T> void setOutputs(IIngredientType<T> ingredientType, List<T> outputs) {
		set(this.outputs, ingredientType.getIngredientClass().getName(), slotsOf(outputs));
	}

	@Override
	public <T> void setOutputLists(IIngredientType<T> ingredientType, List<List<T>> outputs) {
		set(this.outputs, ingredientType.getIngredientClass().getName(), copy(outputs));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> List<List<T>> getInputs(IIngredientType<T> ingredientType) {
		return get(inputs, ingredientType.getIngredientClass().getName());
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> List<List<T>> getOutputs(IIngredientType<T> ingredientType) {
		return get(outputs, ingredientType.getIngredientClass().getName());
	}

	// Deprecated Class-based API, still used by plenty of older plugins.

	@Override
	@Deprecated
	public <T> void setInput(Class<? extends T> ingredientClass, T input) {
		setInputs(ingredientClass, Collections.singletonList(input));
	}

	@Override
	@Deprecated
	public <T> void setInputs(Class<? extends T> ingredientClass, List<T> input) {
		set(inputs, ingredientClass.getName(), slotsOf(input));
	}

	@Override
	@Deprecated
	public <T> void setInputLists(Class<? extends T> ingredientClass, List<List<T>> input) {
		set(inputs, ingredientClass.getName(), copy(input));
	}

	@Override
	@Deprecated
	public <T> void setOutput(Class<? extends T> ingredientClass, T output) {
		setOutputs(ingredientClass, Collections.singletonList(output));
	}

	@Override
	@Deprecated
	public <T> void setOutputs(Class<? extends T> ingredientClass, List<T> outputs) {
		set(this.outputs, ingredientClass.getName(), slotsOf(outputs));
	}

	@Override
	@Deprecated
	public <T> void setOutputLists(Class<? extends T> ingredientClass, List<List<T>> outputs) {
		set(this.outputs, ingredientClass.getName(), copy(outputs));
	}

	@Override
	@Deprecated
	public <T> List<List<T>> getInputs(Class<? extends T> ingredientClass) {
		return get(inputs, ingredientClass.getName());
	}

	@Override
	@Deprecated
	public <T> List<List<T>> getOutputs(Class<? extends T> ingredientClass) {
		return get(outputs, ingredientClass.getName());
	}

	/**
	 * @param asOutputs true for the outputs, false for the inputs
	 * @return ingredient class name -> slots -> ingredients in that slot
	 */
	public JsonObject toJson(boolean asOutputs) {
		Map<String, List<List<Object>>> byType = asOutputs ? outputs : inputs;
		JsonObject json = new JsonObject();
		for (Map.Entry<String, List<List<Object>>> entry : byType.entrySet()) {
			JsonArray slots = new JsonArray();
			for (List<Object> slot : entry.getValue()) {
				JsonArray slotJson = new JsonArray();
				for (Object ingredient : slot) {
					slotJson.add(ingredientJson(ingredient));
				}
				slots.add(slotJson);
			}
			json.add(entry.getKey(), slots);
		}
		return json;
	}

	private static void set(Map<String, List<List<Object>>> map, String key, List<List<Object>> value) {
		if (value != null) {
			map.put(key, value);
		}
	}

	private static List<List<Object>> slotsOf(List<?> ingredients) {
		List<List<Object>> slots = new ArrayList<>();
		if (ingredients != null) {
			for (Object ingredient : ingredients) {
				slots.add(Collections.singletonList(ingredient));
			}
		}
		return slots;
	}

	private static List<List<Object>> copy(List<? extends List<?>> lists) {
		List<List<Object>> slots = new ArrayList<>();
		if (lists != null) {
			for (List<?> list : lists) {
				slots.add(list == null ? Collections.emptyList() : new ArrayList<Object>(list));
			}
		}
		return slots;
	}

	@SuppressWarnings("unchecked")
	private static <T> List<List<T>> get(Map<String, List<List<Object>>> map, String key) {
		List<List<Object>> slots = map.get(key);
		if (slots == null) {
			return Collections.emptyList();
		}
		List<List<T>> result = new ArrayList<>();
		for (List<Object> slot : slots) {
			result.add((List<T>) slot);
		}
		return result;
	}

	/** Ingredient serializers, kept next to the capture because that is the only user. */
	static JsonElement ingredientJson(Object ingredient) {
		if (ingredient == null) {
			return JsonNull.INSTANCE;
		}
		try {
			if (ingredient instanceof ItemStack) {
				return itemJson((ItemStack) ingredient);
			}
			if (ingredient instanceof FluidStack) {
				return fluidJson((FluidStack) ingredient);
			}
			JsonObject other = new JsonObject();
			other.addProperty("type", ingredient.getClass().getName());
			other.addProperty("value", String.valueOf(ingredient));
			return other;
		} catch (RuntimeException e) {
			JsonObject broken = new JsonObject();
			broken.addProperty("type", ingredient.getClass().getName());
			broken.addProperty("error", e.toString());
			return broken;
		}
	}

	static JsonObject itemJson(ItemStack stack) {
		JsonObject json = new JsonObject();
		ResourceLocation name = stack.getItem() == null ? null : stack.getItem().getRegistryName();
		json.addProperty("item", name == null ? "unknown" : name.toString());
		json.addProperty("mod_id", name == null ? "unknown" : name.getNamespace());
		json.addProperty("metadata", stack.getMetadata());
		json.addProperty("count", stack.getCount());
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt != null) {
			json.addProperty("nbt", nbt.toString());
		}
		try {
			json.addProperty("display_name", stack.getDisplayName());
		} catch (RuntimeException ignored) {
			// a broken item should not take the whole dump down
		}
		return json;
	}

	private static JsonObject fluidJson(FluidStack stack) {
		JsonObject json = new JsonObject();
		json.addProperty("fluid", stack.getFluid() == null ? "unknown" : stack.getFluid().getName());
		json.addProperty("amount", stack.amount);
		if (stack.tag != null) {
			json.addProperty("nbt", stack.tag.toString());
		}
		return json;
	}
}
