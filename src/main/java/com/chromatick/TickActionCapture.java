package com.chromatick;

import com.chromatick.Enums.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Bridges RuneLite event signals into the per-tick recorder. The plugin
 * registers an instance with the event bus in {@code startUp} and drains
 * the buffered events in {@code onGameTick}; the recorder filters by the
 * user's enabled categories from there.
 *
 * <p>Captures one click per tick interval (item-use, yellow or red);
 * latest wins. Item-use detection looks at {@link MenuAction}'s family
 * of {@code WIDGET_TARGET_ON_*} values and pulls the source item ID
 * from {@link Client#getSelectedWidget()} — that's the item the player
 * had selected before clicking the target.
 *
 * <p>Threading: RuneLite delivers events on the client thread; the
 * plugin reads on the same thread in {@code onGameTick}. Single-threaded
 * by construction, so the field is plain (no {@code volatile} needed).
 */
@Singleton
class TickActionCapture
{
	private final Client client;

	private TickActionEvent pendingClick;

	@Inject
	TickActionCapture(Client client)
	{
		this.client = client;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		pendingClick = classify(event);
	}

	/**
	 * Return the click captured this tick interval (or {@code null}) and
	 * clear the slot for the next tick. Called once per game tick from the
	 * plugin's onGameTick handler.
	 */
	TickActionEvent drainClick()
	{
		TickActionEvent out = pendingClick;
		pendingClick = null;
		return out;
	}

	/** Forget any buffered click. Called on shutdown to leave a clean slate. */
	void reset()
	{
		pendingClick = null;
	}

	/**
	 * Classify a menu click into one of ITEM_USE / RED_CLICK / YELLOW_CLICK.
	 * Mutually exclusive — a click can only be one of the three at a time.
	 *
	 * <ul>
	 *   <li>ITEM_USE — any {@link MenuAction#WIDGET_TARGET_ON_GAME_OBJECT}
	 *       / NPC / PLAYER / GROUND_ITEM / WIDGET. Source item ID comes
	 *       from {@link Client#getSelectedWidget()} (the item the player
	 *       picked up the cursor with).
	 *   <li>RED_CLICK — menu option was "Attack" (case-insensitive). User
	 *       mental model is "I saw a red cursor"; that's the simplest
	 *       proxy.
	 *   <li>YELLOW_CLICK — everything else (walk-here, talk, examine,
	 *       generic interact, etc.).
	 * </ul>
	 */
	private TickActionEvent classify(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (isItemUseAction(action))
		{
			int sourceItemId = selectedItemId();
			int targetItemId = targetItemId(event, action);
			return TickActionEvent.of(TickActionCategory.ITEM_USE, sourceItemId, targetItemId);
		}
		String option = event.getMenuOption();
		if (option != null && option.equalsIgnoreCase("Attack"))
		{
			return TickActionEvent.of(TickActionCategory.RED_CLICK, TickActionEvent.NONE);
		}
		return TickActionEvent.of(TickActionCategory.YELLOW_CLICK, TickActionEvent.NONE);
	}

	private static boolean isItemUseAction(MenuAction action)
	{
		if (action == null)
		{
			return false;
		}
		switch (action)
		{
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_NPC:
			case WIDGET_TARGET_ON_PLAYER:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case WIDGET_TARGET_ON_WIDGET:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Item ID of the currently-selected widget (the item the player had
	 * "in hand" before clicking the target). Returns {@link TickActionEvent#NONE}
	 * if no widget is selected or it isn't an inventory item.
	 */
	private int selectedItemId()
	{
		if (!client.isWidgetSelected())
		{
			return TickActionEvent.NONE;
		}
		Widget selected = client.getSelectedWidget();
		if (selected == null)
		{
			return TickActionEvent.NONE;
		}
		int itemId = selected.getItemId();
		return itemId > 0 ? itemId : TickActionEvent.NONE;
	}

	/**
	 * Target item ID for an item-use event, or {@link TickActionEvent#NONE}.
	 * Only WIDGET_TARGET_ON_WIDGET clicks have a target inventory item —
	 * use-on-NPC / object / ground-item / player don't, so we return NONE
	 * and the resolver renders the source alone.
	 */
	private static int targetItemId(MenuOptionClicked event, MenuAction action)
	{
		if (action != MenuAction.WIDGET_TARGET_ON_WIDGET)
		{
			return TickActionEvent.NONE;
		}
		int id = event.getItemId();
		return id > 0 ? id : TickActionEvent.NONE;
	}
}
