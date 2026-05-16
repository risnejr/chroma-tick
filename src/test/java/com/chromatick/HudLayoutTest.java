package com.chromatick;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the pure HUD-bar geometry. Pin down the formulas that previously
 * lived inline in {@link ChromatickHudOverlay#render}, so a future tweak
 * (e.g. changing margin/base glyph size) can be reasoned about without
 * launching the client.
 */
public class HudLayoutTest
{
	private static final int MARGIN = HudLayout.MARGIN_PX;

	// ─── Sizing ──────────────────────────────────────────────────────────

	@Test
	public void defaultScaleProducesBaseGlyphAndCell()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false);

		assertEquals(HudLayout.BASE_GLYPH_PX, l.baseGlyph);
		assertEquals(HudLayout.BASE_GLYPH_PX, l.cellSize);
		assertEquals(0, l.gap);
		assertEquals(4, l.slots);
	}

	@Test
	public void scaleMultipliesBaseGlyphAndCell()
	{
		HudLayout l = HudLayout.compute(200, 0, 0, 4, false, false);

		// scale = 2, baseGlyphF = 20 → baseGlyph = 20, cellSize = 20.
		assertEquals(20, l.baseGlyph);
		assertEquals(20, l.cellSize);
	}

	@Test
	public void scaleIsClampedBelow50AndAbove400()
	{
		HudLayout tiny = HudLayout.compute(10, 0, 0, 4, false, false);
		HudLayout huge = HudLayout.compute(9999, 0, 0, 4, false, false);

		// scale = 0.5 → baseGlyphF = 5, baseGlyph = max(6, 5) = 6.
		assertEquals(6, tiny.baseGlyph);
		// scale = 4 → baseGlyphF = 40, baseGlyph = 40.
		assertEquals(40, huge.baseGlyph);
	}

	@Test
	public void baseGlyphIsFlooredAtSixPixels()
	{
		HudLayout l = HudLayout.compute(50, 0, 0, 4, false, false);
		assertEquals(6, l.baseGlyph);
	}

	@Test
	public void popExpandsCellSizeButNotBaseGlyph()
	{
		HudLayout l = HudLayout.compute(100, 100, 0, 4, false, false);

		// popFactor = 2 → cellSize = 20, baseGlyph stays 10.
		assertEquals(10, l.baseGlyph);
		assertEquals(20, l.cellSize);
	}

	@Test
	public void popIsClampedAbove200()
	{
		HudLayout l = HudLayout.compute(100, 9999, 0, 4, false, false);
		// popFactor = 3 → cellSize = 30
		assertEquals(30, l.cellSize);
	}

	@Test
	public void negativePopIsClampedToZero()
	{
		HudLayout l = HudLayout.compute(100, -50, 0, 4, false, false);
		// popFactor = 1 → cellSize = baseGlyph
		assertEquals(l.baseGlyph, l.cellSize);
	}

	// ─── Gap / spacing ───────────────────────────────────────────────────

	@Test
	public void positiveSpacingScalesWithSizeScale()
	{
		HudLayout doubled = HudLayout.compute(200, 0, 5, 4, false, false);
		// gap = round(5 * 2) = 10
		assertEquals(10, doubled.gap);
	}

	@Test
	public void negativeSpacingIsAllowed()
	{
		HudLayout l = HudLayout.compute(100, 0, -3, 4, false, false);
		assertEquals(-3, l.gap);
	}

	@Test
	public void spacingIsClampedToPlusMinusTen()
	{
		HudLayout big = HudLayout.compute(100, 0, 9999, 4, false, false);
		HudLayout sml = HudLayout.compute(100, 0, -9999, 4, false, false);
		assertEquals(10, big.gap);
		assertEquals(-10, sml.gap);
	}

	// ─── Bounding box ────────────────────────────────────────────────────

	@Test
	public void horizontalBoxIsRowOfCellsPlusMargin()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false);
		// 4 * 10 + 0 = 40 wide; 10 tall; +2 margin each side.
		assertEquals(40 + 2 * MARGIN, l.totalWidth);
		assertEquals(10 + 2 * MARGIN, l.totalHeight);
	}

	@Test
	public void verticalBoxIsColumnOfCellsPlusMargin()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, true, false);
		assertEquals(10 + 2 * MARGIN, l.totalWidth);
		assertEquals(40 + 2 * MARGIN, l.totalHeight);
	}

	@Test
	public void boundingBoxIncludesGapsBetweenCells()
	{
		HudLayout l = HudLayout.compute(100, 0, 4, 4, false, false);
		// mainAxis = 4 * 10 + 3 * 4 = 52
		assertEquals(52 + 2 * MARGIN, l.totalWidth);
	}

	@Test
	public void boundingBoxFloorsAtOneCellEvenWithNegativeOverlap()
	{
		// 1 slot * 10 + 0 * (-10) = 10, then max(cellSize=10, 10) = 10 — but with
		// 4 slots and gap=-10: 4*10 + 3*(-10) = 10, max with cellSize=10 → 10.
		HudLayout l = HudLayout.compute(100, 0, -10, 4, false, false);
		assertEquals(10 + 2 * MARGIN, l.totalWidth);
		// And with strong overlap the floor stays at one cellSize.
		HudLayout overlap = HudLayout.compute(100, 0, -10, 2, false, false);
		// 2*10 + 1*(-10) = 10
		assertEquals(10 + 2 * MARGIN, overlap.totalWidth);
	}

	@Test
	public void cycleInPlaceCollapsesToOneSlot()
	{
		HudLayout l = HudLayout.compute(100, 0, 5, 8, false, true);
		assertEquals(1, l.slots);
		assertEquals(10 + 2 * MARGIN, l.totalWidth);
		assertEquals(10 + 2 * MARGIN, l.totalHeight);
	}

	// ─── Per-slot positions ──────────────────────────────────────────────

	@Test
	public void firstCellCenterSitsAtMarginPlusHalfCell()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false);
		assertEquals(MARGIN + 5, l.cellCenterX(0));
		assertEquals(MARGIN + 5, l.cellCenterY(0));
	}

	@Test
	public void horizontalCellsAdvanceOnX()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false);
		// Each cell is 10 wide, gap 0.
		assertEquals(MARGIN + 5,           l.cellCenterX(0));
		assertEquals(MARGIN + 5 + 10,      l.cellCenterX(1));
		assertEquals(MARGIN + 5 + 20,      l.cellCenterX(2));
		assertEquals(MARGIN + 5 + 30,      l.cellCenterX(3));
		// Y stays put.
		assertEquals(MARGIN + 5, l.cellCenterY(0));
		assertEquals(MARGIN + 5, l.cellCenterY(3));
	}

	@Test
	public void verticalCellsAdvanceOnY()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, true, false);
		assertEquals(MARGIN + 5,      l.cellCenterX(0));
		assertEquals(MARGIN + 5,      l.cellCenterX(3));
		assertEquals(MARGIN + 5,           l.cellCenterY(0));
		assertEquals(MARGIN + 5 + 10,      l.cellCenterY(1));
		assertEquals(MARGIN + 5 + 30,      l.cellCenterY(3));
	}

	@Test
	public void cellAdvanceIncludesGap()
	{
		HudLayout l = HudLayout.compute(100, 0, 4, 4, false, false);
		// Step = cellSize(10) + gap(4) = 14.
		assertEquals(MARGIN + 5,           l.cellCenterX(0));
		assertEquals(MARGIN + 5 + 14,      l.cellCenterX(1));
		assertEquals(MARGIN + 5 + 28,      l.cellCenterX(2));
	}

	// ─── Glyph sizing ────────────────────────────────────────────────────

	@Test
	public void activeGlyphMatchesBaseWhenPopIsZero()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false);
		// cellSize == baseGlyph; max(baseGlyph, cellSize - 2) = max(10, 8) = 10.
		assertEquals(l.baseGlyph, l.glyphSize(true));
		assertEquals(l.baseGlyph, l.glyphSize(false));
	}

	@Test
	public void activeGlyphExpandsToCellMinusTwoWhenPopApplied()
	{
		HudLayout l = HudLayout.compute(100, 100, 0, 4, false, false);
		// cellSize = 20, baseGlyph = 10.
		assertEquals(18, l.glyphSize(true));
		assertEquals(10, l.glyphSize(false));
	}

	@Test
	public void glyphSizeNeverDropsBelowBase()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false);
		assertTrue(l.glyphSize(true) >= l.baseGlyph);
		assertTrue(l.glyphSize(false) >= l.baseGlyph);
	}
}
