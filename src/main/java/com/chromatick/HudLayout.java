package com.chromatick;

/**
 * Pure layout math for the HUD metronome bar. Computes cell/glyph sizing,
 * spacing and bounding dimensions from raw config inputs so the geometry
 * can be unit-tested without a real overlay/render context.
 *
 * <p>Optionally reserves an icon band alongside the glyph row, used by the
 * per-tick recorder. The band is only present in row layouts (i.e. not
 * cycle-in-place) and uses the cell size as its cross-axis thickness, so
 * icons stay proportional to the glyphs.
 *
 * <p>When {@code comboLayout} is true, a <em>second</em> icon band is
 * reserved on the opposite side of the glyph row. Combo events
 * (ITEM_USE source + target) render their primary in the position
 * {@link IconPosition} dictates and the secondary directly across the
 * glyph row. Non-combo slots in a combo-capable layout simply leave the
 * secondary band empty.
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
	/** True when a second icon band is reserved on the opposite side of the glyph row. */
	final boolean comboLayout;
	/** Cross-axis thickness of one icon band in px; 0 when {@link #showIcons} is false. */
	final int iconBandPx;
	final IconPosition iconPosition;

	private HudLayout(int cellSize, int baseGlyph, int gap, int slots,
		boolean vertical, int totalWidth, int totalHeight,
		boolean showIcons, boolean comboLayout, int iconBandPx, IconPosition iconPosition)
	{
		this.cellSize = cellSize;
		this.baseGlyph = baseGlyph;
		this.gap = gap;
		this.slots = slots;
		this.vertical = vertical;
		this.totalWidth = totalWidth;
		this.totalHeight = totalHeight;
		this.showIcons = showIcons;
		this.comboLayout = comboLayout;
		this.iconBandPx = iconBandPx;
		this.iconPosition = iconPosition;
	}

	/** Bar without an icon band — original geometry. */
	static HudLayout compute(int scalePct, int popPct, int spacingPx,
		int cycleLength, boolean vertical, boolean cycleInPlace)
	{
		return compute(scalePct, popPct, spacingPx, cycleLength,
			vertical, cycleInPlace, false, IconPosition.ABOVE, false);
	}

	/** Bar with an optional icon band alongside the glyph row, no combo support. */
	static HudLayout compute(int scalePct, int popPct, int spacingPx,
		int cycleLength, boolean vertical, boolean cycleInPlace,
		boolean wantIcons, IconPosition iconPosition)
	{
		return compute(scalePct, popPct, spacingPx, cycleLength,
			vertical, cycleInPlace, wantIcons, iconPosition, false);
	}

	/**
	 * Bar with an optional icon band, optionally combo-capable.
	 *
	 * <p>The icon band is only honored in row layouts — cycle-in-place mode
	 * forces {@code showIcons=false} regardless of the {@code wantIcons}
	 * argument (a single glyph has no timeline to align icons against).
	 * Combo layout requires {@code showIcons} to be effective — when icons
	 * are off, the combo flag is moot.
	 */
	static HudLayout compute(int scalePct, int popPct, int spacingPx,
		int cycleLength, boolean vertical, boolean cycleInPlace,
		boolean wantIcons, IconPosition iconPosition, boolean comboCapable)
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
		final boolean comboLayout = showIcons && comboCapable;
		final int iconBandPx = showIcons ? cellSize : 0;
		final int totalIconPx = iconBandPx * (comboLayout ? 2 : (showIcons ? 1 : 0));

		final int crossAxisLen = cellSize + totalIconPx;
		final int totalW = (vertical ? crossAxisLen : mainAxisLen) + 2 * MARGIN_PX;
		final int totalH = (vertical ? mainAxisLen : crossAxisLen) + 2 * MARGIN_PX;

		return new HudLayout(cellSize, baseGlyph, gap, slots, vertical,
			totalW, totalH, showIcons, comboLayout, iconBandPx, iconPosition);
	}

	/** X center of slot {@code k} relative to the overlay's top-left. */
	int cellCenterX(int k)
	{
		int cellOffset = vertical ? 0 : k * (cellSize + gap);
		// Vertical mode shifts the glyph column right when an icon band sits
		// on the left of the column. The "primary on left" case is iconPosition=ABOVE;
		// in combo layout an icon band always sits on the left regardless of position.
		int iconShift = (showIcons && vertical && hasIconOnLeftOrTop()) ? iconBandPx : 0;
		return MARGIN_PX + iconShift + cellOffset + cellSize / 2;
	}

	/** Y center of slot {@code k} relative to the overlay's top-left. */
	int cellCenterY(int k)
	{
		int cellOffset = vertical ? k * (cellSize + gap) : 0;
		int iconShift = (showIcons && !vertical && hasIconOnLeftOrTop()) ? iconBandPx : 0;
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

	/** X center of the primary icon for slot {@code k}, or -1 when no icon band is shown. */
	int iconCenterX(int k)
	{
		if (!showIcons)
		{
			return -1;
		}
		if (vertical)
		{
			// Primary icon column: ABOVE → left, BELOW → right.
			return iconPosition == IconPosition.ABOVE
				? MARGIN_PX + iconBandPx / 2
				: MARGIN_PX + (comboLayout ? iconBandPx : 0) + cellSize + iconBandPx / 2;
		}
		// Horizontal: primary icons align with their tick column.
		return cellCenterX(k);
	}

	/** Y center of the primary icon for slot {@code k}, or -1 when no icon band is shown. */
	int iconCenterY(int k)
	{
		if (!showIcons)
		{
			return -1;
		}
		if (vertical)
		{
			// Horizontal axis split; primary shares the glyph row's Y.
			return cellCenterY(k);
		}
		// Primary band: ABOVE → top, BELOW → bottom.
		return iconPosition == IconPosition.ABOVE
			? MARGIN_PX + iconBandPx / 2
			: MARGIN_PX + (comboLayout ? iconBandPx : 0) + cellSize + iconBandPx / 2;
	}

	/**
	 * X center of the secondary (combo) icon for slot {@code k}, or -1 when
	 * the layout isn't combo-capable. Lives on the opposite side of the
	 * glyph row from the primary.
	 */
	int comboIconCenterX(int k)
	{
		if (!comboLayout)
		{
			return -1;
		}
		if (vertical)
		{
			// Secondary column: opposite side of primary.
			return iconPosition == IconPosition.ABOVE
				? MARGIN_PX + iconBandPx + cellSize + iconBandPx / 2
				: MARGIN_PX + iconBandPx / 2;
		}
		return cellCenterX(k);
	}

	/** Y center of the secondary (combo) icon for slot {@code k}, or -1 when not combo-capable. */
	int comboIconCenterY(int k)
	{
		if (!comboLayout)
		{
			return -1;
		}
		if (vertical)
		{
			return cellCenterY(k);
		}
		return iconPosition == IconPosition.ABOVE
			? MARGIN_PX + iconBandPx + cellSize + iconBandPx / 2
			: MARGIN_PX + iconBandPx / 2;
	}

	/** Icon side length in px, slightly inset within the band. */
	int iconSize()
	{
		return showIcons ? Math.max(6, iconBandPx - 2) : 0;
	}

	/**
	 * Whether an icon band sits to the left of (vertical) / above (horizontal)
	 * the glyph row. True in combo layout always, or when iconPosition=ABOVE.
	 */
	private boolean hasIconOnLeftOrTop()
	{
		return comboLayout || iconPosition == IconPosition.ABOVE;
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
