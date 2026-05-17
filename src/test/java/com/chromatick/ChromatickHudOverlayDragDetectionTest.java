package com.chromatick;

import java.awt.Point;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Edge-case coverage for {@link ChromatickHudOverlay#wasDragged} — the pure
 * predicate that decides whether the framework's preferred-location update
 * was a user alt-drag (versus a window-resize nudge, a first-frame seed, or
 * a frame where the overlay hasn't been positioned yet).
 */
public class ChromatickHudOverlayDragDetectionTest
{
	@Test
	public void returnsFalseWhenCanvasJustResized()
	{
		// Window resize moves the overlay back inside bounds; that nudge
		// must not be misread as an alt-drag.
		Point lastSet = new Point(100, 100);
		Point current = new Point(95,  100);

		assertFalse(ChromatickHudOverlay.wasDragged(true, lastSet, current));
	}

	@Test
	public void returnsFalseWhenLastSetLocationIsNull()
	{
		// Haven't anchored yet (first frame after re-add) — nothing to compare.
		assertFalse(ChromatickHudOverlay.wasDragged(false, null, new Point(50, 50)));
	}

	@Test
	public void returnsFalseWhenPreferredLocationIsNull()
	{
		// Framework hasn't reported a preferred location yet.
		assertFalse(ChromatickHudOverlay.wasDragged(false, new Point(50, 50), null));
	}

	@Test
	public void returnsFalseWhenLocationsMatch()
	{
		Point p = new Point(100, 100);

		assertFalse(ChromatickHudOverlay.wasDragged(false, p, new Point(100, 100)));
		// Same Point reference is equally fine.
		assertFalse(ChromatickHudOverlay.wasDragged(false, p, p));
	}

	@Test
	public void returnsTrueWhenPreferredLocationHasMovedHorizontally()
	{
		assertTrue(ChromatickHudOverlay.wasDragged(false,
			new Point(100, 100), new Point(150, 100)));
	}

	@Test
	public void returnsTrueWhenPreferredLocationHasMovedVertically()
	{
		assertTrue(ChromatickHudOverlay.wasDragged(false,
			new Point(100, 100), new Point(100, 80)));
	}

	@Test
	public void returnsTrueOnEvenASinglePixelShift()
	{
		// Conservative: any movement is a drag. Avoids missing tiny but
		// intentional repositions.
		assertTrue(ChromatickHudOverlay.wasDragged(false,
			new Point(100, 100), new Point(101, 100)));
	}

	@Test
	public void resizeFlagOverridesAllOtherSignals()
	{
		// Even with a clear position delta, the resize flag suppresses
		// drag detection for that frame.
		assertFalse(ChromatickHudOverlay.wasDragged(true,
			new Point(0, 0), new Point(500, 500)));
	}
}
