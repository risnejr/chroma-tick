package com.chromatick;

import com.chromatick.Enums.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Prayer;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the per-tick recorder state machine. All inputs are plain ints,
 * Prayer constants and TickActionEvent lists — no RuneLite injection
 * required.
 *
 * <p>Constants like {@link #MELEE} remain {@code Set<Prayer>} for readable
 * assertions; the {@link #events(Set)} helper wraps them into the
 * {@code List<TickActionEvent>} the recorder accepts, and
 * {@link #prayersAt(int)} unwraps the recorded payload back into a Prayer
 * set for the same assertions.
 */
public class TickRecorderServiceTest
{
	private static final Set<Prayer> MELEE   = Collections.singleton(Prayer.PROTECT_FROM_MELEE);
	private static final Set<Prayer> MAGIC   = Collections.singleton(Prayer.PROTECT_FROM_MAGIC);
	private static final Set<Prayer> MISSILE = Collections.singleton(Prayer.PROTECT_FROM_MISSILES);
	private static final Set<Prayer> NONE    = Collections.emptySet();

	private TickRecorderService recorder;

	@Before
	public void setUp()
	{
		recorder = new TickRecorderService();
	}

	/** Build the event list the recorder accepts from a prayer set. */
	private static List<TickActionEvent> events(Set<Prayer> prayers)
	{
		if (prayers.isEmpty())
		{
			return Collections.emptyList();
		}
		List<TickActionEvent> out = new ArrayList<>(prayers.size());
		for (Prayer p : prayers)
		{
			out.add(TickActionEvent.of(TickActionCategory.PROTECTION_PRAYER, p.ordinal()));
		}
		return out;
	}

	/** Extract the prayer set back out of the recorded tick. */
	private Set<Prayer> prayersAt(int tickIndex)
	{
		Set<Prayer> out = EnumSet.noneOf(Prayer.class);
		for (TickActionEvent event : recorder.getRecordedAt(tickIndex).actions())
		{
			if (event.category() == TickActionCategory.PROTECTION_PRAYER)
			{
				out.add(Prayer.values()[event.primaryId()]);
			}
		}
		return out;
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
		assertTrue(prayersAt(0).isEmpty());
		assertTrue(prayersAt(5).isEmpty());
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
		recorder.onTick(0, events(MELEE), true,  2);
		recorder.onTick(1, events(MAGIC), false, 2);
		assertTrue(prayersAt(0).isEmpty());
		assertTrue(prayersAt(1).isEmpty());
	}

	@Test
	public void transitioningToOffPreservesBuffer()
	{
		// Manual stop should leave the recording visible — only re-arming
		// (or explicit clear()) wipes the buffer.
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(2, events(MELEE), false, 2);
		assertEquals(MELEE, prayersAt(2));

		recorder.setMode(RecordMode.OFF);
		assertEquals(MELEE, prayersAt(2));
	}

	// ─── ALWAYS mode ────────────────────────────────────────────────────

	@Test
	public void alwaysModeCapturesEveryTickRegardlessOfMovement()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, events(MELEE),   false, 2);
		recorder.onTick(1, events(MAGIC),   true,  2);
		recorder.onTick(2, events(MISSILE), false, 2);

		assertEquals(MELEE,   prayersAt(0));
		assertEquals(MAGIC,   prayersAt(1));
		assertEquals(MISSILE, prayersAt(2));
	}

	@Test
	public void alwaysModeOverdubsOnNextCycle()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, events(MELEE), false, 2);
		assertEquals(MELEE, prayersAt(0));

		// Cycle wraps; tick 0 hits again with a different prayer.
		recorder.onTick(0, events(MAGIC), false, 2);
		assertEquals(MAGIC, prayersAt(0));
	}

	@Test
	public void alwaysModeCapturesEmptyListWhenNothingActive()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, events(NONE), false, 2);
		// Still recorded (so renderer can tell "we were here, nothing active"
		// apart from "we never recorded this tick").
		assertTrue(prayersAt(0).isEmpty());
		assertTrue(recorder.hasCaptures());
	}

	// ─── ARM mode ───────────────────────────────────────────────────────

	@Test
	public void armDoesNothingUntilMovement()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, events(MELEE), false, 2);
		recorder.onTick(1, events(MAGIC), false, 2);
		assertTrue(prayersAt(0).isEmpty());
		assertTrue(prayersAt(1).isEmpty());
	}

	@Test
	public void armCapturesMovementTickAndTrailingWindow()
	{
		recorder.setMode(RecordMode.ARM);
		// armTicks = 2 → capture movement tick + 1 more.
		recorder.onTick(0, events(NONE),    false, 2);
		recorder.onTick(1, events(MELEE),   true,  2);
		recorder.onTick(2, events(MAGIC),   false, 2);
		recorder.onTick(3, events(MISSILE), false, 2);

		assertTrue(prayersAt(0).isEmpty());
		assertEquals(MELEE, prayersAt(1));
		assertEquals(MAGIC, prayersAt(2));
		assertTrue(prayersAt(3).isEmpty());
	}

	@Test
	public void armTicksOneCapturesOnlyTheMovementTick()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, events(MELEE), true,  1);
		recorder.onTick(1, events(MAGIC), false, 1);

		assertEquals(MELEE, prayersAt(0));
		assertTrue(prayersAt(1).isEmpty());
	}

	@Test
	public void movementWhileAlreadyArmedRefreshesWindow()
	{
		recorder.setMode(RecordMode.ARM);
		// Move on tick 0; armTicks=2 → expect captures on 0,1.
		recorder.onTick(0, events(MELEE), true,  2);
		// Move again on tick 1 before window expires → window resets to 2.
		recorder.onTick(1, events(MAGIC), true,  2);
		recorder.onTick(2, events(MISSILE), false, 2);
		recorder.onTick(3, events(NONE),    false, 2);

		assertEquals(MELEE,   prayersAt(0));
		assertEquals(MAGIC,   prayersAt(1));
		assertEquals(MISSILE, prayersAt(2));
		assertTrue(prayersAt(3).isEmpty());
	}

	@Test
	public void armTicksAtLeastOneEvenWhenConfigIsZeroOrNegative()
	{
		recorder.setMode(RecordMode.ARM);
		// Defensive: bad config shouldn't silently disable the recorder.
		recorder.onTick(0, events(MELEE), true, 0);
		recorder.onTick(1, events(MAGIC), false, 0);

		assertEquals(MELEE, prayersAt(0));
		assertTrue(prayersAt(1).isEmpty());
	}

	// ─── Multiple prayers in one tick ───────────────────────────────────

	@Test
	public void capturesAllActivePrayersInTheSet()
	{
		Set<Prayer> two = EnumSet.of(Prayer.PROTECT_FROM_MELEE, Prayer.PROTECT_FROM_MAGIC);
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, events(two), false, 2);

		assertEquals(two, prayersAt(0));
	}

	@Test
	public void capturedBufferIsDefensiveCopy()
	{
		// Caller-provided list mutated after the call — the recorder's
		// snapshot must not change.
		List<TickActionEvent> mutable = new ArrayList<>(events(MELEE));
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, mutable, false, 2);
		mutable.add(TickActionEvent.of(TickActionCategory.PROTECTION_PRAYER,
			Prayer.PROTECT_FROM_MAGIC.ordinal()));

		assertEquals(MELEE, prayersAt(0));
	}

	@Test
	public void returnedActionListIsUnmodifiable()
	{
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, events(MELEE), false, 2);
		List<TickActionEvent> view = recorder.getRecordedAt(0).actions();
		try
		{
			view.add(TickActionEvent.of(TickActionCategory.PROTECTION_PRAYER,
				Prayer.PROTECT_FROM_MAGIC.ordinal()));
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
		recorder.onTick(0, events(MELEE), false, 2);
		recorder.clear();

		assertEquals(RecordMode.ALWAYS, recorder.getMode());
		assertTrue(prayersAt(0).isEmpty());
	}

	@Test
	public void clearResetsArmCountdown()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, events(MELEE), true, 5); // window: 5 ticks
		recorder.clear();
		// After clear, ARM should be quiet again until next movement.
		recorder.onTick(1, events(MAGIC), false, 5);
		assertTrue(prayersAt(1).isEmpty());
	}

	// ─── Mode transitions ───────────────────────────────────────────────

	@Test
	public void switchingArmToAlwaysPreservesExistingCaptures()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, events(MELEE), true, 1);
		assertEquals(MELEE, prayersAt(0));

		recorder.setMode(RecordMode.ALWAYS);
		// Old capture survives the transition.
		assertEquals(MELEE, prayersAt(0));
	}

	@Test
	public void switchingAlwaysToArmClearsExistingCaptures()
	{
		// Re-arming = fresh capture session, so any prior buffer is wiped.
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, events(MELEE), false, 2);
		assertEquals(MELEE, prayersAt(0));

		recorder.setMode(RecordMode.ARM);
		assertTrue(prayersAt(0).isEmpty());

		// And: nothing captured until movement.
		recorder.onTick(1, events(MAGIC), false, 2);
		assertTrue(prayersAt(1).isEmpty());
	}

	@Test
	public void switchingOffToArmClearsAnyResidualCaptures()
	{
		// Auto-exit leaves recordings around; the next ARM trigger should
		// start clean.
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, events(MELEE), true, 1); // captures and auto-exits to OFF

		assertEquals(RecordMode.OFF, recorder.getMode());
		assertEquals(MELEE, prayersAt(0));

		recorder.setMode(RecordMode.ARM);
		assertTrue(prayersAt(0).isEmpty());
	}

	// ─── ARM auto-exit ──────────────────────────────────────────────────

	@Test
	public void armAutoExitsToOffWhenWindowExpires()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, events(MELEE), true, 2); // window starts, captures
		assertEquals(RecordMode.ARM, recorder.getMode());
		recorder.onTick(1, events(MAGIC), false, 2); // window decrements to 0
		assertEquals(RecordMode.OFF, recorder.getMode());
	}

	@Test
	public void armAutoExitPreservesRecordings()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, events(MELEE), true, 1); // captures and auto-exits

		assertEquals(RecordMode.OFF, recorder.getMode());
		// Captured prayer remains visible after auto-exit.
		assertEquals(MELEE, prayersAt(0));
	}

	@Test
	public void armAutoExitStopsFurtherCapturesEvenOnMovement()
	{
		recorder.setMode(RecordMode.ARM);
		recorder.onTick(0, events(MELEE), true, 1); // captures and auto-exits to OFF
		// Movement after auto-exit must not re-arm (mode is OFF now).
		recorder.onTick(1, events(MAGIC), true, 1);
		assertTrue(prayersAt(1).isEmpty());
	}

	// ─── hasCaptures ────────────────────────────────────────────────────

	@Test
	public void hasCapturesReflectsBufferState()
	{
		assertFalse(recorder.hasCaptures());
		recorder.setMode(RecordMode.ALWAYS);
		recorder.onTick(0, events(MELEE), false, 2);
		assertTrue(recorder.hasCaptures());
		recorder.clear();
		assertFalse(recorder.hasCaptures());
	}

	// ─── Defensive ──────────────────────────────────────────────────────

	@Test
	public void offModeWithMovementIsStillNoOp()
	{
		recorder.onTick(0, events(MELEE), true, 5);
		assertTrue(prayersAt(0).isEmpty());
		assertFalse(recorder.getMode() != RecordMode.OFF);
	}
}
