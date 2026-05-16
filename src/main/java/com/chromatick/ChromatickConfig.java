package com.chromatick;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

/**
 * Settings page only exposes hotkeys. All visual settings (cycle length,
 * per-cycle palettes, static colors, tile appearance) live in the sidebar
 * panel — the config values below are still used for persistence but are
 * hidden from the settings UI.
 */
@ConfigGroup("chromatick")
public interface ChromatickConfig extends Config
{
	// ─── Hidden state (driven by the sidebar panel) ─────────────────────

	@ConfigItem(keyName = "staticMode", name = "", description = "", hidden = true)
	default boolean staticMode()
	{
		return false;
	}

	@ConfigItem(keyName = "staticColor", name = "", description = "", hidden = true)
	default Color staticColor()
	{
		return new Color(0, 255, 0, 128);
	}

	@ConfigItem(keyName = "staticFillColor", name = "", description = "", hidden = true)
	default Color staticFillColor()
	{
		return new Color(0, 255, 0, 0);
	}

	@ConfigItem(keyName = "cycleLength", name = "", description = "", hidden = true)
	default int cycleLength()
	{
		return 4;
	}

	@ConfigItem(keyName = "tileBorderWidth", name = "", description = "", hidden = true)
	default double tileBorderWidth()
	{
		return 2;
	}

	@ConfigItem(keyName = "enableFillColor", name = "", description = "", hidden = true)
	default boolean enableFillColor()
	{
		return true;
	}

	@ConfigItem(keyName = "fillOpacity", name = "", description = "", hidden = true)
	default int fillOpacity()
	{
		return 50;
	}

	@ConfigItem(keyName = "drawBelowPlayer", name = "", description = "", hidden = true)
	default boolean drawBelowPlayer()
	{
		return false;
	}

	@ConfigItem(keyName = "paletteMode", name = "", description = "", hidden = true)
	default PaletteMode paletteMode()
	{
		return PaletteMode.GRID;
	}

	@ConfigItem(keyName = "sequentialFill", name = "", description = "", hidden = true)
	default boolean sequentialFill()
	{
		return true;
	}

	// ─── HUD overlay (chatbubble metronome) ─────────────────────────────

	/** Where the tick visualization renders. */
	@ConfigItem(keyName = "displayMode", name = "", description = "", hidden = true)
	default DisplayMode displayMode()
	{
		return DisplayMode.TILE;
	}

	/** Glyph style inside the HUD frame. */
	@ConfigItem(keyName = "hudGlyph", name = "", description = "", hidden = true)
	default HudGlyph hudGlyph()
	{
		return HudGlyph.DOTS;
	}

	/** 50–400, where 100 = native size. */
	@ConfigItem(keyName = "hudScale", name = "", description = "", hidden = true)
	default int hudScale()
	{
		return 200;
	}

	/** 0–100 opacity for the current tick. */
	@ConfigItem(keyName = "hudActiveOpacity", name = "", description = "", hidden = true)
	default int hudActiveOpacity()
	{
		return 100;
	}

	/** 0–100 opacity for the non-current ticks. */
	@ConfigItem(keyName = "hudInactiveOpacity", name = "", description = "", hidden = true)
	default int hudInactiveOpacity()
	{
		return 40;
	}

	/** Active glyph rendered in bold (Numbers variant only). */
	@ConfigItem(keyName = "hudBold", name = "", description = "", hidden = true)
	default boolean hudBold()
	{
		return false;
	}

	/** 0–200 extra-scale (%) on the active glyph; layout cells reserve space so siblings never shift. */
	@ConfigItem(keyName = "hudPop", name = "", description = "", hidden = true)
	default int hudPop()
	{
		return 0;
	}

	/** Pixel gap between glyphs at scale=100. Scales with hudScale. Negative = overlap. */
	@ConfigItem(keyName = "hudSpacing", name = "", description = "", hidden = true)
	default int hudSpacing()
	{
		return 0;
	}

	/** Stack glyphs top-to-bottom instead of left-to-right. */
	@ConfigItem(keyName = "hudVertical", name = "", description = "", hidden = true)
	default boolean hudVertical()
	{
		return false;
	}

