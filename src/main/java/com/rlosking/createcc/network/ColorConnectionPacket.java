package com.rlosking.createcc.network;

import java.util.function.Supplier;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.DyeEffects;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record ColorConnectionPacket(FactoryPanelPosition from, FactoryPanelPosition to, int dyeOrdinal) {

	public ColorConnectionPacket(FriendlyByteBuf buf) {
		this(FactoryPanelPosition.receive(buf), FactoryPanelPosition.receive(buf), buf.readVarInt());
	}

	public void encode(FriendlyByteBuf buf) {
		from.send(buf);
		to.send(buf);
		buf.writeVarInt(dyeOrdinal);
	}

	public static void handle(ColorConnectionPacket payload, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		context.enqueueWork(() -> {
			ServerPlayer player = context.getSender();
			if (player == null)
				return;
			if (!(player.level() instanceof ServerLevel serverLevel))
				return;
			if (player.position().distanceToSqr(payload.to().pos().getCenter()) > 64 * 64)
				return;
			InteractionHand hand = heldDyeHand(player, payload.dyeOrdinal());
			if (hand == null)
				return;
			if (CreateCCConfig.commonLoaded() && CreateCCConfig.DYE_CONSUMPTION.get() && !player.isCreative())
				player.getItemInHand(hand).shrink(1);
			DyeColor dye = payload.dyeOrdinal() < 0 ? null : DyeColor.byId(payload.dyeOrdinal());
			ConnectionColorManager.setColor(serverLevel, payload.from(), payload.to(), dye);
			DyeEffects.play(serverLevel, payload.from(), payload.to(), dye);
		});
		context.setPacketHandled(true);
	}

	public static InteractionHand heldDyeHand(ServerPlayer player, int dyeOrdinal) {
		if (player.isCreative())
			return InteractionHand.MAIN_HAND;
		DyeColor expected = dyeOrdinal < 0 ? DyeColor.BLACK : DyeColor.byId(dyeOrdinal);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof DyeItem dyeItem && dyeItem.getDyeColor() == expected)
				return hand;
		}
		return null;
	}
}
