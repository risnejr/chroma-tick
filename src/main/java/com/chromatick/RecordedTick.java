package com.chromatick;

import java.util.Collections;
import java.util.Set;
import net.runelite.api.Prayer;

/**
 * Immutable snapshot of what the recorder captured at a single tick slot.
 *
 * <p>Currently the only captured payload is the active protect-prayer set.
 * Wrapping it in a value type now (rather than passing {@code Set<Prayer>}
 * around directly) means the recorder→resolver→HUD path is already shaped
 * for the generic action recorder: when {@code TickActionEvent} arrives,
 * this class gains an accessor for it without churning every call site.
 *
 * <p>Always non-null — empty slots return {@link #EMPTY} so callers can
 * use {@link #isEmpty()} instead of null-checks.
 */
final class RecordedTick
{
	static final RecordedTick EMPTY = new RecordedTick(Collections.emptySet());

	private final Set<Prayer> prayers;

	private RecordedTick(Set<Prayer> prayers)
	{
		this.prayers = prayers;
	}

	/** Wrap a captured prayer set. Null/empty inputs collapse to {@link #EMPTY}. */
	static RecordedTick ofPrayers(Set<Prayer> prayers)
	{
		if (prayers == null || prayers.isEmpty())
		{
			return EMPTY;
		}
		return new RecordedTick(Collections.unmodifiableSet(prayers));
	}

	/** True when no payload was recorded for this tick. */
	boolean isEmpty()
	{
		return prayers.isEmpty();
	}

	/** Captured protect-prayer set; never null, possibly empty. */
	Set<Prayer> prayers()
	{
		return prayers;
	}
}
