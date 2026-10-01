package com.rlosking.createcc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.rlosking.createcc.network.ColoredConnectionNetwork;
import com.rlosking.createcc.network.SyncConnectionColorsPacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

/**
 * Central hub for connection color data.
 *
 * <p>Server side: persisted per dimension as {@link SavedData}
 * (Map&lt;ConnectionKey, DyeColor&gt;).</p>
 *
 * <p>Client side: a static mirror holds the current dimension's color table,
 * filled by the sync packet {@code SyncConnectionColorsPacket}. The renderer
 * reads the mirror directly with zero network overhead.</p>
 *
 * <p>Special semantics of the black dye: treated as "clear coloring, restore
 * the vanilla status color" and never stored.</p>
 *
 * <p><b>1.20.1 port notes:</b>
 * <ul>
 *   <li>{@code SavedData} has single-argument {@code save(CompoundTag)} /
 *       {@code load(CompoundTag)} here, with no {@code HolderLookup.Provider};
 *       the 1.21.1 build's codec-based {@code StoredEntry} record is replaced
 *       with plain NBT (a {@code ListTag} of compounds) because
 *       {@code ConnectionKey} no longer has a codec either.</li>
 *   <li>Broadcasts go through Forge's {@link PacketDistributor} instead of
 *       NeoForge's {@code PacketDistributor.sendToPlayersTrackingChunk}.</li>
 *   <li>The client mirror is a {@code ConcurrentHashMap}, not a {@code HashMap}:
 *       a full sync on login / dimension change replaces the whole table, and
 *       the render thread reads it concurrently. The 1.21.1 original used a
 *       plain {@code HashMap}, which a code audit flagged as a real
 *       {@code ConcurrentModificationException} risk.</li>
 * </ul></p>
 */
public class ConnectionColorManager {

	/** NBT tag holding the list of colored connections. */
	private static final String TAG_COLORS = "Colors";
	/** NBT key of one connection key inside an entry. */
	private static final String TAG_KEY = "key";
	/** NBT key of the dye's ordinal inside an entry. */
	private static final String TAG_DYE = "dye";

	/** Client mirror: color table of the current dimension (missing key = uncolored) */
	private static final Map<ConnectionKey, DyeColor> clientColors = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Queries the color of a connection (works on both sides: server reads
	 * SavedData, client reads the mirror).
	 *
	 * @return the dye; empty if uncolored
	 */
	public static Optional<DyeColor> getColor(Level level, FactoryPanelPosition from, FactoryPanelPosition to) {
		ConnectionKey key = new ConnectionKey(from, to);
		if (level.isClientSide())
			return Optional.ofNullable(clientColors.get(key));
		if (level.getServer() == null)
			return Optional.empty();
		return Optional.ofNullable(serverData(level.getServer(), level.dimension()).colors.get(key));
	}

	/**
	 * Server side: writes a color and broadcasts it to players tracking the
	 * chunk containing the target panel (the rendering side).
	 *
	 * @param dye the dye; null or black means clearing the color
	 */
	public static void setColor(ServerLevel level, FactoryPanelPosition from, FactoryPanelPosition to, DyeColor dye) {
		ConnectionColorData data = serverData(level.getServer(), level.dimension());
		ConnectionKey key = new ConnectionKey(from, to);
		int ordinal = (dye == null || dye == DyeColor.BLACK) ? -1 : dye.ordinal();
		if (ordinal < 0)
			data.colors.remove(key);
		else
			data.colors.put(key, dye);
		data.setDirty();

		broadcast(level, level.getChunkAt(to.pos()).getPos(),
			new SyncConnectionColorsPacket(List.of(new SyncConnectionColorsPacket.Entry(key, ordinal)), false));
	}

	/**
	 * Server side: writes one color to a whole batch of connections and
	 * broadcasts it as a single sync packet per tracked chunk - used by
	 * path dyeing, where one action recolors every link between two gauges.
	 *
	 * <p>A path can span several chunks; every player tracking any touched
	 * chunk receives the whole batch (a few duplicate entries on chunk
	 * boundaries beat a per-connection packet fanout).</p>
	 *
	 * @param dye the dye; null or black means clearing the colors
	 */
	public static void setColors(ServerLevel level, List<ConnectionKey> keys, DyeColor dye) {
		if (keys.isEmpty())
			return;
		ConnectionColorData data = serverData(level.getServer(), level.dimension());
		int ordinal = (dye == null || dye == DyeColor.BLACK) ? -1 : dye.ordinal();
		List<SyncConnectionColorsPacket.Entry> entries = new ArrayList<>(keys.size());
		for (ConnectionKey key : keys) {
			if (ordinal < 0)
				data.colors.remove(key);
			else
				data.colors.put(key, dye);
			entries.add(new SyncConnectionColorsPacket.Entry(key, ordinal));
		}
		data.setDirty();

		Set<ChunkPos> chunks = new HashSet<>();
		for (ConnectionKey key : keys)
			chunks.add(level.getChunkAt(key.to().pos()).getPos());
		SyncConnectionColorsPacket packet = new SyncConnectionColorsPacket(entries, false);
		for (ChunkPos chunk : chunks)
			broadcast(level, chunk, packet);
	}

