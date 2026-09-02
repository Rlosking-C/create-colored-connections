package com.rlosking.createcc;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod configuration (COMMON config: shared file on both sides, each side
 * reads only the options it owns — the server owns gameplay/feedback flags,
 * the client owns the rendering flag).
 *
 * <p>Design intent: everything defaults to the behavior the mod shipped
 * with before a config existed. In particular {@code dyeConsumption}
 * defaults to <b>false</b> — link colors are free organizational tags, not
 * crafted products — but pack authors who want dyeing to feel like a real
 * survival cost can enable it.</p>
 */
public class CreateCCConfig {

	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	/** Consume one dye per dyeing action in survival mode (default: off) */
	public static final ModConfigSpec.BooleanValue DYE_CONSUMPTION;

	/** One-time chat hint the first time a player places a factory gauge */
	public static final ModConfigSpec.BooleanValue FIRST_HINT;

	/** Sound + dye-colored particles on successful dyeing */
	public static final ModConfigSpec.BooleanValue DYE_EFFECTS;

	/** Hover lift: raise the hovered link above its neighbors at crossings */
	public static final ModConfigSpec.BooleanValue HOVER_LIFT;

	/** Master switch for goggles tracing (client option) */
	public static final ModConfigSpec.BooleanValue GOGGLES_TRACING;

	/** Max eye-to-hit distance for a hover to start or refresh a trace (blocks, client option) */
	public static final ModConfigSpec.DoubleValue TRACE_DISTANCE;

	/** Trace lines appended to Create's goggle overlay while a trace is active (client option) */
	public static final ModConfigSpec.BooleanValue TRACE_HUD;

	static {
		BUILDER.push("gameplay");
		DYE_CONSUMPTION = BUILDER
			.comment("Consume one dye per dyeing action in survival mode",
				"(one dye per action, not per link, when path dyeing)",
				"Default false: colors are free organizational tags")
			.define("dyeConsumption", false);
		BUILDER.pop();

		BUILDER.push("feedback");
		FIRST_HINT = BUILDER
			.comment("Show a one-time chat hint the first time a player",
				"places a factory gauge (explains dyeing and path dyeing)")
			.define("firstGaugeHint", true);
		DYE_EFFECTS = BUILDER
			.comment("Play the gauge-link sound and dye-colored particles",
				"when a link is dyed")
			.define("dyeEffects", true);
		BUILDER.pop();

		BUILDER.push("rendering");
		HOVER_LIFT = BUILDER
			.comment("Lift the hovered connection line above its neighbors",
				"where links cross or overlap (sticky hover picking stays on)")
			.define("hoverLift", true);
		BUILDER.pop();

		BUILDER.push("tracing");
		GOGGLES_TRACING = BUILDER
			.comment("Goggles tracing: wearing engineer goggles and hovering a dyed",
				"link highlights its whole color group (same color, same connected",
				"factory) while every other connection line of that factory dims",
				"to gray; the highlight follows the crosshair and fades out as",
				"soon as it leaves the dyed links")
			.define("gogglesTracing", true);
		TRACE_DISTANCE = BUILDER
			.comment("Max distance (blocks) from the eye to a dyed link for",
				"hovering it to start or refresh a trace")
			.defineInRange("traceDistance", 24.0, 4.0, 64.0);
		TRACE_HUD = BUILDER
			.comment("Trace lines appended to Create's goggle overlay while tracing:",
				"group color, link and gauge counts, and the idle / running /",
				"done / failed breakdown (failed count in red)")
			.define("traceHud", true);
		BUILDER.pop();
	}

	public static final ModConfigSpec SPEC = BUILDER.build();

	private CreateCCConfig() {}
}
