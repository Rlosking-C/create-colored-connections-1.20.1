package com.rlosking.createcc.compat;

import com.rlosking.createcc.mixin.factorycontroller.client.ConnectionWidgetMixin;

import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2i;

import java.util.List;

public final class DyeUnderlay {

	private DyeUnderlay() {}

	private static final int CELL = 16;
	private static final int FRAME_SIZE = 16;
	private static final float FRAME_TIME = 2f;
	private static final int N_FRAMES = 8;
	private static final ResourceLocation TEX_STATIC =
			new ResourceLocation("createfactorycontroller", "textures/gui/connection/factory_panel_connections.png");
	private static final ResourceLocation TEX_ANIMATED =
			new ResourceLocation("createfactorycontroller", "textures/gui/connection/factory_panel_connections_animated.png");

	private static final float BORDER = 2f;

	private enum Dir {
		UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

		final int x, y;

		Dir(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	private record Sub(int u, int v, int w, int h, int ox, int oy, int t) {}

	private static final Sub[][] LINES = {
			{ new Sub(0, 0, 4, 8, 2, 0, 0), new Sub(0, 0, 4, 8, 2, 8, 0) },
			{ new Sub(4, 0, 4, 8, 2, 8, 0), new Sub(4, 0, 4, 8, 2, 0, 0) },
			{ new Sub(0, 8, 8, 4, 0, 2, 0), new Sub(0, 8, 8, 4, 8, 2, 0) },
			{ new Sub(0, 12, 8, 4, 8, 2, 0), new Sub(0, 12, 8, 4, 0, 2, 0) },
	};
	private static final Sub[] HEADS = {
			new Sub(8, 3, 4, 2, 2, -6, 4),
			new Sub(12, 3, 4, 2, 2, 8, 4),
			new Sub(11, 8, 2, 4, -6, 2, 4),
			new Sub(11, 12, 2, 4, 8, 2, 4),
	};
	private static final Sub[] TAILS = {
			new Sub(0, 0, 4, 2, 2, 8, 0),
			new Sub(4, 6, 4, 2, 2, -6, 0),
			new Sub(0, 8, 2, 4, 8, 2, 0),
			new Sub(6, 12, 2, 4, -6, 2, 0),
	};

	private static final BatchedBlitter STATIC_BLITTER = BatchedBlitter.forTexture(TEX_STATIC, 16, 16);
	private static final BatchedBlitter ANIMATED_BLITTER = BatchedBlitter.forTexture(TEX_ANIMATED, 16, 128);

	public static void draw(GuiGraphics gfx, List<Vector2i> path, int colorARGB, boolean animated) {
		if (path == null || path.size() < 2)
			return;
		BatchedBlitter blitter = animated ? ANIMATED_BLITTER : STATIC_BLITTER;

		for (int i = 0; i < path.size() - 1; i++) {
			Vector2i a = path.get(i), b = path.get(i + 1);

			Dir dir;
			if (a.y > b.y)
				dir = Dir.UP;
			else if (a.y < b.y)
				dir = Dir.DOWN;
			else if (a.x > b.x)
				dir = Dir.LEFT;
			else if (a.x < b.x)
				dir = Dir.RIGHT;
			else
				continue;

			boolean tail = i == 0;
			boolean head = i == path.size() - 2;

			Vector2i pa = new Vector2i(a).mul(CELL).add(CELL / 2, CELL / 2);
			Vector2i pb = new Vector2i(b).mul(CELL).add(CELL / 2, CELL / 2);

			Vector2i lineStart = new Vector2i(pa).add(dir.x * CELL / 4, dir.y * CELL / 4);
			Vector2i lineEnd = new Vector2i(pb).sub(dir.x * CELL / 4, dir.y * CELL / 4);
			if (tail)
				lineStart.add(dir.x * CELL / 2, dir.y * CELL / 2);
			if (head)
				lineEnd.sub(dir.x * CELL / 2, dir.y * CELL / 2);

			int lineLength = (lineEnd.x - lineStart.x) * dir.x + (lineEnd.y - lineStart.y) * dir.y;
			if (lineLength >= 0) {
				Sub st = LINES[dir.ordinal()][0];
				int minX = Math.min(lineStart.x, lineEnd.x);
				int minY = Math.min(lineStart.y, lineEnd.y);
				int sizeX = Math.abs(lineStart.x - lineEnd.x);
				int sizeY = Math.abs(lineStart.y - lineEnd.y);
				quad(blitter, gfx, dir,
						minX - st.w / 2f, minY - st.h / 2f,
						minX - st.w / 2f + sizeX + st.w, minY - st.h / 2f + sizeY + st.h,
						st, animated, colorARGB);
			}

			if (tail) {
				Sub st = TAILS[dir.ordinal()];
				quad(blitter, gfx, dir,
						pa.x - st.ox, pa.y - st.oy, pa.x - st.ox + st.w, pa.y - st.oy + st.h,
						st, animated, colorARGB);
			}
			if (head) {
				Sub st = HEADS[dir.ordinal()];
				quad(blitter, gfx, dir,
						pb.x - st.ox, pb.y - st.oy, pb.x - st.ox + st.w, pb.y - st.oy + st.h,
						st, animated, colorARGB);
			}
		}
	}

	private static void quad(BatchedBlitter blitter, GuiGraphics gfx, Dir dir,
			float x1, float y1, float x2, float y2, Sub st, boolean animated, int colorARGB) {
		if (dir == Dir.UP || dir == Dir.DOWN) {
			x1 -= BORDER;
			x2 += BORDER;
		} else {
			y1 -= BORDER;
			y2 += BORDER;
		}
		int frame = animated ? (int) (AnimationTickHolder.getRenderTime() / FRAME_TIME + st.t) % N_FRAMES : 0;
		int ix1 = (int) Math.floor(x1);
		int iy1 = (int) Math.floor(y1);
		int ix2 = (int) Math.ceil(x2);
		int iy2 = (int) Math.ceil(y2);
		blitter.setColorARGB(colorARGB).blit(gfx.bufferSource(), gfx.pose(),
				ix1, iy1, ix2 - ix1, iy2 - iy1,
				st.u, st.v + frame * FRAME_SIZE, st.w, st.h);
	}
}
