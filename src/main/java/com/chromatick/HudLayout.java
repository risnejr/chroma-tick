package com.chromatick;

/**
 * Pure layout math for the HUD metronome bar. Computes cell/glyph sizing,
 * spacing and bounding dimensions from raw config inputs so the geometry
 * can be unit-tested without a real overlay/render context.
 *
 * <p>All inputs are plain ints/bools; outputs are the values
 * {@link ChromatickHudOverlay#render} historically derived inline.
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

	private HudLayout(int cellSize, int baseGlyph, int gap, int slots,
		boolean vertical, int totalWidth, int totalHeight)
	{
		this.cellSize = cellSize;
		this.baseGlyph = baseGlyph;
		this.gap = gap;
		this.slots = slots;
		this.vertical = vertical;
		this.totalWidth = totalWidth;
		this.totalHeight = totalHeight;
	}

	/**
	 * @param scalePct    config.hudScale() — clamped to [50, 400]
	 * @param popPct      config.hudPop() — clamped to [0, 200]
	 * @param spacingPx   config.hudSpacing() — clamped to [-10, 10]
	 * @param cycleLength current effective cycle length (>= 1)
	 * @param vertical    stack glyphs vertically vs. horizontally
	 * @param cycleInPlace render a single cycling glyph instead of a row
	 */
	static HudLayout compute(int scalePct, int popPct, int spacingPx,
		int cycleLength, boolean vertical, boolean cycleInPlace)
	{
		final float scale = clamp(scalePct, 50, 400) / 100f;
		final float popFactor = 1f + clamp(popPct, 0, 200) / 100f;
		final float baseGlyphF = BASE_GLYPH_PX * scale;
		final int baseGlyph = Math.max(6, Math.round(baseGlyphF));
		final int cellSize = Math.max(baseGlyph, Math.round(baseGlyphF * popFactor));
		final int gap = Math.round(clamp(spacingPx, -10, 10) * scale);

		// In cycle-in-place mode we render a single glyph that changes color
		// (and number) each tick — same footprint regardless of cycle length.
		final int slots = cycleInPlace ? 1 : cycleLength;
		// Spacing may be negative (overlap). Floor the main-axis length so the
		// overlay can't shrink to nothing.
		final int mainAxisLen = Math.max(cellSize, slots * cellSize + (slots - 1) * gap);
		final int crossAxisLen = cellSize;
		final int totalW = (vertical ? crossAxisLen : mainAxisLen) + 2 * MARGIN_PX;
		final int totalH = (vertical ? mainAxisLen : crossAxisLen) + 2 * MARGIN_PX;

		return new HudLayout(cellSize, baseGlyph, gap, slots, vertical, totalW, totalH);
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
		int cellY = MARGIN_PX + (vertical ? cellOffset : 0);
		return cellY + cellSize / 2;
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

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
