package com.chromatick;

import com.chromatick.Enums.*;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TickActionCapture} — specifically the within-tick
 * priority chain: ITEM_USE outranks YELLOW/RED clicks, latest wins among
 * peers, UI clicks and CANCEL never overwrite a real captured click.
 */
public class TickActionCaptureTest
{
	private Client client;
	private TickActionCapture capture;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		// Default: no widget selected. Item-use tests override this.
		when(client.isWidgetSelected()).thenReturn(false);
		capture = new TickActionCapture(client);
	}

	// ─── Helpers ────────────────────────────────────────────────────────

	private static MenuOptionClicked clickEvent(MenuAction action)
	{
		MenuOptionClicked e = mock(MenuOptionClicked.class);
		when(e.getMenuAction()).thenReturn(action);
		return e;
	}

	private void setSelectedItem(int itemId)
	{
		Widget selected = mock(Widget.class);
		when(selected.getItemId()).thenReturn(itemId);
		when(client.isWidgetSelected()).thenReturn(true);
		when(client.getSelectedWidget()).thenReturn(selected);
	}

	private MenuOptionClicked itemUseClick(int targetItemId)
	{
		MenuOptionClicked e = mock(MenuOptionClicked.class);
		when(e.getMenuAction()).thenReturn(MenuAction.WIDGET_TARGET_ON_WIDGET);
		when(e.getItemId()).thenReturn(targetItemId);
		return e;
	}

	// ─── Single-click classification ────────────────────────────────────

	@Test
	public void walkActionClassifiesAsYellow()
	{
		capture.onMenuOptionClicked(clickEvent(MenuAction.WALK));
		assertEquals(TickActionCategory.YELLOW_CLICK, capture.drainClick().category());
	}

	@Test
	public void npcOptionClassifiesAsRed()
	{
		// Talk-to / Attack / etc. — anything actionable in the world.
		capture.onMenuOptionClicked(clickEvent(MenuAction.NPC_FIRST_OPTION));
		assertEquals(TickActionCategory.RED_CLICK, capture.drainClick().category());
	}

	@Test
	public void uiClickIsFilteredOut()
	{
		// Clicking an inventory item or prayer book entry — no cross
		// renders in-game, so nothing is recorded.
		capture.onMenuOptionClicked(clickEvent(MenuAction.CC_OP));
		assertNull(capture.drainClick());
	}

	@Test
	public void cancelIsFilteredOut()
	{
		capture.onMenuOptionClicked(clickEvent(MenuAction.CANCEL));
		assertNull(capture.drainClick());
	}

	// ─── Priority: ITEM_USE outranks YELLOW/RED ─────────────────────────

	@Test
	public void itemUsePreservedWhenYellowClickFollows()
	{
		// Player uses knife on log, then walks somewhere else in the same
		// tick interval. The item-use is the meaningful action; don't bury
		// it under the walk.
		setSelectedItem(946);
		capture.onMenuOptionClicked(itemUseClick(1511));
		capture.onMenuOptionClicked(clickEvent(MenuAction.WALK));

		TickActionEvent drained = capture.drainClick();
		assertNotNull(drained);
		assertEquals(TickActionCategory.ITEM_USE, drained.category());
	}

	@Test
	public void itemUsePreservedWhenRedClickFollows()
	{
		setSelectedItem(946);
		capture.onMenuOptionClicked(itemUseClick(1511));
		capture.onMenuOptionClicked(clickEvent(MenuAction.NPC_FIRST_OPTION));

		assertEquals(TickActionCategory.ITEM_USE, capture.drainClick().category());
	}

	@Test
	public void itemUseReplacedByLaterItemUse()
	{
		// Two item-uses in one tick — latest wins among peers.
		setSelectedItem(946);
		capture.onMenuOptionClicked(itemUseClick(1511));
		setSelectedItem(947);
		capture.onMenuOptionClicked(itemUseClick(1513));

		TickActionEvent drained = capture.drainClick();
		assertEquals(TickActionCategory.ITEM_USE, drained.category());
		assertEquals(947, drained.primaryId());
		assertEquals(1513, drained.secondaryId());
	}

	@Test
	public void itemUseReplacesEarlierYellowClick()
	{
		// Yellow first, then item-use — item-use takes the slot.
		capture.onMenuOptionClicked(clickEvent(MenuAction.WALK));
		setSelectedItem(946);
		capture.onMenuOptionClicked(itemUseClick(1511));

		assertEquals(TickActionCategory.ITEM_USE, capture.drainClick().category());
	}

	// ─── Peer priority: YELLOW vs RED — latest wins ─────────────────────

	@Test
	public void yellowOverwritesEarlierRed()
	{
		capture.onMenuOptionClicked(clickEvent(MenuAction.NPC_FIRST_OPTION));
		capture.onMenuOptionClicked(clickEvent(MenuAction.WALK));
		assertEquals(TickActionCategory.YELLOW_CLICK, capture.drainClick().category());
	}

	@Test
	public void redOverwritesEarlierYellow()
	{
		capture.onMenuOptionClicked(clickEvent(MenuAction.WALK));
		capture.onMenuOptionClicked(clickEvent(MenuAction.NPC_FIRST_OPTION));
		assertEquals(TickActionCategory.RED_CLICK, capture.drainClick().category());
	}

	// ─── UI clicks never overwrite real ones ────────────────────────────

	@Test
	public void uiClickDoesNotOverwritePendingRed()
	{
		capture.onMenuOptionClicked(clickEvent(MenuAction.NPC_FIRST_OPTION));
		capture.onMenuOptionClicked(clickEvent(MenuAction.CC_OP));
		assertEquals(TickActionCategory.RED_CLICK, capture.drainClick().category());
	}

	@Test
	public void uiClickDoesNotOverwritePendingItemUse()
	{
		setSelectedItem(946);
		capture.onMenuOptionClicked(itemUseClick(1511));
		capture.onMenuOptionClicked(clickEvent(MenuAction.CC_OP));
		assertEquals(TickActionCategory.ITEM_USE, capture.drainClick().category());
	}

	// ─── drain/reset semantics ──────────────────────────────────────────

	@Test
	public void drainClearsTheBuffer()
	{
		capture.onMenuOptionClicked(clickEvent(MenuAction.NPC_FIRST_OPTION));
		assertNotNull(capture.drainClick());
		// Second drain in the same tick interval returns nothing.
		assertNull(capture.drainClick());
	}

	@Test
	public void resetForgetsAnyBufferedClick()
	{
		capture.onMenuOptionClicked(clickEvent(MenuAction.NPC_FIRST_OPTION));
		capture.reset();
		assertNull(capture.drainClick());
	}
}
