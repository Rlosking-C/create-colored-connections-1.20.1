package com.rlosking.createcc.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rlosking.createcc.ConnectionColorManager;
import com.rlosking.createcc.ConnectionKey;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;

/**
 * Server-side logic injection: color inheritance, disconnect cleanup,
 * relocation remapping.
 *
 * <p>All three injection points only run on the server (client-side block
 * entity data is synced by Create itself).</p>
 *
 * <p><b>1.20.1 port note:</b> {@code FactoryPanelBehaviour}'s
 * {@code addConnection} / {@code disconnectAll} / {@code moveTo} signatures and
 * the {@code targetedBy} / {@code targeting} fields are unchanged on this
 * platform, so the bodies are ported verbatim.</p>
 *
 * <p><b>{@code remap = false} on every injection is required here.</b> Create is
 * a separate mod, not part of Minecraft, so its methods have no SRG mapping;
 * without the flag the annotation processor emits
 * {@code Unable to locate obfuscation mapping for @Inject target addConnection}
 * for each one. This is the case §4.4.2 of the port notes describes - and it
 * applies to <em>every</em> {@code @At}/{@code method} selector aimed at a
 * Create or Factory Controller member, not just at Forge additions to vanilla
 * classes.</p>
 */
@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {

	/** Position before a panel relocation (captured at HEAD, consumed at TAIL) */
	@Unique
	private FactoryPanelPosition createcc$oldPos;

	/**
	 * After a new connection is added (TAIL only fires on the normal path that
	 * actually writes targetedBy; early returns don't pass through): if all
	 * incoming links of the source gauge share one color, the new connection
	 * inherits it.
	 *
	 * <p>Rule: any uncolored incoming link, or more than one distinct color,
	 * aborts the inheritance (keep the vanilla status color).</p>
	 */
	@Inject(method = "addConnection", at = @At("TAIL"), remap = false)
	private void createcc$inheritColor(FactoryPanelPosition fromPos, CallbackInfo ci) {
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		if (!(self.getWorld() instanceof ServerLevel serverLevel))
			return;
		FactoryPanelBehaviour source = FactoryPanelBehaviour.at(serverLevel, fromPos);
		if (source == null || source.targetedBy.isEmpty())
			return;
		DyeColor inherited = null;
		for (FactoryPanelConnection incoming : source.targetedBy.values()) {
			Optional<DyeColor> color =
				ConnectionColorManager.getColor(serverLevel, incoming.from, source.getPanelPosition());
			// An uncolored incoming link means the color is not unique, abort
			if (color.isEmpty())
				return;
			if (inherited == null)
				inherited = color.get();
			// Two distinct colors seen means not unique, abort
			else if (inherited != color.get())
				return;
		}
		if (inherited != null)
			ConnectionColorManager.setColor(serverLevel, fromPos, self.getPanelPosition(), inherited);
	}

	/**
	 * When a panel is destroyed or disabled all its connections are torn down
	 * (HEAD: targetedBy/targeting are still populated at this point), so the
	 * server-side color data is cleaned up alongside to prevent SavedData key
	 * leaks.
	 */
	@Inject(method = "disconnectAll", at = @At("HEAD"), remap = false)
	private void createcc$clearColors(CallbackInfo ci) {
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		if (!(self.getWorld() instanceof ServerLevel serverLevel))
			return;
		FactoryPanelPosition selfPos = self.getPanelPosition();
		// Incoming links: source panel to this panel
		for (FactoryPanelConnection incoming : self.targetedBy.values())
			ConnectionColorManager.clearConnection(serverLevel, incoming.from, selfPos);
		// Outgoing links: this panel to target panel
		for (FactoryPanelPosition target : self.targeting)
			ConnectionColorManager.clearConnection(serverLevel, selfPos, target);
	}

	/**
	 * Panel relocation starts: remember the old position for the TAIL remap.
	 */
	@Inject(method = "moveTo", at = @At("HEAD"), remap = false)
	private void createcc$captureOldPos(FactoryPanelPosition newPos, ServerPlayer player, CallbackInfo ci) {
		this.createcc$oldPos = ((FactoryPanelBehaviour) (Object) this).getPanelPosition();
	}

	/**
	 * Panel relocation finished: positions inside the connection keys have
	 * changed (moveTo rewrites connection.from in place and migrates the
	 * targetedBy ownership), so color data is remapped from the old keys to
	 * the new keys to survive relocation.
	 */
	@Inject(method = "moveTo", at = @At("TAIL"), remap = false)
	private void createcc$remapColors(FactoryPanelPosition newPos, ServerPlayer player, CallbackInfo ci) {
		FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
		FactoryPanelPosition oldPos = this.createcc$oldPos;
		this.createcc$oldPos = null;
		if (oldPos == null || oldPos.equals(newPos))
			return;
		if (!(self.getWorld() instanceof ServerLevel serverLevel))
			return;
		// Outgoing links: key (oldPos to target) becomes (newPos to target)
		for (FactoryPanelPosition target : self.targeting)
			ConnectionColorManager.remap(serverLevel, new ConnectionKey(oldPos, target),
				new ConnectionKey(newPos, target));
		// Incoming links: key (source to oldPos) becomes (source to newPos)
		for (FactoryPanelConnection incoming : self.targetedBy.values())
			ConnectionColorManager.remap(serverLevel, new ConnectionKey(incoming.from, oldPos),
				new ConnectionKey(incoming.from, newPos));
	}
}
