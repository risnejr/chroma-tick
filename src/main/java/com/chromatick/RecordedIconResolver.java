package com.chromatick;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;

/**
 * Resolves a per-tick recorded payload to the sprite(s) the HUD timeline
 * should draw. Owns its own sprite cache so the HUD overlay only deals in
 * "give me an icon for this tick" — sprite loading, sprite identity, and
 * payload→icon arbitration all live here.
 *
 * <p>Currently the only recorded payload is the active protect-prayer set,
 * so this class loads three sprites and {@link #spriteFor(Set)} arbitrates
 * by enum order. When the recorder widens to generic action events, this
 * class is the single seam that gains tool/item sprite lookups.
 *
 * <p>Thread model: the SpriteManager async callbacks fire from a background
 * thread; the HUD overlay reads sprites from the client (render) thread.
 * The cache fields are {@code volatile} so the render thread sees the
 * callback's write without explicit synchronisation.
 */
@Singleton
class RecordedIconResolver
{
	// Cursor colors used when the live cross sprites aren't loaded yet
	// (early frames before Client.getCrossSprites() returns a populated
	// array). Tuned bright so the fallback dots stay legible.
	private static final Color RED_CLICK_FALLBACK    = new Color(0xE8, 0x35, 0x35);
	private static final Color YELLOW_CLICK_FALLBACK = new Color(0xF5, 0xC5, 0x2A);

	// Fallback color for ITEM_USE when the source-item lookup fails (no
	// selected widget, no item ID, etc.). A muted cyan — distinct from
	// red/yellow without clashing.
	private static final Color ITEM_USE_FALLBACK = new Color(0x40, 0xC8, 0xA0);

	// Indices into Client.getCrossSprites(). OSRS exposes 8 frames total —
	// frames 0-3 are the yellow click animation, frames 4-7 are red.
	// Pick mid-animation (largest X) for the HUD's static glyph.
	private static final int YELLOW_CROSS_INDEX = 2;
	private static final int RED_CROSS_INDEX    = 6;

	private final Client client;
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;

	private volatile BufferedImage spriteMelee;
	private volatile BufferedImage spriteMissiles;
	private volatile BufferedImage spriteMagic;
	private volatile BufferedImage spriteInventory;
	private boolean requested = false;

	// Cross-sprite cache. Populated lazily on first render that needs them
	// — the array isn't always available at plugin startUp.
	private volatile BufferedImage crossYellow;
	private volatile BufferedImage crossRed;

	@Inject
	RecordedIconResolver(Client client, SpriteManager spriteManager, ItemManager itemManager)
	{
		this.client = client;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
	}

	/**
	 * Trigger the async sprite load. Idempotent — the first call kicks off
	 * the SpriteManager fetches, subsequent calls are no-ops. Cheap enough
	 * to call once per render frame.
	 *
	 * <p>Loads three protect-prayer sprites (HUD rendering) and the
	 * inventory tab sprite (panel category-pill icon for ITEM_USE).
	 */
	void ensureLoaded()
	{
		if (requested)
		{
			return;
		}
		requested = true;
		spriteManager.getSpriteAsync(SpriteID.Prayeron.PROTECT_FROM_MELEE,    0, img -> spriteMelee    = img);
		spriteManager.getSpriteAsync(SpriteID.Prayeron.PROTECT_FROM_MISSILES, 0, img -> spriteMissiles = img);
		spriteManager.getSpriteAsync(SpriteID.Prayeron.PROTECT_FROM_MAGIC,    0, img -> spriteMagic    = img);
		spriteManager.getSpriteAsync(SpriteID.SideIcons.INVENTORY,            0, img -> spriteInventory = img);
	}

	/**
	 * Representative icon for a category, used by the settings panel's
	 * capture-category pill row. Returns {@code null} when the sprite
	 * hasn't loaded yet — callers should show a placeholder and rely on
	 * subsequent repaints picking up the loaded image.
	 *
	 * <p>Distinct from the per-event HUD rendering: ITEM_USE in the HUD
	 * draws the actual source item sprite (knife, herb, etc.), but the
	 * panel pill needs a stable abstract icon — the inventory backpack.
	 */
	BufferedImage categoryIcon(TickActionCategory category)
	{
		ensureLoaded();
		switch (category)
		{
			case PROTECTION_PRAYER:
				return spriteMelee;
			case ITEM_USE:
				return spriteInventory;
			case RED_CLICK:
				if (crossRed == null)
				{
					crossRed = loadCrossSprite(RED_CROSS_INDEX);
				}
				return crossRed;
			case YELLOW_CLICK:
				if (crossYellow == null)
				{
					crossYellow = loadCrossSprite(YELLOW_CROSS_INDEX);
				}
				return crossYellow;
			default:
				return null;
		}
	}

