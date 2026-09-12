package com.weienx.heirecipedump;

import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * {@code /heirecipedump dump_all|dump_recipes [category]|dump_items [modid]}
 * <p>
 * Client-side only, registered with {@link net.minecraftforge.client.ClientCommandHandler}.
 */
public class CommandHEIRecipeDump extends CommandBase {

	public static final String NAME = "heirecipedump";
	private static final List<String> SUBCOMMANDS = Arrays.asList("dump_all", "dump_recipes", "dump_items");

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/" + NAME + " <dump_all|dump_recipes [category]|dump_items [modid]>";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		return false;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0) {
			reply(sender, getUsage(sender));
			return;
		}
		if (HEIDumpPlugin.runtime == null || HEIDumpPlugin.ingredients == null) {
			reply(sender, "HEI is not ready yet, wait until the game has fully started.");
			return;
		}

		reply(sender, "Dumping HEI data, the game may freeze while this runs...");
		JeiDumper dumper = new JeiDumper(Minecraft.getMinecraft().gameDir, HEIDumpPlugin.runtime.getRecipeRegistry(), HEIDumpPlugin.ingredients);
		Consumer<String> out = message -> sender.sendMessage(new TextComponentString(message));

		try {
			switch (args[0].toLowerCase(Locale.ROOT)) {
				case "dump_all":
					dumper.dumpAll(out);
					break;
				case "dump_recipes":
					dumper.dumpRecipes(filter(args, 1), out);
					break;
				case "dump_items":
					dumper.dumpItems(filter(args, 1), out);
					break;
				default:
					reply(sender, "Unknown subcommand '" + args[0] + "'");
					reply(sender, getUsage(sender));
					break;
			}
		} catch (Exception e) {
			HEIRecipeDump.LOGGER.error("Dump failed", e);
			throw new CommandException("Dump failed: " + e);
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
		}
		if (args.length == 2) {
			String subcommand = args[0].toLowerCase(Locale.ROOT);
			if ("dump_recipes".equals(subcommand)) {
				return getListOfStringsMatchingLastWord(args, categoryUids());
			}
			if ("dump_items".equals(subcommand)) {
				return getListOfStringsMatchingLastWord(args, modIds());
			}
		}
		return Collections.emptyList();
	}

	/** {@code null} means "no filter", the dumper then dumps everything. */
	private static String filter(String[] args, int index) {
		if (args.length <= index || args[index].isEmpty() || "all".equalsIgnoreCase(args[index])) {
			return null;
		}
		return args[index];
	}

	private static List<String> categoryUids() {
		if (HEIDumpPlugin.runtime == null) {
			return Collections.emptyList();
		}
		List<String> uids = new ArrayList<>();
		for (IRecipeCategory category : HEIDumpPlugin.runtime.getRecipeRegistry().getRecipeCategories()) {
			uids.add(category.getUid());
		}
		return uids;
	}

	private static List<String> modIds() {
		TreeSet<String> modIds = new TreeSet<>();
		for (ResourceLocation name : Item.REGISTRY.getKeys()) {
			modIds.add(name.getNamespace());
		}
		return new ArrayList<>(modIds);
	}

	private static void reply(ICommandSender sender, String message) {
		sender.sendMessage(new TextComponentString(message));
	}
}
