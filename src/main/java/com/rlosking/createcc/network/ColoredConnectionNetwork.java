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

/**
 * Network-layer channel registration and color data synchronization.
 *
 * <p>Sync strategy:
 * <ul>
 *   <li>Incremental: when a connection is colored or cleared, broadcast to
 *       players tracking the chunk containing the target panel (see
 *       {@code ConnectionColorManager.setColor})</li>
 *   <li>Full: on login and dimension change the whole table is sent; the
 *       client mirror is cleared before landing</li>
 *   <li>Catch-up: when a player starts tracking a chunk, that chunk's
 *       connection colors are re-sent, covering the "colored while the player
 *       was far away, walked closer and loaded it later" scenario</li>
 * </ul></p>
 *
 * <p><b>1.20.1 port note:</b> NeoForge's payload system
 * ({@code RegisterPayloadHandlersEvent} / {@code PayloadRegistrar} /
 * {@code CustomPacketPayload} / {@code IPayloadContext}) does not exist on
 * 1.20.1, and neither does the {@code StreamCodec} abstraction the 1.21.1 build
 * expressed every packet through ({@code net.minecraft.network.codec} arrived in
 * 1.20.2). This is therefore a structural rewrite onto Forge's
 * {@link SimpleChannel}: each packet is a plain object with an
 * {@code encode(FriendlyByteBuf)} method and a {@code FriendlyByteBuf}
 * constructor, and each handler takes a {@code Supplier<NetworkEvent.Context>}
 * and runs its work via {@code enqueueWork}.</p>
 *
 * <p>Protocol semantics (fields, direction, idempotence) are carried over
 * unchanged from the 1.21.1 original.</p>
 */
public final class ColoredConnectionNetwork {

	/** Protocol version string; mismatched clients are refused by Forge. */
	private static final String PROTOCOL = "1";

	/** The mod's single channel. */
	public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
		.named(new ResourceLocation(ColoredConnections.MODID, "main"))
		.clientAcceptedVersions(PROTOCOL::equals)
		.serverAcceptedVersions(PROTOCOL::equals)
		.networkProtocolVersion(() -> PROTOCOL)
		.simpleChannel();

	private ColoredConnectionNetwork() {}

	/**
	 * Registers every packet. Called once from the mod constructor.
	 *
	 * <p>Discriminators are fixed and must never be reordered: they are the wire
	 * identity of each packet type.</p>
	 */
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
		// Optional Factory Controller integration: GUI wire dyeing. Appended as
		// id 4, never reordered - discriminators are wire identity. The packet
		// itself imports no FC types, so registering it is safe whether or not
		// the optional mod is installed; the compat layer sits behind
		// FCCompatMixinPlugin's version gate and FactoryControllerCompat.isLoaded().
		CHANNEL.messageBuilder(FCColorConnectionPacket.class, id++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
			.encoder(FCColorConnectionPacket::encode)
			.decoder(FCColorConnectionPacket::new)
			.consumerMainThread(FCColorConnectionPacket::handle)
			.add();
	}

	/**
	 * Full sync: sends every color entry of the player's dimension
	 * (the fullSync flag makes the client clear its mirror first).
	 *
	 * <p>1.20.1: a SimpleChannel message cannot be sent through
	 * {@code player.connection.send(...)} - that overload takes a vanilla
	 * {@code Packet<?>} only. Forge routes it with the {@code PLAYER} distributor.</p>
	 */
	static void syncAll(ServerPlayer player) {
		List<SyncConnectionColorsPacket.Entry> entries =
			ConnectionColorManager.snapshot(player.server, player.level().dimension());
		CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
			new SyncConnectionColorsPacket(entries, true));
	}

	/**
	 * Game-bus events: full sync on login / dimension change, incremental
	 * catch-up on chunk watch.
	 *
	 * <p><b>These are what make colors survive a world reload.</b> The client
	 * mirror is populated only by {@code SyncConnectionColorsPacket}, so without
	 * the login sync a dyed network looks uncolored again after rejoining even
	 * though the server's SavedData is intact - the per-click broadcast in
	 * {@code ConnectionColorManager.setColor} only reaches players who were
	 * already online and tracking that chunk.</p>
	 *
	 * <p>Nested class so {@code @Mod.EventBusSubscriber} uses the default GAME
	 * bus, keeping this separate from the static channel registration above.</p>
	 */
	@Mod.EventBusSubscriber(modid = ColoredConnections.MODID)
	public static class GameEvents {

		/** Player login: send the full color table of their dimension. */
		@SubscribeEvent
		public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				syncAll(player);
		}

		/**
		 * Player logout: drop the batch-selection mode mirror so a returning
		 * player starts clean (their client's "selection ended" sync may
		 * never have made it out before the disconnect).
		 */
		@SubscribeEvent
		public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				BatchSelectionModePacket.forget(player);
		}

		/**
		 * Dimension change: the client mirror is now stale (it still holds the
		 * old dimension's data), so re-send the new dimension's full table.
		 */
		@SubscribeEvent
		public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
			if (event.getEntity() instanceof ServerPlayer player)
				syncAll(player);
		}

		/**
		 * Player starts tracking a chunk: re-send the colors of connections
		 * whose target panel lives in that chunk.
		 *
		 * <p>Scenario: player B colors something while player A is far away
		 * (A doesn't track that chunk, so A missed the incremental broadcast);
		 * later A walks closer and the chunk loads - the catch-up now restores
		 * A's mirror to completeness.</p>
		 */
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
