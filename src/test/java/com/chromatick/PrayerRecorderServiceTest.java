package com.chromatick;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Prayer;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the per-tick recorder state machine. All inputs are plain ints
 * and Prayer sets — no RuneLite injection required.
 */
public class PrayerRecorderServiceTest
{
	private static final Set<Prayer> MELEE   = Collections.singleton(Prayer.PROTECT_FROM_MELEE);
	private static final Set<Prayer> MAGIC   = Collections.singleton(Prayer.PROTECT_FROM_MAGIC);
	private static final Set<Prayer> MISSILE = Collections.singleton(Prayer.PROTECT_FROM_MISSILES);
	private static final Set<Prayer> NONE    = Collections.emptySet();

	private PrayerRecorderService recorder;

	@Before
	public void setUp()
	{
		recorder = new PrayerRecorderService();
	}

	// ─── Initial state ──────────────────────────────────────────────────

	@Test
	public void startsInOffMode()
	{
		assertEquals(RecordMode.OFF, recorder.getMode());
	}

	@Test
	public void emptyBufferReturnsEmptySetPerTick()
	{
		assertTrue(recorder.getPrayersAtTick(0).isEmpty());
		assertTrue(recorder.getPrayersAtTick(5).isEmpty());
	}

	// ─── Mode cycling ───────────────────────────────────────────────────

	@Test
	public void cycleModeAdvancesOffArmAlwaysOff()
	{
		assertEquals(RecordMode.ARM,    recorder.cycleMode());
		assertEquals(RecordMode.ALWAYS, recorder.cycleMode());
		assertEquals(RecordMode.OFF,    recorder.cycleMode());
		assertEquals(RecordMode.ARM,    recorder.cycleMode());
	}

	// ─── OFF mode ───────────────────────────────────────────────────────

	@Test
	public void offModeNeverCaptures()
	{
		recorder.onTick(0, MELEE, true,  2);
		recorder.onTick(1, MAGIC, false, 2);
		assertTrue(recorder.getPrayersAtTick(0).isEmpty());
		assertTrue(recorder.getPrayersAtTick(1).isEmpty());
	}

	@Test
	public void transitioningToOffClearsBuffer()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(2, MELEE, false, 2);
		assertEquals(MELEE, recorder.getPrayersAtTick(2));

