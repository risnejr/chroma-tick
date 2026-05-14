package com.chromatick;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * HSV color wheel: a continuous hue ring around the outside, with a
 * saturation/value square inside. Click or drag in either region to pick.
 * Listeners are notified continuously while dragging.
 */
class ColorWheelPicker extends JPanel
{
	private static final int SIZE = 184;
	private static final int RING_THICKNESS = 18;
	private static final int RING_GAP = 4;

	private float hue = 0f;
	private float saturation = 1f;
	private float value = 1f;

	private BufferedImage ringCache;
	private BufferedImage squareCache;
	private float cachedHue = -1f;

	private enum DragTarget { NONE, RING, SQUARE }

	private DragTarget activeDrag = DragTarget.NONE;

	private final List<Consumer<Color>> listeners = new ArrayList<>();
	private final List<Runnable> commitListeners = new ArrayList<>();

	ColorWheelPicker()
	{
		setPreferredSize(new Dimension(SIZE, SIZE));
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				Point center = center();
				int dx = e.getX() - center.x;
				int dy = e.getY() - center.y;
				double dist = Math.sqrt(dx * dx + dy * dy);
				double outer = SIZE / 2.0;
				double inner = outer - RING_THICKNESS;
				double squareHalf = (inner - RING_GAP) / Math.sqrt(2);

				if (dist <= outer && dist >= inner)
				{
					activeDrag = DragTarget.RING;
					updateFromRing(e.getX(), e.getY());
				}
				else if (Math.abs(dx) <= squareHalf && Math.abs(dy) <= squareHalf)
				{
					activeDrag = DragTarget.SQUARE;
					updateFromSquare(e.getX(), e.getY());
				}
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (activeDrag == DragTarget.RING)
				{
					updateFromRing(e.getX(), e.getY());
				}
				else if (activeDrag == DragTarget.SQUARE)
				{
					updateFromSquare(e.getX(), e.getY());
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (activeDrag != DragTarget.NONE)
				{
					activeDrag = DragTarget.NONE;
					for (Runnable r : commitListeners)
					{
						r.run();
					}
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void addColorListener(Consumer<Color> l)
	{
		listeners.add(l);
	}

	void addCommitListener(Runnable r)
	{
		commitListeners.add(r);
	}

	/** Set the wheel's selection without firing listeners. */
	void setColorSilent(Color c)
	{
		float[] hsv = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
		hue = hsv[0];
		saturation = hsv[1];
		value = hsv[2];
		repaint();
	}

	Color getColor()
	{
		return Color.getHSBColor(hue, saturation, value);
	}

	private Point center()
	{
		return new Point(getWidth() / 2, getHeight() / 2);
	}

	private void updateFromRing(int x, int y)
	{
		Point c = center();
		// Ring is drawn CW from red at 12 o'clock (i=0 → startAngle=89° in Java arc coords).
		// Map screen-space atan2 to hue with that orientation.
		double angle = Math.atan2(y - c.y, x - c.x);
		float newHue = (float) (angle / (2 * Math.PI) + 0.25f);
		newHue = ((newHue % 1f) + 1f) % 1f;
		hue = newHue;
		repaint();
		fire();
	}

	private void updateFromSquare(int x, int y)
	{
		Point c = center();
		double outer = SIZE / 2.0;
		double inner = outer - RING_THICKNESS;
		double squareHalf = (inner - RING_GAP) / Math.sqrt(2);

		double sx = (x - c.x + squareHalf) / (2 * squareHalf);
		double sy = (y - c.y + squareHalf) / (2 * squareHalf);
		sx = Math.max(0, Math.min(1, sx));
		sy = Math.max(0, Math.min(1, sy));

		saturation = (float) sx;
		value = (float) (1.0 - sy);
		repaint();
		fire();
	}

	private void fire()
	{
		Color c = getColor();
		for (Consumer<Color> l : listeners)
		{
			l.accept(c);
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Point c = center();
		double outer = SIZE / 2.0;
		double inner = outer - RING_THICKNESS;
		double squareHalf = (inner - RING_GAP) / Math.sqrt(2);

		drawRing(g2, c, outer, inner);
		drawSquare(g2, c, squareHalf);
		drawRingIndicator(g2, c, outer, inner);
		drawSquareIndicator(g2, c, squareHalf);

		g2.dispose();
	}

	private void drawRing(Graphics2D g2, Point c, double outer, double inner)
	{
		if (ringCache == null)
		{
			ringCache = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D rg = ringCache.createGraphics();
			rg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int cx = SIZE / 2;
			int cy = SIZE / 2;
			// draw thin pie slices around the circle
			int slices = 360;
			for (int i = 0; i < slices; i++)
			{
				float h = (float) i / slices;
				rg.setColor(Color.getHSBColor(h, 1f, 1f));
				// our angle 0 corresponds to top (12 o'clock) — Java arcs start at 3 o'clock and go CCW
				int startAngle = 90 - (i * 360 / slices) - (360 / slices);
				int extent = 360 / slices + 1;
				rg.fillArc(cx - (int) outer, cy - (int) outer, (int) (outer * 2), (int) (outer * 2),
					startAngle, extent);
			}
			rg.setComposite(AlphaComposite.Clear);
			rg.fillOval(cx - (int) inner, cy - (int) inner, (int) (inner * 2), (int) (inner * 2));
			rg.dispose();
		}
		g2.drawImage(ringCache, 0, 0, null);
	}

	private void drawSquare(Graphics2D g2, Point c, double half)
	{
		if (squareCache == null || cachedHue != hue)
		{
			int side = (int) Math.round(half * 2);
			squareCache = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
			Color pure = Color.getHSBColor(hue, 1f, 1f);
			Graphics2D sg = squareCache.createGraphics();
			// horizontal: white -> pure hue (saturation)
			LinearGradientPaint hGrad = new LinearGradientPaint(
				0, 0, side, 0,
				new float[]{0f, 1f},
				new Color[]{Color.WHITE, pure}
			);
			sg.setPaint(hGrad);
			sg.fillRect(0, 0, side, side);
			// vertical: transparent -> black (value)
			LinearGradientPaint vGrad = new LinearGradientPaint(
				0, 0, 0, side,
				new float[]{0f, 1f},
				new Color[]{new Color(0, 0, 0, 0), Color.BLACK}
			);
			sg.setPaint(vGrad);
			sg.fillRect(0, 0, side, side);
			sg.dispose();
			cachedHue = hue;
		}
		int side = squareCache.getWidth();
		g2.drawImage(squareCache, c.x - side / 2, c.y - side / 2, null);
	}

	private void drawRingIndicator(Graphics2D g2, Point c, double outer, double inner)
	{
		double mid = (outer + inner) / 2.0;
		double angle = (hue - 0.25f) * 2 * Math.PI;
		int x = (int) Math.round(c.x + Math.cos(angle) * mid);
		int y = (int) Math.round(c.y + Math.sin(angle) * mid);
		int r = (int) Math.round((outer - inner) / 2.0) - 1;
		g2.setStroke(new BasicStroke(2f));
		g2.setColor(Color.BLACK);
		g2.drawOval(x - r, y - r, r * 2, r * 2);
		g2.setColor(Color.WHITE);
		g2.drawOval(x - r + 1, y - r + 1, (r - 1) * 2, (r - 1) * 2);
	}

	private void drawSquareIndicator(Graphics2D g2, Point c, double half)
	{
		double sx = saturation * (2 * half) - half;
		double sy = (1.0 - value) * (2 * half) - half;
		int x = (int) Math.round(c.x + sx);
		int y = (int) Math.round(c.y + sy);
		int r = 5;
		g2.setStroke(new BasicStroke(2f));
		g2.setColor(Color.BLACK);
		g2.drawOval(x - r, y - r, r * 2, r * 2);
		g2.setColor(Color.WHITE);
		g2.drawOval(x - r + 1, y - r + 1, (r - 1) * 2, (r - 1) * 2);
	}
}
