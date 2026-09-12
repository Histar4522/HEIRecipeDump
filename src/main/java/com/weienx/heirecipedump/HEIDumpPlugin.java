package com.weienx.heirecipedump;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientRegistry;

/**
 * Holds on to HEI's runtime objects as soon as HEI hands them out, so the command can read them
 * long after startup. HEI instantiates this itself because of the {@link JEIPlugin} annotation.
 */
@JEIPlugin
public class HEIDumpPlugin implements IModPlugin {

	static IJeiRuntime runtime;
	static IIngredientRegistry ingredients;

	@Override
	public void register(IModRegistry registry) {
		ingredients = registry.getIngredientRegistry();
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
	}
}
