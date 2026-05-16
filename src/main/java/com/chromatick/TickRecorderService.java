package com.chromatick;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.Prayer;

/**
 * Per-tick recording log for the HUD timeline. State container only — the
 * plugin feeds it the per-tick payload + movement signal each game tick;
 * the HUD overlay reads back what was recorded at each tick-in-cycle when
 * rendering.
 *
 * <p>Currently the payload is the active protect-prayer set; the recorder
 * is structured so the captured-type can be widened to a generic action
 * event later without changing this service's lifecycle, mode semantics,
 * or buffer model.
 *
 * <p>Mode semantics:
 * <ul>
 *   <li>{@link RecordMode#OFF} — never captures.
 *   <li>{@link RecordMode#ARM} — captures the movement tick and a
 *       configurable trailing window (armTicks total). When the window
 *       expires, the recorder transitions itself to OFF (one-shot) but
 *       <em>preserves</em> the captured buffer so the user can review it.
 *   <li>{@link RecordMode#ALWAYS} — captures every tick; subsequent cycles
 *       overdub at the same tick index.
 * </ul>
 *
 * <p>Buffer lifecycle:
 * <ul>
 *   <li>Entering ARM (from any other state) clears the buffer — each ARM
 *       trigger is a fresh capture.
 *   <li>All other transitions preserve the buffer (the ARM auto-exit, the
 *       Always-overdub model, and manual switches to OFF).
 *   <li>{@link #clear()} is the explicit forget-everything path used at
 *       shutdown.
 * </ul>
 *
 * <p>Recordings are keyed by tick-index-in-cycle. Cycle-length changes are
 * not auto-cleared — out-of-range indices are simply ignored by the renderer
 * and will be overwritten as new ticks come around.
 */
@Singleton
class TickRecorderService
{
	private final Map<Integer, EnumSet<Prayer>> store = new HashMap<>();

	private RecordMode mode = RecordMode.OFF;
	private int armRemaining = 0;

	RecordMode getMode()
	{
		return mode;
	}

	/**
	 * Set the recorder mode. Entering ARM (from a non-ARM state) clears
	 * the buffer to start a fresh capture session. Other transitions —
	 * including manual moves to OFF and the auto-exit from ARM after a
	 * window expires — preserve the buffer so the recording stays visible.
	 */
	void setMode(RecordMode next)
	{
		if (next == RecordMode.ARM && this.mode != RecordMode.ARM)
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
					if (armRemaining == 0)
					{
						// One-shot: window expired, exit ARM. Direct assignment
						// (not via setMode) so the captured buffer is preserved
						// for the user to review.
						mode = RecordMode.OFF;
					}
				}
				return;
		}
	}

	/** True if the buffer holds at least one tick's worth of recorded payload. */
	boolean hasCaptures()
	{
		return !store.isEmpty();
	}

	/**
	 * Recorded payload at this tick-in-cycle, or {@link RecordedTick#EMPTY}
	 * if nothing was captured at that slot.
	 */
	RecordedTick getRecordedAt(int tickIndex)
	{
		EnumSet<Prayer> captured = store.get(tickIndex);
		return RecordedTick.ofPrayers(captured);
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
