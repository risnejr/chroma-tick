package com.chromatick;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class ChromatickOverlay extends Overlay
{
	private final Client client;
	private final ChromatickConfig config;
	private final ChromatickPlugin plugin;

	@Inject
	public ChromatickOverlay(Client client, ChromatickConfig config, ChromatickPlugin plugin)
	{
		super(plugin);
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
		if (playerPos == null)
		{
			return null;
		}

		final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
		if (playerPosLocal == null)
		{
			return null;
		}

		final Color borderColor = plugin.getCurrentColor();
		final Color fillColor;

		if (config.enableFillColor())
		{
			// fillOpacity is the single source of truth for fill alpha in both modes.
			// In static mode the static-fill swatch only contributes RGB.
			final Color fillRgb = config.staticMode() ? config.staticFillColor() : borderColor;
			fillColor = new Color(
				fillRgb.getRed(),
				fillRgb.getGreen(),
				fillRgb.getBlue(),
				config.fillOpacity()
			);
		}
		else
		{
			fillColor = new Color(0, 0, 0, 0);
		}

		renderTile(graphics, playerPosLocal, borderColor, fillColor, config.tileBorderWidth());

		if (config.drawBelowPlayer() && client.isGpu())
		{
			removePlayer(graphics, client.getLocalPlayer());
		}

		return null;
	}

	private void renderTile(Graphics2D graphics, LocalPoint dest, Color color, Color fillColor, double borderWidth)
	{
		final Polygon poly = Perspective.getCanvasTilePoly(client, dest);
		if (poly == null)
		{
			return;
		}
		OverlayUtil.renderPolygon(graphics, poly, color, fillColor, new BasicStroke((float) borderWidth));
	}

	// ─── Draw below player ───────────────────────────────────────────────────
	// Adapted from LeikvollE's Improved Tile Indicators (BSD-2 Clause)
	// https://github.com/LeikvollE/tileindicators
	//
	// After the tile polygon is drawn onto ABOVE_SCENE, the player model's
	// front-facing triangles are projected to 2D canvas space and filled with
	// AlphaComposite.Clear, punching holes through the overlay so the player
	// model visually appears on top of the tile. GPU rendering mode required.

	private void removePlayer(final Graphics2D graphics, final Player player)
	{
		final int localZ = Perspective.getFootprintTileHeight(
			client,
			player.getLocalLocation(),
			client.getTopLevelWorldView().getPlane(),
			player.getFootprintSize()
		) - player.getAnimationHeightOffset();
		removeActor(graphics, player, localZ);
	}

	private void removeActor(final Graphics2D graphics, final Actor actor, final int localZ)
	{
		final int clipX1 = client.getViewportXOffset();
		final int clipY1 = client.getViewportYOffset();
		final int clipX2 = client.getViewportWidth() + clipX1;
		final int clipY2 = client.getViewportHeight() + clipY1;

		final Object origAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		final Model model = actor.getModel();
		final int vCount = model.getVerticesCount();
		final float[] x3d = model.getVerticesX();
		final float[] y3d = model.getVerticesY();
		final float[] z3d = model.getVerticesZ();

		final int[] x2d = new int[vCount];
		final int[] y2d = new int[vCount];

		final LocalPoint lp = actor.getLocalLocation();
		Perspective.modelToCanvas(
			client, client.getTopLevelWorldView(), vCount,
			lp.getX(), lp.getY(), localZ,
			actor.getCurrentOrientation(),
			x3d, z3d, y3d,
			x2d, y2d
		);

		boolean anyVisible = false;
		for (int i = 0; i < vCount; i++)
		{
			anyVisible |= x2d[i] >= clipX1 && x2d[i] < clipX2
				&& y2d[i] >= clipY1 && y2d[i] < clipY2;
		}
		if (!anyVisible)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, origAA);
			return;
		}

		final int tCount = model.getFaceCount();
		final int[] tx = model.getFaceIndices1();
		final int[] ty = model.getFaceIndices2();
		final int[] tz = model.getFaceIndices3();
		final byte[] transparencies = model.getFaceTransparencies();

		final Composite orig = graphics.getComposite();
		graphics.setComposite(AlphaComposite.Clear);
		graphics.setColor(Color.WHITE);

		for (int i = 0; i < tCount; i++)
		{
			if (getTriDirection(
				x2d[tx[i]], y2d[tx[i]],
				x2d[ty[i]], y2d[ty[i]],
				x2d[tz[i]], y2d[tz[i]]) >= 0)
			{
				continue;
			}
			if (transparencies != null && (transparencies[i] & 0xFF) >= 254)
			{
				continue;
			}
			graphics.fill(new Polygon(
				new int[]{x2d[tx[i]], x2d[ty[i]], x2d[tz[i]]},
				new int[]{y2d[tx[i]], y2d[ty[i]], y2d[tz[i]]},
				3
			));
		}

		graphics.setComposite(orig);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, origAA);
	}

	// 2D cross product — positive means back-facing (cull), negative means front-facing (draw)
	private int getTriDirection(int x1, int y1, int x2, int y2, int x3, int y3)
	{
		return (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
	}
}
