package com.rlosking.createcc.network;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to Server: "a path-dye selection is now active / just ended".
 *
 * <p>The chain of swept gauges itself is pure client state - the server
 * never needs it, because the confirming click re-sends the whole node
 * list in {@link BatchColorConnectionPacket} and the server re-derives
 * every segment from its own world state. What the server DOES need is
 * the boolean: while a selection is pending, a plain dye right-click on
 * a gauge means "confirm" or "cancel" instead of vanilla's "open the
 * panel screen", and that vanilla interaction must be suppressed on the
 * server side too - the client's event cancellation never reaches it.</p>
 *
 * <p>The client sends {@code active = true} when the selection starts and
 * {@code active = false} one tick AFTER it ends. The delay is a race fix:
 * ending the selection and confirming it happen inside the same click, so
 * an immediate {@code false} would land before the click's use-item-on
 * packet and re-open the panel screen the confirm just suppressed.</p>
 */
public record BatchSelectionModePacket(boolean active) {

	public BatchSelectionModePacket(FriendlyByteBuf buf) {
		this(buf.readBoolean());
	}

	/** Writes this packet onto the wire; mirrors the constructor above. */
	public void encode(FriendlyByteBuf buf) {
		buf.writeBoolean(active);
	}

	/**
	 * Server-side mirror of "this player is mid-selection", keyed by player
	 * UUID so it survives the entity instance being replaced (dimension
	 * travel). Written only by this packet's handler; read by the
	 * interaction handler on the server thread.
	 */
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

	/** Records the mode on the server (called when the packet lands). */
	public static void setPending(ServerPlayer player, boolean active) {
		if (active)
			PENDING.add(player.getUUID());
		else
			PENDING.remove(player.getUUID());
	}

	/** Whether the player is mid-selection, per the client's last sync. */
	public static boolean isPending(ServerPlayer player) {
		return PENDING.contains(player.getUUID());
	}

	/** Drops the mirror entry (logout) so a returning player starts clean. */
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
