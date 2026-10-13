package com.rlosking.createcc.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record FCColorConnectionPacket(BlockPos pos, int fromX, int fromY, int toX, int toY,
		String typeName, int dyeOrdinal) {

	public FCColorConnectionPacket(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
			buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt());
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBlockPos(pos);
		buf.writeVarInt(fromX);
		buf.writeVarInt(fromY);
		buf.writeVarInt(toX);
		buf.writeVarInt(toY);
		buf.writeUtf(typeName);
		buf.writeVarInt(dyeOrdinal);
	}

	public static void handle(FCColorConnectionPacket payload, Supplier<NetworkEvent.Context> ctx) {
		FCColorConnectionHandler.handle(payload, ctx);
	}
}
