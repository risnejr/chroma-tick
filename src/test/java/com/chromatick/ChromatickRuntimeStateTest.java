package com.chromatick;

import java.awt.Color;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure unit tests for the runtime-state container. No RuneLite injection —
 * the class is intentionally a plain POJO so tests can pin down the
 * cycle-override and tick-index transitions without standing up a client.
 */
public class ChromatickRuntimeStateTest
{
	private ChromatickRuntimeState state;

	@Before
	public void setUp()
	{
		state = new ChromatickRuntimeState();
	}

	// ─── Initial state ──────────────────────────────────────────────────

	@Test
	public void startsAtTickZero()
	{
		assertEquals(0, state.getTickIndex());
	}

	@Test
	public void startsWithWhiteColor()
	{
		assertEquals(Color.WHITE, state.getCurrentColor());
	}

	@Test
	public void startsWithNoCycleOverride()
	{
		assertFalse(state.hasCycleLengthOverride());
		assertEquals(-1, state.getCycleLengthOverride());
	}

	@Test
	public void startsWithNoLastWorldPoint()
	{
		assertNull(state.getLastWorldPoint());
	}

	// ─── Cycle-length override ──────────────────────────────────────────

	@Test
	public void effectiveCycleFallsBackToConfigWhenNoOverride()
	{
		assertEquals(4, state.effectiveCycleLength(4));
		assertEquals(10, state.effectiveCycleLength(10));
	}

	@Test
	public void effectiveCycleReturnsOverrideWhenSet()
	{
		state.setCycleLengthOverride(6);
		assertTrue(state.hasCycleLengthOverride());
		assertEquals(6, state.effectiveCycleLength(4));
		// Override wins regardless of supplied config value.
		assertEquals(6, state.effectiveCycleLength(99));
	}

	@Test
	public void clearOverrideRestoresConfigFallback()
	{
		state.setCycleLengthOverride(8);
		state.clearCycleLengthOverride();
		assertFalse(state.hasCycleLengthOverride());
		assertEquals(4, state.effectiveCycleLength(4));
	}

	@Test
	public void negativeOverrideTreatedAsAbsent()
	{
		// -1 sentinel is the documented "no override" value; any non-positive
		// value should fall through to the config fallback.
		state.setCycleLengthOverride(-1);
		assertFalse(state.hasCycleLengthOverride());
		assertEquals(4, state.effectiveCycleLength(4));

		state.setCycleLengthOverride(0);
		assertFalse(state.hasCycleLengthOverride());
		assertEquals(4, state.effectiveCycleLength(4));
	}

	// ─── Tick index ─────────────────────────────────────────────────────

	@Test
	public void tickIndexCanBeSetAndRead()
	{
		state.setTickIndex(3);
		assertEquals(3, state.getTickIndex());
	}

	// ─── Color ──────────────────────────────────────────────────────────

	@Test
	public void currentColorCanBeSetAndRead()
	{
		state.setCurrentColor(Color.RED);
		assertEquals(Color.RED, state.getCurrentColor());
	}

	// ─── Last world point ───────────────────────────────────────────────

	@Test
	public void lastWorldPointCanBeSetAndRead()
	{
		WorldPoint p = new WorldPoint(3200, 3200, 0);
		state.setLastWorldPoint(p);
		assertEquals(p, state.getLastWorldPoint());
	}

	// ─── reset() ────────────────────────────────────────────────────────

	@Test
	public void resetClearsAllTransientState()
	{
		state.setTickIndex(5);
		state.setCurrentColor(Color.RED);
		state.setCycleLengthOverride(8);
		state.setLastWorldPoint(new WorldPoint(3200, 3200, 0));

		state.reset();

		assertEquals(0, state.getTickIndex());
		assertEquals(Color.WHITE, state.getCurrentColor());
		assertFalse(state.hasCycleLengthOverride());
		assertNull(state.getLastWorldPoint());
	}
}
