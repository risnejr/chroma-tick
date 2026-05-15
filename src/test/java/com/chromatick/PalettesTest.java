package com.chromatick;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Characterization tests for the static {@code PALETTES} table. These pin
 * down the perceptual contract (one palette per cycle 2..10, no nulls, fully
 * opaque, no duplicate colors within a palette) so a future refactor that
 * regenerates the table doesn't silently break it.
 */
public class PalettesTest
{
	private static final int MIN_CYCLE = 2;
	private static final int MAX_CYCLE = 10;

	@Test
	public void paletteArrayCoversCyclesTwoThroughTen()
	{
		assertEquals("PALETTES is indexed by cycle length; needs slots 0..10",
			MAX_CYCLE + 1, ChromatickPlugin.PALETTES.length);
	}

	@Test
	public void cycleZeroAndOneAreUnused()
	{
		assertNull("cycle 0 slot is intentionally unused", ChromatickPlugin.PALETTES[0]);
		assertNull("cycle 1 slot is intentionally unused", ChromatickPlugin.PALETTES[1]);
	}

	@Test
	public void everyValidCycleHasMatchingPaletteLength()
	{
		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			Color[] palette = ChromatickPlugin.PALETTES[n];
			assertNotNull("palette for cycle " + n + " must exist", palette);
			assertEquals("palette[" + n + "] must have " + n + " colors",
				n, palette.length);
		}
	}

	@Test
	public void noPaletteContainsNullEntries()
	{
		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			Color[] palette = ChromatickPlugin.PALETTES[n];
			for (int i = 0; i < palette.length; i++)
			{
				assertNotNull("palette[" + n + "][" + i + "] is null", palette[i]);
			}
		}
	}

	@Test
	public void allPaletteColorsAreFullyOpaque()
	{
		// Tile/HUD overlays layer their own alpha on top of palette RGB. If a
		// palette entry ever lands with a non-255 alpha (e.g. via a Color(int)
		// constructor that picked up the alpha byte), the rendered alpha would
		// silently double up.
		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			Color[] palette = ChromatickPlugin.PALETTES[n];
			for (int i = 0; i < palette.length; i++)
			{
				assertEquals("palette[" + n + "][" + i + "] must be fully opaque",
					255, palette[i].getAlpha());
			}
		}
	}

	@Test
	public void paletteColorsAreUniqueWithinEachCycle()
	{
		// Preattentive distinctness is the whole point — duplicates inside a
		// single cycle would defeat the plugin's UX goal.
		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			Color[] palette = ChromatickPlugin.PALETTES[n];
			Set<Integer> seen = new HashSet<>();
			for (int i = 0; i < palette.length; i++)
			{
				int rgb = palette[i].getRGB();
				assertTrue("palette[" + n + "] contains duplicate color at index " + i,
					seen.add(rgb));
			}
		}
	}

	@Test
	public void firstColorIsRedAcrossAllCycles()
	{
		// The current palettes all start at red (255, 64, 64) — this anchors
		// the cycle's "tick 0" to a consistent landmark so users developing
		// muscle memory at one cycle length carry it over to others.
		Color expected = new Color(255, 64, 64);
		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			assertEquals("palette[" + n + "][0] should anchor at red",
				expected, ChromatickPlugin.PALETTES[n][0]);
		}
	}
}
