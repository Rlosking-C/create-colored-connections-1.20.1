package com.rlosking.createcc;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Mod configuration, split into the two types Forge expects.
 *
 * <p><b>COMMON</b> ({@code config/create_colored_connections-common.toml}) holds
 * the flags that describe the <i>server-side</i> behaviour of the mod: whether
 * dyeing costs a dye, whether the first-gauge hint fires, whether dyeing plays
 * sound and particles, and whether the Factory Controller GUI accepts dye. All
 * of them are read on the server (see {@code ColorConnectionPacket},
 * {@code BatchColorConnectionPacket}, {@code FirstGaugeHint}, {@code DyeEffects},
 * {@code FactoryControllerCompat}), so a server operator owns them.</p>
 *
 * <p><b>CLIENT</b> ({@code config/create_colored_connections-client.toml}) holds
 * the options that only ever affect rendering on one player's own screen: the
 * hover lift, goggles tracing and its reach distance and overlay readout.</p>
 *
 * <p>Design intent, unchanged by the port: everything defaults to the behaviour
 * the mod shipped with before a config existed. In particular
 * {@code dyeConsumption} defaults to <b>false</b> — link colors are free
 * organizational tags, not crafted products.</p>
 *
 * <p><b>1.20.1 port note:</b> the specs are {@link ForgeConfigSpec} rather than
 * NeoForge's {@code ModConfigSpec}, which is a drop-in rename for every method
 * used here. Option <em>names</em> and the file names are deliberately identical
 * to the 1.21.1 build so a player moving between the two platforms keeps their
 * settings; see the port notes for the {@code translation(...)} caveat.</p>
 */
public class CreateCCConfig {

	/**
	 * Translation key prefix for in-game config editors.
	 *
	 * <p>Kept identical to the 1.21.1 build so the existing nine language files
	 * keep working. On NeoForge the prefix is fed to {@code Builder.translation(...)}
	 * so editors such as Configured can show a localized name; Forge 1.20.1 has no
	 * such builder method (it was added later), so the keys live only in the
	 * language files and are looked up by those editors themselves. The string is
	 * therefore retained as the documented contract rather than dropped.</p>
	 */
	static final String CONFIG_LANG = "create_colored_connections.configuration.";

	private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
	private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

	// ------------------------------------------------------------------
	// COMMON — server-side gameplay and feedback
	// ------------------------------------------------------------------

	/** Consume one dye per dyeing action in survival mode (default: off) */
	public static final ForgeConfigSpec.BooleanValue DYE_CONSUMPTION;

	/** Dye ingredient wires inside the Factory Controller GUI (compat option) */
	public static final ForgeConfigSpec.BooleanValue FACTORY_CONTROLLER_DYEING;

	/** One-time chat hint the first time a player places a factory gauge */
	public static final ForgeConfigSpec.BooleanValue FIRST_HINT;

	/** Sound + dye-colored particles on successful dyeing */
	public static final ForgeConfigSpec.BooleanValue DYE_EFFECTS;

	// ------------------------------------------------------------------
	// CLIENT — this player's own rendering and tracing
	// ------------------------------------------------------------------

	/** Hover lift: raise the hovered link above its neighbors at crossings */
	public static final ForgeConfigSpec.BooleanValue HOVER_LIFT;

	/** Master switch for goggles tracing (client option) */
	public static final ForgeConfigSpec.BooleanValue GOGGLES_TRACING;

	/** Max eye-to-hit distance for a hover to start or refresh a trace (blocks, client option) */
	public static final ForgeConfigSpec.DoubleValue TRACE_DISTANCE;

	/** Trace lines appended to Create's goggle overlay while a trace is active (client option) */
	public static final ForgeConfigSpec.BooleanValue TRACE_HUD;

	static {
		COMMON_BUILDER.push("gameplay");
		DYE_CONSUMPTION = COMMON_BUILDER
			.comment("Consume one dye per dyeing action in survival mode",
				"(one dye per action, not per link, when path dyeing)",
				"Default false: colors are free organizational tags")
			.define("dyeConsumption", false);
		COMMON_BUILDER.pop();

		COMMON_BUILDER.push("feedback");
		FIRST_HINT = COMMON_BUILDER
			.comment("Show a one-time chat hint the first time a player",
				"places a factory gauge (explains dyeing and path dyeing)")
			.define("firstGaugeHint", true);
		FACTORY_CONTROLLER_DYEING = COMMON_BUILDER
			.comment("Dye ingredient wires inside the Factory Controller GUI:",
				"pick a dye up onto the cursor and right-click the wire",
				"(a dye held in the main hand also works)",
				"(Create: Factory Controller integration; no effect without the mod)")
			.define("factoryControllerDyeing", true);
		DYE_EFFECTS = COMMON_BUILDER
			.comment("Play the gauge-link sound and dye-colored particles",
				"when a link is dyed")
			.define("dyeEffects", true);
		COMMON_BUILDER.pop();

		CLIENT_BUILDER.push("rendering");
		HOVER_LIFT = CLIENT_BUILDER
			.comment("Lift the hovered connection line above its neighbors",
				"where links cross or overlap (sticky hover picking stays on)",
				"Client-side: only changes what this player sees")
			.define("hoverLift", true);
		CLIENT_BUILDER.pop();

		CLIENT_BUILDER.push("tracing");
		GOGGLES_TRACING = CLIENT_BUILDER
			.comment("Goggles tracing: wearing engineer goggles and hovering a dyed",
				"link highlights its whole color group (same color, same connected",
				"factory) while every other connection line of that factory dims",
				"to gray; the highlight follows the crosshair and fades out as",
				"soon as it leaves the dyed links")
			.define("gogglesTracing", true);
		TRACE_DISTANCE = CLIENT_BUILDER
			.comment("Max distance (blocks) from the eye to a dyed link for",
				"hovering it to start or refresh a trace")
			.defineInRange("traceDistance", 24.0, 4.0, 64.0);
		TRACE_HUD = CLIENT_BUILDER
			.comment("Trace lines appended to Create's goggle overlay while tracing:",
				"group color, link and gauge counts, and the idle / running /",
				"done / failed breakdown (failed count in red)")
			.define("traceHud", true);
		CLIENT_BUILDER.pop();
	}

	/** Server-side options — read on both sides, owned by the server. */
	public static final ForgeConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

	/** Client-only options — never read on a dedicated server. */
	public static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

	private CreateCCConfig() {}

	/**
	 * Whether the COMMON spec has been loaded.
	 *
	 * <p>Needed because an effect supplier can be invoked at registration time,
	 * before the config file is read; reading a value before then throws.</p>
	 */
	public static boolean commonLoaded() {
		return COMMON_SPEC.isLoaded();
	}

	/** Whether the CLIENT spec has been loaded (client-only callers). */
	public static boolean clientLoaded() {
		return CLIENT_SPEC.isLoaded();
	}
}
