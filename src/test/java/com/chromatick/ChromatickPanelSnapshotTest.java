package com.chromatick;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ChromatickPanelSnapshot#from(ChromatickConfig, int)} — verify
 * each config getter maps to the matching snapshot field, and that the
 * effective-cycle argument is passed through verbatim (so hotkey overrides
 * surface in the panel without it having to call back into the plugin).
 *
 * <p>Uses a Mockito-mocked {@link ChromatickConfig}; RuneLite's
 * {@link ConfigManager} isn't needed because the snapshot only reads via the
 * typed config interface.
 */
public class ChromatickPanelSnapshotTest
{
	private static final Set<TickActionCategory> PRAYER_ONLY =
		Collections.singleton(TickActionCategory.PROTECTION_PRAYER);

	@Test
	public void defaultsRoundTripThroughTheSnapshot()
	{
		// A config returning all its declared defaults — the snapshot should
		// mirror them exactly.
		ChromatickConfig cfg = mock(ChromatickConfig.class, Mockito.CALLS_REAL_METHODS);

		ChromatickPanelSnapshot s = ChromatickPanelSnapshot.from(cfg, 4, PRAYER_ONLY);

		assertFalse(s.staticMode);
		assertEquals(PaletteMode.GRID, s.paletteMode);
		assertTrue(s.sequentialFill);
		assertEquals(2.0, s.tileBorderWidth, 0.0001);
		assertTrue(s.enableFillColor);
		assertEquals(50, s.fillOpacity);
		assertFalse(s.drawBelowPlayer);
		assertEquals(DisplayMode.TILE, s.displayMode);
		assertEquals(HudGlyph.DOTS, s.hudGlyph);
		assertEquals(200, s.hudScale);
		assertEquals(100, s.hudActiveOpacity);
		assertEquals(40, s.hudInactiveOpacity);
		assertFalse(s.hudBold);
		assertEquals(0, s.hudPop);
		assertEquals(0, s.hudSpacing);
		assertFalse(s.hudVertical);
		assertEquals(HudAnchorTarget.FEET, s.hudAnchorTarget);
		assertEquals(30, s.hudVerticalOffset);
		assertEquals(0, s.hudHorizontalOffset);
		assertFalse(s.hudCycleInPlace);
		assertEquals(RecordMode.OFF, s.recordMode);
		assertEquals(IconPosition.ABOVE, s.recordIconPosition);
		assertEquals(1, s.recordArmTicks);
		assertEquals(PRAYER_ONLY, s.recordCategories);
		assertEquals(4, s.effectiveCycleLength);
	}

