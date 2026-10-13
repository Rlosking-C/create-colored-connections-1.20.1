package com.rlosking.createcc.client;

import java.util.ArrayList;
import java.util.List;

import com.rlosking.createcc.CreateCCConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;

public class CreateCCConfigScreen extends Screen {

	private static final int WIDGET_WIDTH = 150;
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW_STEP = 40;
	private static final int LABEL_RISE = 13;
	private static final int COLUMN_GAP = 30;

	public static void register() {
		ModLoadingContext.get().registerExtensionPoint(
			ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new ConfigScreenHandler.ConfigScreenFactory(
				(client, modsScreen) -> new CreateCCConfigScreen(modsScreen)));
	}

	private record Row(Component label, int centerX, Button button, EditBox box) {
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();

	public CreateCCConfigScreen(Screen parent) {
		super(Component.translatable("create_colored_connections.configuration.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rows.clear();

		List<ForgeConfigSpec.BooleanValue> bools = List.of(
			CreateCCConfig.DYE_CONSUMPTION,
			CreateCCConfig.FIRST_HINT,
			CreateCCConfig.FACTORY_CONTROLLER_DYEING,
			CreateCCConfig.DYE_EFFECTS,
			CreateCCConfig.HOVER_LIFT,
			CreateCCConfig.GOGGLES_TRACING,
			CreateCCConfig.TRACE_HUD);
		List<String> boolKeys = List.of(
			"gameplay.dyeConsumption",
			"feedback.firstGaugeHint",
			"feedback.factoryControllerDyeing",
			"feedback.dyeEffects",
			"rendering.hoverLift",
			"tracing.gogglesTracing",
			"tracing.traceHud");

		int leftX = this.width / 2 - WIDGET_WIDTH - COLUMN_GAP / 2;
		int rightX = this.width / 2 + COLUMN_GAP / 2;
		int rowsPerColumn = 4;
		int total = rowsPerColumn * ROW_STEP + 44;
		int top = Math.max(36, (this.height - total) / 2 + 6);

		for (int i = 0; i < bools.size(); i++) {
			ForgeConfigSpec.BooleanValue value = bools.get(i);
			Component label = Component.translatable(
				"create_colored_connections.configuration." + boolKeys.get(i));
			int col = i / rowsPerColumn;
			int row = i % rowsPerColumn;
			int x = col == 0 ? leftX : rightX;
			int y = top + row * ROW_STEP;
			Button button = Button.builder(toggleLabel(value), b -> {
				value.set(!value.get());
				b.setMessage(toggleLabel(value));
			}).bounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT).build();
			this.addRenderableWidget(button);
			rows.add(new Row(label, x + WIDGET_WIDTH / 2, button, null));
		}

		Component distanceLabel =
			Component.translatable("create_colored_connections.configuration.tracing.traceDistance");
		int distanceY = top + (rowsPerColumn - 1) * ROW_STEP;
		EditBox box = new EditBox(this.font, rightX, distanceY, WIDGET_WIDTH, WIDGET_HEIGHT, distanceLabel);
		box.setValue(String.valueOf(CreateCCConfig.TRACE_DISTANCE.get()));
		this.addRenderableWidget(box);
		rows.add(new Row(distanceLabel, rightX + WIDGET_WIDTH / 2, null, box));

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
			.bounds(this.width / 2 - 100, top + rowsPerColumn * ROW_STEP + 8, 200, WIDGET_HEIGHT).build());
	}

	private static Component toggleLabel(ForgeConfigSpec.BooleanValue value) {
		return CommonComponents.optionStatus(value.get());
	}

	@Override
	public void onClose() {
		for (Row row : rows) {
			if (row.box() == null)
				continue;
			try {
				double parsed = Double.parseDouble(row.box().getValue().trim());
				CreateCCConfig.TRACE_DISTANCE.set(Math.max(4.0, Math.min(64.0, parsed)));
			} catch (NumberFormatException ignored) {
			}
		}
		CreateCCConfig.COMMON_SPEC.save();
		CreateCCConfig.CLIENT_SPEC.save();
		if (this.minecraft != null)
			this.minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);

		graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

		for (Row row : rows) {
			int widgetY = row.button() != null ? row.button().getY() : row.box().getY();
			graphics.drawCenteredString(this.font, row.label(), row.centerX(), widgetY - LABEL_RISE, 0xC8C8C8);
		}
	}
}
