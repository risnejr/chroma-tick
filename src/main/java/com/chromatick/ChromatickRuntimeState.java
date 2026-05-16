package com.chromatick;

import java.awt.Color;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;

/**
 * Transient runtime state for the cycle. Lives across game ticks but is not
 * persisted — config remains the persistence backend. Centralizing this state
 * here lets the plugin, overlays and tests reason about cycle/tick behavior
 * without each component owning its own slice.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code tickIndex} — current position in the color cycle (0-based).
 *   <li>{@code currentColor} — color resolved for the current tick.
 *   <li>{@code cycleLengthOverride} — hotkey-driven override of the configured
 *       cycle length; {@code -1} means "no override, use config".
 *   <li>{@code lastWorldPoint} — last observed player tile, used to detect
 *       movement for the prayer recorder.
 * </ul>
 */
@Singleton
class ChromatickRuntimeState
{
	private int tickIndex = 0;
	private Color currentColor = Color.WHITE;
	private int cycleLengthOverride = -1;
	private WorldPoint lastWorldPoint = null;

	int getTickIndex()
	{
		return tickIndex;
	}

	void setTickIndex(int t)
	{
		this.tickIndex = t;
	}

	Color getCurrentColor()
	{
		return currentColor;
	}

	void setCurrentColor(Color c)
	{
		this.currentColor = c;
	}

	/** True when a hotkey has set a per-session cycle-length override. */
	boolean hasCycleLengthOverride()
	{
		return cycleLengthOverride > 0;
	}

	int getCycleLengthOverride()
	{
		return cycleLengthOverride;
	}

	void setCycleLengthOverride(int n)
	{
		this.cycleLengthOverride = n;
	}

	void clearCycleLengthOverride()
	{
		this.cycleLengthOverride = -1;
	}

	/**
	 * Resolve the effective cycle length: the hotkey override if active,
	 * otherwise the supplied configured value. Kept as a plain function so
	 * tests don't need to inject a config.
	 */
	int effectiveCycleLength(int configCycleLength)
	{
		return cycleLengthOverride > 0 ? cycleLengthOverride : configCycleLength;
	}

	WorldPoint getLastWorldPoint()
	{
		return lastWorldPoint;
	}

	void setLastWorldPoint(WorldPoint wp)
	{
		this.lastWorldPoint = wp;
	}

	/** Reset all transient state. Called from plugin startUp/shutDown. */
	void reset()
	{
		tickIndex = 0;
		currentColor = Color.WHITE;
		cycleLengthOverride = -1;
		lastWorldPoint = null;
	}
}
