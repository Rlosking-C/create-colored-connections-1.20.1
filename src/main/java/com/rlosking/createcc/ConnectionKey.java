package com.rlosking.createcc;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record ConnectionKey(FactoryPanelPosition from, FactoryPanelPosition to) {

	private static final String TAG_FROM = "from";
	private static final String TAG_TO = "to";

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put(TAG_FROM, from.write());
		tag.put(TAG_TO, to.write());
		return tag;
	}

	public static ConnectionKey load(CompoundTag tag) {
		return new ConnectionKey(
			FactoryPanelPosition.read(tag.getCompound(TAG_FROM)),
			FactoryPanelPosition.read(tag.getCompound(TAG_TO)));
	}

	public void write(FriendlyByteBuf buf) {
		from.send(buf);
		to.send(buf);
	}

	public static ConnectionKey read(FriendlyByteBuf buf) {
		return new ConnectionKey(FactoryPanelPosition.receive(buf), FactoryPanelPosition.receive(buf));
	}
}
