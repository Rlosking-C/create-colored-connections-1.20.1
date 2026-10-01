package com.rlosking.createcc.network;

import java.util.List;
import java.util.function.Supplier;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.ConnectionKey;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to Client: connection color sync packet.
 *
 * <p>Two scenarios:
 * <ul>
 *   <li>Incremental (fullSync = false): when one connection is colored or
 *       cleared, broadcast to players tracking the chunk containing the panel</li>
 *   <li>Full (fullSync = true): on player login or dimension change, clear
 *       the client mirror first and then land the whole table, so stale
 *       entries from the old dimension can't pollute the new one</li>
 * </ul></p>
 *
 * <p><b>1.20.1 port note:</b> the 1.21.1 build used
 * {@code StreamCodec.composite} plus {@code ByteBufCodecs.list()}/{@code .BOOL};
 * neither exists here, so the list and the flag are written by hand. Wire format:
 * varint count, then (key, varint ordinal) per entry, then one boolean.</p>
 */
public record SyncConnectionColorsPacket(List<Entry> entries, boolean fullSync) {

	/** One color record; dyeOrdinal = -1 means clearing that connection's color */
	public record Entry(ConnectionKey key, int dyeOrdinal) {
	}

	public SyncConnectionColorsPacket(FriendlyByteBuf buf) {
		this(readEntries(buf), buf.readBoolean());
	}

	private static List<Entry> readEntries(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<Entry> entries = new java.util.ArrayList<>(size);
		for (int i = 0; i < size; i++)
			entries.add(new Entry(ConnectionKey.read(buf), buf.readVarInt()));
		return entries;
	}

	/** Writes this packet onto the wire; must mirror the constructor above. */
	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (Entry entry : entries) {
			entry.key().write(buf);
			buf.writeVarInt(entry.dyeOrdinal());
		}
		buf.writeBoolean(fullSync);
	}

	/**
	 * Client handler: on fullSync replace the mirror wholesale, otherwise
	 * apply entries incrementally.
	 */
	public static void handle(SyncConnectionColorsPacket payload, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		context.enqueueWork(() -> {
			// Port note vs the 1.21.1 original: this ran directly in the payload
			// handler, which a code audit flagged as a thread-safety hazard - a
			// full sync could clear the mirror while the render thread iterated
			// it. enqueueWork is the 1.20.1-idiomatic fix and puts the mutation
			// on the client main thread unconditionally.
			if (payload.fullSync())
				ConnectionColorManager.clientReplaceAll(payload.entries());
			else
				payload.entries().forEach(entry -> ConnectionColorManager.clientApply(entry.key(), entry.dyeOrdinal()));
		});
		context.setPacketHandled(true);
	}
}
