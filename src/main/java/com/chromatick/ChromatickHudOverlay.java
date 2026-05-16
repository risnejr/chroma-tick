package com.chromatick;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * HUD metronome — a frameless row (or column) of palette-colored glyphs.
 *
 * Anchoring: target is "head", "feet", or "none". Head and feet auto-position
 * the bar each frame relative to the player (with X/Y offsets layered on top).
 * Alt+drag flips the target to "none" — the bar stays where the user dropped
 * it. The panel pill toggle lets the user re-anchor explicitly.
 *
 * Static mode only freezes the *color* (all glyphs use the static color); the
 * tick cycle still advances and the active glyph still moves through the slots.
 */
@SuppressWarnings("deprecation")
public class ChromatickHudOverlay extends Overlay
{
	private static final int BASE_GLYPH_PX = 10;
	// 1px margin around the row to leave room for the drop shadow.
	private static final int MARGIN_PX     = 2;

	private final ChromatickPlugin plugin;
	private final ChromatickConfig config;
	private final Client client;
	private final PaletteService palettes;

	/** The location we last set on the overlay ourselves; used to detect user drag. */
	private Point lastSetLocation = null;
	/** Last canvas dimensions; if they change between frames, skip drag-detection that frame. */
	private int lastCanvasW = -1;
	private int lastCanvasH = -1;

	@Inject
	ChromatickHudOverlay(ChromatickPlugin plugin, ChromatickConfig config, Client client, PaletteService palettes)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.palettes = palettes;
		setPosition(OverlayPosition.DYNAMIC);
		setMovable(true);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		final int cycleLength = plugin.getEffectiveCycleLength();
		if (cycleLength < 1)
		{
			return null;
		}

		// Track canvas dimensions — when the user resizes the RuneLite window,
		// the framework can nudge our overlay's position to keep it in bounds.
		// That nudge would otherwise look like a user drag, so skip drag
		// detection on the frame after a resize.
		final Canvas canvas = client.getCanvas();
		final int canvasW = canvas != null ? canvas.getWidth()  : 0;
		final int canvasH = canvas != null ? canvas.getHeight() : 0;
		final boolean canvasResized = (canvasW != lastCanvasW || canvasH != lastCanvasH);
		lastCanvasW = canvasW;
		lastCanvasH = canvasH;

		// Drag detection: if we *think* we're anchored but the framework's
		// preferred location no longer matches what we last set, the user must
		// have alt-dragged the overlay. Flip the target to "none" — preserves
		// the dragged position by virtue of not re-positioning each frame.
		String anchorTarget = config.hudAnchorTarget();
		boolean anchored = !"none".equals(anchorTarget);
		if (anchored && !canvasResized && lastSetLocation != null)
		{
			Point current = getPreferredLocation();
			if (current != null && !current.equals(lastSetLocation))
			{
				plugin.setHudAnchorTarget("none");
				lastSetLocation = null;
				anchored = false;
			}
		}

		final int currentTick = Math.floorMod(plugin.getTickIndex(), cycleLength);
		final Color[] palette = resolvePalette(cycleLength);
		final boolean cycleInPlace = config.hudCycleInPlace();

		final int configScalePct = clamp(config.hudScale(), 50, 400);
		final float scale        = configScalePct / 100f;
		final float popFactor    = 1f + clamp(config.hudPop(), 0, 200) / 100f;
		final float baseGlyphF   = BASE_GLYPH_PX * scale;
		final int   baseGlyph    = Math.max(6, Math.round(baseGlyphF));
		final int   cellSize     = Math.max(baseGlyph, Math.round(baseGlyphF * popFactor));
		// Spacing may be negative (overlap). Floor the main-axis length so the
		// overlay can't shrink to nothing. Spacing/direction are irrelevant in
		// cycle-in-place mode (only one glyph renders), but we still compute
		// them harmlessly.
		final int   gap          = Math.round(clamp(config.hudSpacing(), -10, 10) * scale);
		final boolean vertical   = config.hudVertical();

		// In cycle-in-place mode we render a single glyph that changes color
		// (and number) each tick — same footprint regardless of cycle length.
		final int slots = cycleInPlace ? 1 : cycleLength;
		final int mainAxisLen = Math.max(cellSize, slots * cellSize + (slots - 1) * gap);
		final int crossAxisLen = cellSize;
		final int totalW = (vertical ? crossAxisLen : mainAxisLen) + 2 * MARGIN_PX;
		final int totalH = (vertical ? mainAxisLen : crossAxisLen) + 2 * MARGIN_PX;

		// While anchored, place the overlay so the bar's center sits at the
		// configured offset from the player's head/feet. We always overwrite
		// the framework's preferred location so the HUD follows the player
		// every frame (and re-positions correctly after window resizes).
		if (anchored)
		{
			Point base = "head".equals(anchorTarget)
				? computePlayerHeadCanvasPoint()
				: computePlayerFeetCanvasPoint();
			if (base != null)
			{
				int xOff = config.hudHorizontalOffset();
				int yOff = config.hudVerticalOffset();
				int x = base.x + xOff - totalW / 2;
				int y = base.y + yOff - totalH / 2;
				Point newLoc = new Point(x, y);
				setPreferredLocation(newLoc);
				lastSetLocation = newLoc;
			}
		}

