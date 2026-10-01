package com.rlosking.createcc.mixin.factorycontroller.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.network.ColoredConnectionNetwork;
import com.rlosking.createcc.network.FCColorConnectionPacket;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintPlacement;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ConnectionPathResolver;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states.ConnectionModeState;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ConnectionWidget;
import io.github.nbcss.createfactorycontroller.content.helper.Rect2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GUI dyeing interaction in the Factory Controller board screen.
 *
 * <p>Right-click an ingredient wire with a dye — on the cursor or in the main
 * hand — and the wire is dyed; black clears it. Nothing is consumed unless the
 * {@code dyeConsumption} config asks for it.</p>
 *
 * <p>This class is the whole screen-side integration: a single
 * {@code mouseClicked} hook plus the hit test that finds the wire under the
 * cursor. The visual half lives in {@link ConnectionWidgetMixin}.</p>
 *
 * <p><b>How the wire is located.</b> Factory Controller suppresses connection
 * hover while the cursor carries an item, so its own {@code hoveredConn} is null
 * exactly when the player is aiming a dye — it cannot serve as the click target.
 * Instead the screen point is converted to a canvas point with FC's own transform
 * and hit-tested against the path of every ingredient wire, mirroring FC's
 * {@code buildConnectionWidgets}.</p>
 *
 * <p>Two alternatives were tried and do not work, which is why the hit test is
 * written out here rather than shared:</p>
 * <ul>
 *   <li><b>Neutering FC's {@code getCarried()} to keep its hover alive</b> is not
 *       portable. That member belongs to vanilla
 *       {@code AbstractContainerMenu} and is merely inherited by FC's menu: with
 *       {@code remap = false} the injection matches only in a dev run, with the
 *       default {@code remap} only in production, and naming
 *       {@code AbstractContainerMenu} as the owner satisfies neither. The
 *       annotation processor cannot resolve a vanilla member reached through a mod
 *       class, so no single annotation covers both name spaces.</li>
 *   <li><b>Sharing the hit test with the widget mixin</b> is impossible. A
 *       {@code public static} helper on a mixin is rejected outright; an
 *       {@code @Implements} accessor interface never reaches the target (Factory
 *       Controller is a separate JPMS module) and the cast throws
 *       {@code ClassCastException}; and referencing the other mixin's class throws
 *       {@code NoClassDefFoundError: ... is invalid}, because a mixin class is
 *       invalidated once it has been applied.</li>
 * </ul>
 *
 * <p><b>On {@code remap}:</b> {@code mouseClicked} below is this project's only
 * annotation targeting a vanilla member, and it keeps the default remapping on
 * purpose. Everything else targets Factory Controller members, whose names are
 * identical in a dev run and a production install because mod jars are not
 * remapped, so those carry {@code remap = false}.</p>
 */
@Mixin(FactoryControllerScreen.class)
public abstract class FactoryControllerScreenMixin {

	private static final Logger LOGGER = LogManager.getLogger("createcc-fc");

	/** Canvas scroll offset; FC's {@code worldAt} adds exactly this back. */
	@Shadow(remap = false)
	private double viewX;

	@Shadow(remap = false)
	private double viewY;

	@Shadow(remap = false)
	@Nullable
	private BlueprintPlacement pendingPlacement;

	@Shadow(remap = false)
	@Nullable
	private EditBox nameBox;

	@Shadow(remap = false)
	@Nullable
	private VirtualComponentPosition pendingRelocateTarget;

	@Shadow(remap = false)
	@Final
	private ConnectionModeState connectionMode;

	/**
	 * Whether a <em>screen</em> point is inside the zoomed canvas.
	 *
	 * <p>FC's {@code canvasArea()} is in screen space — it is built from
	 * {@code leftPos/topPos/imageWidth/imageHeight}, and its centre is the pivot
	 * both view transforms are built around — so this predicate, not those bounds
	 * directly, is the right gate for {@code mouseClicked} coordinates.</p>
	 */
	@Shadow(remap = false)
	private boolean isInCanvasArea(double x, double y) {
		throw new AssertionError();
	}

	/** FC's zoomed viewport rectangle, in <b>screen</b> space. */
	@Shadow(remap = false)
	private Rect2i canvasArea() {
		throw new AssertionError();
	}

	/** Canvas pixels per screen pixel at the current zoom. */
	@Shadow(remap = false)
	private double getZoomFactor() {
		throw new AssertionError();
	}

	/** Board cells occupied by components — the obstacle set the path resolver needs. */
	@Shadow(remap = false)
	private Set<VirtualComponentPosition> occupiedCells() {
		throw new AssertionError();
	}

