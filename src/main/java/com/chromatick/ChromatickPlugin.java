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
	private ChromatickHudOverlay hudOverlay;

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
		applyDisplayMode();
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
		overlayManager.remove(hudOverlay);
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
		// Static mode freezes the *color*, not the cycle. The HUD overlay still
		// uses tickIndex to advance its active-glyph highlight.
		int cycleLength = getEffectiveCycleLength();
		tickIndex = (tickIndex + 1) % cycleLength;
		currentColor = config.staticMode() ? config.staticColor() : getColorByIndex(tickIndex);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("chromatick"))
		{
			return;
		}
		String key = event.getKey();
		if ("cycleLength".equals(key))
		{
			cycleLengthOverride = -1;
		}
		int cycleLength = getEffectiveCycleLength();
		tickIndex = tickIndex % cycleLength;
		currentColor = config.staticMode() ? config.staticColor() : getColorByIndex(tickIndex);

		if ("displayMode".equals(key))
		{
			applyDisplayMode();
		}

		if (panel == null)
		{
			return;
		}
		if ("cycleLength".equals(key) || "staticMode".equals(key) || "displayMode".equals(key)
			|| "hudScale".equals(key) || "hudAnchorTarget".equals(key))
		{
			// Active state changed — panel mirrors active state. hudAnchorTarget is
			// here so the panel pill toggle flips to "None" when the overlay
			// self-unpins on alt+drag.
			SwingUtilities.invokeLater(panel::refreshFromConfig);
		}
		else if (key.startsWith(CUSTOM_PALETTE_PREFIX))
		{
			// Palette change — only repaint swatches if it matches the cycle
			// the panel is currently editing. Do NOT snap selection.
			int n = parsePaletteKey(key);
			if (n > 0)
			{
				SwingUtilities.invokeLater(() -> panel.onPaletteChanged(n));
			}
		}
	}

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
			currentColor = config.staticMode() ? config.staticColor() : getColorByIndex(0);
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

	/** Make {@code n} the active cycle, clearing any hotkey override. */
	void setActiveCycle(int n)
	{
		cycleLengthOverride = -1;
		configManager.setConfiguration("chromatick", "cycleLength", clampCycle(n));
	}

	void setStaticMode(boolean on)
	{
		configManager.setConfiguration("chromatick", "staticMode", on);
	}

	boolean isStaticMode()
	{
		return config.staticMode();
	}

	// ─── Panel-driven setters for moved settings ────────────────────────

	void setStaticColor(Color c)
	{
		configManager.setConfiguration("chromatick", "staticColor", c);
	}

	void setStaticFillColor(Color c)
	{
		configManager.setConfiguration("chromatick", "staticFillColor", c);
	}

	void setBorderWidth(double w)
	{
		configManager.setConfiguration("chromatick", "tileBorderWidth", w);
	}

	void setEnableFillColor(boolean on)
	{
		configManager.setConfiguration("chromatick", "enableFillColor", on);
	}

	void setFillOpacity(int o)
	{
		configManager.setConfiguration("chromatick", "fillOpacity", o);
	}

	void setDrawBelowPlayer(boolean on)
	{
		configManager.setConfiguration("chromatick", "drawBelowPlayer", on);
	}

	void setPaletteMode(String mode)
	{
		configManager.setConfiguration("chromatick", "paletteMode", mode);
	}

	void setSequentialFill(boolean on)
	{
		configManager.setConfiguration("chromatick", "sequentialFill", on);
	}

	// ─── HUD overlay setters ────────────────────────────────────────────

	void setDisplayMode(String mode)
	{
		configManager.setConfiguration("chromatick", "displayMode", mode);
	}

	void setHudGlyph(String glyph)
	{
		configManager.setConfiguration("chromatick", "hudGlyph", glyph);
	}

	void setHudScale(int pct)
	{
		configManager.setConfiguration("chromatick", "hudScale", pct);
	}

	void setHudActiveOpacity(int pct)
	{
		configManager.setConfiguration("chromatick", "hudActiveOpacity", pct);
	}

	void setHudInactiveOpacity(int pct)
	{
		configManager.setConfiguration("chromatick", "hudInactiveOpacity", pct);
	}

	void setHudBold(boolean on)
	{
		configManager.setConfiguration("chromatick", "hudBold", on);
	}

	void setHudPop(int pct)
	{
		configManager.setConfiguration("chromatick", "hudPop", pct);
	}

	void setHudSpacing(int px)
	{
		configManager.setConfiguration("chromatick", "hudSpacing", px);
	}

	void setHudVertical(boolean on)
	{
		configManager.setConfiguration("chromatick", "hudVertical", on);
	}

	/**
	 * Set the anchor target. When switching to head/feet we also clear the
	 * overlay's drag-tracking state so it re-positions cleanly on the next
	 * frame. Switching to "none" preserves the current position.
	 */
	void setHudAnchorTarget(String target)
	{
		configManager.setConfiguration("chromatick", "hudAnchorTarget", target);
		if (!"none".equals(target))
		{
			hudOverlay.clearDragState();
		}
	}

	void setHudVerticalOffset(int px)
	{
		configManager.setConfiguration("chromatick", "hudVerticalOffset", px);
	}

	void setHudHorizontalOffset(int px)
	{
		configManager.setConfiguration("chromatick", "hudHorizontalOffset", px);
	}

	void setHudCycleInPlace(boolean on)
	{
		configManager.setConfiguration("chromatick", "hudCycleInPlace", on);
	}

	private void applyDisplayMode()
	{
		String mode = config.displayMode();
		boolean tile = "tile".equals(mode) || "both".equals(mode);
		boolean hud  = "hud".equals(mode)  || "both".equals(mode);
		// Default to tile if config value is unrecognised (forward-compat).
		if (!tile && !hud)
		{
			tile = true;
		}
		if (tile)
		{
			overlayManager.add(tileOverlay);
		}
		else
		{
			overlayManager.remove(tileOverlay);
		}
		if (hud)
		{
			overlayManager.add(hudOverlay);
		}
		else
		{
			overlayManager.remove(hudOverlay);
		}
	}

	ChromatickConfig getConfig()
	{
		return config;
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

	static int clampCycle(int n)
	{
		return Math.max(MIN_CYCLE, Math.min(MAX_CYCLE, n));
	}
}
