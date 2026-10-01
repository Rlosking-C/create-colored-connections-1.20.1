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

/**
 * The mod-list "Configure" screen (plus its registration).
 *
 * <p>NeoForge ships a generic editor for config specs, so the 1.21.1 build had
 * no screen of its own - an in-game editor such as Configured picked the two
 * specs up automatically. <b>Forge 1.20.1 has no such editor and no automatic
 * Config button binding</b>, so without this class the mod list's Configure
 * button stays greyed out. This is therefore new code, not a port.</p>
 *
 * <p><b>Layout note (two in-game iterations):</b> the first version drew each
 * label at {@code widget.getY() - 10} while rows were only 24px apart and
 * widgets 20px tall, so every label overlapped the widget above it. The second
 * version switched to label-line + widget-line rows but kept
 * {@link #ROW_STEP} at 26 against a 20px widget and hung the label 13px above
 * the widget — only 6px of clearance for a 9px glyph, so the label's ascent
 * still collided with the button above it (visible in-game as touching rows).
 * The current numbers give the label its own full line: 40px per row against a
 * 20px widget leaves 20px, and the label sits 13px above its widget, which
 * leaves 7px above the label. <b>Changing one of {@link #ROW_STEP} and
 * {@link #LABEL_RISE} means re-checking the other.</b></p>
 *
 * <p>Client-only: the whole class lives in the {@code client} package so the
 * common mod class never touches a client-only type on a dedicated server.</p>
 */
public class CreateCCConfigScreen extends Screen {

	/**
	 * Widget width.
	 *
	 * <p>Kept deliberately small: the first version used 220 and, with two 220px
	 * columns plus a gap, the block filled most of a 1080p window at GUI scale 2
	 * and looked oversized. 150px is the width vanilla uses for the widest
	 * buttons on its own option screens.</p>
	 */
	private static final int WIDGET_WIDTH = 150;
	/** Widget height. */
	private static final int WIDGET_HEIGHT = 20;
	/**
	 * Vertical distance from one widget's top to the next widget's top.
	 * {@code ROW_STEP - WIDGET_HEIGHT} (20px) is the label's line.
	 */
	private static final int ROW_STEP = 40;
	/**
	 * How far above its widget the label is centred (13px ≈ half the widget
	 * height, i.e. roughly the vertical centre of the 20px line above it).
	 * Must stay well under {@code ROW_STEP - WIDGET_HEIGHT} so the label's
	 * ascent never reaches the widget above; 20 − 13 = 7px of headroom.
	 */
	private static final int LABEL_RISE = 13;
	/**
	 * Horizontal gap between the two widget columns. Sized so the two labels
	 * ("Factory Controller 染色" / "追踪 HUD 读数") and the buttons read as
	 * separate columns instead of one run-on block — at the earlier 10px the
	 * rows visually merged across the middle.
	 */
	private static final int COLUMN_GAP = 30;

	/** Hangs this screen on the mod list's Configure button. */
	public static void register() {
		ModLoadingContext.get().registerExtensionPoint(
			ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new ConfigScreenHandler.ConfigScreenFactory(
				(client, modsScreen) -> new CreateCCConfigScreen(modsScreen)));
	}

	/** One row: its label, the column centre it belongs to, and its widget. */
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

		// Two columns of four, kept compact so the block stays small on a 1080p
		// window at GUI scale 2 (the single-column version overflowed, and the
		// first two-column version at 220px still read as oversized). At GUI
		// scale 3 the logical window is 640x360, and this block is 330x240 plus
		// the title, so it still fits.
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

		// Trace distance: a text field in the last right-hand slot, clamped to the
		// same range the spec declares (4..64) - change one, change the other.
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

	/** Vanilla's own "On"/"Off" wording, so this matches the rest of the game. */
	private static Component toggleLabel(ForgeConfigSpec.BooleanValue value) {
		return CommonComponents.optionStatus(value.get());
	}

	@Override
	public void onClose() {
		// Persist the distance field; malformed input keeps the previous value.
		for (Row row : rows) {
			if (row.box() == null)
				continue;
			try {
				double parsed = Double.parseDouble(row.box().getValue().trim());
				CreateCCConfig.TRACE_DISTANCE.set(Math.max(4.0, Math.min(64.0, parsed)));
			} catch (NumberFormatException ignored) {
				// leave the stored value untouched
			}
		}
		// Save both specs so edits survive a restart and reach the server-side file.
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

		// Each label sits on its own line above its widget. ROW_STEP (40) minus
		// WIDGET_HEIGHT (20) is the 20px that line gets, and the label is
		// centred 13px up, so its ascent keeps 7px clear of the widget above.
		for (Row row : rows) {
			int widgetY = row.button() != null ? row.button().getY() : row.box().getY();
			graphics.drawCenteredString(this.font, row.label(), row.centerX(), widgetY - LABEL_RISE, 0xC8C8C8);
		}
	}
}
