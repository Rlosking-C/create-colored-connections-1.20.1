package com.rlosking.createcc;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Unique identity key of a factory gauge connection.
 *
 * <p>In Create, a connection is uniquely determined by its "source panel
 * position from" pointing at "target panel position to" (mirroring the
 * key/value structure of FactoryPanelBehaviour.targetedBy). Color data is
 * persisted under this key, so colors survive even when connection objects
 * are rebuilt.</p>
 *
 * <p>Note: when a panel is relocated its position changes; the behaviour
 * Mixin is responsible for remapping or cleaning up color keys at that time.</p>
 *
 * <p><b>1.20.1 port note - two API shapes changed, not just package names:</b></p>
 * <ol>
 *   <li>{@code FactoryPanelPosition} has no {@code CODEC} and no
 *       {@code STREAM_CODEC} on 1.20.1. It exposes
 *       {@code CompoundTag write()} / {@code static read(CompoundTag)} for NBT and
 *       {@code void send(FriendlyByteBuf)} / {@code static receive(FriendlyByteBuf)}
 *       for the wire, so both forms are hand-written here.</li>
 *   <li>{@code net.minecraft.network.codec.StreamCodec} <b>does not exist</b> on
 *       1.20.1 at all - it arrived in 1.20.2. The 1.21.1 original expressed every
 *       packet through it; on 1.20.1 the codec layer is dropped entirely and
 *       entities are written to / read from a {@link FriendlyByteBuf} directly.</li>
 * </ol>
 */
public record ConnectionKey(FactoryPanelPosition from, FactoryPanelPosition to) {

	/** NBT key of the source panel position. */
	private static final String TAG_FROM = "from";
	/** NBT key of the target panel position. */
	private static final String TAG_TO = "to";

	/** Writes this key into a fresh compound tag. */
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put(TAG_FROM, from.write());
		tag.put(TAG_TO, to.write());
		return tag;
	}

	/** Reads a key back out of the tag written by {@link #save()}. */
	public static ConnectionKey load(CompoundTag tag) {
		return new ConnectionKey(
			FactoryPanelPosition.read(tag.getCompound(TAG_FROM)),
			FactoryPanelPosition.read(tag.getCompound(TAG_TO)));
	}

	/** Writes this key onto the wire. */
	public void write(FriendlyByteBuf buf) {
		from.send(buf);
		to.send(buf);
	}

	/** Reads a key back off the wire. */
	public static ConnectionKey read(FriendlyByteBuf buf) {
		return new ConnectionKey(FactoryPanelPosition.receive(buf), FactoryPanelPosition.receive(buf));
	}
}
