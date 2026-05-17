package com.chromatick;

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
 * 6-column × 5-row HSL colour grid (30 cells). Row 0 = six neutrals (white→black);
 * rows 1–4 = six hue columns at four lightness steps. Generated from HSL so the
 * palette matches the V1+ Refined design spec exactly.
 *
 * Clicking any cell fires listeners and commits immediately.
 */
class DiscretePalette extends JPanel
{
	private static final int COLS = 6;
	private static final int ROWS = 5;
	private static final int CELL_H = 20;
	private static final int CELL_GAP = 3;
	private static final int PADDING = 4;

	// Row 0: neutral greys, white→black
	private static final int[] NEUTRAL_RGB = {
		0xFFFFFF, 0xCCCCCC, 0x999999, 0x666666, 0x333333, 0x000000
	};

	// Hue angles (degrees) for the 6 hue columns
	private static final int[] HUE_ANGLES = {0, 28, 50, 130, 215, 285};

	// (saturation%, lightness%) per row; rows 1–4 go light→dark
	private static final int[][] HSL_ROWS = {
		{95, 82},
		{85, 68},
		{75, 52},
		{65, 36},
	};

	// Precomputed cell colours [row][col]
	private final Color[][] cells = new Color[ROWS][COLS];

	// Currently highlighted cell (null if none)
	private Color selected;

	private final List<Consumer<Color>> listeners = new ArrayList<>();
	private final List<Runnable> commitListeners = new ArrayList<>();

	DiscretePalette()
	{
		// Build the colour grid
		for (int col = 0; col < COLS; col++)
		{
			cells[0][col] = new Color(NEUTRAL_RGB[col]);
		}
		for (int row = 0; row < HSL_ROWS.length; row++)
		{
			for (int col = 0; col < COLS; col++)
			{
				cells[row + 1][col] = fromHSL(HUE_ANGLES[col], HSL_ROWS[row][0], HSL_ROWS[row][1]);
			}
		}

		int prefH = ROWS * CELL_H + (ROWS - 1) * CELL_GAP + PADDING * 2;
		setPreferredSize(new Dimension(212, prefH));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, prefH));
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				Color c = hitTest(e.getX(), e.getY());
				if (c != null)
				{
					selected = c;
					repaint();
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

	/** Highlight the closest grid cell to c without firing listeners. */
	void setColorSilent(Color c)
	{
		Color best = null;
		double bestDist = Double.MAX_VALUE;
		for (Color[] row : cells)
		{
			for (Color cell : row)
			{
				double d = colorDist(c, cell);
				if (d < bestDist)
				{
					bestDist = d;
					best = cell;
				}
			}
		}
		selected = best;
		repaint();
	}

	private Color hitTest(int mx, int my)
	{
		int availW = getWidth() - PADDING * 2;
		float step = (float) (availW - (COLS - 1) * CELL_GAP) / COLS;

		for (int row = 0; row < ROWS; row++)
		{
			for (int col = 0; col < COLS; col++)
			{
				int x = PADDING + Math.round(col * (step + CELL_GAP));
				int y = PADDING + row * (CELL_H + CELL_GAP);
				if (mx >= x && mx < x + step && my >= y && my < y + CELL_H)
				{
					return cells[row][col];
				}
			}
		}
		return null;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int availW = getWidth() - PADDING * 2;
		float step = (float) (availW - (COLS - 1) * CELL_GAP) / COLS;

		for (int row = 0; row < ROWS; row++)
		{
			for (int col = 0; col < COLS; col++)
			{
				Color c = cells[row][col];
				int x = PADDING + Math.round(col * (step + CELL_GAP));
				int y = PADDING + row * (CELL_H + CELL_GAP);
				int w = Math.round(step);

				g2.setColor(c);
				g2.fillRoundRect(x, y, w, CELL_H, 4, 4);
			}
		}

		g2.dispose();
	}

	// ─── Shared HSL → Color helper (also used by ColorWheelPicker) ────────

	/** Convert HSL (h: 0–360, s: 0–100, l: 0–100) to a {@link Color}. */
	static Color fromHSL(int h, int s, int l)
	{
		float hf = h;
		float sf = s / 100f;
		float lf = l / 100f;
		float c = (1f - Math.abs(2f * lf - 1f)) * sf;
		float x = c * (1f - Math.abs(hf / 60f % 2f - 1f));
		float m = lf - c / 2f;
		float r, gr, b;
		if (hf < 60)       { r = c;  gr = x;  b = 0;  }
		else if (hf < 120) { r = x;  gr = c;  b = 0;  }
		else if (hf < 180) { r = 0;  gr = c;  b = x;  }
		else if (hf < 240) { r = 0;  gr = x;  b = c;  }
		else if (hf < 300) { r = x;  gr = 0;  b = c;  }
		else               { r = c;  gr = 0;  b = x;  }
		return new Color(
			Math.min(255, Math.round((r  + m) * 255)),
			Math.min(255, Math.round((gr + m) * 255)),
			Math.min(255, Math.round((b  + m) * 255))
		);
	}

	private static double colorDist(Color a, Color b)
	{
		int dr = a.getRed()   - b.getRed();
		int dg = a.getGreen() - b.getGreen();
		int db = a.getBlue()  - b.getBlue();
		return dr * dr + dg * dg + db * db;
	}
}
