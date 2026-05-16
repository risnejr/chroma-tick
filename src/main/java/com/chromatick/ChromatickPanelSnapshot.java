package com.chromatick;

import java.awt.Color;
import java.util.Set;
import lombok.Builder;

/**
 * Immutable view-state snapshot the plugin hands the panel to render itself.
 *
 * <p>Replaces the panel's pre-refactor habit of calling
 * {@code plugin.getConfig().xxx()} for every widget. The panel asks the
 * plugin once per refresh and renders from this snapshot — config remains
 * the persistence backend but never leaks into UI code, and one consistent
 * snapshot is read in one place instead of ~30 scattered config calls.
 *
 * <p>Includes {@link #effectiveCycleLength} so the panel doesn't have to
 * call back into the plugin for hotkey-override-aware cycle length.
 */
@Builder
final class ChromatickPanelSnapshot
{
	// ── Mode / palette ──────────────────────────────────────────────────
	final boolean staticMode;
	final PaletteMode paletteMode;
	final boolean sequentialFill;

	// ── Static-mode colors ──────────────────────────────────────────────
	final Color staticColor;
	final Color staticFillColor;

	// ── Tile appearance ─────────────────────────────────────────────────
	final double tileBorderWidth;
	final boolean enableFillColor;
	final int fillOpacity;
	final boolean drawBelowPlayer;

	// ── Display ─────────────────────────────────────────────────────────
	final DisplayMode displayMode;

	// ── HUD appearance ──────────────────────────────────────────────────
	final HudGlyph hudGlyph;
	final int hudScale;
	final int hudActiveOpacity;
	final int hudInactiveOpacity;
	final boolean hudBold;
	final int hudPop;
	final int hudSpacing;
	final boolean hudVertical;
	final HudAnchorTarget hudAnchorTarget;
	final int hudVerticalOffset;
	final int hudHorizontalOffset;
	final boolean hudCycleInPlace;

	// ── Recorder ────────────────────────────────────────────────────────
	final RecordMode recordMode;
	final IconPosition recordIconPosition;
	final int recordArmTicks;
	final Set<TickActionCategory> recordCategories;

	// ── Runtime-resolved (config + hotkey override) ─────────────────────
	final int effectiveCycleLength;

	/**
	 * Build a snapshot from the current config + already-resolved runtime
	 * values (effective cycle length, parsed category set). Kept as a
	 * single factory so the field-to-getter mapping lives in one place and
	 * is straightforward to unit-test.
	 */
	static ChromatickPanelSnapshot from(ChromatickConfig cfg, int effectiveCycleLength,
		Set<TickActionCategory> recordCategories)
	{
		return ChromatickPanelSnapshot.builder()
			.staticMode(cfg.staticMode())
			.paletteMode(cfg.paletteMode())
			.sequentialFill(cfg.sequentialFill())
			.staticColor(cfg.staticColor())
			.staticFillColor(cfg.staticFillColor())
			.tileBorderWidth(cfg.tileBorderWidth())
			.enableFillColor(cfg.enableFillColor())
			.fillOpacity(cfg.fillOpacity())
			.drawBelowPlayer(cfg.drawBelowPlayer())
			.displayMode(cfg.displayMode())
			.hudGlyph(cfg.hudGlyph())
			.hudScale(cfg.hudScale())
			.hudActiveOpacity(cfg.hudActiveOpacity())
			.hudInactiveOpacity(cfg.hudInactiveOpacity())
			.hudBold(cfg.hudBold())
			.hudPop(cfg.hudPop())
			.hudSpacing(cfg.hudSpacing())
			.hudVertical(cfg.hudVertical())
			.hudAnchorTarget(cfg.hudAnchorTarget())
			.hudVerticalOffset(cfg.hudVerticalOffset())
			.hudHorizontalOffset(cfg.hudHorizontalOffset())
			.hudCycleInPlace(cfg.hudCycleInPlace())
			.recordMode(cfg.recordMode())
			.recordIconPosition(cfg.recordIconPosition())
			.recordArmTicks(cfg.recordArmTicks())
			.recordCategories(recordCategories)
			.effectiveCycleLength(effectiveCycleLength)
			.build();
	}
}