	/**
	 * Right-click on the ingredient wire under the cursor: send the dye request.
	 *
	 * <p><b>{@code remap} is deliberately left at its default.</b>
	 * {@code mouseClicked} is inherited from {@code AbstractContainerScreen}, so it
	 * is official {@code mouseClicked} in a dev run and {@code m_6375_} in a
	 * production install; only a refmap entry bridges that, and the annotation
	 * processor writes one only for a remappable reference — which
	 * {@code remap = false} is precisely what prevents. (With it set, production
	 * died at startup with {@code could not find any targets matching
	 * 'mouseClicked' ... No refMap loaded}.) Being a member of a <em>vanilla</em>
	 * class is what the processor can resolve here, unlike the inherited menu member
	 * described in the class note.</p>
	 */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void createcc$dyeWireClick(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> cir) {
		// Plain right click only (same gesture as world dyeing); shift+click is FC's wire removal.
		// Screen.hasShiftDown() is static because this mixin deliberately does not
		// extend AbstractContainerScreen: Mixin merges the mixin into the target, so
		// every superclass would need a no-arg constructor, which
		// AbstractContainerScreen does not have.
		if (button != 1 || Screen.hasShiftDown())
			return;
		if (!CreateCCConfig.FACTORY_CONTROLLER_DYEING.get())
			return;
		// Never steal clicks from FC's own modal interactions
		if (this.pendingPlacement != null || this.connectionMode.isActive() || this.pendingRelocateTarget != null)
			return;
		if (this.nameBox != null && this.nameBox.isFocused())
			return;
		if (!isInCanvasArea(mouseX, mouseY))
			return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null)
			return;
		ConnectionWidget wire = createcc$wireAt(mouseX, mouseY);
		if (wire == null)
			return;
		// The container menu of the screen this mixin is merged into, reached
		// through the target's own public API: a @Shadow for the `menu` field is
		// impossible, because that field belongs to AbstractContainerScreen and the
		// annotation processor cannot resolve an inherited field through a mod class
		// ("Cannot find target for @Shadow field").
		FactoryControllerMenu menu = (FactoryControllerMenu) ((FactoryControllerScreen) (Object) this).getMenu();
		// Dye source, GUI-first: a dye picked up onto the cursor is the intended
		// flow, a main-hand dye still works like world dyeing. Black clears. The dye
		// stays where it is; the server may charge it only under the dyeConsumption
		// config.
		DyeColor color = null;
		if (menu.getCarried().getItem() instanceof DyeItem carriedDye)
			color = carriedDye.getDyeColor();
		else if (player.getMainHandItem().getItem() instanceof DyeItem handDye)
			color = handDye.getDyeColor();
		if (color == null)
			return;
		int ordinal = color == DyeColor.BLACK ? -1 : color.ordinal();
		Connection conn = wire.connection;
		LOGGER.debug("[createcc-fc] sending dye packet: {} from=({},{}) to=({},{}) controller={}",
			color, conn.from.x(), conn.from.y(), conn.to.x(), conn.to.y(), menu.controllerPos);
		// 1.20.1: a SimpleChannel message cannot go through
		// player.connection.send(...) (that overload takes a vanilla Packet<?>
		// only) — Forge routes it with the PLAYER distributor.
		ColoredConnectionNetwork.CHANNEL.sendToServer(new FCColorConnectionPacket(menu.controllerPos,
			conn.from.x(), conn.from.y(), conn.to.x(), conn.to.y(), conn.type.name(), ordinal));
		// No optimistic client feedback: the server's delta sync recolors the wire
		// for every viewer, and a rejected dye simply never appears
		cir.setReturnValue(true);
	}

	/**
	 * The ingredient wire under a screen point, or null. This is FC's own hover
	 * derivation lifted out of {@code renderBoard}: screen point to canvas point
	 * with FC's transform, then hit-test each wire's path.
	 *
	 * <p>Redstone and number wires are skipped — they carry status semantics in
	 * their colour and are never dyed, exactly like redstone/display links in the
	 * world. Wires are tested in reverse insertion order so the one drawn on top
	 * wins, matching how a stack of overlapping wires looks.</p>
	 *
	 * <p>FC's transform is {@code canvas = view + (screen - canvasCentre) / zoom},
	 * the centre coming from {@code canvasArea()}; that matches FC's own source,
	 * which computes {@code worldMouse} the same way. The member walk mirrors FC's
	 * {@code buildConnectionWidgets} because the menu's connection graph is private
	 * with no accessor.</p>
	 */
	@Nullable
	private ConnectionWidget createcc$wireAt(double screenX, double screenY) {
		Rect2i canvas = canvasArea();
		int centerX = canvas.minX() + canvas.w() / 2;
		int centerY = canvas.minY() + canvas.h() / 2;
		double zoom = getZoomFactor();
		Vector2d canvasPoint = new Vector2d(
			viewX + (screenX - centerX) / zoom,
			viewY + (screenY - centerY) / zoom);

		Set<VirtualComponentPosition> occupied = occupiedCells();
		FactoryControllerMenu menu = (FactoryControllerMenu) ((FactoryControllerScreen) (Object) this).getMenu();
		List<Connection> connections = new ArrayList<>();
		for (VirtualComponentBehaviour component : menu.components)
			connections.addAll(component.incomingConnections());
		for (int i = connections.size() - 1; i >= 0; i--) {
			Connection conn = connections.get(i);
			if (!(conn instanceof LogisticsConnection))
				continue;
			List<Vector2i> path = ConnectionPathResolver.resolvePath(conn, occupied);
			if (path == null || path.isEmpty())
				continue;
			if (new ConnectionWidget(conn, path).hitTest(canvasPoint.x, canvasPoint.y))
				return new ConnectionWidget(conn, path);
		}
		return null;
	}
}
