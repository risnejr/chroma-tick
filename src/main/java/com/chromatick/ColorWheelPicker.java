package com.chromatick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * Discrete 12-hue × 3-ring colour wheel. Fills its entire component area — set
 * the preferred/maximum size from outside to control dimensions. The wheel is
 * always circular and centred; it uses the smaller of width/height as its
 * diameter so it adapts to any square or rectangular bounds.
 *
 * Geometry: uniform 30° wedges with a 2° visual gap, three concentric rings of
 * equal radial width separated by 2 px gaps, hub radius = 1 px.
 */
class ColorWheelPicker extends JPanel
{
	private static final int     HUES   = 12;
	private static final float   LINE_W = 2f;

	// (saturation %, lightness %) per ring, outer → inner
	private static final int[][] RINGS  = {
		{90, 50},
		{80, 66},
		{70, 82},
	};

	// Precomputed colours: cells[ring][hue]
	private final Color[][] cells = new Color[RINGS.length][HUES];

	private int selRing = -1;
	private int selHue  = -1;

	private final List<Consumer<Color>> listeners      = new ArrayList<>();
	private final List<Runnable>        commitListeners = new ArrayList<>();

	ColorWheelPicker()
	{
		for (int r = 0; r < RINGS.length; r++)
		{
			for (int h = 0; h < HUES; h++)
			{
				cells[r][h] = DiscretePalette.fromHSL(h * 30, RINGS[r][0], RINGS[r][1]);
			}
		}

		// Preferred is a hint; CardLayout stretches it to fill the container.
		setPreferredSize(new Dimension(212, 212));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				int[] cell = hitTest(e.getX(), e.getY());
				if (cell != null)
				{
					selRing = cell[0];
					selHue  = cell[1];
					repaint();
					Color c = cells[selRing][selHue];
					for (Consumer<Color> l : listeners)
					{
						l.accept(c);
					}
					for (Runnable r : commitListeners)
					{
						r.run();
					}
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

	void setColorSilent(Color c)
	{
		int bestR = -1, bestH = -1;
		double bestDist = Double.MAX_VALUE;
		for (int r = 0; r < RINGS.length; r++)
		{
			for (int h = 0; h < HUES; h++)
			{
				int dr = c.getRed()   - cells[r][h].getRed();
				int dg = c.getGreen() - cells[r][h].getGreen();
				int db = c.getBlue()  - cells[r][h].getBlue();
				double d = dr * dr + dg * dg + db * db;
				if (d < bestDist)
				{
					bestDist = d;
					bestR = r;
					bestH = h;
				}
			}
		}
		selRing = bestR;
		selHue  = bestH;
		repaint();
	}

	Color getColor()
	{
		if (selRing < 0)
		{
			return Color.RED;
		}
		return cells[selRing][selHue];
	}

	// ─── Geometry helper ─────────────────────────────────────────────────
	//   Returns: [cx, cy, outerR, hubR, ringW]

	private float[] geom()
	{
		float size   = Math.min(getWidth(), getHeight());
		float cx     = getWidth()  / 2f;
		float cy     = getHeight() / 2f;
		float outerR = size / 2f - 2f;
		float hubR   = LINE_W / 2f;
		float ringW  = (outerR - hubR - LINE_W * (RINGS.length - 1)) / RINGS.length;
		return new float[]{cx, cy, outerR, hubR, ringW};
	}

	// ─── Hit test ─────────────────────────────────────────────────────────

	private int[] hitTest(int mx, int my)
	{
		float[] g     = geom();
		float   cx    = g[0], cy = g[1], outerR = g[2], hubR = g[3], ringW = g[4];
		float   dx    = mx - cx;
		float   dy    = my - cy;
		float   dist  = (float) Math.sqrt(dx * dx + dy * dy);
		if (dist < hubR || dist > outerR)
		{
			return null;
		}
		for (int r = 0; r < RINGS.length; r++)
		{
			float ro = outerR - r * (ringW + LINE_W);
			float ri = ro - ringW;
			if (dist <= ro && dist >= ri)
			{
				double screenDeg = Math.toDegrees(Math.atan2(dy, dx)) + 90;
				if (screenDeg < 0)
				{
					screenDeg += 360;
				}
				int h = (int) ((screenDeg + 15) % 360 / 30);
				if (h >= HUES)
				{
					h = HUES - 1;
				}
				return new int[]{r, h};
			}
		}
		return null;
	}

	// ─── Paint ────────────────────────────────────────────────────────────

	@Override
	protected void paintComponent(Graphics g)
	{
		if (getWidth() == 0 || getHeight() == 0)
		{
			return;
		}

		float[] geom  = geom();
		float   cx    = geom[0], cy = geom[1], outerR = geom[2], hubR = geom[3], ringW = geom[4];

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING,    RenderingHints.VALUE_RENDER_QUALITY);

		// Paint rings outer → inner, masking each inner radius after each ring
		for (int r = 0; r < RINGS.length; r++)
		{
			float ro = outerR - r * (ringW + LINE_W);
			float ri = ro - ringW;

			for (int h = 0; h < HUES; h++)
			{
				float javaCentre = 90f - h * 30f;
				float halfArc    = 14f;
				float startAngle = javaCentre + halfArc;
				float arcAngle   = -(halfArc * 2f);

				g2.setColor(cells[r][h]);
				int x = Math.round(cx - ro);
				int y = Math.round(cy - ro);
				int d = Math.round(ro * 2);
				g2.fillArc(x, y, d, d, Math.round(startAngle), Math.round(arcAngle));
			}

			// Mask inner gap
			if (ri > hubR)
			{
				g2.setColor(getBackground() != null ? getBackground() : new Color(0x242424));
				int x = Math.round(cx - ri);
				int y = Math.round(cy - ri);
				int d = Math.round(ri * 2);
				g2.fillOval(x, y, d, d);
			}
		}

		// Radial spokes
		Color bgColor = getBackground() != null ? getBackground() : new Color(0x242424);
		g2.setColor(bgColor);
		g2.setStroke(new BasicStroke(LINE_W, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		for (int h = 0; h < HUES; h++)
		{
			double b  = Math.toRadians(h * 30.0 - 15.0);
			float  x1 = cx + hubR   * (float) Math.sin(b);
			float  y1 = cy - hubR   * (float) Math.cos(b);
			float  x2 = cx + outerR * (float) Math.sin(b);
			float  y2 = cy - outerR * (float) Math.cos(b);
			g2.drawLine(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2));
		}

		// Concentric ring separators
		g2.setStroke(new BasicStroke(LINE_W));
		for (int r = 0; r < RINGS.length - 1; r++)
		{
			float ro   = outerR - r * (ringW + LINE_W);
			float ri   = ro - ringW;
			float sepR = ri - LINE_W / 2f;
			g2.setColor(bgColor);
			g2.drawOval(Math.round(cx - sepR), Math.round(cy - sepR),
				Math.round(sepR * 2), Math.round(sepR * 2));
		}

		// Outer boundary
		g2.setStroke(new BasicStroke(LINE_W * 2));
		g2.setColor(bgColor);
		g2.drawOval(Math.round(cx - outerR), Math.round(cy - outerR),
			Math.round(outerR * 2), Math.round(outerR * 2));

		// Hub
		g2.setColor(bgColor);
		g2.fillOval(Math.round(cx - hubR), Math.round(cy - hubR),
			Math.round(hubR * 2 + 1), Math.round(hubR * 2 + 1));

		g2.dispose();
	}
}
