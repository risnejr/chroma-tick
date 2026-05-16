package com.chromatick;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Prayer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link RecordedTick} value type. Narrow surface — it just
 * wraps "what the recorder captured at one tick slot" — but the contract
 * (EMPTY singleton for absent payload, unmodifiable view, defensive
 * collapse of null/empty input) is what downstream consumers rely on.
 */
public class RecordedTickTest
{
	@Test
	public void emptyIsTheSentinelForAbsentPayload()
	{
		assertTrue(RecordedTick.EMPTY.isEmpty());
		assertTrue(RecordedTick.EMPTY.prayers().isEmpty());
	}

	@Test
	public void ofPrayersWithNullCollapsesToEmpty()
	{
		assertSame(RecordedTick.EMPTY, RecordedTick.ofPrayers(null));
	}

	@Test
	public void ofPrayersWithEmptyCollapsesToEmpty()
	{
		assertSame(RecordedTick.EMPTY, RecordedTick.ofPrayers(Collections.emptySet()));
		assertSame(RecordedTick.EMPTY, RecordedTick.ofPrayers(EnumSet.noneOf(Prayer.class)));
	}

	@Test
	public void ofPrayersExposesTheCapturedSet()
	{
		Set<Prayer> captured = EnumSet.of(Prayer.PROTECT_FROM_MELEE);
		RecordedTick tick = RecordedTick.ofPrayers(captured);

		assertFalse(tick.isEmpty());
		assertEquals(captured, tick.prayers());
	}

	@Test
	public void prayersViewIsUnmodifiable()
	{
		// HUD render reads the view each frame; mutations from there must not
		// be possible (would corrupt the recorder's internal buffer).
		RecordedTick tick = RecordedTick.ofPrayers(EnumSet.of(Prayer.PROTECT_FROM_MELEE));
		try
		{
			tick.prayers().add(Prayer.PROTECT_FROM_MAGIC);
			assertTrue("expected UnsupportedOperationException", false);
		}
		catch (UnsupportedOperationException expected)
		{
			// good
		}
	}
}
