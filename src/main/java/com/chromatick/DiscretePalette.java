package com.chromatick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * Discrete swatch wheel: 12 hue wedges × 3 saturation rings (36 chips) plus a
 * 5-cell grayscale row below. Hues are evenly-stepped HSV; each inner ring
 * reduces saturation toward white. The innermost ring fills the centre — no
 * dead hole. Clicking a chip fires a Color event and commits immediately
 * (no drag). Optimized for rapid sequential picking.
 */
class DiscretePalette extends JPanel
{
	private static final int SIZE = 200;
	private static final int CX = SIZE / 2;
	private static final int CY = SIZE / 2;
	private static final int WEDGES = 12;
	private static final int GRAY_HEIGHT = 24;

	// Ring boundaries (outermost first). Small radial gaps make the chips read as separate.
	// The innermost ring extends to the centre (R_INNER[2] = 0) so there is no dead hole.
	private static final int[] R_OUTER = {96, 68, 40};
	private static final int[] R_INNER = {72, 44, 0};

	// (S, V) per ring: outer = pure hue; inner rings add white (lower S, V stays high).
	// Vivid on the rim, fading to near-white at the centre.
	private static final float[][] RING_SV = {
		{1.00f, 1.00f},
		{0.66f, 1.00f},
		{0.33f, 1.00f},
	};

	private static final float WEDGE_DEG = 360f / WEDGES;
	private static final float WEDGE_GAP_DEG = 2f;

	private static final int[] GRAYS = {16, 80, 144, 200, 240};

	private static Color chipColor(int wedge, int ring)
	{
		float hue = (float) (((wedge % WEDGES) + WEDGES) % WEDGES) / WEDGES;
		float sat = RING_SV[ring][0];
		float val = RING_SV[ring][1];
		return Color.getHSBColor(hue, sat, val);
	}

	private final List<Consumer<Color>> listeners = new ArrayList<>();
	private final List<Runnable> commitListeners = new ArrayList<>();

	DiscretePalette()
	{
		setPreferredSize(new Dimension(SIZE, SIZE + GRAY_HEIGHT + 6));
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				Color picked = hitTest(e.getX(), e.getY());
				if (picked != null)
				{
					fire(picked);
				}
			}
		});
	}

	void addColorListener(Consumer<Color> l)
	{
		listeners.add(l);
	}

	void addCommitListener(Runnable r)
	{
		commitListeners.add(r);
	}

	private void fire(Color c)
	{
		for (Consumer<Color> l : listeners)
		{
			l.accept(c);
		}
		for (Runnable r : commitListeners)
		{
			r.run();
		}
	}

	private Color hitTest(int x, int y)
	{
		// Grayscale row?
		int grayTop = SIZE + 6;
		if (y >= grayTop && y < grayTop + GRAY_HEIGHT)
		{
			int cellW = SIZE / GRAYS.length;
			int idx = x / cellW;
			if (idx >= 0 && idx < GRAYS.length)
			{
				int v = GRAYS[idx];
				return new Color(v, v, v);
			}
			return null;
		}

		// Wheel?
		int dx = x - CX;
		int dy = y - CY;
		double r = Math.sqrt(dx * dx + dy * dy);
		if (r > R_OUTER[0] || r < R_INNER[R_INNER.length - 1])
		{
			return null;
		}

		int ring = -1;
		for (int i = 0; i < R_OUTER.length; i++)
		{
			if (r <= R_OUTER[i] && r >= R_INNER[i])
			{
				ring = i;
				break;
			}
		}
		if (ring < 0)
		{
			return null;
		}

		// Convert atan2 to "hue angle" measured CW from top: red at top, hue index increases CW.
		double a = Math.atan2(dy, dx);
		double hueAngle = a + Math.PI / 2;
		hueAngle = ((hueAngle % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
		int wedge = (int) Math.floor((hueAngle + Math.PI / WEDGES) / (2 * Math.PI / WEDGES)) % WEDGES;

		return chipColor(wedge, ring);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Paint outer→inner: each fillArc is a full pie slice from center, so a
		// later (smaller) iteration would overwrite earlier rings if we went the
		// other way. The punch-out at each ring's inner radius leaves a radial gap.
		for (int ring = 0; ring < R_OUTER.length; ring++)
		{
			int outer = R_OUTER[ring];
			int inner = R_INNER[ring];

			for (int w = 0; w < WEDGES; w++)
			{
				g2.setColor(chipColor(w, ring));
				// Wedge centered on screen-CW angle: wedge 0 = up.
				// Java arc: 0° = east, positive = CCW (visually).
				float centerJavaDeg = 90f - w * WEDGE_DEG;
				float startAngle = centerJavaDeg + WEDGE_DEG / 2f - WEDGE_GAP_DEG / 2f;
				float arcAngle = -(WEDGE_DEG - WEDGE_GAP_DEG);
				g2.fillArc(CX - outer, CY - outer, outer * 2, outer * 2,
					Math.round(startAngle), Math.round(arcAngle));
			}

			// Punch the inner radius with the parent background to leave a radial gap.
			// For the innermost ring inner=0, so this is a no-op and the wedges
			// meet at the centre — closing the hole.
			if (inner > 0)
			{
				g2.setColor(getParent() != null ? getParent().getBackground() : Color.BLACK);
				g2.fillOval(CX - inner, CY - inner, inner * 2, inner * 2);
			}
		}

		// Grayscale row
		int grayTop = SIZE + 6;
		int cellW = SIZE / GRAYS.length;
		for (int i = 0; i < GRAYS.length; i++)
		{
			int v = GRAYS[i];
			g2.setColor(new Color(v, v, v));
			g2.fillRoundRect(i * cellW + 2, grayTop + 2, cellW - 4, GRAY_HEIGHT - 4, 6, 6);
		}
		g2.setStroke(new BasicStroke(1f));
		g2.setColor(new Color(255, 255, 255, 30));
		for (int i = 0; i < GRAYS.length; i++)
		{
			g2.drawRoundRect(i * cellW + 2, grayTop + 2, cellW - 4 - 1, GRAY_HEIGHT - 4 - 1, 6, 6);
		}

		g2.dispose();
	}

	// API parity with ColorWheelPicker so the panel can swap them transparently.
	void setColorSilent(Color c)
	{
		// Discrete palette has no persistent indicator state — repaint is a no-op.
	}

	/** Exposed for callers that may want to provide hover feedback later. */
	@SuppressWarnings("unused")
	Point centerPoint()
	{
		return new Point(CX, CY);
	}
}
