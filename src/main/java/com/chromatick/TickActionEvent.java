package com.chromatick;

/**
 * One categorized player action captured at a single game tick. Immutable
 * value type — the recorder stores these in its per-tick buffer and the
 * icon resolver maps each to its rendered sprite(s).
 *
 * <p>The two ID slots are category-specific and intentionally opaque
 * integers — the resolver is the single seam that knows how to interpret
 * them. Slot conventions:
 *
 * <ul>
 *   <li>{@link TickActionCategory#PROTECTION_PRAYER} —
 *       {@code primaryId} = {@link net.runelite.api.Prayer#ordinal()}.
 *   <li>{@link TickActionCategory#RED_CLICK} —
 *       {@code primaryId} = equipped-weapon item ID (or -1 if unknown),
 *       {@code secondaryId} = target NPC ID (or -1).
 *   <li>{@link TickActionCategory#YELLOW_CLICK} —
 *       {@code primaryId} = -1 (generic cursor glyph),
 *       {@code secondaryId} = target ID (-1 if floor walk-here).
 *   <li>{@link TickActionCategory#ITEM_USE} —
 *       {@code primaryId} = source item ID,
 *       {@code secondaryId} = target item ID (-1 for use-on-object/NPC).
 *   <li>{@link TickActionCategory#MOVEMENT} —
 *       {@code primaryId} = 0 (walk) or 1 (run), both currently unused
 *       by the renderer — kept for future telemetry/labelling.
 * </ul>
 *
 * <p>{@code label} is an optional short human-readable hint for tooltips
 * / future inspector UI. May be {@code null}.
 */
final class TickActionEvent
{
	static final int NONE = -1;

	private final TickActionCategory category;
	private final int primaryId;
	private final int secondaryId;
	private final String label;

	private TickActionEvent(TickActionCategory category, int primaryId, int secondaryId, String label)
	{
		this.category = category;
		this.primaryId = primaryId;
		this.secondaryId = secondaryId;
		this.label = label;
	}

	/** Two-id event (label = null). */
	static TickActionEvent of(TickActionCategory category, int primaryId, int secondaryId)
	{
		return new TickActionEvent(category, primaryId, secondaryId, null);
	}

	/** Single-id event (secondary = NONE, label = null). */
	static TickActionEvent of(TickActionCategory category, int primaryId)
	{
		return new TickActionEvent(category, primaryId, NONE, null);
	}

	/** Full constructor with label. */
	static TickActionEvent of(TickActionCategory category, int primaryId, int secondaryId, String label)
	{
		return new TickActionEvent(category, primaryId, secondaryId, label);
	}

	TickActionCategory category()
	{
		return category;
	}

	int primaryId()
	{
		return primaryId;
	}

	int secondaryId()
	{
		return secondaryId;
	}

	String label()
	{
		return label;
	}
}
