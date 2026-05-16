package com.chromatick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link RecordedTick} value type. Narrow surface — it just
 * wraps "what the recorder captured at one tick slot" — but the contract
 * (EMPTY singleton for absent payload, unmodifiable view, defensive copy
 * of caller-mutated input) is what downstream consumers rely on.
 */
public class RecordedTickTest
{
	private static TickActionEvent prayer()
	{
		return TickActionEvent.of(TickActionCategory.PROTECTION_PRAYER, 0);
	}

	@Test
	public void emptyIsTheSentinelForAbsentPayload()
	{
		assertTrue(RecordedTick.EMPTY.isEmpty());
		assertTrue(RecordedTick.EMPTY.actions().isEmpty());
	}

	@Test
	public void ofWithNullCollapsesToEmpty()
	{
		assertSame(RecordedTick.EMPTY, RecordedTick.of(null));
	}

	@Test
	public void ofWithEmptyCollapsesToEmpty()
	{
		assertSame(RecordedTick.EMPTY, RecordedTick.of(Collections.emptyList()));
		assertSame(RecordedTick.EMPTY, RecordedTick.of(new ArrayList<>()));
	}

	@Test
	public void ofExposesTheCapturedEvents()
	{
		List<TickActionEvent> captured = Arrays.asList(prayer(), prayer());
		RecordedTick tick = RecordedTick.of(captured);

		assertFalse(tick.isEmpty());
		assertEquals(2, tick.actions().size());
	}

	@Test
	public void actionsViewIsUnmodifiable()
	{
		// HUD render reads the view each frame; mutations from there must not
		// be possible (would corrupt the recorder's internal buffer).
		RecordedTick tick = RecordedTick.of(Collections.singletonList(prayer()));
		try
		{
			tick.actions().add(prayer());
			assertTrue("expected UnsupportedOperationException", false);
		}
		catch (UnsupportedOperationException expected)
		{
			// good
		}
	}

	@Test
	public void ofDefensiveCopiesTheCallerList()
	{
		// Caller mutates the list it passed in — the recorded snapshot
		// must not reflect that change.
		List<TickActionEvent> mutable = new ArrayList<>();
		mutable.add(prayer());
		RecordedTick tick = RecordedTick.of(mutable);

		mutable.add(prayer());
		mutable.add(prayer());

		assertEquals(1, tick.actions().size());
	}
}
