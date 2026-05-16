package com.chromatick;

/**
 * Centralized enum types for the ChromaTick package. Kept in one file because
 * each enum is short and they're easier to discover/edit when grouped — the
 * old layout had six separate single-purpose files. None of these escape the
 * package, so they're package-private (a Java file may declare multiple
 * non-public top-level types).
 */

/** Where the tick visualization renders. */
enum DisplayMode
{
	TILE,
	HUD,
	BOTH
}

/** Where the HUD anchors to the player model. */
enum HudAnchorTarget
{
	HEAD,
	FEET,
	NONE
}

/** Glyph style inside the HUD frame. */
enum HudGlyph
{
	DOTS,
	NUMBERS
}

/** Where the recorded icons render relative to the HUD glyph row. */
enum IconPosition
{
	ABOVE,
	BELOW
}

/** Palette-picker variant in the sidebar panel. */
enum PaletteMode
{
	GRID,
	WHEEL
}

/**
 * State of the per-tick recorder.
 *
 * <p>OFF — no recording, no display.
 * <br>ARM — recorder waits for the player to move, then captures the
 * movement tick plus a configurable trailing window.
 * <br>ALWAYS — every tick is captured (subsequent cycles overwrite older
 * data at the same tick index).
 */
enum RecordMode
{
	OFF,
	ARM,
	ALWAYS;

	/** Advance to the next mode in OFF → ARM → ALWAYS → OFF order. */
	RecordMode next()
	{
		RecordMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}

/**
 * What kind of player action a {@link TickActionEvent} represents. The
 * recorder filters captured events by the user's selected categories;
 * the icon resolver maps each category to its sprite(s).
 *
 * <p>Declaration order is also default render priority when multiple
 * categories fire on the same tick — earlier categories win when slot
 * space is limited.
 */
enum TickActionCategory
{
	/** Active Protect-from-X prayer this tick. */
	PROTECTION_PRAYER,
	/** Attack-type menu click (red on RuneScape's UI). */
	RED_CLICK,
	/** Non-attack, non-walk menu click (yellow on RuneScape's UI). */
	YELLOW_CLICK,
	/** Use-item-on-X click. Carries source + target item IDs. */
	ITEM_USE,
	/** Player tile changed between ticks. */
	MOVEMENT
}
