package com.rlosking.createcc;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * One-time discovery hint: the first time a player places a factory gauge,
 * a chat message explains that links can be dyed (and path-dyed).
 *
 * <p>The single biggest enemy of a small QoL mod is players never learning
 * it exists; the hint fires exactly once per player, at the moment they
 * demonstrably engage with the feature's parent system. The "seen" flag
 * lives in the player's persistent data, so it survives relogs (but not
 * death by design - a refresher after respawn is harmless).</p>
 *
 * <p><b>1.20.1 port note:</b> Forge has no top-level {@code EventBusSubscriber};
 * it is the nested {@code Mod.EventBusSubscriber}. The default {@code bus} is
 * {@code GAME}, which is what this needs, so no explicit bus is given.</p>
 */
@Mod.EventBusSubscriber(modid = ColoredConnections.MODID)
public class FirstGaugeHint {

	private static final String SEEN_FLAG = "createcc_gauge_hint_seen";

	@SubscribeEvent
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		// Server side only - the message is sent through the server so both
		// the flag and the delivery are authoritative
		if (event.getLevel().isClientSide())
			return;
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		if (!(event.getPlacedBlock().getBlock() instanceof FactoryPanelBlock))
			return;
		if (!CreateCCConfig.commonLoaded() || !CreateCCConfig.FIRST_HINT.get())
			return;

		CompoundTag data = player.getPersistentData();
		if (data.getBoolean(SEEN_FLAG))
			return;
		data.putBoolean(SEEN_FLAG, true);

		player.displayClientMessage(
			Component.translatable("message.create_colored_connections.first_hint"), false);
	}
}
