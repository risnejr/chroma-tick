package com.chromatick;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

// Inspired by MarlGames' "Learn Solo Olm Without Counting Ticks Using This Brainhack (OSRS)"
// https://www.youtube.com/watch?v=4fc4eIUmj6U — adapted from vincent0955's Visual Metronome plugin.
@Slf4j
@PluginDescriptor(
	name = "ChromaTick",
	description = "Cycles your true tile color each game tick for preattentive tick tracking",
	tags = {
		"true", "tile", "tick", "metronome", "color", "overlay", "preattentive", "chroma",
	}
)
public class ChromatickPlugin extends Plugin implements KeyListener
{
	private static final int MIN_CYCLE = 2;
	private static final int MAX_CYCLE = 10;

	private static final String CUSTOM_PALETTE_PREFIX = "customPalette";
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

	@Inject
	private Client client;

	@Inject
	private ChromatickConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private ChromatickOverlay tileOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientToolbar clientToolbar;

	/** Current position in the color cycle (0-based). */
	@Getter
	protected int tickIndex = 0;

	/** The current color to render this tick. */
	@Getter
	protected Color currentColor = Color.WHITE;

	/** Hotkey override for cycle length; -1 = use config slider value. */
	private int cycleLengthOverride = -1;

	private ChromatickPanel panel;
	private NavigationButton navButton;

	@Provides
	ChromatickConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChromatickConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(tileOverlay);
		keyManager.registerKeyListener(this);
		tickIndex = 0;
		cycleLengthOverride = -1;
		currentColor = config.staticMode() ? config.staticColor() : getColorByIndex(0);

		panel = new ChromatickPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("ChromaTick")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(tileOverlay);
		keyManager.unregisterKeyListener(this);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
		tickIndex = 0;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (config.staticMode())
		{
			return;
		}
		int cycleLength = getEffectiveCycleLength();
		tickIndex = (tickIndex + 1) % cycleLength;
		currentColor = getColorByIndex(tickIndex);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("chromatick"))
		{
			return;
		}
		if ("cycleLength".equals(event.getKey()))
		{
			cycleLengthOverride = -1;
		}
		if (config.staticMode())
		{
			currentColor = config.staticColor();
		}
		else
		{
			int cycleLength = getEffectiveCycleLength();
			tickIndex = tickIndex % cycleLength;
			currentColor = getColorByIndex(tickIndex);
		}

		// Sync panel when palette or cycle length changes externally
		if (panel != null
			&& (event.getKey().startsWith(CUSTOM_PALETTE_PREFIX) || "cycleLength".equals(event.getKey())))
		{
			SwingUtilities.invokeLater(panel::refreshFromConfig);
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (config.toggleOverlayHotkey().matches(e))
		{
			configManager.setConfiguration("chromatick", "staticMode", !config.staticMode());
		}

		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			if (getCycleHotkeyByLength(n).matches(e))
			{
				cycleLengthOverride = n;
				if (panel != null)
				{
					SwingUtilities.invokeLater(panel::refreshFromConfig);
				}
				return;
			}
		}

		if (config.resetCycleHotkey().matches(e))
		{
			cycleLengthOverride = -1;
			tickIndex = 0;
			currentColor = getColorByIndex(0);
			if (panel != null)
			{
				SwingUtilities.invokeLater(panel::refreshFromConfig);
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {}

	public int getEffectiveCycleLength()
	{
		return cycleLengthOverride > 0 ? cycleLengthOverride : config.cycleLength();
	}

	private Keybind getCycleHotkeyByLength(int n)
	{
		switch (n)
		{
			case 2: return config.cycle2Hotkey();
			case 3: return config.cycle3Hotkey();
			case 4: return config.cycle4Hotkey();
			case 5: return config.cycle5Hotkey();
			case 6: return config.cycle6Hotkey();
			case 7: return config.cycle7Hotkey();
			case 8: return config.cycle8Hotkey();
			case 9: return config.cycle9Hotkey();
			case 10: return config.cycle10Hotkey();
			default: return Keybind.NOT_SET;
		}
	}

	private Color getColorByIndex(int index)
	{
		Color[] palette = getCustomPaletteForCycle(getEffectiveCycleLength());
		return palette[index % palette.length];
	}

	// ─── Per-cycle palette storage (consumed by ChromatickPanel) ─────────

	Color[] getCustomPaletteForCycle(int cycleLength)
	{
		int n = clampCycle(cycleLength);
		Color[] defaults = PALETTES[n];
		String json = configManager.getConfiguration("chromatick", CUSTOM_PALETTE_PREFIX + n);
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
		configManager.setConfiguration("chromatick", CUSTOM_PALETTE_PREFIX + n, gson.toJson(rgbs));
	}

	void resetCustomPaletteForCycle(int cycleLength)
	{
		int n = clampCycle(cycleLength);
		configManager.unsetConfiguration("chromatick", CUSTOM_PALETTE_PREFIX + n);
	}

	private static int clampCycle(int n)
	{
		return Math.max(MIN_CYCLE, Math.min(MAX_CYCLE, n));
	}
}
