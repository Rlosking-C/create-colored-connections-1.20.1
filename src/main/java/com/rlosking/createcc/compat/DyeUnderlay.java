package com.rlosking.createcc.compat;

import com.rlosking.createcc.mixin.factorycontroller.client.ConnectionWidgetMixin;

import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2i;

import java.util.List;

/**
 * Draws the dye underlay for an active GUI wire: Factory Controller's own
 * connection texture tinted with the dye color, covering the wire's path
 * expanded two pixels perpendicular on each side (50% of the 4px core, the
 * same proportion as the world renderer's widened underlay), so the
 * status-colored core rendered afterwards leaves a <em>textured</em> dye
 * border — the GUI counterpart of the world look, which also shows the
 * washboard texture (not a flat color) at the dye edge.
 *
 * <p><b>Why this class exists (and why it duplicates FC's walk):</b> the path
 * walk, atlas rects and frame timing replicate FC's own
 * {@code VirtualConnectionRenderer.drawPathSegments}, but with the quad
 * widened perpendicular to the wire. FC only exposes whole-cell blits, and a
 * sub-pixel-precise border needs its own quads, so the tables are copied
 * here.</p>
 *
 * <p><b>1.20.1 port note — this is a rewrite, not a port.</b> The 1.21.1
 * original drove FC's {@code TiledSpriteRenderer} through a
 * {@code @Accessor}-mixined private {@code renderType} field, because that
 * class emits through the tiled-sprite shader (UV1/UV2) and owns its own
 * {@code RenderType}. FC 1.20.1 has no such field and no public render-type
 * accessor: its GUI connections are drawn by the vanilla
 * {@code position_tex_color} shader through {@link BatchedBlitter}, which is
 * a <em>public</em> class with a public factory, public per-quad
 * {@code blit(...)} overload and public {@code setColorARGB(...)}. So the
 * whole accessor-mixin route disappears:</p>
 * <ul>
 *   <li>no {@code TiledSpriteRendererAccessor} — and therefore no mixin —
 *       is needed at all (the 1.21.1 file of that name is deleted, not
 *       ported);</li>
 *   <li>the atlas sub-rect is handed to {@code blit} as raw u/v offsets in
 *       the 16x16 connection atlas instead of UV1/UV2 sprite bounds;</li>
 *   <li>{@code GuiSpriteScaling} does not exist on 1.20.1, so the
 *       {@code Tile(w, h)} argument the 1.21.1 code passed is not needed
 *       either.</li>
 * </ul>
 * <p>The subtexture tables below are taken verbatim from FC 1.2.1 for 1.20.1
 * ({@code VirtualConnectionRenderer.Subtexture}); its own {@code t} field is
 * the per-sprite frame phase, which the frame index here reproduces
 * exactly.</p>
 *
 * <p>Although this is a plain helper (not a mixin), it must stay out of the
 * mixin package declared by {@code create_colored_connections-fc.mixins.json}:
 * the mixin framework only allows real mixin classes there. It is referenced
 * only from {@link ConnectionWidgetMixin}, which never loads when Factory
 * Controller is absent.</p>
 */
public final class DyeUnderlay {

	private DyeUnderlay() {}

	// --- Copied from FC 1.2.1 for 1.20.1 VirtualConnectionRenderer ---
	private static final int CELL = 16;
	private static final int FRAME_SIZE = 16;
	private static final float FRAME_TIME = 2f;
	private static final int N_FRAMES = 8;
	private static final ResourceLocation TEX_STATIC =
			new ResourceLocation("createfactorycontroller", "textures/gui/connection/factory_panel_connections.png");
	private static final ResourceLocation TEX_ANIMATED =
			new ResourceLocation("createfactorycontroller", "textures/gui/connection/factory_panel_connections_animated.png");

	/**
	 * How far the underlay peeks past the 4px core on each side, in canvas px.
	 * Scaled from the world renderer's look (2px core widened 2×, 1px peeking
	 * per side → border = 50% of core): the GUI core is 4px, so the border is
	 * 2px per side — the same border-to-core proportion as a dyed wall link.
	 */
	private static final float BORDER = 2f;

	private enum Dir {
		UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

		final int x, y;

