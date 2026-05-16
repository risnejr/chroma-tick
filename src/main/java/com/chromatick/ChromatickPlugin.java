package com.chromatick;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
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
	private PrayerRecorderService recorder;

	@Inject
	private ChromatickRuntimeState state;

	@Inject
	private ChromatickConfigMigrator configMigrator;

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
		configMigrator.migrate();
		applyDisplayMode();
		keyManager.registerKeyListener(this);
		state.reset();
		recorder.setMode(config.recordMode());
		state.setCurrentColor(config.staticMode() ? config.staticColor() : getColorByIndex(0));

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
		state.reset();
		recorder.clear();
	}


	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Static mode freezes the *color*, not the cycle. The HUD overlay still
		// uses tickIndex to advance its active-glyph highlight.
		int cycleLength = getEffectiveCycleLength();
		int nextTick = (state.getTickIndex() + 1) % cycleLength;
		state.setTickIndex(nextTick);
		state.setCurrentColor(config.staticMode() ? config.staticColor() : getColorByIndex(nextTick));

		// Feed the prayer recorder. Cheap when mode == OFF (early return).
		Player local = client.getLocalPlayer();
		WorldPoint pos = local != null ? local.getWorldLocation() : null;
		WorldPoint last = state.getLastWorldPoint();
		boolean moved = pos != null && last != null && !last.equals(pos);
		if (pos != null)
		{
			state.setLastWorldPoint(pos);
		}
		RecordMode beforeMode = recorder.getMode();
		// Clamp the ARM window at the effective cycle length here, not by
		// persisting back to config. Persisting would mean a hotkey cycle
		// shrink → expand round-trip permanently destroys the user's setting.
		int armTicks = Math.min(config.recordArmTicks(), cycleLength);
		recorder.onTick(nextTick, activeProtectPrayers(), moved, armTicks);
		RecordMode afterMode = recorder.getMode();
		if (beforeMode != afterMode)
		{
			// ARM auto-exited to OFF; persist so the panel + config reflect it.
			configManager.setConfiguration("chromatick", "recordMode", afterMode);
		}

		// Drain the HUD overlay's drag signal. render() may have detected an
		// alt-drag and flagged it; flipping the persisted anchor target lives
		// here so render stays free of state mutation.
		if (hudOverlay.consumeUserDragged())
		{
			setHudAnchorTarget(HudAnchorTarget.NONE);
		}
	}

	private Set<Prayer> activeProtectPrayers()
	{
		EnumSet<Prayer> active = EnumSet.noneOf(Prayer.class);
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MELEE))    active.add(Prayer.PROTECT_FROM_MELEE);
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)) active.add(Prayer.PROTECT_FROM_MISSILES);
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))    active.add(Prayer.PROTECT_FROM_MAGIC);
		return active;
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
			state.clearCycleLengthOverride();
		}
		int cycleLength = getEffectiveCycleLength();
		int idx = state.getTickIndex() % cycleLength;
		state.setTickIndex(idx);
		state.setCurrentColor(config.staticMode() ? config.staticColor() : getColorByIndex(idx));

		if ("displayMode".equals(key))
		{
			applyDisplayMode();
		}

		if ("recordMode".equals(key))
		{
			recorder.setMode(config.recordMode());
		}

		if (panel == null)
		{
			return;
		}
		if ("cycleLength".equals(key) || "staticMode".equals(key) || "displayMode".equals(key)
			|| "hudScale".equals(key) || "hudAnchorTarget".equals(key)
			|| "hudVertical".equals(key)
			|| "recordMode".equals(key) || "recordIconPosition".equals(key)
			|| "recordArmTicks".equals(key))
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

		if (config.recordModeHotkey().matches(e))
		{
			setRecordMode(config.recordMode().next());
		}

		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			if (getCycleHotkeyByLength(n).matches(e))
			{
				state.setCycleLengthOverride(n);
				if (panel != null)
				{
					SwingUtilities.invokeLater(panel::refreshFromConfig);
				}
				return;
			}
		}

		if (config.resetCycleHotkey().matches(e))
		{
			state.clearCycleLengthOverride();
			state.setTickIndex(0);
			state.setCurrentColor(config.staticMode() ? config.staticColor() : getColorByIndex(0));
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
		return state.effectiveCycleLength(config.cycleLength());
	}

	public int getTickIndex()
	{
		return state.getTickIndex();
	}

	public Color getCurrentColor()
	{
		return state.getCurrentColor();
	}

	/** Make {@code n} the active cycle, clearing any hotkey override. */
	void setActiveCycle(int n)
	{
		state.clearCycleLengthOverride();
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
		// Floor at 1; do not clamp against the effective cycle length here —
		// onGameTick clamps at the recorder feed site, so the persisted value
		// can stay at the user's intent (e.g. 8) even while a hotkey shrinks
		// the effective cycle (e.g. 4). When the cycle expands again the
		// persisted value comes back without rewriting config.
		configManager.setConfiguration("chromatick", "recordArmTicks", Math.max(1, ticks));
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
			// On re-add, the overlay's stale lastSetLocation can mismatch the
			// framework's persisted preferred location, false-positiving the
			// next render's drag-detection check and flipping anchor to NONE.
			// Resetting the drag state lets the overlay re-anchor cleanly.
			hudOverlay.clearDragState();
		}
		else
		{
			overlayManager.remove(hudOverlay);
		}
	}

	/**
	 * Immutable view-state for the sidebar panel. Captures all settings + the
	 * runtime-resolved effective cycle length in a single read, so the panel
	 * doesn't reach into config directly.
	 */
	ChromatickPanelSnapshot snapshot()
	{
		return ChromatickPanelSnapshot.from(config, getEffectiveCycleLength());
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
}
