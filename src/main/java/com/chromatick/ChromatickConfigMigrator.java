package com.chromatick;

import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * One-shot migration of legacy config values that pre-date the move to enum-typed
 * config getters. Pre-1.0 the four enum keys (displayMode/hudGlyph/hudAnchorTarget/
 * paletteMode) were stored as raw lowercase strings; once the getters return enums
 * those raw values fail to deserialize and silently snap to the default. This
 * helper rewrites legacy values to their canonical {@code Enum#name()} form.
 *
 * <p>Idempotent — already-migrated values are no-ops, so running on every
 * {@code startUp} is safe.
 */
@Singleton
class ChromatickConfigMigrator
{
	private static final String GROUP = "chromatick";

	private final ConfigManager configManager;

	@Inject
	ChromatickConfigMigrator(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Run all known migrations. Safe to call repeatedly. */
	void migrate()
	{
		migrateEnumKey("displayMode", DisplayMode.class);
		migrateEnumKey("hudGlyph", HudGlyph.class);
		migrateEnumKey("hudAnchorTarget", HudAnchorTarget.class);
		migrateEnumKey("paletteMode", PaletteMode.class);
	}

	private <T extends Enum<T>> void migrateEnumKey(String key, Class<T> enumType)
	{
		String raw = configManager.getConfiguration(GROUP, key);
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		try
		{
			Enum.valueOf(enumType, raw);
			return; // already in canonical form
		}
		catch (IllegalArgumentException ignored)
		{
			// fall through to migration attempt
		}
		try
		{
			T migrated = Enum.valueOf(enumType, raw.toUpperCase(Locale.ROOT));
			configManager.setConfiguration(GROUP, key, migrated.name());
		}
		catch (IllegalArgumentException ignored)
		{
			// Unknown legacy value — leave it so RuneLite falls back to default.
		}
	}
}
