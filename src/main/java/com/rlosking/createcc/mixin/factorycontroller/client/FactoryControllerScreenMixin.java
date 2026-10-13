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

@Mixin(FactoryControllerScreen.class)
public abstract class FactoryControllerScreenMixin {

	private static final Logger LOGGER = LogManager.getLogger("createcc-fc");

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

	@Shadow(remap = false)
	private boolean isInCanvasArea(double x, double y) {
		throw new AssertionError();
	}

	@Shadow(remap = false)
	private Rect2i canvasArea() {
		throw new AssertionError();
	}

	@Shadow(remap = false)
	private double getZoomFactor() {
		throw new AssertionError();
	}

	@Shadow(remap = false)
	private Set<VirtualComponentPosition> occupiedCells() {
		throw new AssertionError();
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void createcc$dyeWireClick(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> cir) {
		if (button != 1 || Screen.hasShiftDown())
			return;
		if (!CreateCCConfig.FACTORY_CONTROLLER_DYEING.get())
			return;
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
		FactoryControllerMenu menu = (FactoryControllerMenu) ((FactoryControllerScreen) (Object) this).getMenu();
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
		ColoredConnectionNetwork.CHANNEL.sendToServer(new FCColorConnectionPacket(menu.controllerPos,
			conn.from.x(), conn.from.y(), conn.to.x(), conn.to.y(), conn.type.name(), ordinal));
		cir.setReturnValue(true);
	}

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
