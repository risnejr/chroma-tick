package com.chromatick;

import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the legacy-value config migrator. Uses a mocked ConfigManager so
 * we can assert exact read/write calls without standing up a RuneLite runtime.
 *
 * <p>The migrator's job is narrow: rewrite raw lowercase strings stored under
 * the four enum-typed keys into their canonical {@code Enum#name()} form.
 * Already-canonical and unknown values must be left alone.
 */
public class ChromatickConfigMigratorTest
{
	private static final String GROUP = "chromatick";

	private ConfigManager configManager;
	private ChromatickConfigMigrator migrator;

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(eq(GROUP), anyString())).thenReturn(null);
		migrator = new ChromatickConfigMigrator(configManager);
	}

	@Test
	public void doesNothingWhenAllValuesAreNull()
	{
		migrator.migrate();

		verify(configManager, never()).setConfiguration(eq(GROUP), anyString(), any(String.class));
	}

	@Test
	public void leavesAlreadyCanonicalValuesAlone()
	{
		when(configManager.getConfiguration(GROUP, "displayMode")).thenReturn("HUD");
		when(configManager.getConfiguration(GROUP, "hudGlyph")).thenReturn("NUMBERS");
		when(configManager.getConfiguration(GROUP, "hudAnchorTarget")).thenReturn("FEET");
		when(configManager.getConfiguration(GROUP, "paletteMode")).thenReturn("WHEEL");

		migrator.migrate();

		verify(configManager, never()).setConfiguration(eq(GROUP), anyString(), any(String.class));
	}

	@Test
	public void rewritesLegacyLowercaseDisplayMode()
	{
		when(configManager.getConfiguration(GROUP, "displayMode")).thenReturn("hud");

		migrator.migrate();

		verify(configManager).setConfiguration(GROUP, "displayMode", "HUD");
	}

	@Test
	public void rewritesLegacyLowercaseHudGlyph()
	{
		when(configManager.getConfiguration(GROUP, "hudGlyph")).thenReturn("numbers");

		migrator.migrate();

		verify(configManager).setConfiguration(GROUP, "hudGlyph", "NUMBERS");
	}

	@Test
	public void rewritesLegacyLowercaseHudAnchorTarget()
	{
		when(configManager.getConfiguration(GROUP, "hudAnchorTarget")).thenReturn("head");

		migrator.migrate();

		verify(configManager).setConfiguration(GROUP, "hudAnchorTarget", "HEAD");
	}

	@Test
	public void rewritesLegacyLowercasePaletteMode()
	{
		when(configManager.getConfiguration(GROUP, "paletteMode")).thenReturn("wheel");

		migrator.migrate();

		verify(configManager).setConfiguration(GROUP, "paletteMode", "WHEEL");
	}

	@Test
	public void rewritesMixedCaseAndOtherCasings()
	{
		when(configManager.getConfiguration(GROUP, "displayMode")).thenReturn("Hud");
		when(configManager.getConfiguration(GROUP, "paletteMode")).thenReturn("Grid");

		migrator.migrate();

		verify(configManager).setConfiguration(GROUP, "displayMode", "HUD");
		verify(configManager).setConfiguration(GROUP, "paletteMode", "GRID");
	}

	@Test
	public void leavesUnknownLegacyValuesAlone()
	{
		// Garbage value — shouldn't crash, shouldn't write anything. RuneLite
		// will then fall back to the @ConfigItem default.
		when(configManager.getConfiguration(GROUP, "displayMode")).thenReturn("garbage");

		migrator.migrate();

		verify(configManager, never()).setConfiguration(eq(GROUP), anyString(), any(String.class));
	}

	@Test
	public void treatsEmptyStringAsAbsent()
	{
		when(configManager.getConfiguration(GROUP, "displayMode")).thenReturn("");

		migrator.migrate();

		verify(configManager, never()).setConfiguration(eq(GROUP), anyString(), any(String.class));
	}

	@Test
	public void migrationIsIdempotent()
	{
		// Run, then re-run with the now-canonical value — the second pass must
		// not re-write anything.
		when(configManager.getConfiguration(GROUP, "displayMode"))
			.thenReturn("hud")     // first call: legacy lowercase
			.thenReturn("HUD");    // second call: already migrated

		migrator.migrate();
		migrator.migrate();

		// Only the first call should have written.
		verify(configManager).setConfiguration(GROUP, "displayMode", "HUD");
	}
}
