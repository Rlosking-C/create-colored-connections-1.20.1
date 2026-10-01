package com.rlosking.createcc.mixin.factorycontroller;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rlosking.createcc.compat.VirtualConnectionDye;

import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client sync read side for GUI wire dyeing.
 *
 * <p>FC's delta sync serializes each changed wire as captured
 * {@code writeClient} bytes and decodes them with the static
 * {@code Connection.fromClient} — the single funnel every wire type flows
 * through on the client. By reading the dye byte <i>after</i> that method
 * returns (its buffer is then positioned exactly past FC's own payload,
 * where {@code LogisticsConnectionMixin} appended ours), every sync path —
 * menu open, delta upsert, full resync — picks the color up without a
 * per-subclass hook. Only ingredient wires carry the extra byte, matching
 * the write side's instanceof guard, so the buffer never desyncs.</p>
 *
 * <p><b>1.20.1 port note:</b> the buffer is a plain
 * {@link FriendlyByteBuf}, not 1.21.1's {@code RegistryFriendlyByteBuf}
 * ({@code RegistryFriendlyByteBuf} and the whole {@code StreamCodec} layer
 * arrived in 1.20.2 — see the port notes, section 8.6). FC 1.20.1 itself
 * declares both {@code writeClientExtra(FriendlyByteBuf)} and
 * {@code fromClient(FriendlyByteBuf)}, so the pair lines up exactly as on
 * 1.21.1. Also note that 1.20.1 FC implements {@code fromClient} directly on
 * {@code Connection} (no per-type {@code Type.fromClient} funnel), so this
 * single hook still covers every wire type — but it is the
 * <em>package-private</em> member here, hence {@code remap = false} is
 * required anyway (FC is a mod jar with no SRG names).</p>
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

	@ModifyReturnValue(remap = false, method = "fromClient", at = @At("RETURN"))
	private static Connection createcc$readDye(Connection original, FriendlyByteBuf buf) {
		// Non-ingredient wire types never appended a dye byte; skip them so
		// the buffer position stays consistent for any trailing data
		if (original instanceof LogisticsConnection)
			((VirtualConnectionDye) original).createcc$setDye(buf.readByte() & 0xFF);
		return original;
	}
}
