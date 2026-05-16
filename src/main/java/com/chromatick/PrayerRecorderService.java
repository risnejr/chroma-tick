package com.chromatick;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.Prayer;

/**
 * Per-tick prayer log for the HUD timeline. State container only — the
 * plugin feeds it active prayers and movement signals each game tick;
 * the HUD overlay reads back per-tick {@link Prayer} sets when rendering.
 *
 * <p>Mode semantics:
 * <ul>
 *   <li>{@link RecordMode#OFF} — never captures; on entry, clears the buffer.
 *   <li>{@link RecordMode#ARM} — captures the movement tick and a
 *       configurable trailing window (armTicks total).
 *   <li>{@link RecordMode#ALWAYS} — captures every tick; subsequent cycles
 *       overdub at the same tick index.
 * </ul>
 *
 * <p>Recordings are keyed by tick-index-in-cycle. Cycle-length changes are
 * not auto-cleared — out-of-range indices are simply ignored by the renderer
 * and will be overwritten as new ticks come around.
 */
@Singleton
class PrayerRecorderService
{
	private final Map<Integer, EnumSet<Prayer>> store = new HashMap<>();

	private RecordMode mode = RecordMode.OFF;
	private int armRemaining = 0;

	RecordMode getMode()
	{
		return mode;
	}

	/**
	 * Set the recorder mode. Transitioning to {@link RecordMode#OFF} clears
	 * the captured buffer; transitions between ARM and ALWAYS preserve it
	 * (new captures simply overdub).
	 */
	void setMode(RecordMode next)
	{
		if (next == RecordMode.OFF)
		{
			clear();
		}
		this.mode = next;
		this.armRemaining = 0;
	}

	/** Convenience for the cycle-mode hotkey. Returns the new mode. */
	RecordMode cycleMode()
	{
		setMode(mode.next());
		return mode;
	}

	/**
	 * Capture (or skip) the current tick. Called once per {@code GameTick}.
	 *
	 * @param tickIndex     position in the cycle, 0-based
	 * @param activePrayers protect prayers that were active this tick
	 * @param moved         player moved between last tick and this one
	 * @param armTicks      configured ARM window length (1..10); only used
	 *                      when {@code mode == ARM}
	 */
	void onTick(int tickIndex, Set<Prayer> activePrayers, boolean moved, int armTicks)
	{
		switch (mode)
		{
			case OFF:
				return;
			case ALWAYS:
				capture(tickIndex, activePrayers);
				return;
			case ARM:
				if (moved)
				{
					armRemaining = Math.max(1, armTicks);
				}
				if (armRemaining > 0)
				{
					capture(tickIndex, activePrayers);
					armRemaining--;
				}
				return;
		}
	}

	/** Prayers captured at this tick-in-cycle, or an empty set if none. */
	Set<Prayer> getPrayersAtTick(int tickIndex)
	{
		EnumSet<Prayer> captured = store.get(tickIndex);
		return captured != null ? Collections.unmodifiableSet(captured) : Collections.emptySet();
	}

	/** Forget everything. Called on entering OFF, on shutdown, and on demand. */
	void clear()
	{
		store.clear();
		armRemaining = 0;
	}

	private void capture(int tickIndex, Set<Prayer> activePrayers)
	{
		EnumSet<Prayer> snapshot = activePrayers.isEmpty()
			? EnumSet.noneOf(Prayer.class)
			: EnumSet.copyOf(activePrayers);
		store.put(tickIndex, snapshot);
	}
}
