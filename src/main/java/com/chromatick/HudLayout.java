package com.chromatick;

/**
 * Pure layout math for the HUD metronome bar. Computes cell/glyph sizing,
 * spacing and bounding dimensions from raw config inputs so the geometry
 * can be unit-tested without a real overlay/render context.
 *
 * <p>Optionally reserves an icon band above or below the glyph row, used
 * by the per-tick prayer recorder. The icon band is only present in
 * horizontal row mode (i.e. not vertical, not cycle-in-place) and uses the
 * cell size as its height, so icons stay proportional to the glyphs.
 */
final class HudLayout
{
	static final int BASE_GLYPH_PX = 10;
	/** 1px margin around the row to leave room for the drop shadow. */
	static final int MARGIN_PX = 2;

	final int cellSize;
	final int baseGlyph;
	final int gap;
	final int slots;
	final boolean vertical;
	final int totalWidth;
	final int totalHeight;

	/** True when an icon band is present (and large enough to render). */
	final boolean showIcons;
	/** Height of the icon band in px; 0 when {@link #showIcons} is false. */
	final int iconBandPx;
	final IconPosition iconPosition;

	private HudLayout(int cellSize, int baseGlyph, int gap, int slots,
		boolean vertical, int totalWidth, int totalHeight,
		boolean showIcons, int iconBandPx, IconPosition iconPosition)
	{
		this.cellSize = cellSize;
		this.baseGlyph = baseGlyph;
		this.gap = gap;
		this.slots = slots;
		this.vertical = vertical;
		this.totalWidth = totalWidth;
		this.totalHeight = totalHeight;
		this.showIcons = showIcons;
		this.iconBandPx = iconBandPx;
		this.iconPosition = iconPosition;
	}

	/** Bar without an icon band — original geometry. */
	static HudLayout compute(int scalePct, int popPct, int spacingPx,
		int cycleLength, boolean vertical, boolean cycleInPlace)
	{
		return compute(scalePct, popPct, spacingPx, cycleLength,
			vertical, cycleInPlace, false, IconPosition.ABOVE);
	}

	/**
	 * Bar with an optional icon band above or below the glyph row.
	 *
	 * <p>The icon band is only honored in horizontal row mode — vertical
	 * orientation and cycle-in-place mode force {@code showIcons=false}
	 * regardless of the {@code wantIcons} argument.
	 */
	static HudLayout compute(int scalePct, int popPct, int spacingPx,
		int cycleLength, boolean vertical, boolean cycleInPlace,
		boolean wantIcons, IconPosition iconPosition)
	{
		final float scale = clamp(scalePct, 50, 400) / 100f;
		final float popFactor = 1f + clamp(popPct, 0, 200) / 100f;
		final float baseGlyphF = BASE_GLYPH_PX * scale;
		final int baseGlyph = Math.max(6, Math.round(baseGlyphF));
		final int cellSize = Math.max(baseGlyph, Math.round(baseGlyphF * popFactor));
		final int gap = Math.round(clamp(spacingPx, -10, 10) * scale);

		final int slots = cycleInPlace ? 1 : cycleLength;
		final int mainAxisLen = Math.max(cellSize, slots * cellSize + (slots - 1) * gap);

		// Icons only make sense alongside a timeline — disable in vertical/in-place modes.
		final boolean showIcons = wantIcons && !vertical && !cycleInPlace;
		final int iconBandPx = showIcons ? cellSize : 0;

		final int crossAxisLen = cellSize + iconBandPx;
		final int totalW = (vertical ? crossAxisLen : mainAxisLen) + 2 * MARGIN_PX;
		final int totalH = (vertical ? mainAxisLen : crossAxisLen) + 2 * MARGIN_PX;

		return new HudLayout(cellSize, baseGlyph, gap, slots, vertical,
			totalW, totalH, showIcons, iconBandPx, iconPosition);
	}

	/** X center of slot {@code k} relative to the overlay's top-left. */
	int cellCenterX(int k)
	{
		int cellOffset = k * (cellSize + gap);
		int cellX = MARGIN_PX + (vertical ? 0 : cellOffset);
		return cellX + cellSize / 2;
	}

	/** Y center of slot {@code k} relative to the overlay's top-left. */
	int cellCenterY(int k)
	{
		int cellOffset = k * (cellSize + gap);
		// When the icon band sits above the row, the glyph row shifts down by iconBandPx.
		int rowTopY = MARGIN_PX
			+ (showIcons && iconPosition == IconPosition.ABOVE ? iconBandPx : 0);
		return rowTopY + (vertical ? cellOffset : 0) + cellSize / 2;
	}

	/**
	 * Glyph diameter (px). The active glyph expands to fill the cell so the
	 * pop effect is visible; inactive glyphs sit at the base size so the row
	 * stays visually quiet.
	 */
	int glyphSize(boolean active)
	{
		return active ? Math.max(baseGlyph, cellSize - 2) : baseGlyph;
	}

	/** Center Y of the icon band, or -1 when no icon band is shown. */
	int iconCenterY()
	{
		if (!showIcons)
		{
			return -1;
		}
		return iconPosition == IconPosition.ABOVE
			? MARGIN_PX + iconBandPx / 2
			: MARGIN_PX + cellSize + iconBandPx / 2;
	}

	/** Icon side length in px, slightly inset within the band. */
	int iconSize()
	{
		return showIcons ? Math.max(6, iconBandPx - 2) : 0;
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
