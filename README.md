# HEI Recipe Dump

Client-side only Minecraft 1.12.2 Forge mod that dumps everything
[HadEnoughItems](https://github.com/CleanroomMC/HadEnoughItems) knows about into plain JSON
files, so the data can be diffed, grepped or fed to other tools.

Requires HadEnoughItems 4.x (`jei`). Nothing is dumped until you ask for it.

## Command

| Command | What it writes |
| --- | --- |
| `/heirecipedump dump_all` | Everything: all categories, all recipes, all items, plus `index.json` |
| `/heirecipedump dump_recipes [category]` | One file per category (all categories when omitted) |
| `/heirecipedump dump_items [modid]` | One file per mod (all mods when omitted) |

`category` matches a category uid exactly, or any category whose uid or title contains the text
(pass `all` for everything). `modid` is an item namespace, e.g. `minecraft`, `thermalfoundation`.

Dumping runs on the client thread, so a big `dump_all` freezes the game for a moment and prints
progress in chat.

## Output

Written to `jei_dump/` in the game directory (the folder that holds `mods/`, `config/`, ...).

```
jei_dump/
  index.json                   manifest: HEI version, dump time, every category and mod with counts
  categories/<uid>.json        {"metainfo": {...}, "recipes": [...]}
  items/<modid>.json           {"mod_id": ..., "item_count": ..., "items": [...]}
```

Each category file carries its own metainfo: uid, title, mod name, category class, background
size, recipe count, the wrapper classes used, and the recipe catalysts (the workstations).

Recipes are dumped generically: the wrapper's own `getIngredients` is captured through a
throw-away `IIngredients` implementation and serialized by ingredient class, one list of
alternatives per slot. ItemStacks become `{"item", "mod_id", "metadata", "count", "nbt",
"display_name"}`, FluidStacks become `{"fluid", "amount", "nbt"}`, and any other ingredient type
a mod registered falls back to `{"type", "value"}`.

```
jei_dump/categories/minecraft.crafting.json
{
  "metainfo": {
    "uid": "minecraft.crafting",
    "title": "Crafting",
    "mod_name": "Minecraft",
    "recipe_count": 1437,
    ...
  },
  "recipes": [
    {
      "index": 0,
      "wrapper_class": "mezz.jei.plugins.vanilla.crafting.ShapedRecipeWrapper",
      "recipe_id": "minecraft:stone_stairs",
      "inputs": {"net.minecraft.item.ItemStack": [[{"item": "minecraft:cobblestone", ...}]]},
      "outputs": {"net.minecraft.item.ItemStack": [[{"item": "minecraft:stone_stairs", ...}]]}
    }
  ]
}
```

## Building

See the [TemplateDevEnv](https://github.com/CleanroomMC/TemplateDevEnv) instructions the workspace
is based on. `gradle/scripts/dependencies.gradle` compiles against HadEnoughItems 4.34.3; to run
the dev client, uncomment the `runtimeOnly` line there and drop MixinBooter into `run/mods`.

## License

MIT
