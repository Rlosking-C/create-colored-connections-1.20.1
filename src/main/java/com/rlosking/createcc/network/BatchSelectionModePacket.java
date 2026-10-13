package com.rlosking.createcc.network;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record BatchSelectionModePacket(boolean active) {

	public BatchSelectionModePacket(FriendlyByteBuf buf) {
		this(buf.readBoolean());
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBoolean(active);
	}

	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

	public static void setPending(ServerPlayer player, boolean active) {
		if (active)
			PENDING.add(player.getUUID());
		else
			PENDING.remove(player.getUUID());
	}

	public static boolean isPending(ServerPlayer player) {
		return PENDING.contains(player.getUUID());
	}

	public static void forget(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}

	public static void handle(BatchSelectionModePacket payload, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		context.enqueueWork(() -> {
			if (context.getSender() != null)
				setPending(context.getSender(), payload.active());
		});
		context.setPacketHandled(true);
	}
}
