package com.chromatick;

import com.chromatick.Enums.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Per-tick recording log for the HUD timeline. State container only — the
 * plugin feeds it the categorized events captured this tick plus a movement
 * signal; the HUD overlay reads back what was recorded at each
 * tick-in-cycle when rendering.
 *
 * <p>Payload is a list of {@link TickActionEvent}s, one per category that
 * fired this tick. Currently only PROTECTION_PRAYER events are emitted by
 * the plugin; the upcoming capture service will add click / item-use /
 * movement events without changing this service.
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
	private final Map<Integer, List<TickActionEvent>> store = new HashMap<>();

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
	 * @param tickIndex position in the cycle, 0-based
	 * @param events    categorized events the plugin observed this tick;
	 *                  may be empty
	 * @param moved     player moved between last tick and this one
	 * @param armTicks  configured ARM window length (1..10); only used
	 *                  when {@code mode == ARM}
	 */
	void onTick(int tickIndex, List<TickActionEvent> events, boolean moved, int armTicks)
	{
		switch (mode)
		{
			case OFF:
				return;
			case ALWAYS:
				capture(tickIndex, events);
				return;
			case ARM:
				if (moved)
				{
					armRemaining = Math.max(1, armTicks);
				}
				if (armRemaining > 0)
				{
					capture(tickIndex, events);
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
		return RecordedTick.of(store.get(tickIndex));
	}

	/** Forget everything. Called on entering OFF, on shutdown, and on demand. */
	void clear()
	{
		store.clear();
		armRemaining = 0;
	}

	private void capture(int tickIndex, List<TickActionEvent> events)
	{
		// Defensive copy so the caller can reuse its list. Events themselves
		// are immutable, so a shallow copy of the references is sufficient.
		store.put(tickIndex, events == null || events.isEmpty()
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(events)));
	}
}
