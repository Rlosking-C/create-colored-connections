package com.rlosking.createcc;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod configuration, split into the two types NeoForge expects.
 *
 * <p><b>COMMON</b> ({@code config/create_colored_connections-common.toml}) holds
 * the flags that describe the <i>server-side</i> behaviour of the mod: whether
 * dyeing costs a dye, whether the first-gauge hint fires, whether dyeing plays
 * sound and particles, and whether the Factory Controller GUI accepts dye. All
 * four are read on the server (see {@code ColorConnectionPacket},
 * {@code BatchColorConnectionPacket}, {@code FirstGaugeHint}, {@code DyeEffects},
 * {@code FactoryControllerCompat}), so a server operator owns them.</p>
 *
 * <p><b>CLIENT</b> ({@code config/create_colored_connections-client.toml}) holds
 * the four options that only ever affect rendering on one player's own screen:
 * the hover lift, goggles tracing and its reach distance and overlay readout.
 * They are read exclusively from client-only classes ({@code GogglesTracing},
 * {@code FactoryPanelRendererMixin}, {@code GoggleOverlayRendererMixin}), so
 * keeping them out of COMMON means a client can tune them on any server and a
 * server's config file no longer carries rendering switches.</p>
 *
 * <p>Both specs are standard NeoForge {@link ModConfigSpec}s, which is what
 * in-game config editors such as Configured surface automatically — Configured
 * maps {@code COMMON} to its "universal" bucket and {@code CLIENT} to its
 * client-only bucket, and writes both back to the local file on save.</p>
 *
 * <p>Design intent, unchanged by the split: everything defaults to the
 * behaviour the mod shipped with before a config existed. In particular
 * {@code dyeConsumption} defaults to <b>false</b> — link colors are free
 * organizational tags, not crafted products — but pack authors who want
 * dyeing to feel like a real survival cost can enable it.</p>
 */
public class CreateCCConfig {

	/**
	 * Translation key prefix for in-game config editors.
	 *
	 * <p>NeoForge only hands a translation key to such editors when it is set
	 * explicitly with {@code Builder.translation(...)} — the default is null,
	 * which is why the config UI used to show the raw option names
	 * ({@code dyeConsumption}) and the English comments in every language.
	 * Configured looks up the key itself for an option's name and
	 * {@code <key>.tooltip} for its comment, and the level key set right
	 * before a {@code push(...)} for a category's name, so all three shapes
	 * below are needed. Setting one does not leak into the next entry: the
	 * builder drops its context after every define and push.</p>
	 */
	private static final String CONFIG_LANG = "create_colored_connections.configuration.";

	private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
	private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

	// ------------------------------------------------------------------
	// COMMON — server-side gameplay and feedback
	// ------------------------------------------------------------------

	/** Consume one dye per dyeing action in survival mode (default: off) */
	public static final ModConfigSpec.BooleanValue DYE_CONSUMPTION;

	/** Dye ingredient wires inside the Factory Controller GUI (compat option) */
	public static final ModConfigSpec.BooleanValue FACTORY_CONTROLLER_DYEING;

	/** One-time chat hint the first time a player places a factory gauge */
	public static final ModConfigSpec.BooleanValue FIRST_HINT;

	/** Sound + dye-colored particles on successful dyeing */
	public static final ModConfigSpec.BooleanValue DYE_EFFECTS;

	// ------------------------------------------------------------------
	// CLIENT — this player's own rendering and tracing
	// ------------------------------------------------------------------

	/** Hover lift: raise the hovered link above its neighbors at crossings */
	public static final ModConfigSpec.BooleanValue HOVER_LIFT;

	/** Master switch for goggles tracing (client option) */
	public static final ModConfigSpec.BooleanValue GOGGLES_TRACING;

	/** Max eye-to-hit distance for a hover to start or refresh a trace (blocks, client option) */
	public static final ModConfigSpec.DoubleValue TRACE_DISTANCE;

	/** Trace lines appended to Create's goggle overlay while a trace is active (client option) */
	public static final ModConfigSpec.BooleanValue TRACE_HUD;

	static {
		// Every category carries its own key (set before push, where the
		// builder consumes it) and every option carries a key plus <key>.tooltip
		COMMON_BUILDER.translation(CONFIG_LANG + "gameplay").push("gameplay");
		DYE_CONSUMPTION = COMMON_BUILDER
			.translation(CONFIG_LANG + "gameplay.dyeConsumption")
			.comment("Consume one dye per dyeing action in survival mode",
				"(one dye per action, not per link, when path dyeing)",
				"Default false: colors are free organizational tags")
			.define("dyeConsumption", false);
		COMMON_BUILDER.pop();

		COMMON_BUILDER.translation(CONFIG_LANG + "feedback").push("feedback");
		FIRST_HINT = COMMON_BUILDER
			.translation(CONFIG_LANG + "feedback.firstGaugeHint")
			.comment("Show a one-time chat hint the first time a player",
				"places a factory gauge (explains dyeing and path dyeing)")
			.define("firstGaugeHint", true);
		FACTORY_CONTROLLER_DYEING = COMMON_BUILDER
			.translation(CONFIG_LANG + "feedback.factoryControllerDyeing")
			.comment("Dye ingredient wires inside the Factory Controller GUI:",
				"pick a dye up onto the cursor and right-click the wire",
				"(a dye held in the main hand also works)",
				"(Create: Factory Controller integration; no effect without the mod)")
			.define("factoryControllerDyeing", true);
		DYE_EFFECTS = COMMON_BUILDER
			.translation(CONFIG_LANG + "feedback.dyeEffects")
			.comment("Play the gauge-link sound and dye-colored particles",
				"when a link is dyed")
			.define("dyeEffects", true);
		COMMON_BUILDER.pop();

		CLIENT_BUILDER.translation(CONFIG_LANG + "rendering").push("rendering");
		HOVER_LIFT = CLIENT_BUILDER
			.translation(CONFIG_LANG + "rendering.hoverLift")
			.comment("Lift the hovered connection line above its neighbors",
				"where links cross or overlap (sticky hover picking stays on)",
				"Client-side: only changes what this player sees")
			.define("hoverLift", true);
		CLIENT_BUILDER.pop();

		CLIENT_BUILDER.translation(CONFIG_LANG + "tracing").push("tracing");
		GOGGLES_TRACING = CLIENT_BUILDER
			.translation(CONFIG_LANG + "tracing.gogglesTracing")
			.comment("Goggles tracing: wearing engineer goggles and hovering a dyed",
				"link highlights its whole color group (same color, same connected",
				"factory) while every other connection line of that factory dims",
				"to gray; the highlight follows the crosshair and fades out as",
				"soon as it leaves the dyed links")
			.define("gogglesTracing", true);
		TRACE_DISTANCE = CLIENT_BUILDER
			.translation(CONFIG_LANG + "tracing.traceDistance")
			.comment("Max distance (blocks) from the eye to a dyed link for",
				"hovering it to start or refresh a trace")
			.defineInRange("traceDistance", 24.0, 4.0, 64.0);
		TRACE_HUD = CLIENT_BUILDER
			.translation(CONFIG_LANG + "tracing.traceHud")
			.comment("Trace lines appended to Create's goggle overlay while tracing:",
				"group color, link and gauge counts, and the idle / running /",
				"done / failed breakdown (failed count in red)")
			.define("traceHud", true);
		CLIENT_BUILDER.pop();
	}

	/** Server-side options — read on both sides, owned by the server. */
	public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

	/** Client-only options — never read on a dedicated server. */
	public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

	private CreateCCConfig() {}
}
