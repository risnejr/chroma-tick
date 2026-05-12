package com.chromatick;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
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
import net.runelite.client.ui.overlay.OverlayManager;

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
	// PALETTES[n] = the perceptually-optimal n-color palette, indexed by cycle length.
	// Computed using OKLab perceptual color space with a greedy farthest-point algorithm.
	// Each palette has its colors maximally distinct and spread evenly around the hue wheel.
	private static final Color[][] PALETTES = {
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
	private ChromatickOverlay tileOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	/** Current position in the color cycle (0-based). */
	@Getter
	protected int tickIndex = 0;

	/** The current color to render this tick. */
	@Getter
	protected Color currentColor = Color.WHITE;

	/** Hotkey override for cycle length; -1 = use config slider value. */
	private int cycleLengthOverride = -1;

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
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(tileOverlay);
		keyManager.unregisterKeyListener(this);
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
			return;
		}
		int cycleLength = getEffectiveCycleLength();
		tickIndex = tickIndex % cycleLength;
		currentColor = getColorByIndex(tickIndex);
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

		for (int n = 2; n <= 10; n++)
		{
			if (getCycleHotkeyByLength(n).matches(e))
			{
				cycleLengthOverride = n;
				return;
			}
		}

		if (config.resetCycleHotkey().matches(e))
		{
			cycleLengthOverride = -1;
			tickIndex = 0;
			currentColor = getColorByIndex(0);
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
		if (config.usePreattentivePalette())
		{
			Color[] palette = PALETTES[getEffectiveCycleLength()];
			return palette[index % palette.length];
		}

		switch (index)
		{
			case 0: return config.color1();
			case 1: return config.color2();
			case 2: return config.color3();
			case 3: return config.color4();
			case 4: return config.color5();
			case 5: return config.color6();
			case 6: return config.color7();
			case 7: return config.color8();
			case 8: return config.color9();
			case 9: return config.color10();
			default: return config.color1();
		}
	}
}
