package com.rlosking.createcc.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.ConnectionKey;
import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.DyeEffects;
import com.rlosking.createcc.PanelGraph;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to Server: a player's request to dye every link along the chain of
 * gauges they swept (path dyeing).
 *
 * <p>The packet carries only the swept gauges plus the dye - never the
 * connections themselves. The server re-runs the search against its own
 * state for every consecutive pair of nodes, so a tampered client cannot
 * dye arbitrary connections: the worst a hostile packet can achieve is
 * exactly what a legitimate sweep could.</p>
 *
 * <p>Validation mirrors {@link ColorConnectionPacket}: identity, reach of
 * <b>every</b> node, and the matching dye held in hand; the optional dye
 * cost (config-gated, default off) charges one dye per action, not one
 * per link - a chain is one decision, one cost. A chain whose any segment
 * no longer resolves on the server dyes nothing at all: a partial batch
 * would silently re-color half a network and surprise the player far more
 * than a dropped click.</p>
 */
public record BatchColorConnectionPacket(List<FactoryPanelPosition> pathNodes, int dyeOrdinal) {

	/** Defensive cap on chain length; real sweeps are a handful of gauges */
	private static final int MAX_NODES = 64;

	public BatchColorConnectionPacket(FriendlyByteBuf buf) {
		this(readNodes(buf), buf.readVarInt());
	}

	private static List<FactoryPanelPosition> readNodes(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<FactoryPanelPosition> nodes = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			nodes.add(FactoryPanelPosition.receive(buf));
		return nodes;
	}

	/** Writes this packet onto the wire; mirrors the constructor above. */
	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(pathNodes.size());
		for (FactoryPanelPosition node : pathNodes)
			node.send(buf);
		buf.writeVarInt(dyeOrdinal);
	}

	/**
	 * Server handler: validate every node, re-derive each segment from
	 * server state, then persist and broadcast the whole chain as one batch.
	 */
	public static void handle(BatchColorConnectionPacket payload, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		context.enqueueWork(() -> {
			ServerPlayer player = context.getSender();
			if (player == null)
				return;
			if (!(player.level() instanceof ServerLevel serverLevel))
				return;
			List<FactoryPanelPosition> nodes = payload.pathNodes();
			if (nodes.size() < 2 || nodes.size() > MAX_NODES)
				return;
			// Reach validation on every node (the same magnitude as the
			// single-link packet; a chain may not reach farther than its nodes)
			for (FactoryPanelPosition node : nodes)
				if (player.position().distanceToSqr(node.pos().getCenter()) > 64 * 64)
					return;
			// Held-dye validation shared with the single-link packet
			InteractionHand hand = ColorConnectionPacket.heldDyeHand(player, payload.dyeOrdinal());
			if (hand == null)
				return;
			// Optional dye cost: one dye per action (survival only), never per link
			if (CreateCCConfig.commonLoaded() && CreateCCConfig.DYE_CONSUMPTION.get() && !player.isCreative())
				player.getItemInHand(hand).shrink(1);

			// Authoritative path search; untrusted packet data ends at the nodes
			List<ConnectionKey> path = new ArrayList<>();
			for (int i = 0; i + 1 < nodes.size(); i++) {
				List<ConnectionKey> segment = PanelGraph.pathBetween(serverLevel, nodes.get(i), nodes.get(i + 1));
				if (segment == null)
					return;
				path.addAll(segment);
			}
			if (path.isEmpty())
				return;

			DyeColor dye = payload.dyeOrdinal() < 0 ? null : DyeColor.byId(payload.dyeOrdinal());
			ConnectionColorManager.setColors(serverLevel, path, dye);
			DyeEffects.play(serverLevel, path, dye);
		});
		context.setPacketHandled(true);
	}
}
