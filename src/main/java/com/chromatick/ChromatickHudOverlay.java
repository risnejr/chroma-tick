package com.chromatick;

import com.chromatick.Enums.*;
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
 * Anchoring: target is HEAD, FEET, or NONE. HEAD and FEET auto-position the
 * bar each frame relative to the player (with X/Y offsets layered on top).
 * Alt+drag flips the target to NONE — the bar stays where the user dropped
 * it. The panel pill toggle lets the user re-anchor explicitly.
 *
 * Static mode only freezes the *color* (all glyphs use the static color); the
 * tick cycle still advances and the active glyph still moves through the slots.
 */
@SuppressWarnings("deprecation")
public class ChromatickHudOverlay extends Overlay
{
	private final ChromatickPlugin plugin;
	private final ChromatickConfig config;
	private final Client client;
	private final PaletteService palettes;
	private final TickRecorderService recorder;
	private final RecordedIconResolver iconResolver;

	/** The location we last set on the overlay ourselves; used to detect user drag. */
	private Point lastSetLocation = null;
	/** Last canvas dimensions; if they change between frames, skip drag-detection that frame. */
	private int lastCanvasW = -1;
	private int lastCanvasH = -1;

	/**
	 * Signal that render() detected an alt-drag. The plugin drains this flag
	 * on its next game tick and flips the anchor target to NONE. Volatile
	 * because clearDragState() may run on the Swing EDT (panel callbacks)
	 * while render runs on the client thread.
	 */
	private volatile boolean userDragged = false;

	@Inject
	ChromatickHudOverlay(ChromatickPlugin plugin, ChromatickConfig config, Client client,
		PaletteService palettes, TickRecorderService recorder, RecordedIconResolver iconResolver)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.palettes = palettes;
		this.recorder = recorder;
		this.iconResolver = iconResolver;
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
		// have alt-dragged the overlay. Set userDragged so subsequent frames
		// stop re-anchoring; the plugin observes the flag on its next game
		// tick and flips the persisted anchor target to NONE.
		HudAnchorTarget anchorTarget = config.hudAnchorTarget();
		boolean anchored = anchorTarget != HudAnchorTarget.NONE && !userDragged;
		if (anchored && wasDragged(canvasResized, lastSetLocation, getPreferredLocation()))
		{
			userDragged = true;
			lastSetLocation = null;
			anchored = false;
		}

		final int currentTick = Math.floorMod(plugin.getTickIndex(), cycleLength);
		final Color[] palette = resolvePalette(cycleLength);
		final boolean cycleInPlace = config.hudCycleInPlace();
		final RecordMode recordMode = recorder.getMode();
		// Keep icons visible after a one-shot ARM has auto-exited so the user
		// can review the recording. Hidden only when mode is OFF AND there's
		// nothing to show.
		final boolean wantIcons = recordMode != RecordMode.OFF || recorder.hasCaptures();
		// Combo layout reserves a second icon band so ITEM_USE renders
		// source + target on opposite sides of the glyph row instead of
		// cramming both into one slot.
		final boolean comboCapable = plugin.enabledRecordCategories()
			.contains(TickActionCategory.ITEM_USE);

		final HudLayout layout = HudLayout.compute(
			config.hudScale(),
			config.hudPop(),
			config.hudSpacing(),
			cycleLength,
			config.hudVertical(),
			cycleInPlace,
			wantIcons,
			config.recordIconPosition(),
			comboCapable
		);

		if (layout.showIcons)
		{
			iconResolver.ensureLoaded();
		}

		// While anchored, place the overlay so the bar's center sits at the
		// configured offset from the player's head/feet. We always overwrite
		// the framework's preferred location so the HUD follows the player
		// every frame (and re-positions correctly after window resizes).
		if (anchored)
		{
			Point base = anchorTarget == HudAnchorTarget.HEAD
				? computePlayerHeadCanvasPoint()
				: computePlayerFeetCanvasPoint();
			if (base != null)
			{
				int xOff = config.hudHorizontalOffset();
				int yOff = config.hudVerticalOffset();
				int x = base.x + xOff - layout.totalWidth / 2;
				int y = base.y + yOff - layout.totalHeight / 2;
				Point newLoc = new Point(x, y);
				setPreferredLocation(newLoc);
				lastSetLocation = newLoc;
			}
		}

		final int activeAlpha   = pctToAlpha(config.hudActiveOpacity());
		final int inactiveAlpha = pctToAlpha(config.hudInactiveOpacity());
		final boolean useBold   = config.hudBold();
		final HudGlyph glyphType = config.hudGlyph();