	/** Where the HUD anchors to the player model. */
	@ConfigItem(keyName = "hudAnchorTarget", name = "", description = "", hidden = true)
	default HudAnchorTarget hudAnchorTarget()
	{
		return HudAnchorTarget.FEET;
	}

	/** Vertical offset (canvas px) from the anchor point to the HUD bar's center. */
	@ConfigItem(keyName = "hudVerticalOffset", name = "", description = "", hidden = true)
	default int hudVerticalOffset()
	{
		return 30;
	}

	/** Horizontal offset (canvas px) from the anchor point to the HUD bar's center. */
	@ConfigItem(keyName = "hudHorizontalOffset", name = "", description = "", hidden = true)
	default int hudHorizontalOffset()
	{
		return 0;
	}

	/** Render a single glyph that cycles color/number in place instead of a row of all ticks. */
	@ConfigItem(keyName = "hudCycleInPlace", name = "", description = "", hidden = true)
	default boolean hudCycleInPlace()
	{
		return false;
	}

	// ─── Per-tick prayer recorder ───────────────────────────────────────

	/** Recorder state: OFF, ARM (waits for movement) or ALWAYS (every tick). */
	@ConfigItem(keyName = "recordMode", name = "", description = "", hidden = true)
	default RecordMode recordMode()
	{
		return RecordMode.OFF;
	}

	/** Icon band placement relative to the HUD row. */
	@ConfigItem(keyName = "recordIconPosition", name = "", description = "", hidden = true)
	default IconPosition recordIconPosition()
	{
		return IconPosition.ABOVE;
	}

	/** In ARM mode, total ticks to capture (movement tick + N-1 more). 1..10. */
	@ConfigItem(keyName = "recordArmTicks", name = "", description = "", hidden = true)
	default int recordArmTicks()
	{
		return 2;
	}

	// ─── Hotkeys ──────────────────────────────────────────────────────────

	@ConfigSection(
		name = "Hotkeys",
		description = "Hotkeys for controlling the tick overlay during gameplay",
		position = 1
	)
	String hotkeySettings = "hotkeySettings";

	@ConfigItem(
		position = 1,
		keyName = "toggleOverlayHotkey",
		name = "Toggle Static / Cycle",
		description = "Switch between static color mode and cycling mode. " +
			"The tile stays visible either way.",
		section = "hotkeySettings"
	)
	default Keybind toggleOverlayHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 2,
		keyName = "resetCycleHotkey",
		name = "Reset Cycle",
		description = "Reset the tick cycle to position 0 and clear any hotkey override",
		section = "hotkeySettings"
	)
	default Keybind resetCycleHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 3,
		keyName = "cycle2Hotkey",
		name = "2-Tick Cycle",
		description = "Switch to a 2-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle2Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 4,
		keyName = "cycle3Hotkey",
		name = "3-Tick Cycle",
		description = "Switch to a 3-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle3Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 5,
		keyName = "cycle4Hotkey",
		name = "4-Tick Cycle",
		description = "Switch to a 4-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle4Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 6,
		keyName = "cycle5Hotkey",
		name = "5-Tick Cycle",
		description = "Switch to a 5-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle5Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 7,
		keyName = "cycle6Hotkey",
		name = "6-Tick Cycle",
		description = "Switch to a 6-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle6Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 8,
		keyName = "cycle7Hotkey",
		name = "7-Tick Cycle",
		description = "Switch to a 7-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle7Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 9,
		keyName = "cycle8Hotkey",
		name = "8-Tick Cycle",
		description = "Switch to an 8-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle8Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 10,
		keyName = "cycle9Hotkey",
		name = "9-Tick Cycle",
		description = "Switch to a 9-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle9Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 11,
		keyName = "cycle10Hotkey",
		name = "10-Tick Cycle",
		description = "Switch to a 10-tick cycle",
		section = "hotkeySettings"
	)
	default Keybind cycle10Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 12,
		keyName = "recordModeHotkey",
		name = "Cycle Recorder Mode",
		description = "Cycle the per-tick prayer recorder through OFF, ARM and ALWAYS",
		section = "hotkeySettings"
	)
	default Keybind recordModeHotkey()
	{
		return Keybind.NOT_SET;
	}
}
