package com.rlosking.createcc.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → Server: a player's request to dye one ingredient wire inside a
 * Factory Controller GUI.
 *
 * <p>The wire is addressed the way Factory Controller's own
 * {@code RemoveConnectionPacket} addresses it — controller position, both
 * board endpoints and the connection type name — but expressed as plain
 * ints/strings so this class never imports Factory Controller types. That
 * keeps the payload registrable (and loadable) even when the mod is absent;
 * the actual application lives behind {@link FactoryControllerCompat}.</p>
 *
 * <p><b>1.20.1 port note:</b> rewritten from NeoForge's payload system
 * ({@code CustomPacketPayload} + {@code StreamCodec} + {@code IPayloadContext})
 * onto Forge's {@code SimpleChannel}, following
 * {@link ColorConnectionPacket} exactly: a {@code FriendlyByteBuf} constructor
 * for decoding, an {@code encode(FriendlyByteBuf)} method, and a handler taking
 * a {@code Supplier<NetworkEvent.Context>} that runs its work through
 * {@code enqueueWork} and then sets the packet handled. The wire format is
 * unchanged from the 1.21.1 original: controller position, both endpoints as
 * var-ints, the type name as a UTF-8 string, and the dye ordinal (-1 = clear).
 * Notably 1.20.1 has no {@code StreamCodec} at all (it arrived in 1.20.2), and
 * the six-field {@code composite} limit that forced the {@code WireEnd}
 * sub-record on 1.21.1 does not exist here — the two endpoints are simply
 * written in sequence.</p>
 */
public record FCColorConnectionPacket(BlockPos pos, int fromX, int fromY, int toX, int toY,
		String typeName, int dyeOrdinal) {

	/** Decoding constructor; mirrors {@link #encode(FriendlyByteBuf)}. */
	public FCColorConnectionPacket(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
			buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt());
	}

	/** Writes this packet onto the wire; mirrors the constructor above. */
	public void encode(FriendlyByteBuf buf) {
		buf.writeBlockPos(pos);
		buf.writeVarInt(fromX);
		buf.writeVarInt(fromY);
		buf.writeVarInt(toX);
		buf.writeVarInt(toY);
		buf.writeUtf(typeName);
		buf.writeVarInt(dyeOrdinal);
	}

	/**
	 * Server handler: hands off to {@link FCColorConnectionHandler}.
	 *
	 * <p>Like {@link ColorConnectionPacket}, failed validations drop the
	 * packet silently — the client does no optimistic feedback for GUI
	 * dyeing, so a rejected dye simply never shows up on the wire.</p>
	 *
	 * <p>This method deliberately contains no Factory Controller reference:
	 * it is registered on the channel by every client and server build, so it
	 * must stay loadable when the optional dependency is absent. The compat
	 * layer is reached only from {@link FCColorConnectionHandler}, behind its
	 * own presence check.</p>
	 */
	public static void handle(FCColorConnectionPacket payload, Supplier<NetworkEvent.Context> ctx) {
		FCColorConnectionHandler.handle(payload, ctx);
	}
}
