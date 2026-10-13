package com.rlosking.createcc.network;

import java.util.List;
import java.util.function.Supplier;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.ConnectionKey;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record SyncConnectionColorsPacket(List<Entry> entries, boolean fullSync) {

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

	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (Entry entry : entries) {
			entry.key().write(buf);
			buf.writeVarInt(entry.dyeOrdinal());
		}
		buf.writeBoolean(fullSync);
	}

	public static void handle(SyncConnectionColorsPacket payload, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		context.enqueueWork(() -> {
			if (payload.fullSync())
				ConnectionColorManager.clientReplaceAll(payload.entries());
			else
				payload.entries().forEach(entry -> ConnectionColorManager.clientApply(entry.key(), entry.dyeOrdinal()));
		});
		context.setPacketHandled(true);
	}
}
