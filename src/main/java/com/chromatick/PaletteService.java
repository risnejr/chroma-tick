package com.chromatick;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Storage and retrieval of the per-cycle color palettes. Holds the static
 * default {@link #PALETTES} table (perceptually-optimal n-color palettes for
 * cycles 2..10) and reads/writes the user's custom palette overrides via
 * {@link ConfigManager}.
 *
 * <p>Custom palettes are stored as JSON arrays of RGB ints keyed
 * {@code customPalette2}..{@code customPalette10}. A missing or unparseable
 * key falls back to the default palette for that cycle length.
 */
@Slf4j
@Singleton
class PaletteService
{
	static final int MIN_CYCLE = 2;
	static final int MAX_CYCLE = 10;

	private static final String CONFIG_GROUP = "chromatick";
	static final String CUSTOM_PALETTE_PREFIX = "customPalette";
	private static final Type PALETTE_TYPE = new TypeToken<List<Integer>>(){}.getType();

	// PALETTES[n] = the perceptually-optimal n-color palette, indexed by cycle length.
	// Computed using OKLab perceptual color space with a greedy farthest-point algorithm.
	// Each palette has its colors maximally distinct and spread evenly around the hue wheel.
	static final Color[][] PALETTES = {
		null, // index 0 unused
		null, // index 1 unused
		{
			// cycle 2
			new Color(255, 64, 64),  // red
			new Color(64, 224, 224), // cyan
		},
		{
			// cycle 3
			new Color(255, 64, 64),  // red
			new Color(255, 224, 32), // yellow
			new Color(64, 128, 255), // blue
		},
		{
			// cycle 4
			new Color(255, 64, 64),  // red
			new Color(255, 224, 32), // yellow
			new Color(96, 224, 96),  // green
			new Color(64, 128, 255), // blue
		},
		{
			// cycle 5
			new Color(255, 64, 64),  // red
			new Color(255, 160, 32), // orange
			new Color(255, 224, 32), // yellow
			new Color(96, 224, 96),  // green
			new Color(64, 128, 255), // blue
		},
		{
			// cycle 6
			new Color(255, 64, 64),  // red
			new Color(255, 160, 32), // orange
			new Color(255, 224, 32), // yellow
			new Color(96, 224, 96),  // green
			new Color(64, 224, 224), // cyan
			new Color(64, 128, 255), // blue
		},
		{
			// cycle 7
			new Color(255, 64, 64),  // red
			new Color(255, 160, 32), // orange
			new Color(255, 224, 32), // yellow
			new Color(96, 224, 96),  // green
			new Color(64, 224, 224), // cyan
			new Color(64, 128, 255), // blue
			new Color(128, 96, 255), // violet
		},
		{
			// cycle 8
			new Color(255, 64, 64),  // red
			new Color(255, 160, 32), // orange
			new Color(255, 224, 32), // yellow
			new Color(96, 224, 96),  // green
			new Color(64, 224, 224), // cyan
			new Color(64, 128, 255), // blue
			new Color(128, 96, 255), // indigo
			new Color(224, 96, 224), // magenta
		},
		{
			// cycle 9
			new Color(255, 64, 64),  // red
			new Color(255, 160, 32), // orange
			new Color(255, 224, 32), // yellow
			new Color(192, 224, 32), // yellow-green
			new Color(64, 224, 64),  // green
			new Color(64, 224, 224), // cyan
			new Color(64, 128, 255), // blue
			new Color(160, 64, 255), // purple
			new Color(224, 64, 192), // magenta
		},
		{
			// cycle 10
			new Color(255, 64, 64),  // red
			new Color(255, 144, 32), // orange
			new Color(255, 192, 32), // amber
			new Color(255, 224, 32), // yellow
			new Color(192, 224, 32), // yellow-green
			new Color(64, 224, 64),  // green
			new Color(64, 224, 224), // cyan
			new Color(64, 128, 255), // blue
			new Color(128, 64, 255), // purple
			new Color(224, 64, 192), // magenta
		},
	};

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	PaletteService(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * Resolve the palette for {@code cycleLength}, layering the user's custom
	 * overrides on top of {@link #PALETTES}. Returns a fresh array — safe for
	 * the caller to mutate.
	 */
	Color[] getCustomPaletteForCycle(int cycleLength)
	{
		int n = clampCycle(cycleLength);
		Color[] defaults = PALETTES[n];
		String json = configManager.getConfiguration(CONFIG_GROUP, CUSTOM_PALETTE_PREFIX + n);
		if (json == null || json.isEmpty())
		{
			return defaults.clone();
		}
		try
		{
			List<Integer> rgbs = gson.fromJson(json, PALETTE_TYPE);
			Color[] result = new Color[n];
			for (int i = 0; i < n; i++)
			{
				if (rgbs != null && i < rgbs.size() && rgbs.get(i) != null)
				{
					result[i] = new Color(rgbs.get(i), true);
				}
				else
				{
					result[i] = defaults[i];
				}
			}
			return result;
		}
		catch (Exception e)
		{
			log.debug("Failed to parse custom palette for cycle {}: {}", n, e.toString());
			return defaults.clone();
		}
	}

	void setCustomPaletteColor(int cycleLength, int index, Color color)
	{
		int n = clampCycle(cycleLength);
		if (index < 0 || index >= n)
		{
			return;
		}
		Color[] current = getCustomPaletteForCycle(n);
		current[index] = color;
		List<Integer> rgbs = new ArrayList<>(n);
		for (Color c : current)
		{
			rgbs.add(c.getRGB());
		}
		configManager.setConfiguration(CONFIG_GROUP, CUSTOM_PALETTE_PREFIX + n, gson.toJson(rgbs));
	}

	void resetCustomPaletteForCycle(int cycleLength)
	{
		int n = clampCycle(cycleLength);
		configManager.unsetConfiguration(CONFIG_GROUP, CUSTOM_PALETTE_PREFIX + n);
	}

	static int clampCycle(int n)
	{
		return Math.max(MIN_CYCLE, Math.min(MAX_CYCLE, n));
	}

	/**
	 * Extract the cycle-length integer from a {@code customPaletteN} config
	 * key. Returns -1 for any suffix that isn't a parseable integer; callers
	 * rely on the sentinel rather than catching exceptions.
	 */
	static int parsePaletteKey(String key)
	{
		try
		{
			return Integer.parseInt(key.substring(CUSTOM_PALETTE_PREFIX.length()));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
