package com.chromatick;

import javax.inject.Singleton;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;

/**
 * Bridges RuneLite event signals into the per-tick recorder. The plugin
 * registers an instance with the event bus in {@code startUp} and drains
 * the buffered events in {@code onGameTick}; the recorder filters by the
 * user's enabled categories from there.
 *
 * <p>Currently captures one click per tick interval (yellow vs red),
 * latest wins. Item-use classification + per-tick prayer/movement
 * already happen on the plugin side, so this class deliberately only
 * holds the click slot — the next commit widens it to detect ITEM_USE.
 *
 * <p>Threading: RuneLite delivers events on the client thread; the
 * plugin reads on the same thread in {@code onGameTick}. Single-threaded
 * by construction, so the field is plain (no {@code volatile} needed).
 */
@Singleton
class TickActionCapture
{
	private TickActionEvent pendingClick;

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
	 * Classify a single menu click as red vs yellow. The user's mental
	 * model is "what cursor color did I see" — the simplest heuristic that
	 * matches: the menu option text was "Attack". Everything else is
	 * yellow, including Walk-here / Talk / Examine / generic interactions.
	 *
	 * <p>{@code primaryId} stays NONE for both — the renderer paints a
	 * colored dot, not a sprite, so no item/sprite ID is needed.
	 */
	private static TickActionEvent classify(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		if (option != null && option.equalsIgnoreCase("Attack"))
		{
			return TickActionEvent.of(TickActionCategory.RED_CLICK, TickActionEvent.NONE);
		}
		return TickActionEvent.of(TickActionCategory.YELLOW_CLICK, TickActionEvent.NONE);
	}
}
