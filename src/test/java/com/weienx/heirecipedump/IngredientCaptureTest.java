package com.weienx.heirecipedump;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mezz.jei.api.recipe.IIngredientType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IngredientCaptureTest {

	private static final IIngredientType<String> STRINGS = () -> String.class;

	/** setInputs gives each element its own slot, the way HEI's own Ingredients does. */
	@Test
	public void givesEachIngredientItsOwnSlot() {
		IngredientCapture capture = new IngredientCapture();
		capture.setInputs(STRINGS, Arrays.asList("a", "b"));
		capture.setOutput(STRINGS, "out");

		JsonArray slots = capture.toJson(false).getAsJsonArray(String.class.getName());
		assertEquals(2, slots.size());
		assertEquals("a", slots.get(0).getAsJsonArray().get(0).getAsJsonObject().get("value").getAsString());

		assertEquals(Arrays.asList("a"), capture.getInputs(STRINGS).get(0));
		assertEquals(Arrays.asList("b"), capture.getInputs(STRINGS).get(1));
		assertEquals("out", capture.getOutputs(STRINGS).get(0).get(0));
	}

	/** setInputLists keeps the alternatives of one slot together. */
	@Test
	public void keepsGroupedSlotsTogether() {
		IngredientCapture capture = new IngredientCapture();
		capture.setInputLists(STRINGS, Arrays.asList(Arrays.asList("a", "b"), Arrays.asList("c")));

		JsonArray slots = capture.toJson(false).getAsJsonArray(String.class.getName());
		assertEquals(2, slots.size());
		assertEquals(2, slots.get(0).getAsJsonArray().size());
		assertEquals(1, slots.get(1).getAsJsonArray().size());
	}

	/** Like HEI's Ingredients: the last call for one ingredient type wins, and the Class API
	 *  shares the bucket of the matching IIngredientType. */
	@Test
	public void lastWritePerIngredientTypeWins() {
		IngredientCapture capture = new IngredientCapture();
		capture.setInputs(STRINGS, Arrays.asList("dropped"));
		capture.setInput(String.class, "kept");

		List<List<String>> inputs = capture.getInputs(STRINGS);
		assertEquals(1, inputs.size());
		assertEquals("kept", inputs.get(0).get(0));
		assertEquals(1, capture.toJson(false).getAsJsonArray(String.class.getName()).size());
	}

	@Test
	public void itemsAndNullsSerialize() {
		JsonObject json = new JsonObject();
		json.add("null_ingredient", IngredientCapture.ingredientJson(null));
		assertEquals(true, json.get("null_ingredient").isJsonNull());
	}
}
