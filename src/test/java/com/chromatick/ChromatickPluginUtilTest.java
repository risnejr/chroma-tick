package com.chromatick;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Characterization tests for the two pure static helpers on
 * {@link ChromatickPlugin}: cycle-length clamping and custom-palette-key
 * parsing. Both are invoked from event handlers and bad behavior there is
 * hard to diagnose post-hoc, so we pin them down here.
 */
public class ChromatickPluginUtilTest
{
	// ─── clampCycle ──────────────────────────────────────────────────────

	@Test
	public void clampCyclePassesThroughValidValues()
	{
		for (int n = 2; n <= 10; n++)
		{
			assertEquals(n, ChromatickPlugin.clampCycle(n));
		}
	}

	@Test
	public void clampCycleRaisesValuesBelowMin()
	{
		assertEquals(2, ChromatickPlugin.clampCycle(1));
		assertEquals(2, ChromatickPlugin.clampCycle(0));
		assertEquals(2, ChromatickPlugin.clampCycle(-1));
		assertEquals(2, ChromatickPlugin.clampCycle(Integer.MIN_VALUE));
	}

	@Test
	public void clampCycleLowersValuesAboveMax()
	{
		assertEquals(10, ChromatickPlugin.clampCycle(11));
		assertEquals(10, ChromatickPlugin.clampCycle(99));
		assertEquals(10, ChromatickPlugin.clampCycle(Integer.MAX_VALUE));
	}

	// ─── parsePaletteKey ─────────────────────────────────────────────────

	@Test
	public void parsePaletteKeyExtractsCycleNumber()
	{
		assertEquals(2, ChromatickPlugin.parsePaletteKey("customPalette2"));
		assertEquals(4, ChromatickPlugin.parsePaletteKey("customPalette4"));
		assertEquals(10, ChromatickPlugin.parsePaletteKey("customPalette10"));
	}

	@Test
	public void parsePaletteKeyReturnsNegativeForNonNumericSuffix()
	{
		// Defensive: a config event with a key that starts with the prefix but
		// isn't followed by an int (e.g. accidental "customPaletteFoo") must
		// not throw — the onConfigChanged caller relies on the -1 sentinel.
		assertEquals(-1, ChromatickPlugin.parsePaletteKey("customPaletteFoo"));
		assertEquals(-1, ChromatickPlugin.parsePaletteKey("customPalette"));
		assertEquals(-1, ChromatickPlugin.parsePaletteKey("customPalette4x"));
	}
}
