package com.chromatick;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * What the HUD should draw for one recorded event. Three shapes:
 *
 * <ul>
 *   <li><b>Single sprite</b> — {@code sprite} non-null, the rest null.
 *       Prayer protection icons, single-item item-use, etc.
 *   <li><b>Combo</b> — {@code sprite} + {@code secondarySprite} both
 *       non-null. ITEM_USE source + target rendered on opposite sides
 *       of the glyph row.
 *   <li><b>Primitive dot</b> — {@code primitiveColor} non-null, sprites
 *       null. Fallback / no-sprite categories (e.g. cross-sprites not
 *       loaded yet).
 * </ul>
 *
 * <p>Exactly one of these shapes applies per instance; the HUD branches
 * on which field is set.
 */
final class RecordedIcon
{
	final BufferedImage sprite;
	final BufferedImage secondarySprite;
	final Color primitiveColor;

	private RecordedIcon(BufferedImage sprite, BufferedImage secondarySprite, Color primitiveColor)
	{
		this.sprite = sprite;
		this.secondarySprite = secondarySprite;
		this.primitiveColor = primitiveColor;
	}

	/** Render the given sprite at the icon slot. */
	static RecordedIcon sprite(BufferedImage sprite)
	{
		return new RecordedIcon(sprite, null, null);
	}

	/**
	 * Render two sprites — primary in the iconPosition-controlled band,
	 * secondary across the glyph row in the opposite band. Used by
	 * ITEM_USE when both source + target item IDs were captured.
	 */
	static RecordedIcon combo(BufferedImage primary, BufferedImage secondary)
	{
		return new RecordedIcon(primary, secondary, null);
	}

	/** Render a filled colored disc at the icon slot. */
	static RecordedIcon dot(Color color)
	{
		return new RecordedIcon(null, null, color);
	}

	/** True when both sprites are present and the HUD should render combo geometry. */
	boolean isCombo()
	{
		return sprite != null && secondarySprite != null;
	}
}