	/**
	 * Server side: clears color data when a connection is disconnected,
	 * preventing key leaks in SavedData.
	 */
	public static void clearConnection(ServerLevel level, FactoryPanelPosition from, FactoryPanelPosition to) {
		ConnectionColorData data = serverData(level.getServer(), level.dimension());
		ConnectionKey key = new ConnectionKey(from, to);
		if (data.colors.remove(key) != null) {
			data.setDirty();
			broadcast(level, level.getChunkAt(to.pos()).getPos(),
				new SyncConnectionColorsPacket(List.of(new SyncConnectionColorsPacket.Entry(key, -1)), false));
		}
	}

	/**
	 * Server side: moves color data from the old key to the new key when a
	 * panel is relocated (either from or to may change), then broadcasts to
	 * players tracking the new target panel's chunk.
	 */
	public static void remap(ServerLevel level, ConnectionKey oldKey, ConnectionKey newKey) {
		ConnectionColorData data = serverData(level.getServer(), level.dimension());
		DyeColor dye = data.colors.remove(oldKey);
		if (dye == null)
			return;
		data.colors.put(newKey, dye);
		data.setDirty();
		broadcast(level, level.getChunkAt(newKey.to().pos()).getPos(),
			new SyncConnectionColorsPacket(List.of(new SyncConnectionColorsPacket.Entry(newKey, dye.ordinal())), false));
	}

	/**
	 * Server side: collects every color entry of a dimension (used by the full
	 * sync on login / dimension change and by the chunk-watch catch-up).
	 */
	public static List<SyncConnectionColorsPacket.Entry> snapshot(MinecraftServer server, ResourceKey<Level> dimension) {
		List<SyncConnectionColorsPacket.Entry> list = new ArrayList<>();
		serverData(server, dimension).colors.forEach((key, dye) ->
			list.add(new SyncConnectionColorsPacket.Entry(key, dye.ordinal())));
		return list;
	}

	/**
	 * Client side: every colored connection key of one dye - the candidate set
	 * a goggles trace filters down to the hovered link's connected factory.
	 */
	public static Set<ConnectionKey> clientKeysOfColor(DyeColor dye) {
		Set<ConnectionKey> keys = new HashSet<>();
		for (Map.Entry<ConnectionKey, DyeColor> entry : clientColors.entrySet())
			if (entry.getValue() == dye)
				keys.add(entry.getKey());
		return keys;
	}

	/**
	 * Client side: applies one sync entry (ordinal = -1 removes the entry).
	 */
	public static void clientApply(ConnectionKey key, int dyeOrdinal) {
		if (dyeOrdinal < 0)
			clientColors.remove(key);
		else
			clientColors.put(key, DyeColor.byId(dyeOrdinal));
	}

	/**
	 * Client side: replaces the whole mirror (login / dimension change).
	 */
	public static void clientReplaceAll(List<SyncConnectionColorsPacket.Entry> entries) {
		clientColors.clear();
		entries.forEach(entry -> clientApply(entry.key(), entry.dyeOrdinal()));
	}

	/**
	 * Sends a sync packet to every player tracking the given chunk.
	 *
	 * <p>Forge's equivalent of NeoForge's
	 * {@code sendToPlayersTrackingChunk}: the distributor is fed the loaded
	 * {@code LevelChunk}, which also makes the server load it if needed.</p>
	 */
	private static void broadcast(ServerLevel level, ChunkPos pos, SyncConnectionColorsPacket packet) {
		ColoredConnectionNetwork.CHANNEL.send(
			PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(pos.x, pos.z)), packet);
	}

	private static ConnectionColorData serverData(MinecraftServer server, ResourceKey<Level> dimension) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null)
			level = server.overworld();
		// 1.20.1 has no SavedData.Factory: DimensionDataStorage.computeIfAbsent
		// takes the loader Function and the create-supplier separately, then the
		// file name. (Confirmed by compiler probe, not guessed - see the port
		// notes in colored-connections/docs/port-to-1.20.1-forge.md section 8.6.)
		return level.getDataStorage().computeIfAbsent(
			ConnectionColorData::load, ConnectionColorData::new, "create_colored_connections");
	}

	/**
	 * Server-side persistence carrier: the connection color table
	 * (one SavedData per dimension).
	 */
	public static class ConnectionColorData extends SavedData {

		private final Map<ConnectionKey, DyeColor> colors = new HashMap<>();

		@Override
		public CompoundTag save(CompoundTag tag) {
			ListTag list = new ListTag();
			colors.forEach((key, dye) -> {
				CompoundTag entry = new CompoundTag();
				entry.put(TAG_KEY, key.save());
				entry.putInt(TAG_DYE, dye.ordinal());
				list.add(entry);
			});
			tag.put(TAG_COLORS, list);
			return tag;
		}

		public static ConnectionColorData load(CompoundTag tag) {
			ConnectionColorData data = new ConnectionColorData();
			ListTag list = tag.getList(TAG_COLORS, Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				data.colors.put(ConnectionKey.load(entry.getCompound(TAG_KEY)),
					DyeColor.byId(entry.getInt(TAG_DYE)));
			}
			return data;
		}
	}
}
