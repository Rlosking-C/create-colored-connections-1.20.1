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

public record BatchColorConnectionPacket(List<FactoryPanelPosition> pathNodes, int dyeOrdinal) {

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

	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(pathNodes.size());
		for (FactoryPanelPosition node : pathNodes)
			node.send(buf);
		buf.writeVarInt(dyeOrdinal);
	}

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
			for (FactoryPanelPosition node : nodes)
				if (player.position().distanceToSqr(node.pos().getCenter()) > 64 * 64)
					return;
			InteractionHand hand = ColorConnectionPacket.heldDyeHand(player, payload.dyeOrdinal());
			if (hand == null)
				return;
			if (CreateCCConfig.commonLoaded() && CreateCCConfig.DYE_CONSUMPTION.get() && !player.isCreative())
				player.getItemInHand(hand).shrink(1);

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
