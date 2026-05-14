package com.chromatick;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("chromatick")
public interface ChromatickConfig extends Config
{
	// ─── Tick Cycle Settings ─────────────────────────────────────────────

	@ConfigItem(keyName = "staticMode", name = "", description = "", hidden = true)
	default boolean staticMode()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		position = 1,
		keyName = "staticColor",
		name = "Static Border Color",
		description = "True tile border color used in static mode."
	)
	default Color staticColor()
	{
		return new Color(0, 255, 0, 128);
	}

	@Alpha
	@ConfigItem(
		position = 2,
		keyName = "staticFillColor",
		name = "Static Fill Color",
		description = "True tile fill color used in static mode (alpha controls opacity). " +
			"Only applies when Fill Color is enabled in the True Tile section."
	)
	default Color staticFillColor()
	{
		return new Color(0, 255, 0, 0);
	}

	@Range(min = 2, max = 10)
	@ConfigItem(
		position = 3,
		keyName = "cycleLength",
		name = "Tick Cycle Length",
		description = "Number of ticks per color cycle (default). Color changes every tick. " +
			"Set to 4 for a 4-tick weapon, 6 for a 6-tick, etc. " +
			"Cycle hotkeys override this in-session without updating this slider; " +
			"Reset Cycle restores it. Pick the colors for each cycle from the sidebar panel."
	)
	default int cycleLength()
	{
		return 4;
	}

	// ─── Tile Overlay ─────────────────────────────────────────────────────

	@ConfigSection(
		name = "True Tile",
		description = "Configure how the true tile overlay looks",
		position = 5
	)
	String tileSettings = "tileSettings";

	@Range(min = 1, max = 5)
	@ConfigItem(
		position = 1,
		keyName = "tileBorderWidth",
		name = "Border Width",
		description = "Border thickness of the true tile",
		section = "tileSettings"
	)
	default double tileBorderWidth()
	{
		return 2;
	}

	@ConfigItem(
		position = 2,
		keyName = "enableFillColor",
		name = "Enable Fill Color",
		description = "Fill the true tile interior with color. " +
			"In cycle mode, uses the cycle color at the opacity set below. " +
			"In static mode, uses Static Fill Color.",
		section = "tileSettings"
	)
	default boolean enableFillColor()
	{
		return true;
	}

	@Range(min = 0, max = 255)
	@ConfigItem(
		position = 3,
		keyName = "fillOpacity",
		name = "Fill Opacity",
		description = "Opacity of the true tile fill in cycle mode (0 = transparent, 255 = solid). " +
			"In static mode, opacity is controlled by the alpha of Static Fill Color.",
		section = "tileSettings"
	)
	default int fillOpacity()
	{
		return 50;
	}

	@ConfigItem(
		position = 4,
		keyName = "drawBelowPlayer",
		name = "Draw Below Player",
		description = "Erase the tile from under the player model so it appears behind them. " +
			"Requires GPU rendering mode (enable in RuneLite settings). " +
			"Adapted from LeikvollE's Improved Tile Indicators.",
		section = "tileSettings"
	)
	default boolean drawBelowPlayer()
	{
		return false;
	}

	// ─── Hotkeys ──────────────────────────────────────────────────────────

	@ConfigSection(
		name = "Hotkeys",
		description = "Hotkeys for controlling the tick overlay during gameplay",
		position = 6
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
}
