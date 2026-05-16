package com.chromatick;

/**
 * Centralized enum types for the ChromaTick package. Nested inside a
 * public holder so RuneLite's {@code ConfigManager} dynamic proxy can
 * see them (a proxy implementing {@link ChromatickConfig} is generated
 * in a context that can't access package-private types in this package
 * — it throws {@code IllegalAccessError} on any config getter that
 * returns one). With the holder class public and the nested enums
 * public, the proxy resolves the return types via standard reflection.
 *
 * <p>Call sites can import the nested types via
 * {@code import com.chromatick.Enums.*;} (or one at a time) so the
 * unqualified names {@code DisplayMode}, {@code HudGlyph} etc. still
 * work everywhere.
 */
public final class Enums
{
	private Enums()
	{
		// Utility holder; never instantiated.
	}

	/** Where the tick visualization renders. */
	public enum DisplayMode
	{
		TILE,
		HUD,
		BOTH
	}

	/** Where the HUD anchors to the player model. */
	public enum HudAnchorTarget
	{
		HEAD,
		FEET,
		NONE
	}

	/** Glyph style inside the HUD frame. */
	public enum HudGlyph
	{
		DOTS,
		NUMBERS
	}

	/** Where the recorded icons render relative to the HUD glyph row. */
	public enum IconPosition
	{
		ABOVE,
		BELOW
	}

	/** Palette-picker variant in the sidebar panel. */
	public enum PaletteMode
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

	/**
	 * What kind of player action a {@link TickActionEvent} represents. The
	 * recorder filters captured events by the user's selected categories;
	 * the icon resolver maps each category to its sprite or primitive glyph.
	 *
	 * <p>Declaration order is also default render priority when multiple
	 * categories fire on the same tick — earlier categories win when slot
	 * space is limited. ITEM_USE outranks RED/YELLOW because a "use knife on
	 * log" click is more informative than "you clicked yellow this tick".
	 */
	public enum TickActionCategory
	{
		/** Active Protect-from-X prayer this tick. */
		PROTECTION_PRAYER,
		/** Use-item-on-X click. Carries source + target item IDs. */
		ITEM_USE,
		/** Attack-type click — cursor was red when the player clicked. */
		RED_CLICK,
		/** Any other click — cursor was yellow. Includes walk-here. */
		YELLOW_CLICK
	}
}
