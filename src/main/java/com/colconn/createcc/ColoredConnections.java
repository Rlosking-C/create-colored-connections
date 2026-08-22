package com.colconn.createcc;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Create: Colored Connections
 *
 * <p>Adds 16-color dyeing to the recipe-mode connection lines of Create 6.0
 * Factory Gauges, so that heavily crossed links stay distinguishable.</p>
 *
 * <p>Core design:
 * <ul>
 *   <li>Right-click a link while holding a dye to color it; black dye restores
 *       the vanilla status color</li>
 *   <li>When a new link is created, it inherits the source gauge's incoming
 *       color — only if all incoming links share one single color
 *       (inheritance happens at creation time only, no dynamic cascading)</li>
 *   <li>Idle lines (vanilla gray) are fully replaced by the dye color;
 *       status lines (active / satisfied / failed / flashing) keep the vanilla
 *       status-colored core and surround it with a 1px dye border
 *       (no color mixing, status stays readable)</li>
 *   <li>Looking at any link smoothly lifts the whole line above all others
 *       (about 0.01 blocks — only changes occlusion order at crossings,
 *       no visible displacement), which helps tell overlapping links apart</li>
 *   <li>Redstone / display link lines carry status semantics in their color
 *       and are never dyed</li>
 * </ul></p>
 *
 * <p>Code map:
 * <ul>
 *   <li>{@code client.DyeInteractionHandler} — dye right-click interaction entry (event subscriber)</li>
 *   <li>{@code ConnectionHitTester} — crosshair-to-polyline hit testing (shared by dyeing and hover lift)</li>
 *   <li>{@code ConnectionKey} / {@code ConnectionColorManager} — connection identity key and color data hub</li>
 *   <li>{@code network.*} — coloring request (C→S) and sync (S→C) packets</li>
 *   <li>{@code mixin.*} — three Mixin injections: rendering, data lifecycle, interaction canceling</li>
 * </ul></p>
 */
@Mod(ColoredConnections.MODID)
public class ColoredConnections {

	/** Mod id; must match neoforge.mods.toml */
	public static final String MODID = "create_colored_connections";

	/**
	 * Mod entry point (NeoForge 1.21.1 injects the mod event bus via constructor).
	 */
	public ColoredConnections(IEventBus modBus, ModContainer container) {
		// Payload registration is a mod-bus event: attach explicitly to avoid
		// the bus ambiguity of @EventBusSubscriber
		modBus.addListener(com.colconn.createcc.network.ColoredConnectionNetwork::register);
	}

	/**
	 * Single helper for building resource locations in this mod's namespace.
	 */
	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}
}
