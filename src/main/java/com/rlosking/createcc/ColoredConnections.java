package com.rlosking.createcc;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

@Mod(ColoredConnections.MODID)
public class ColoredConnections {

    public static final String MODID = "create_colored_connections";

    public ColoredConnections() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.COMMON, CreateCCConfig.COMMON_SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.CLIENT, CreateCCConfig.CLIENT_SPEC);

        com.rlosking.createcc.network.ColoredConnectionNetwork.register();

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.rlosking.createcc.client.CreateCCConfigScreen.register();
        }
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MODID, path);
    }
}