		final int activeAlpha   = pctToAlpha(config.hudActiveOpacity());
		final int inactiveAlpha = pctToAlpha(config.hudInactiveOpacity());
		final boolean useBold   = config.hudBold();
		final String glyphType  = config.hudGlyph();

		for (int k = 0; k < slots; k++)
		{
			// In cycle-in-place mode the single slot always shows the current
			// tick's palette entry and counts as "active". In row mode, slot k
			// maps to palette[k] and is active only when k == currentTick.
			final int paletteSlot = cycleInPlace ? currentTick : k;
			final boolean active  = cycleInPlace || (k == currentTick);
			final int alpha       = active ? activeAlpha : inactiveAlpha;
			final Color base      = palette[paletteSlot % palette.length];
			final Color glyphCol  = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);

			final int cellOffset = k * (cellSize + gap);
			final int cellX = MARGIN_PX + (vertical ? 0          : cellOffset);
			final int cellY = MARGIN_PX + (vertical ? cellOffset : 0         );
			final int cx    = cellX + cellSize / 2;
			final int cy    = cellY + cellSize / 2;

			// Active fills (cellSize - 2) so it doesn't kiss the cell boundary; inactive sits at baseGlyph.
			final int glyphSize = active ? Math.max(baseGlyph, cellSize - 2) : baseGlyph;

			if ("numbers".equals(glyphType))
			{
				renderNumber(g, paletteSlot + 1, cx, cy, glyphSize, glyphCol, active && useBold);
			}
			else
			{
				renderDot(g, cx, cy, glyphSize, glyphCol);
			}
		}

		return new Dimension(totalW, totalH);
	}

	/**
	 * Clear the drag-tracking state so the next render() re-anchors cleanly.
	 * Called from the plugin's setHudAnchorTarget after switching off "none".
	 */
	void clearDragState()
	{
		lastSetLocation = null;
	}

	/** Player's feet at ground level in canvas pixel space, or null if unavailable. */
	private Point computePlayerFeetCanvasPoint()
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return null;
		}
		LocalPoint lp = p.getLocalLocation();
		if (lp == null)
		{
			return null;
		}
		net.runelite.api.Point pt = Perspective.localToCanvas(
			client, lp, client.getTopLevelWorldView().getPlane());
		if (pt == null)
		{
			return null;
		}
		return new Point(pt.getX(), pt.getY());
	}

	/** Player's head (top of model) in canvas pixel space, or null if unavailable. */
	private Point computePlayerHeadCanvasPoint()
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return null;
		}
		LocalPoint lp = p.getLocalLocation();
		if (lp == null)
		{
			return null;
		}
		net.runelite.api.Point pt = Perspective.localToCanvas(
			client, lp, client.getTopLevelWorldView().getPlane(), p.getLogicalHeight());
		if (pt == null)
		{
			return null;
		}
		return new Point(pt.getX(), pt.getY());
	}

	private Color[] resolvePalette(int cycleLength)
	{
		if (config.staticMode())
		{
			Color sc = config.staticColor();
			Color opaque = new Color(sc.getRed(), sc.getGreen(), sc.getBlue());
			Color[] arr = new Color[cycleLength];
			for (int i = 0; i < cycleLength; i++)
			{
				arr[i] = opaque;
			}
			return arr;
		}
		return palettes.getCustomPaletteForCycle(cycleLength);
	}

	private void renderDot(Graphics2D g, int cx, int cy, int size, Color color)
	{
		// Fully transparent glyph → no shadow either. Without this guard the
		// shadow alpha would clamp up to 80 and leave a ghost at opacity 0.
		if (color.getAlpha() == 0)
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int shadowAlpha = Math.max(color.getAlpha(), 80);
		g2.setColor(new Color(0, 0, 0, shadowAlpha));
		g2.fillOval(cx - size / 2 + 1, cy - size / 2 + 1, size, size);

		g2.setColor(color);
		g2.fillOval(cx - size / 2, cy - size / 2, size, size);
		g2.dispose();
	}

	private void renderNumber(Graphics2D g, int number, int cx, int cy, int size, Color color, boolean bold)
	{
		if (color.getAlpha() == 0)
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Font font = FontManager.getRunescapeSmallFont();
		if (bold)
		{
			font = font.deriveFont(Font.BOLD);
		}
		font = font.deriveFont(Math.max(9f, size * 1.0f));
		g2.setFont(font);

		String s = String.valueOf(number);
		FontMetrics fm = g2.getFontMetrics();
		int textW = fm.stringWidth(s);
		int textH = fm.getAscent();
		int baseX = cx - textW / 2;
		int baseY = cy + textH / 2 - 1;

		int shadowAlpha = Math.max(color.getAlpha(), 80);
		g2.setColor(new Color(0, 0, 0, shadowAlpha));
		g2.drawString(s, baseX + 1, baseY + 1);
		g2.setColor(color);
		g2.drawString(s, baseX, baseY);
		g2.dispose();
	}

	private static int pctToAlpha(int pct)
	{
		return Math.round(clamp(pct, 0, 100) * 2.55f);
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
