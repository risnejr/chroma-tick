package com.chromatick;

/**
 * Pure layout math for the HUD metronome bar. Computes cell/glyph sizing,
 * spacing and bounding dimensions from raw config inputs so the geometry
 * can be unit-tested without a real overlay/render context.
 *
 * <p>Optionally reserves an icon band alongside the glyph row, used by the
 * per-tick prayer recorder. The band is only present in row layouts (i.e.
 * not cycle-in-place) and uses the cell size as its cross-axis thickness,
 * so icons stay proportional to the glyphs.
 *
 * <p>Orientation:
 * <ul>
 *   <li>Horizontal row: icons go ABOVE or BELOW the glyph row.
 *   <li>Vertical column: icons go to the LEFT (ABOVE) or RIGHT (BELOW) of
 *       the glyph column.
 * </ul>
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
	/** Cross-axis thickness of the icon band in px; 0 when {@link #showIcons} is false. */
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
	 * Bar with an optional icon band alongside the glyph row.
	 *
	 * <p>The icon band is only honored in row layouts — cycle-in-place mode
	 * forces {@code showIcons=false} regardless of the {@code wantIcons}
	 * argument (a single glyph has no timeline to align icons against).
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

		// Icons need a timeline to align against — disable in cycle-in-place mode.
		final boolean showIcons = wantIcons && !cycleInPlace;
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
		int cellOffset = vertical ? 0 : k * (cellSize + gap);
		int iconShift = (showIcons && vertical && iconPosition == IconPosition.ABOVE)
			? iconBandPx : 0;
		return MARGIN_PX + iconShift + cellOffset + cellSize / 2;
	}

	/** Y center of slot {@code k} relative to the overlay's top-left. */
	int cellCenterY(int k)
	{
		int cellOffset = vertical ? k * (cellSize + gap) : 0;
		int iconShift = (showIcons && !vertical && iconPosition == IconPosition.ABOVE)
			? iconBandPx : 0;
		return MARGIN_PX + iconShift + cellOffset + cellSize / 2;
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

	/** X center of the icon for slot {@code k}, or -1 when no icon band is shown. */
	int iconCenterX(int k)
	{
		if (!showIcons)
		{
			return -1;
		}
		if (vertical)
		{
			// Icon column to the LEFT (ABOVE) or RIGHT (BELOW) of the glyph column.
			return iconPosition == IconPosition.ABOVE
				? MARGIN_PX + iconBandPx / 2
				: MARGIN_PX + cellSize + iconBandPx / 2;
		}
		// Horizontal: icons align with their tick column.
		return cellCenterX(k);
	}

	/** Y center of the icon for slot {@code k}, or -1 when no icon band is shown. */
	int iconCenterY(int k)
	{
		if (!showIcons)
		{
			return -1;
		}
		if (vertical)
		{
			// Icons share the glyph row's Y in vertical mode.
			return cellCenterY(k);
		}
		// Horizontal: icon row sits ABOVE or BELOW the glyph row.
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
