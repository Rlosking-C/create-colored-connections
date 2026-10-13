package com.rlosking.createcc;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CreateCCConfig {

	private static final String CONFIG_LANG = "create_colored_connections.configuration.";

	private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
	private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

	public static final ModConfigSpec.BooleanValue DYE_CONSUMPTION;

	public static final ModConfigSpec.BooleanValue FACTORY_CONTROLLER_DYEING;


	public static final ModConfigSpec.BooleanValue DYE_EFFECTS;

	public static final ModConfigSpec.BooleanValue HOVER_LIFT;

	public static final ModConfigSpec.BooleanValue GOGGLES_TRACING;

	public static final ModConfigSpec.DoubleValue TRACE_DISTANCE;

	public static final ModConfigSpec.BooleanValue TRACE_HUD;

	static {
		COMMON_BUILDER.translation(CONFIG_LANG + "gameplay").push("gameplay");
		DYE_CONSUMPTION = COMMON_BUILDER
			.translation(CONFIG_LANG + "gameplay.dyeConsumption")
			.comment("Consume one dye per dyeing action in survival mode",
				"(one dye per action, not per link, when path dyeing)",
				"Default false: colors are free organizational tags")
			.define("dyeConsumption", false);
		COMMON_BUILDER.pop();

		COMMON_BUILDER.translation(CONFIG_LANG + "feedback").push("feedback");
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

	public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

	public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

	private CreateCCConfig() {}
}
