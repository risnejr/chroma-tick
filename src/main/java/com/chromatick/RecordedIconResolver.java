package com.chromatick;

import java.awt.image.BufferedImage;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.SpriteID;
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
	private final SpriteManager spriteManager;

	private volatile BufferedImage spriteMelee;
	private volatile BufferedImage spriteMissiles;
	private volatile BufferedImage spriteMagic;
	private boolean requested = false;

	@Inject
	RecordedIconResolver(SpriteManager spriteManager)
	{
		this.spriteManager = spriteManager;
	}

	/**
	 * Trigger the async sprite load. Idempotent — the first call kicks off
	 * the SpriteManager fetches, subsequent calls are no-ops. Cheap enough
	 * to call once per render frame.
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
	}

	/**
	 * Resolve the sprite to render for a set of recorded prayers at one
	 * tick slot. Only one Protect-from-X prayer can be active at a time in
	 * OSRS, so we return the first one we find whose sprite has loaded —
	 * which is also the first by enum order if all three load before
	 * anything renders. Returns {@code null} when the set is empty or no
	 * matching sprite has loaded yet.
	 */
	BufferedImage spriteFor(Set<Prayer> recorded)
	{
		if (recorded.isEmpty())
		{
			return null;
		}
		for (Prayer p : recorded)
		{
			BufferedImage sprite = spriteFor(p);
			if (sprite != null)
			{
				return sprite;
			}
		}
		return null;
	}

	private BufferedImage spriteFor(Prayer p)
	{
		switch (p)
		{
			case PROTECT_FROM_MELEE:    return spriteMelee;
			case PROTECT_FROM_MISSILES: return spriteMissiles;
			case PROTECT_FROM_MAGIC:    return spriteMagic;
			default: return null;
		}
	}
}
