package com.chromatick;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
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
@PluginDescriptor(
	name = "ChromaTick",
	description = "Cycles your true tile color each game tick for preattentive tick tracking",
	tags = {
		"true", "tile", "tick", "metronome", "color", "overlay", "preattentive", "chroma",
	}
)
public class ChromatickPlugin extends Plugin implements KeyListener
{
	private static final int MIN_CYCLE = PaletteService.MIN_CYCLE;
	private static final int MAX_CYCLE = PaletteService.MAX_CYCLE;

	@Inject
	private Client client;

	@Inject
	private ChromatickConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PaletteService palettes;

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
		migrateLegacyEnumValues();
		applyDisplayMode();
		keyManager.registerKeyListener(this);
		tickIndex = 0;
		cycleLengthOverride = -1;
		currentColor = config.staticMode() ? config.staticColor() : getColorByIndex(0);

		panel = new ChromatickPanel(this, palettes);
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
		else if (key.startsWith(PaletteService.CUSTOM_PALETTE_PREFIX))
		{
			// Palette change — only repaint swatches if it matches the cycle
			// the panel is currently editing. Do NOT snap selection.
			int n = PaletteService.parsePaletteKey(key);
			if (n > 0)
			{
				SwingUtilities.invokeLater(() -> panel.onPaletteChanged(n));
			}
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
		configManager.setConfiguration("chromatick", "cycleLength", PaletteService.clampCycle(n));
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

	void setPaletteMode(PaletteMode mode)
	{
		configManager.setConfiguration("chromatick", "paletteMode", mode);
	}

	void setSequentialFill(boolean on)
	{
		configManager.setConfiguration("chromatick", "sequentialFill", on);
	}

	// ─── HUD overlay setters ────────────────────────────────────────────

	void setDisplayMode(DisplayMode mode)
	{
		configManager.setConfiguration("chromatick", "displayMode", mode);
	}

	void setHudGlyph(HudGlyph glyph)
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
	 * frame. Switching to NONE preserves the current position.
	 */
	void setHudAnchorTarget(HudAnchorTarget target)
	{
		configManager.setConfiguration("chromatick", "hudAnchorTarget", target);
		if (target != HudAnchorTarget.NONE)
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

	// ─── Per-tick prayer recorder ───────────────────────────────────────

	void setRecordMode(RecordMode mode)
	{
		configManager.setConfiguration("chromatick", "recordMode", mode);
	}

	void setRecordIconPosition(IconPosition pos)
	{
		configManager.setConfiguration("chromatick", "recordIconPosition", pos);
	}

	void setRecordArmTicks(int ticks)
	{
		configManager.setConfiguration("chromatick", "recordArmTicks", Math.max(1, Math.min(10, ticks)));
	}

	private void applyDisplayMode()
	{
		DisplayMode mode = config.displayMode();
		boolean tile = mode == DisplayMode.TILE || mode == DisplayMode.BOTH;
		boolean hud  = mode == DisplayMode.HUD  || mode == DisplayMode.BOTH;
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
		Color[] palette = palettes.getCustomPaletteForCycle(getEffectiveCycleLength());
		return palette[index % palette.length];
	}

	/**
	 * Rewrite legacy lowercase string values for the four enum-typed config
	 * keys (displayMode/hudGlyph/hudAnchorTarget/paletteMode) to their
	 * uppercase enum names. Pre-1.0 the plugin stored these as raw lowercase
	 * strings; once the getters return enums those raw values fail to
	 * deserialize and silently snap to the default. Runs once per startUp;
	 * already-migrated values are no-ops.
	 */
	private void migrateLegacyEnumValues()
	{
		migrateEnumKey("displayMode", DisplayMode.class);
		migrateEnumKey("hudGlyph", HudGlyph.class);
		migrateEnumKey("hudAnchorTarget", HudAnchorTarget.class);
		migrateEnumKey("paletteMode", PaletteMode.class);
	}

	private <T extends Enum<T>> void migrateEnumKey(String key, Class<T> enumType)
	{
		String raw = configManager.getConfiguration("chromatick", key);
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
			T migrated = Enum.valueOf(enumType, raw.toUpperCase(java.util.Locale.ROOT));
			configManager.setConfiguration("chromatick", key, migrated.name());
		}
		catch (IllegalArgumentException ignored)
		{
			// Unknown legacy value — leave it so RuneLite falls back to default.
		}
	}
}
