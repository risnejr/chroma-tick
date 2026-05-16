package com.chromatick;

/**
 * State of the per-tick prayer recorder.
 *
 * <p>OFF — no recording, no display.
 * <br>ARM — recorder waits for the player to move, then captures the
 * movement tick plus a configurable trailing window.
 * <br>ALWAYS — every tick is captured (subsequent cycles overwrite older
 * data at the same tick index).
 */
public enum RecordMode
{
	OFF,
	ARM,
	ALWAYS;

	/** Advance to the next mode in OFF → ARM → ALWAYS → OFF order. */
	public RecordMode next()
	{
		RecordMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
