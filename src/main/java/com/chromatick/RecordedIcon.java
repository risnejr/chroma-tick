package com.chromatick;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * What the HUD should draw for one recorded event. Either a loaded sprite
 * (for prayer / item icons) or a primitive colored dot (for yellow / red
 * clicks where there's no sprite to look up).
 *
 * <p>Exactly one of {@code sprite} or {@code primitiveColor} is non-null;
 * the HUD's render loop branches on which is set.
 *
 * <p>Combo (two-sprite) events land in the next commit by adding a
 * {@code secondarySprite} field with a corresponding factory method.
 */
final class RecordedIcon
{
	final BufferedImage sprite;
	final Color primitiveColor;

	private RecordedIcon(BufferedImage sprite, Color primitiveColor)
	{
		this.sprite = sprite;
		this.primitiveColor = primitiveColor;
	}

	/** Render the given sprite at the icon slot. */
	static RecordedIcon sprite(BufferedImage sprite)
	{
		return new RecordedIcon(sprite, null);
	}

	/** Render a filled colored disc at the icon slot. */
	static RecordedIcon dot(Color color)
	{
		return new RecordedIcon(null, color);
	}
}
