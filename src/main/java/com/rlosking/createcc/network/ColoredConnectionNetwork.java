package com.rlosking.createcc.network;

import java.util.ArrayList;
import java.util.List;

import com.rlosking.createcc.ColoredConnections;
import com.rlosking.createcc.ConnectionColorManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ColoredConnectionNetwork {

	private static final String PROTOCOL = "1";

	public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
		.named(new ResourceLocation(ColoredConnections.MODID, "main"))
		.clientAcceptedVersions(PROTOCOL::equals)
		.serverAcceptedVersions(PROTOCOL::equals)
		.networkProtocolVersion(() -> PROTOCOL)
		.simpleChannel();

	private ColoredConnectionNetwork() {}

	public static void register() {
		int id = 0;
		CHANNEL.messageBuilder(SyncConnectionColorsPacket.class, id++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
			.encoder(SyncConnectionColorsPacket::encode)
			.decoder(SyncConnectionColorsPacket::new)
			.consumerMainThread(SyncConnectionColorsPacket::handle)
			.add();
		CHANNEL.messageBuilder(BatchSelectionModePacket.class, id++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
			.encoder(BatchSelectionModePacket::encode)
			.decoder(BatchSelectionModePacket::new)
			.consumerMainThread(BatchSelectionModePacket::handle)
			.add();
		CHANNEL.messageBuilder(ColorConnectionPacket.class, id++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
			.encoder(ColorConnectionPacket::encode)
			.decoder(ColorConnectionPacket::new)
			.consumerMainThread(ColorConnectionPacket::handle)
			.add();
		CHANNEL.messageBuilder(BatchColorConnectionPacket.class, id++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
			.encoder(BatchColorConnectionPacket::encode)
			.decoder(BatchColorConnectionPacket::new)
			.consumerMainThread(BatchColorConnectionPacket::handle)
			.add();
		CHANNEL.messageBuilder(FCColorConnectionPacket.class, id++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
			.encoder(FCColorConnectionPacket::encode)
			.decoder(FCColorConnectionPacket::new)
			.consumerMainThread(FCColorConnectionPacket::handle)
			.add();
	}

	static void syncAll(ServerPlayer player) {
		List<SyncConnectionColorsPacket.Entry> entries =
			ConnectionColorManager.snapshot(player.server, player.level().dimension());
		CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
			new SyncConnectionColorsPacket(entries, true));
	}

	@Mod.EventBusSubscriber(modid = ColoredConnections.MODID)
	public static class GameEvents {

		@SubscribeEvent
		public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				syncAll(player);
		}

		@SubscribeEvent
		public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				BatchSelectionModePacket.forget(player);
		}

		@SubscribeEvent
		public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				syncAll(player);
		}

		@SubscribeEvent
		public static void onChunkWatch(ChunkWatchEvent.Watch event) {
			ServerPlayer player = event.getPlayer();
			ChunkPos watched = event.getPos();
			List<SyncConnectionColorsPacket.Entry> entries =
				ConnectionColorManager.snapshot(player.server, player.level().dimension());
			List<SyncConnectionColorsPacket.Entry> inChunk = new ArrayList<>();
			for (SyncConnectionColorsPacket.Entry entry : entries)
				if (new ChunkPos(entry.key().to().pos()).equals(watched))
					inChunk.add(entry);
			if (!inChunk.isEmpty())
				CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
					new SyncConnectionColorsPacket(inChunk, false));
		}
	}
}
