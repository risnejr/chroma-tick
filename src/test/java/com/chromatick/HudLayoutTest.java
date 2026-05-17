package com.chromatick;

import com.chromatick.Enums.*;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

	// ─── Icon band ───────────────────────────────────────────────────────

	@Test
	public void noIconBandWhenWantIconsFalse()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false);
		assertFalse(l.showIcons);
		assertEquals(0, l.iconBandPx);
		assertEquals(-1, l.iconCenterY(0));
		assertEquals(-1, l.iconCenterX(0));
		assertEquals(10 + 2 * MARGIN, l.totalHeight);
	}

	@Test
	public void iconBandAddsCellSizeToHeightWhenAbove()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false, true, IconPosition.ABOVE);
		assertTrue(l.showIcons);
		assertEquals(10, l.iconBandPx);
		// Glyph row(10) + icon band(10) + margins(4) = 24.
		assertEquals(24, l.totalHeight);
		// Glyph row shifts down to make room for the icon band.
		assertEquals(MARGIN + 10 + 5, l.cellCenterY(0));
		// Icons center within their band at the top — at every column.
		assertEquals(MARGIN + 5, l.iconCenterY(0));
		assertEquals(MARGIN + 5, l.iconCenterY(3));
		// Icon X aligns with the glyph column's X.
		assertEquals(l.cellCenterX(0), l.iconCenterX(0));
		assertEquals(l.cellCenterX(3), l.iconCenterX(3));
	}

	@Test
	public void iconBandAddsCellSizeToHeightWhenBelow()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false, true, IconPosition.BELOW);
		assertTrue(l.showIcons);
		assertEquals(10, l.iconBandPx);
		assertEquals(24, l.totalHeight);
		// Glyph row stays put.
		assertEquals(MARGIN + 5, l.cellCenterY(0));
		// Icons sit under the glyph row.
		assertEquals(MARGIN + 10 + 5, l.iconCenterY(0));
		assertEquals(l.cellCenterX(0), l.iconCenterX(0));
	}

	@Test
	public void iconBandWorksInVerticalModeAsLeftColumnWhenAbove()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, true, false, true, IconPosition.ABOVE);
		assertTrue(l.showIcons);
		assertEquals(10, l.iconBandPx);
		// Cross-axis is now WIDTH: cellSize(10) + iconBandPx(10) + margins(4) = 24.
		assertEquals(24, l.totalWidth);
		// Glyph column shifts right to make room for the icon column on the left.
		assertEquals(MARGIN + 10 + 5, l.cellCenterX(0));
		// Icons sit in the left column, sharing the glyph column's Y.
		assertEquals(MARGIN + 5, l.iconCenterX(0));
		assertEquals(MARGIN + 5, l.iconCenterX(3));
		assertEquals(l.cellCenterY(0), l.iconCenterY(0));
		assertEquals(l.cellCenterY(3), l.iconCenterY(3));
	}

	@Test
	public void iconBandWorksInVerticalModeAsRightColumnWhenBelow()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, true, false, true, IconPosition.BELOW);
		assertTrue(l.showIcons);
		assertEquals(24, l.totalWidth);
		// Glyph column stays on the left.
		assertEquals(MARGIN + 5, l.cellCenterX(0));
		// Icons sit in the right column.
		assertEquals(MARGIN + 10 + 5, l.iconCenterX(0));
		assertEquals(l.cellCenterY(0), l.iconCenterY(0));
	}

	@Test
	public void iconBandIsSuppressedInCycleInPlaceMode()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, true, true, IconPosition.ABOVE);
		assertFalse(l.showIcons);
		assertEquals(0, l.iconBandPx);
	}

	@Test
	public void iconBandScalesWithGlyphScale()
	{
		HudLayout l = HudLayout.compute(200, 0, 0, 4, false, false, true, IconPosition.ABOVE);
		// cellSize = 20 → iconBandPx = 20, totalH = 20 + 20 + 2*MARGIN = 44.
		assertEquals(20, l.iconBandPx);
		assertEquals(20 + 20 + 2 * MARGIN, l.totalHeight);
	}

	@Test
	public void iconCenterXAlignsWithCellCenterXInHorizontalMode()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false, true, IconPosition.ABOVE);
		for (int k = 0; k < l.slots; k++)
		{
			assertEquals("icon column should align with glyph k=" + k,
				l.cellCenterX(k), l.iconCenterX(k));
		}
	}

	@Test
	public void iconSizeIsBandHeightMinusInset()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4, false, false, true, IconPosition.ABOVE);
		assertEquals(Math.max(6, l.iconBandPx - 2), l.iconSize());
	}

	// ─── Combo layout (ITEM_USE source + target opposite the glyph row) ───

	@Test
	public void comboLayoutReservesSecondBandOppositeThePrimary()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4,
			false, false, true, IconPosition.ABOVE, true);
		assertTrue(l.showIcons);
		assertTrue(l.comboLayout);
		assertEquals(10, l.iconBandPx);
		// primary band(10) + glyph row(10) + secondary band(10) + margins(4) = 34.
		assertEquals(34, l.totalHeight);
	}

	@Test
	public void comboLayoutAbovePutsPrimaryUpAndSecondaryDown()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4,
			false, false, true, IconPosition.ABOVE, true);
		// Primary band centered at top: y = margin + iconBand/2.
		assertEquals(MARGIN + 5, l.iconCenterY(0));
		// Glyph row pushed down by one icon band.
		assertEquals(MARGIN + 10 + 5, l.cellCenterY(0));
		// Secondary band at the bottom: y = margin + iconBand + glyphRow + iconBand/2.
		assertEquals(MARGIN + 10 + 10 + 5, l.comboIconCenterY(0));
	}

	@Test
	public void comboLayoutBelowMirrorsTheArrangement()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4,
			false, false, true, IconPosition.BELOW, true);
		// In BELOW + combo, the secondary lives ABOVE the glyph row and the
		// primary BELOW. Glyph still sits between them.
		assertEquals(MARGIN + 5, l.comboIconCenterY(0));
		assertEquals(MARGIN + 10 + 5, l.cellCenterY(0));
		assertEquals(MARGIN + 10 + 10 + 5, l.iconCenterY(0));
	}

	@Test
	public void comboInVerticalModeUsesLeftAndRightColumns()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4,
			true, false, true, IconPosition.ABOVE, true);
		assertTrue(l.comboLayout);
		// Cross-axis is now WIDTH: primary(10) + glyph(10) + secondary(10) + margins(4) = 34.
		assertEquals(34, l.totalWidth);
		// Primary column on the left (iconPosition=ABOVE).
		assertEquals(MARGIN + 5, l.iconCenterX(0));
		// Glyph column shifted right by one band.
		assertEquals(MARGIN + 10 + 5, l.cellCenterX(0));
		// Secondary column on the right.
		assertEquals(MARGIN + 10 + 10 + 5, l.comboIconCenterX(0));
	}

	@Test
	public void comboHorizontalIconsAlignWithTheirSlotXOnBothBands()
	{
		HudLayout l = HudLayout.compute(100, 0, 0, 4,
			false, false, true, IconPosition.ABOVE, true);
		for (int k = 0; k < l.slots; k++)
		{
			assertEquals("primary icon column aligns with glyph k=" + k,
				l.cellCenterX(k), l.iconCenterX(k));
			assertEquals("secondary icon column aligns with glyph k=" + k,
				l.cellCenterX(k), l.comboIconCenterX(k));
		}
	}

	@Test
	public void comboLayoutWithoutIconsCollapsesToNoCombo()
	{
		// comboCapable is moot when wantIcons=false — there's no icon band
		// to reserve a second copy of.
		HudLayout l = HudLayout.compute(100, 0, 0, 4,
			false, false, false, IconPosition.ABOVE, true);
		assertFalse(l.showIcons);
		assertFalse(l.comboLayout);
		assertEquals(-1, l.comboIconCenterX(0));
		assertEquals(-1, l.comboIconCenterY(0));
		// Total height = single glyph row + margins, no icon bands.
		assertEquals(10 + 2 * MARGIN, l.totalHeight);
	}

	@Test
	public void cycleInPlaceSuppressesComboLayoutToo()
	{
		// Single-slot cycle-in-place has no timeline; combo is moot.
		HudLayout l = HudLayout.compute(100, 0, 0, 4,
			false, true, true, IconPosition.ABOVE, true);
		assertFalse(l.showIcons);
		assertFalse(l.comboLayout);
	}

	@Test
	public void comboBandScalesWithGlyphScale()
	{
		HudLayout l = HudLayout.compute(200, 0, 0, 4,
			false, false, true, IconPosition.ABOVE, true);
		// At 2x scale: cellSize=20 → iconBandPx=20.
		// totalH = 20 + 20 + 20 + 2*MARGIN = 60 + 2*MARGIN.
		assertEquals(20, l.iconBandPx);
		assertEquals(20 + 20 + 20 + 2 * MARGIN, l.totalHeight);
	}
}