		Dir(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	/** Subtexture rect in the connection atlas: u, v, w, h, anchor ox, anchor oy, frame phase t. */
	private record Sub(int u, int v, int w, int h, int ox, int oy, int t) {}

	/**
	 * FC 1.2.1's own tables, in its own layout. For each direction there are
	 * two line sprites (body and the neighbour-facing half) plus a head and a
	 * tail; the 1.21.1 build's single-entry tables are a later refactor of the
	 * same atlas. Only the {@code _0} halves are used for the path body here,
	 * matching FC's own walk (the {@code _1} halves belong to the same cell and
	 * overlap the {@code _0} quad's inner edge).
	 */
	private static final Sub[][] LINES = {
			// index by Dir.ordinal(): UP, DOWN, LEFT, RIGHT
			{ new Sub(0, 0, 4, 8, 2, 0, 0), new Sub(0, 0, 4, 8, 2, 8, 0) },
			{ new Sub(4, 0, 4, 8, 2, 8, 0), new Sub(4, 0, 4, 8, 2, 0, 0) },
			{ new Sub(0, 8, 8, 4, 0, 2, 0), new Sub(0, 8, 8, 4, 8, 2, 0) },
			{ new Sub(0, 12, 8, 4, 8, 2, 0), new Sub(0, 12, 8, 4, 0, 2, 0) },
	};
	private static final Sub[] HEADS = {
			new Sub(8, 3, 4, 2, 2, -6, 4),   // UP
			new Sub(12, 3, 4, 2, 2, 8, 4),   // DOWN
			new Sub(11, 8, 2, 4, -6, 2, 4),  // LEFT
			new Sub(11, 12, 2, 4, 8, 2, 4),  // RIGHT
	};
	private static final Sub[] TAILS = {
			new Sub(0, 0, 4, 2, 2, 8, 0),    // UP
			new Sub(4, 6, 4, 2, 2, -6, 0),   // DOWN
			new Sub(0, 8, 2, 4, 8, 2, 0),    // LEFT
			new Sub(6, 12, 2, 4, -6, 2, 0),  // RIGHT
	};

	/**
	 * One blitter per texture, mirroring FC's own {@code STATIC_BLITTER} /
	 * {@code ANIMATED_BLITTER}. They are only caches of the per-texture
	 * {@code RenderType}: the color is re-applied on every quad, and
	 * {@code BatchedBlitter}'s UV arguments are absolute here (the 4-arg
	 * constructor leaves the initial u/v window at the whole texture), so a
	 * single instance per texture is safe. Creating a fresh one per quad would
	 * re-enter the render-type cache for no benefit.
	 */
	private static final BatchedBlitter STATIC_BLITTER = BatchedBlitter.forTexture(TEX_STATIC, 16, 16);
	private static final BatchedBlitter ANIMATED_BLITTER = BatchedBlitter.forTexture(TEX_ANIMATED, 16, 128);

	/**
	 * Submits the whole wire path in dye tint, two pixels wider than the core
	 * on each side, into the same batch the wires render in — anything drawn
	 * afterwards (the vanilla status core) stacks on top.
	 *
	 * <p>{@code colorARGB} must carry alpha 255: FC's blitter shader is the
	 * vanilla {@code position_tex_color} one, whose texture is cut out, so the
	 * tint's alpha only modulates blending rather than punching softer edges.</p>
	 */
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

	/**
	 * One sprite quad, expanded perpendicular to the wire direction, emitted
	 * through FC's own blitter so it lands in the same {@code RenderType}
	 * batch (and therefore the same draw order) as the status core.
	 *
	 * <p>The sub-rect is passed at its native size ({@code st.w × st.h}) even
	 * though the quad is wider: {@code BatchedBlitter.blit} maps the sub-rect
	 * across the whole destination quad, so the texture's outermost pixels are
	 * stretched into the {@link #BORDER} band — the visible, textured dye
	 * edge. Passing the destination size instead would re-sample and blur the
	 * washboard pattern.</p>
	 */
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
		// Rounding outward keeps the border symmetric about the core rather
		// than drifting half a pixel to one side.
		int ix1 = (int) Math.floor(x1);
		int iy1 = (int) Math.floor(y1);
		int ix2 = (int) Math.ceil(x2);
		int iy2 = (int) Math.ceil(y2);
		blitter.setColorARGB(colorARGB).blit(gfx.bufferSource(), gfx.pose(),
				ix1, iy1, ix2 - ix1, iy2 - iy1,
				st.u, st.v + frame * FRAME_SIZE, st.w, st.h);
	}
}
