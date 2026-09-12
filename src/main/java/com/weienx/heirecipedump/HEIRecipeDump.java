package com.weienx.heirecipedump;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-side only mod that dumps everything HadEnoughItems knows about (recipe categories,
 * their recipes and all registered items) into JSON files under {@code <game dir>/jei_dump}.
 * <p>
 * Drive it with the client command {@code /heirecipedump}.
 */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION,
	clientSideOnly = true, acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]",
	dependencies = "required-after:jei")
public class HEIRecipeDump {

	public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		ClientCommandHandler.instance.registerCommand(new CommandHEIRecipeDump());
		LOGGER.info("Registered /{}", CommandHEIRecipeDump.NAME);
	}
}