		for (int k = 0; k < layout.slots; k++)
		{
			// In cycle-in-place mode the single slot always shows the current
			// tick's palette entry and counts as "active". In row mode, slot k
			// maps to palette[k] and is active only when k == currentTick.
			final int paletteSlot = cycleInPlace ? currentTick : k;
			final boolean active  = cycleInPlace || (k == currentTick);
			final int alpha       = active ? activeAlpha : inactiveAlpha;
			final Color base      = palette[paletteSlot % palette.length];
			final Color glyphCol  = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);

			final int cx = layout.cellCenterX(k);
			final int cy = layout.cellCenterY(k);
			final int glyphSize = layout.glyphSize(active);

			if (glyphType == HudGlyph.NUMBERS)
			{
				renderNumber(g, paletteSlot + 1, cx, cy, glyphSize, glyphCol, active && useBold);
			}
			else
			{
				renderDot(g, cx, cy, glyphSize, glyphCol);
			}

			// Recorded prayer icon for this tick (row mode only).
			if (layout.showIcons)
			{
				renderRecordedIcon(g, layout, k);
			}
		}

		// Minimal mode indicator — tiny colored dot at the top-left corner
		// when the recorder isn't OFF. Sits inside the bounding box so the
		// framework's drag-tracking sees it as part of the overlay.
		if (recordMode != RecordMode.OFF)
		{
			renderModeIndicator(g, recordMode);
		}

		return new Dimension(layout.totalWidth, layout.totalHeight);
	}

	/**
	 * Clear the drag-tracking state so the next render() re-anchors cleanly.
	 * Called from the plugin's setHudAnchorTarget after switching off NONE,
	 * and on plugin startUp when re-adding the overlay.
	 */
	void clearDragState()
	{
		lastSetLocation = null;
		userDragged = false;
	}

	/**
	 * Read-and-clear: returns {@code true} once for each render-detected drag,
	 * then resets the flag. The plugin calls this on each game tick so the
	 * persisted anchor target can flip to NONE without render mutating state.
	 */
	boolean consumeUserDragged()
	{
		if (userDragged)
		{
			userDragged = false;
			return true;
		}
		return false;
	}

	/**
	 * True when the framework's preferred location no longer matches what we
	 * last set, meaning the user alt-dragged the overlay. Pure predicate —
	 * extracted from render() so the edge cases (window resize, null seed
	 * location, missing preferred location) can be pinned down with unit
	 * tests without standing up a render context.
	 */
	static boolean wasDragged(boolean canvasResized, Point lastSet, Point current)
	{
		if (canvasResized || lastSet == null || current == null)
		{
			return false;
		}
		return !current.equals(lastSet);
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

	// ─── Per-tick recorder ──────────────────────────────────────────────

	/**
	 * Render the recorded icon (if any) for tick slot {@code k}. Sprite,
	 * combo (two sprites), or primitive-dot is decided in
	 * {@link RecordedIconResolver}; the overlay just draws what it gets.
	 */
	private void renderRecordedIcon(Graphics2D g, HudLayout layout, int k)
	{
		RecordedIcon icon = iconResolver.iconFor(recorder.getRecordedAt(k));
		if (icon == null)
		{
			return;
		}
		int size = layout.glyphSize(false);
		int cx = layout.iconCenterX(k);
		int cy = layout.iconCenterY(k);
		if (icon.sprite != null)
		{
			g.drawImage(icon.sprite, cx - size / 2, cy - size / 2, size, size, null);
		}
		else
		{
			// Primitive dot — antialiased filled oval at the icon slot.
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(icon.primitiveColor);
			g2.fillOval(cx - size / 2, cy - size / 2, size, size);
			g2.dispose();
		}
		if (icon.secondarySprite != null && layout.comboLayout)
		{
			int sx = layout.comboIconCenterX(k);
			int sy = layout.comboIconCenterY(k);
			g.drawImage(icon.secondarySprite, sx - size / 2, sy - size / 2, size, size, null);
		}
	}

	private void renderModeIndicator(Graphics2D g, RecordMode mode)
	{
		Color dotColor;
		switch (mode)
		{
			case ARM:    dotColor = new Color(255, 180, 30); break;  // amber
			case ALWAYS: dotColor = new Color(220, 50,  50); break;  // red
			default: return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// 4px dot inset 1px from the top-left of the bounding box.
		g2.setColor(new Color(0, 0, 0, 160));
		g2.fillOval(2, 2, 5, 5);
		g2.setColor(dotColor);
		g2.fillOval(2, 2, 4, 4);
		g2.dispose();
	}
}
