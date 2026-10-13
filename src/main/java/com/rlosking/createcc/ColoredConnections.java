package com.rlosking.createcc;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ColoredConnections.MODID)
public class ColoredConnections {

	public static final String MODID = "create_colored_connections";

	public ColoredConnections(IEventBus modBus, ModContainer container) {
		modBus.addListener(com.rlosking.createcc.network.ColoredConnectionNetwork::register);
		container.registerConfig(ModConfig.Type.COMMON, CreateCCConfig.COMMON_SPEC);
		container.registerConfig(ModConfig.Type.CLIENT, CreateCCConfig.CLIENT_SPEC);
	}

	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}
}
