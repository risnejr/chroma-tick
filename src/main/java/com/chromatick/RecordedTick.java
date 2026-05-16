package com.chromatick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of what the recorder captured at a single tick slot —
 * a list of {@link TickActionEvent}s, one per category that fired this
 * tick. Empty slots return {@link #EMPTY} so callers can use
 * {@link #isEmpty()} instead of null checks.
 *
 * <p>The list is unmodifiable and the events themselves are immutable, so
 * the HUD renderer can read this each frame without coordination.
 */
final class RecordedTick
{
	static final RecordedTick EMPTY = new RecordedTick(Collections.emptyList());

	private final List<TickActionEvent> actions;

	private RecordedTick(List<TickActionEvent> actions)
	{
		this.actions = actions;
	}

	/** Wrap a captured event list. Null/empty inputs collapse to {@link #EMPTY}. */
	static RecordedTick of(List<TickActionEvent> events)
	{
		if (events == null || events.isEmpty())
		{
			return EMPTY;
		}
		return new RecordedTick(Collections.unmodifiableList(new ArrayList<>(events)));
	}

	/** True when no payload was recorded for this tick. */
	boolean isEmpty()
	{
		return actions.isEmpty();
	}

	/** Captured events; never null, possibly empty. Unmodifiable. */
	List<TickActionEvent> actions()
	{
		return actions;
	}
}