		recorder.setMode(RecordMode.OFF);
		assertTrue(recorder.getPrayersAtTick(2).isEmpty());
	}

	// ─── ALWAYS mode ────────────────────────────────────────────────────

	@Test
	public void alwaysModeCapturesEveryTickRegardlessOfMovement()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, MELEE,   false, 2);
		recorder.onTick(1, MAGIC,   true,  2);
		recorder.onTick(2, MISSILE, false, 2);

		assertEquals(MELEE,   recorder.getPrayersAtTick(0));
		assertEquals(MAGIC,   recorder.getPrayersAtTick(1));
		assertEquals(MISSILE, recorder.getPrayersAtTick(2));
	}

	@Test
	public void alwaysModeOverdubsOnNextCycle()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, MELEE, false, 2);
		assertEquals(MELEE, recorder.getPrayersAtTick(0));

		// Cycle wraps; tick 0 hits again with a different prayer.
		recorder.onTick(0, MAGIC, false, 2);
		assertEquals(MAGIC, recorder.getPrayersAtTick(0));
	}

	@Test
	public void alwaysModeCapturesEmptySetWhenNoPrayersActive()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, NONE, false, 2);
		// Still captured (so renderer can tell "we were here, nothing active"
		// apart from "we never recorded this tick").
		assertTrue(recorder.getPrayersAtTick(0).isEmpty());
	}

	// ─── ARM mode ───────────────────────────────────────────────────────

	@Test
	public void armDoesNothingUntilMovement()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, MELEE, false, 2);
		recorder.onTick(1, MAGIC, false, 2);
		assertTrue(recorder.getPrayersAtTick(0).isEmpty());
		assertTrue(recorder.getPrayersAtTick(1).isEmpty());
	}

	@Test
	public void armCapturesMovementTickAndTrailingWindow()
	{
		recorder.setMode(RecordMode.ARM);
		// armTicks = 2 → capture movement tick + 1 more.
		recorder.onTick(0, NONE,    false, 2);
		recorder.onTick(1, MELEE,   true,  2);
		recorder.onTick(2, MAGIC,   false, 2);
		recorder.onTick(3, MISSILE, false, 2);

		assertTrue(recorder.getPrayersAtTick(0).isEmpty());
		assertEquals(MELEE, recorder.getPrayersAtTick(1));
		assertEquals(MAGIC, recorder.getPrayersAtTick(2));
		assertTrue(recorder.getPrayersAtTick(3).isEmpty());
	}

	@Test
	public void armTicksOneCapturesOnlyTheMovementTick()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, MELEE, true,  1);
		recorder.onTick(1, MAGIC, false, 1);

		assertEquals(MELEE, recorder.getPrayersAtTick(0));
		assertTrue(recorder.getPrayersAtTick(1).isEmpty());
	}

	@Test
	public void movementWhileAlreadyArmedRefreshesWindow()
	{
		recorder.setMode(RecordMode.ARM);
		// Move on tick 0; armTicks=2 → expect captures on 0,1.
		recorder.onTick(0, MELEE, true,  2);
		// Move again on tick 1 before window expires → window resets to 2.
		recorder.onTick(1, MAGIC, true,  2);
		recorder.onTick(2, MISSILE, false, 2);
		recorder.onTick(3, NONE,    false, 2);

		assertEquals(MELEE,   recorder.getPrayersAtTick(0));
		assertEquals(MAGIC,   recorder.getPrayersAtTick(1));
		assertEquals(MISSILE, recorder.getPrayersAtTick(2));
		assertTrue(recorder.getPrayersAtTick(3).isEmpty());
	}

	@Test
	public void armTicksAtLeastOneEvenWhenConfigIsZeroOrNegative()
	{
		recorder.setMode(RecordMode.ARM);
		// Defensive: bad config shouldn't silently disable the recorder.
		recorder.onTick(0, MELEE, true, 0);
		recorder.onTick(1, MAGIC, false, 0);

		assertEquals(MELEE, recorder.getPrayersAtTick(0));
		assertTrue(recorder.getPrayersAtTick(1).isEmpty());
	}

	// ─── Multiple prayers in one tick ───────────────────────────────────

	@Test
	public void capturesAllActivePrayersInTheSet()
	{
		Set<Prayer> two = EnumSet.of(Prayer.PROTECT_FROM_MELEE, Prayer.PROTECT_FROM_MAGIC);
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, two, false, 2);

		assertEquals(two, recorder.getPrayersAtTick(0));
	}

	@Test
	public void capturedSetIsDefensiveCopy()
	{
		EnumSet<Prayer> mutable = EnumSet.of(Prayer.PROTECT_FROM_MELEE);
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, mutable, false, 2);
		// Caller mutates after capture — our stored snapshot must not change.
		mutable.add(Prayer.PROTECT_FROM_MAGIC);

		assertEquals(EnumSet.of(Prayer.PROTECT_FROM_MELEE), recorder.getPrayersAtTick(0));
	}

	@Test
	public void getReturnedSetIsUnmodifiable()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, MELEE, false, 2);
		Set<Prayer> view = recorder.getPrayersAtTick(0);
		try
		{
			view.add(Prayer.PROTECT_FROM_MAGIC);
			assertTrue("expected UnsupportedOperationException", false);
		}
		catch (UnsupportedOperationException expected)
		{
			// good
		}
	}

	// ─── clear() ────────────────────────────────────────────────────────

	@Test
	public void clearWipesBufferButPreservesMode()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, MELEE, false, 2);
		recorder.clear();

		assertEquals(RecordMode.ALWAYS, recorder.getMode());
		assertTrue(recorder.getPrayersAtTick(0).isEmpty());
	}

	@Test
	public void clearResetsArmCountdown()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, MELEE, true, 5); // window: 5 ticks
		recorder.clear();
		// After clear, ARM should be quiet again until next movement.
		recorder.onTick(1, MAGIC, false, 5);
		assertTrue(recorder.getPrayersAtTick(1).isEmpty());
	}

	// ─── Mode transitions ───────────────────────────────────────────────

	@Test
	public void switchingArmToAlwaysPreservesExistingCaptures()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, MELEE, true, 1);
		assertEquals(MELEE, recorder.getPrayersAtTick(0));

		recorder.setMode(RecordMode.ALWAYS);
		// Old capture survives the transition.
		assertEquals(MELEE, recorder.getPrayersAtTick(0));
	}

	@Test
	public void switchingAlwaysToArmStopsCapturingUntilMovement()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, MELEE, false, 2);

		recorder.setMode(RecordMode.ARM);
		recorder.onTick(1, MAGIC, false, 2);
		// Old MELEE preserved; new tick not captured (no movement).
		assertEquals(MELEE, recorder.getPrayersAtTick(0));
		assertTrue(recorder.getPrayersAtTick(1).isEmpty());
	}

	// ─── Defensive ──────────────────────────────────────────────────────

	@Test
	public void offModeWithMovementIsStillNoOp()
	{
		recorder.onTick(0, MELEE, true, 5);
		assertTrue(recorder.getPrayersAtTick(0).isEmpty());
		assertFalse(recorder.getMode() != RecordMode.OFF);
	}
}