	@Test
	public void overriddenConfigValuesAreMirrored()
	{
		// Spot-check every field with a non-default value to guard against
		// builder-arg ordering mishaps.
		ChromatickConfig cfg = mock(ChromatickConfig.class);
		Color sc = new Color(10, 20, 30, 200);
		Color sf = new Color(40, 50, 60, 100);
		when(cfg.staticMode()).thenReturn(true);
		when(cfg.paletteMode()).thenReturn(PaletteMode.WHEEL);
		when(cfg.sequentialFill()).thenReturn(false);
		when(cfg.staticColor()).thenReturn(sc);
		when(cfg.staticFillColor()).thenReturn(sf);
		when(cfg.tileBorderWidth()).thenReturn(3.5);
		when(cfg.enableFillColor()).thenReturn(false);
		when(cfg.fillOpacity()).thenReturn(77);
		when(cfg.drawBelowPlayer()).thenReturn(true);
		when(cfg.displayMode()).thenReturn(DisplayMode.BOTH);
		when(cfg.hudGlyph()).thenReturn(HudGlyph.NUMBERS);
		when(cfg.hudScale()).thenReturn(310);
		when(cfg.hudActiveOpacity()).thenReturn(85);
		when(cfg.hudInactiveOpacity()).thenReturn(15);
		when(cfg.hudBold()).thenReturn(true);
		when(cfg.hudPop()).thenReturn(120);
		when(cfg.hudSpacing()).thenReturn(-5);
		when(cfg.hudVertical()).thenReturn(true);
		when(cfg.hudAnchorTarget()).thenReturn(HudAnchorTarget.HEAD);
		when(cfg.hudVerticalOffset()).thenReturn(-12);
		when(cfg.hudHorizontalOffset()).thenReturn(42);
		when(cfg.hudCycleInPlace()).thenReturn(true);
		when(cfg.recordMode()).thenReturn(RecordMode.ALWAYS);
		when(cfg.recordIconPosition()).thenReturn(IconPosition.BELOW);
		when(cfg.recordArmTicks()).thenReturn(7);
		Set<TickActionCategory> threeCats = EnumSet.of(
			TickActionCategory.PROTECTION_PRAYER,
			TickActionCategory.RED_CLICK,
			TickActionCategory.YELLOW_CLICK);

		ChromatickPanelSnapshot s = ChromatickPanelSnapshot.from(cfg, 9, threeCats);

		assertTrue(s.staticMode);
		assertEquals(PaletteMode.WHEEL, s.paletteMode);
		assertFalse(s.sequentialFill);
		assertSame(sc, s.staticColor);
		assertSame(sf, s.staticFillColor);
		assertEquals(3.5, s.tileBorderWidth, 0.0001);
		assertFalse(s.enableFillColor);
		assertEquals(77, s.fillOpacity);
		assertTrue(s.drawBelowPlayer);
		assertEquals(DisplayMode.BOTH, s.displayMode);
		assertEquals(HudGlyph.NUMBERS, s.hudGlyph);
		assertEquals(310, s.hudScale);
		assertEquals(85, s.hudActiveOpacity);
		assertEquals(15, s.hudInactiveOpacity);
		assertTrue(s.hudBold);
		assertEquals(120, s.hudPop);
		assertEquals(-5, s.hudSpacing);
		assertTrue(s.hudVertical);
		assertEquals(HudAnchorTarget.HEAD, s.hudAnchorTarget);
		assertEquals(-12, s.hudVerticalOffset);
		assertEquals(42, s.hudHorizontalOffset);
		assertTrue(s.hudCycleInPlace);
		assertEquals(RecordMode.ALWAYS, s.recordMode);
		assertEquals(IconPosition.BELOW, s.recordIconPosition);
		assertEquals(7, s.recordArmTicks);
		assertEquals(threeCats, s.recordCategories);
		assertEquals(9, s.effectiveCycleLength);
	}

	@Test
	public void effectiveCycleLengthIsPassedThroughUnchanged()
	{
		// effectiveCycleLength reflects hotkey override; the snapshot factory
		// must not second-guess it (no clamping, no reading cycleLength from
		// config). Pass-through is the contract.
		ChromatickConfig cfg = mock(ChromatickConfig.class, Mockito.CALLS_REAL_METHODS);

		assertEquals(2,  ChromatickPanelSnapshot.from(cfg, 2, PRAYER_ONLY).effectiveCycleLength);
		assertEquals(10, ChromatickPanelSnapshot.from(cfg, 10, PRAYER_ONLY).effectiveCycleLength);
		// Even nonsensical values are passed through — clamping is the
		// caller's responsibility.
		assertEquals(99, ChromatickPanelSnapshot.from(cfg, 99, PRAYER_ONLY).effectiveCycleLength);
	}

	@Test
	public void recordCategoriesArePassedThroughUnchanged()
	{
		// The plugin parses recordCategories from a CSV string before
		// calling from(...); the snapshot factory just stores the set.
		ChromatickConfig cfg = mock(ChromatickConfig.class, Mockito.CALLS_REAL_METHODS);

		Set<TickActionCategory> all = EnumSet.allOf(TickActionCategory.class);
		assertEquals(all, ChromatickPanelSnapshot.from(cfg, 4, all).recordCategories);

		Set<TickActionCategory> none = EnumSet.noneOf(TickActionCategory.class);
		assertEquals(none, ChromatickPanelSnapshot.from(cfg, 4, none).recordCategories);
	}
}
