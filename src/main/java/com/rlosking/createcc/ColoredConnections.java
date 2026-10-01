package com.rlosking.createcc;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

/**
 * Create: Colored Connections - Forge 1.20.1 port of the NeoForge 1.21.1 original.
 *
 * <p><b>Skeleton stage.</b> This entry point exists so the build-time surface
 * (ForgeGradle, MixinGradle, the MixinExtras JiJ, the mixin config, {@code mods.toml})
 * can be verified before any Create-facing code is ported. See
 * {@code docs/port-to-1.20.1-forge.md} section 8.4 for the intended step order.</p>
 *
 * <p>Forge 1.20.1 injects neither the event bus nor the mod container into the
 * constructor, so the mod bus comes from {@link FMLJavaModLoadingContext} - the same
 * shape the sibling Kaleidoscope Flora port uses.</p>
 *
 * <p>Not ported yet (next steps):</p>
 * <ul>
 *   <li>the two configs (NeoForge {@code ModConfigSpec} to Forge {@code ForgeConfigSpec});</li>
 *   <li>the network layer - NeoForge's payload system has no 1.20.1 equivalent, so the
 *       six packet classes are rewritten onto {@code SimpleChannel} (the largest single
 *       piece of this port);</li>
 *   <li>all of {@code ConnectionColorManager}, {@code ConnectionHitTester},
 *       {@code PanelGraph} and the mixins, which need Create on the classpath.</li>
 * </ul>
 */
@Mod(ColoredConnections.MODID)
public class ColoredConnections {

    /** Mod id; must match mods.toml. */
    public static final String MODID = "create_colored_connections";

    public ColoredConnections() {
        // Forge does not inject the event bus or the mod container, so the mod bus
        // comes from FMLJavaModLoadingContext (the same shape the sibling
        // Kaleidoscope Flora port uses). Config registration goes through
        // ModLoadingContext.
        //
        // Two standard configs, both of which in-game config editors pick up
        // automatically:
        //   COMMON — server-owned gameplay / feedback flags
        //   CLIENT — this player's own rendering / tracing options
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.COMMON, CreateCCConfig.COMMON_SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.CLIENT, CreateCCConfig.CLIENT_SPEC);

        // Network channel registration has no mod-bus event on 1.20.1: Forge's
        // SimpleChannel is plain static setup, so it happens right here.
        com.rlosking.createcc.network.ColoredConnectionNetwork.register();

        // The mod-list "Configure" button. Unlike NeoForge (whose generic spec
        // editor + IConfigScreenFactory made this one line), Forge 1.20.1 needs a
        // hand-rolled screen hung off ConfigScreenHandler - without this the
        // button stays greyed out.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.rlosking.createcc.client.CreateCCConfigScreen.register();
        }
    }

    /**
     * Single helper for building resource locations in this mod's namespace.
     *
     * <p>1.20.1 has no {@code ResourceLocation.fromNamespaceAndPath}, so this is the
     * {@code new ResourceLocation(...)} form the sibling port uses throughout.</p>
     */
    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MODID, path);
    }
}
