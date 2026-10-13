package com.rlosking.createcc.network;

import java.util.function.Supplier;

import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.compat.FactoryControllerCompat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class FCColorConnectionHandler {

	private FCColorConnectionHandler() {}

	static void handle(FCColorConnectionPacket payload, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		ServerPlayer player = context.getSender();
		if (player != null
				&& CreateCCConfig.commonLoaded() && CreateCCConfig.FACTORY_CONTROLLER_DYEING.get()
				&& FactoryControllerCompat.isLoaded())
			FactoryControllerCompat.dyeConnection(payload, player);
		context.setPacketHandled(true);
	}
}
