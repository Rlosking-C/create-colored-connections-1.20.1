package com.rlosking.createcc.network;

import java.util.function.Supplier;

import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.compat.FactoryControllerCompat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server-side handler adapter for {@link FCColorConnectionPacket}.
 *
 * <p>Why this is its own class rather than a method on the packet: the compat
 * layer's entry point takes Factory Controller types in its body
 * ({@code FactoryControllerCompat.dyeConnection} reaches into
 * {@code FactoryControllerMenu} and friends as soon as it runs), while the
 * packet class itself is loaded by every client and by every server —
 * including the vast majority that never install Factory Controller. Keeping
 * the reference in a separate class means the packet's own class loading and
 * verification never touch the compat layer at all.</p>
 *
 * <p>Registered with {@code consumerMainThread}, exactly like the mod's other
 * three server-bound packets, so this runs on the server thread and does not
 * need {@code enqueueWork}.</p>
 *
 * <p>The config gate and the presence check are read before anything else:
 * both are plain Forge/FML calls, so they resolve without pulling in any
 * Factory Controller class, and they let the whole path disappear when a pack
 * turns the feature off or the optional mod is missing.</p>
 */
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
