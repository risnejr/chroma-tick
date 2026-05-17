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
		TickActionEvent classified = classify(event);
		// Null = filtered (e.g. CANCEL dismissals). Don't overwrite an
		// earlier real click in the same tick interval with a no-op.
		if (classified != null)
		{
			pendingClick = classified;
		}
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
	 * Classify a menu click into one of ITEM_USE / YELLOW_CLICK / RED_CLICK,
	 * or return {@code null} to skip it entirely.
	 *
	 * <p>Matches the OSRS click-cross convention the player actually sees —
	 * the cross only appears on world clicks, never on inventory tab /
	 * spellbook / prayer book / chatbox / RuneLite menu interactions.
	 *
	 * <ul>
	 *   <li><b>null (skipped)</b> — UI/menu clicks (CC_OP, WIDGET_*, RuneLite
	 *       custom menus, CANCEL dismissals). No cross renders in-game, so
	 *       we don't record one either.
	 *   <li>ITEM_USE — any {@code WIDGET_TARGET_ON_*}. Source item ID comes
	 *       from {@link Client#getSelectedWidget()} (the item the player
	 *       picked up the cursor with).
	 *   <li>YELLOW_CLICK — {@link MenuAction#WALK} only. The yellow cross
	 *       in-game appears solely on walk-here clicks (empty terrain or
	 *       a tile with no interactable).
	 *   <li>RED_CLICK — everything else <em>in the world</em>. Talk-to,
	 *       Attack, mine, chop, use-object, examine, cast on NPC, etc.
	 * </ul>
	 */
	private TickActionEvent classify(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (isUiClick(action))
		{
			return null;
		}
		if (isItemUseAction(action))
		{
			int sourceItemId = selectedItemId();
			int targetItemId = targetItemId(event, action);
			return TickActionEvent.of(TickActionCategory.ITEM_USE, sourceItemId, targetItemId);
		}
		if (action == MenuAction.WALK)
		{
			return TickActionEvent.of(TickActionCategory.YELLOW_CLICK, TickActionEvent.NONE);
		}
		return TickActionEvent.of(TickActionCategory.RED_CLICK, TickActionEvent.NONE);
	}

	/**
	 * True for menu actions that fire on UI/widget interactions — inventory
	 * tabs, spellbook entries, prayer book toggles, chat options, RuneLite
	 * custom menu items, and the dismissal of any context menu. The OSRS
	 * click cross doesn't render for these, so we don't record them as
	 * red/yellow clicks either.
	 *
	 * <p>Note: {@link MenuAction#WIDGET_TARGET} (the "I selected this item
	 * to use on something" step) is also UI-only — the actual use-on click
	 * comes through as a {@code WIDGET_TARGET_ON_*} action and lands in
	 * ITEM_USE classification later.
	 */
	private static boolean isUiClick(MenuAction action)
	{
		if (action == null)
		{
			return true;
		}
		switch (action)
		{
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
			case WIDGET_TYPE_1:
			case WIDGET_TYPE_4:
			case WIDGET_TYPE_5:
			case WIDGET_TARGET:
			case WIDGET_CLOSE:
			case WIDGET_CONTINUE:
			case WIDGET_FIRST_OPTION:
			case WIDGET_SECOND_OPTION:
			case WIDGET_THIRD_OPTION:
			case WIDGET_FOURTH_OPTION:
			case WIDGET_FIFTH_OPTION:
			case RUNELITE:
			case RUNELITE_WIDGET:
			case RUNELITE_HIGH_PRIORITY:
			case RUNELITE_LOW_PRIORITY:
			case RUNELITE_OVERLAY:
			case RUNELITE_OVERLAY_CONFIG:
			case RUNELITE_PLAYER:
			case RUNELITE_INFOBOX:
			case CANCEL:
				return true;
			default:
				return false;
		}
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