	/**
	 * Resolve the icon to render for a recorded tick. Walks the event
	 * list in insertion order — the plugin's {@code buildTickEvents} adds
	 * prayer events first, then the captured click, so this naturally
	 * yields the rendering priority:
	 * <ol>
	 *   <li>PROTECTION_PRAYER always wins when one is active and the
	 *       prayer sprite has loaded.
	 *   <li>ITEM_USE or YELLOW/RED click otherwise (the click slot's
	 *       internal ITEM_USE-vs-YELLOW/RED priority is enforced by
	 *       {@link TickActionCapture}, so only one click event ever
	 *       reaches the recorder per tick).
	 * </ol>
	 * Returns {@code null} when nothing was recorded or no event yet has
	 * a renderable icon (e.g. sprite still loading — render falls back to
	 * the next event, or nothing if none).
	 */
	RecordedIcon iconFor(RecordedTick recorded)
	{
		if (recorded.isEmpty())
		{
			return null;
		}
		for (TickActionEvent event : recorded.actions())
		{
			RecordedIcon icon = iconFor(event);
			if (icon != null)
			{
				return icon;
			}
		}
		return null;
	}

	private RecordedIcon iconFor(TickActionEvent event)
	{
		switch (event.category())
		{
			case PROTECTION_PRAYER:
				BufferedImage prayerSprite = prayerSprite(event.primaryId());
				return prayerSprite == null ? null : RecordedIcon.sprite(prayerSprite);
			case ITEM_USE:
				return itemUseIcon(event);
			case RED_CLICK:
				return crossIcon(true);
			case YELLOW_CLICK:
				return crossIcon(false);
			default:
				return null;
		}
	}

	/**
	 * Return the cached cross sprite (yellow or red). Pulls from
	 * {@link Client#getCrossSprites()} on first call and converts the
	 * native {@link SpritePixels} to a {@link BufferedImage}; cached for
	 * the remainder of the session. Falls back to a colored dot if the
	 * sprite array isn't populated yet — the client doesn't always have
	 * it ready immediately at plugin startup.
	 */
	private RecordedIcon crossIcon(boolean red)
	{
		BufferedImage cached = red ? crossRed : crossYellow;
		if (cached == null)
		{
			cached = loadCrossSprite(red ? RED_CROSS_INDEX : YELLOW_CROSS_INDEX);
			if (cached != null)
			{
				if (red)
				{
					crossRed = cached;
				}
				else
				{
					crossYellow = cached;
				}
			}
		}
		if (cached != null)
		{
			return RecordedIcon.sprite(cached);
		}
		return RecordedIcon.dot(red ? RED_CLICK_FALLBACK : YELLOW_CLICK_FALLBACK);
	}

	private BufferedImage loadCrossSprite(int index)
	{
		SpritePixels[] sprites = client.getCrossSprites();
		if (sprites == null || index < 0 || index >= sprites.length || sprites[index] == null)
		{
			return null;
		}
		return sprites[index].toBufferedImage();
	}

	/**
	 * Render an ITEM_USE event:
	 * <ul>
	 *   <li>Both source + target known → combo (two sprites, opposite sides
	 *       of the glyph row — knife + log, herb + tar, etc.).
	 *   <li>Source only known → single sprite of the source item.
	 *   <li>Neither known (selection lookup failed) → muted cyan fallback dot.
	 * </ul>
	 * ItemManager returns an AsyncBufferedImage that self-paints once the
	 * underlying item icon has loaded.
	 */
	private RecordedIcon itemUseIcon(TickActionEvent event)
	{
		int sourceId = event.primaryId();
		int targetId = event.secondaryId();
		if (sourceId <= 0)
		{
			return RecordedIcon.dot(ITEM_USE_FALLBACK);
		}
		BufferedImage source = itemManager.getImage(sourceId);
		if (targetId > 0)
		{
			BufferedImage target = itemManager.getImage(targetId);
			return RecordedIcon.combo(source, target);
		}
		return RecordedIcon.sprite(source);
	}

	/** {@code primaryId} = {@link Prayer#ordinal()}. Defensive bounds check. */
	private BufferedImage prayerSprite(int prayerOrdinal)
	{
		Prayer[] prayers = Prayer.values();
		if (prayerOrdinal < 0 || prayerOrdinal >= prayers.length)
		{
			return null;
		}
		switch (prayers[prayerOrdinal])
		{
			case PROTECT_FROM_MELEE:    return spriteMelee;
			case PROTECT_FROM_MISSILES: return spriteMissiles;
			case PROTECT_FROM_MAGIC:    return spriteMagic;
			default: return null;
		}
	}
}
