package com.chromatick;

import com.google.gson.Gson;
import java.awt.Color;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the persistence-backed instance methods on {@link PaletteService}.
 * Uses a real {@link Gson} (so JSON shape is exercised end-to-end) and a
 * mocked {@link ConfigManager} (so we can assert on read/write calls without
 * a real RuneLite runtime).
 */
public class PaletteServiceTest
{
	private static final String GROUP = "chromatick";

	private ConfigManager configManager;
	private PaletteService palettes;

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		palettes = new PaletteService(configManager, new Gson());
		// Default: every config read returns null (i.e. no custom palette set).
		// Individual tests can override per-key with their own when(...).
		when(configManager.getConfiguration(eq(GROUP), any())).thenReturn(null);
	}

	// ─── getCustomPaletteForCycle ────────────────────────────────────────

	@Test
	public void returnsDefaultPaletteCloneWhenConfigIsNull()
	{
		Color[] result = palettes.getCustomPaletteForCycle(4);

		assertArrayEquals(PaletteService.PALETTES[4], result);
		// Mutating the returned array must not touch the static defaults.
		result[0] = Color.BLACK;
		assertEquals(new Color(255, 64, 64), PaletteService.PALETTES[4][0]);
	}

	@Test
	public void returnsDefaultPaletteCloneWhenConfigIsEmptyString()
	{
		when(configManager.getConfiguration(GROUP, "customPalette4")).thenReturn("");

		Color[] result = palettes.getCustomPaletteForCycle(4);

		assertArrayEquals(PaletteService.PALETTES[4], result);
	}

	@Test
	public void returnsCustomPaletteWhenConfigIsValidJson()
	{
		// 4 RGBs as ARGB ints — black, white, red, green (alpha 0xFF).
		String json = "[-16777216,-1,-65536,-16711936]";
		when(configManager.getConfiguration(GROUP, "customPalette4")).thenReturn(json);

		Color[] result = palettes.getCustomPaletteForCycle(4);

		assertEquals(4, result.length);
		assertEquals(Color.BLACK.getRGB(), result[0].getRGB());
		assertEquals(Color.WHITE.getRGB(), result[1].getRGB());
		assertEquals(Color.RED.getRGB(),   result[2].getRGB());
		assertEquals(Color.GREEN.getRGB(), result[3].getRGB());
	}

	@Test
	public void fallsBackToDefaultsForMissingEntriesInShortJson()
	{
		// Only 2 of 4 slots present — the rest must inherit defaults.
		String json = "[-16777216,-1]";
		when(configManager.getConfiguration(GROUP, "customPalette4")).thenReturn(json);

		Color[] result = palettes.getCustomPaletteForCycle(4);

		assertEquals(4, result.length);
		assertEquals(Color.BLACK.getRGB(), result[0].getRGB());
		assertEquals(Color.WHITE.getRGB(), result[1].getRGB());
		assertEquals(PaletteService.PALETTES[4][2], result[2]);
		assertEquals(PaletteService.PALETTES[4][3], result[3]);
	}

	@Test
	public void fallsBackToDefaultsForNullEntriesInJson()
	{
		String json = "[null,-1,null,-65536]";
		when(configManager.getConfiguration(GROUP, "customPalette4")).thenReturn(json);

		Color[] result = palettes.getCustomPaletteForCycle(4);

		assertEquals(PaletteService.PALETTES[4][0], result[0]);
		assertEquals(Color.WHITE.getRGB(), result[1].getRGB());
		assertEquals(PaletteService.PALETTES[4][2], result[2]);
		assertEquals(Color.RED.getRGB(), result[3].getRGB());
	}

	@Test
	public void fallsBackToDefaultsForUnparseableJson()
	{
		when(configManager.getConfiguration(GROUP, "customPalette4")).thenReturn("not-json");

		Color[] result = palettes.getCustomPaletteForCycle(4);

		assertArrayEquals(PaletteService.PALETTES[4], result);
	}

	@Test
	public void clampsCycleLengthOnRead()
	{
		// Out-of-range cycle should resolve against PALETTES[10], not crash.
		Color[] result = palettes.getCustomPaletteForCycle(99);

		assertEquals(10, result.length);
		assertArrayEquals(PaletteService.PALETTES[10], result);
		// And it should have read the config under the clamped key, not "customPalette99".
		verify(configManager).getConfiguration(GROUP, "customPalette10");
	}

	// ─── setCustomPaletteColor ───────────────────────────────────────────

	@Test
	public void setCustomPaletteColorPersistsRgbList()
	{
		palettes.setCustomPaletteColor(4, 1, Color.MAGENTA);

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq(GROUP), eq("customPalette4"), json.capture());

		// Decode the written JSON back through Gson and verify it round-trips
		// the magenta override at index 1, with defaults preserved elsewhere.
		Integer[] rgbs = new Gson().fromJson(json.getValue(), Integer[].class);
		assertEquals(4, rgbs.length);
		assertEquals(PaletteService.PALETTES[4][0].getRGB(), (int) rgbs[0]);
		assertEquals(Color.MAGENTA.getRGB(),                  (int) rgbs[1]);
		assertEquals(PaletteService.PALETTES[4][2].getRGB(), (int) rgbs[2]);
		assertEquals(PaletteService.PALETTES[4][3].getRGB(), (int) rgbs[3]);
	}

	@Test
	public void setCustomPaletteColorIsNoOpForOutOfBoundsIndex()
	{
		palettes.setCustomPaletteColor(4, -1, Color.MAGENTA);
		palettes.setCustomPaletteColor(4, 4,  Color.MAGENTA);
		palettes.setCustomPaletteColor(4, 99, Color.MAGENTA);

		verify(configManager, never()).setConfiguration(eq(GROUP), any(), any(String.class));
	}

	@Test
	public void setCustomPaletteColorClampsCycleLength()
	{
		palettes.setCustomPaletteColor(99, 0, Color.MAGENTA);

		// Should write under the clamped key (cycle 10), not "customPalette99".
		verify(configManager).setConfiguration(eq(GROUP), eq("customPalette10"), any(String.class));
	}

	// ─── resetCustomPaletteForCycle ──────────────────────────────────────

	@Test
	public void resetCustomPaletteUnsetsConfigKey()
	{
		palettes.resetCustomPaletteForCycle(6);

		verify(configManager, times(1)).unsetConfiguration(GROUP, "customPalette6");
	}

	@Test
	public void resetCustomPaletteClampsCycleLength()
	{
		palettes.resetCustomPaletteForCycle(0);

		verify(configManager).unsetConfiguration(GROUP, "customPalette2");
	}
}
