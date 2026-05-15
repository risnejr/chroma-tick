package com.chromatick;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel — V1+ Refined layout.
 */
class ChromatickPanel extends PluginPanel
{
	private static final int MIN_CYCLE = 2;
	private static final int MAX_CYCLE = 10;
	private static final String CARD_GRID  = "grid";
	private static final String CARD_WHEEL = "wheel";
	// Pill-index → config string; must stay in sync with the Anchor pill order.
	private static final String[] ANCHOR_TARGETS = {"head", "feet", "none"};

	// Design tokens
	private static final Color TRACK_BG  = new Color(0x1A1A1A);
	private static final Color TEXT_DIM  = new Color(0x6E6E6E);
	private static final Color TEXT_BRIGHT = new Color(0xE4E4E4);
	private static final Color DIVIDER   = new Color(0x3A3A3A);

	private final ChromatickPlugin plugin;

	// Mode toggle
	private final PillToggle modeToggle;

	// Cycle length (always visible, may be grayed out)
	private final JPanel cycleLengthSection;
	private final JPanel cycleTabs;
	private final List<CycleTabButton> tabButtons = new ArrayList<>();

	// Cycle section (tick colors — only visible in Cycle mode)
	private final JPanel cycleSection;
	private final JPanel swatchRow;
	private final List<TickSwatch> tickSwatches = new ArrayList<>();

	// Static section
	private final JPanel       staticSection;
	private final StaticSwatch staticBorderSwatch;
	private final StaticSwatch staticFillSwatch;

	// Picker
	private final PillToggle       pickerToggle;
	private final DiscretePalette  discrete;
	private final ColorWheelPicker wheel;
	private final JPanel           pickerCard;
	private final JCheckBox        sequentialFill;
	private final HexDisplay       hexDisplay;

	// Display + sections
	private final PillToggle displayToggle;
	private final JPanel     tileSection;
	private final JPanel     hudSection;

	// Tile appearance
	private final JSlider   borderWidthSlider;
	private final JLabel    borderWidthValueLabel;
	private final JCheckBox fillEnableBox;
	private final JLabel    fillEnableTextLabel;
	private final FillTileGlyph fillTileGlyph;
	private final JSlider   fillOpacitySlider;
	private final JLabel    fillOpacityValueLabel;
	private final JCheckBox drawBelowBox;
	private final JLabel    drawBelowTextLabel;
	private final PlayerTileGlyph tileGlyph;

	// HUD appearance
	private final PillToggle hudStyleToggle;
	private final JCheckBox  hudOrientBox;
	private final JLabel     hudOrientTextLabel;
	private final HudOrientGlyph hudOrientGlyph;
	private final JSlider    hudScaleSlider;
	private final JLabel     hudScaleValueLabel;
	private final JSlider    hudSpacingSlider;
	private final JLabel     hudSpacingValueLabel;
	private final JSlider    hudPopSlider;
	private final JLabel     hudPopValueLabel;
	private final JSlider    hudActiveOpacitySlider;
	private final JLabel     hudActiveOpacityValueLabel;
	private final JSlider    hudInactiveOpacitySlider;
	private final JLabel     hudInactiveOpacityValueLabel;
	private final JCheckBox  hudBoldBox;
	private final PillToggle hudAnchorToggle;
	private final JCheckBox  hudCycleInPlaceBox;
	private final JSlider    hudOffsetSlider;
	private final JLabel     hudOffsetValueLabel;
	private final JSlider    hudXOffsetSlider;
	private final JLabel     hudXOffsetValueLabel;

	private int  selectedCycle;
	private Slot editing;

	ChromatickPanel(ChromatickPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(getBackground());

		// ─── Title ───────────────────────────────────────────────────────
		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 7, 0));
		titleRow.setBackground(getBackground());
		titleRow.setAlignmentX(CENTER_ALIGNMENT);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		JLabel titleLabel = new JLabel("ChromaTick");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		titleRow.add(new ChromaTickGlyph());
		titleRow.add(titleLabel);
		content.add(titleRow);
		content.add(Box.createVerticalStrut(8));

		// ─── Cycle length (always visible, may be grayed) ────────────────
		cycleLengthSection = column();
		cycleLengthSection.add(labelRow("Cycle length", null));
		cycleLengthSection.add(Box.createVerticalStrut(4));

		cycleTabs = new JPanel(new GridLayout(1, MAX_CYCLE - MIN_CYCLE + 1, 2, 0));
		cycleTabs.setBackground(getBackground());
		cycleTabs.setAlignmentX(CENTER_ALIGNMENT);
		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			CycleTabButton btn = new CycleTabButton(n);
			tabButtons.add(btn);
			cycleTabs.add(btn);
		}
		cycleLengthSection.add(cycleTabs);
		content.add(cycleLengthSection);
		content.add(Box.createVerticalStrut(10));

		// ─── Mode toggle ─────────────────────────────────────────────────
		modeToggle = new PillToggle(new String[]{"Cycle", "Static"});
		modeToggle.setAlignmentX(CENTER_ALIGNMENT);
		modeToggle.addListener(idx -> plugin.setStaticMode(idx == 1));
		content.add(modeToggle);
		content.add(Box.createVerticalStrut(10));

		// ─── Tick colors (only visible in Cycle mode) ────────────────────
		cycleSection = column();

		JLabel resetLink = new JLabel("RESET");
		resetLink.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		resetLink.setForeground(TEXT_DIM);
		resetLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		resetLink.setToolTipText("Reset this cycle's colors to defaults");
		resetLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				plugin.resetCustomPaletteForCycle(selectedCycle);
				rebuildTickSwatches();
				selectFirstTickSlot();
			}
		});
		cycleSection.add(labelRow("Tick colors", resetLink));
		cycleSection.add(Box.createVerticalStrut(4));

		swatchRow = new JPanel();
		swatchRow.setBackground(getBackground());
		swatchRow.setAlignmentX(CENTER_ALIGNMENT);
		cycleSection.add(swatchRow);
		content.add(cycleSection);

		// ─── Static section ──────────────────────────────────────────────
		staticSection = column();
		staticSection.add(labelRow("Static colors", null));
		staticSection.add(Box.createVerticalStrut(4));
		JPanel staticRow = new JPanel(new GridLayout(1, 2, 8, 0));
		staticRow.setBackground(getBackground());
		staticRow.setAlignmentX(CENTER_ALIGNMENT);
		staticRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
		staticBorderSwatch = new StaticSwatch("Border", true);
		staticFillSwatch   = new StaticSwatch("Fill", false);
		staticRow.add(staticBorderSwatch);
		staticRow.add(staticFillSwatch);
		staticSection.add(staticRow);
		content.add(staticSection);
		content.add(Box.createVerticalStrut(10));

		// ─── Palette ─────────────────────────────────────────────────────
		pickerToggle = new PillToggle(new String[]{"GRID", "WHEEL"});

		JPanel pickerHeader = new JPanel(new BorderLayout());
		pickerHeader.setBackground(getBackground());
		pickerHeader.setAlignmentX(CENTER_ALIGNMENT);
		pickerHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		pickerHeader.add(sectionLabel("Palette"), BorderLayout.WEST);
		pickerHeader.add(pickerToggle, BorderLayout.EAST);
		content.add(pickerHeader);
		content.add(Box.createVerticalStrut(4));

		discrete = new DiscretePalette();
		wheel    = new ColorWheelPicker();
		discrete.addColorListener(this::onPickerColor);
		discrete.addCommitListener(this::onPickerCommit);
		wheel.addColorListener(this::onPickerColor);
		wheel.addCommitListener(this::onPickerCommit);

		// DynamicCardLayout sizes to the *visible* card only — without it the
		// picker reserves the wheel's height even when GRID is showing.
		pickerCard = new JPanel(new DynamicCardLayout());
		pickerCard.setBackground(getBackground());
		pickerCard.setAlignmentX(CENTER_ALIGNMENT);
		pickerCard.add(discrete, CARD_GRID);
		pickerCard.add(wheel, CARD_WHEEL);
		content.add(pickerCard);
		content.add(Box.createVerticalStrut(4));

		pickerToggle.addListener(idx -> {
			String mode = idx == 0 ? CARD_GRID : CARD_WHEEL;
			plugin.setPaletteMode(mode);
			((CardLayout) pickerCard.getLayout()).show(pickerCard, mode);
			pickerCard.revalidate();
			pickerCard.repaint();
			syncPickerToEditingSlot();
		});

		// Hex display + auto-advance
		hexDisplay     = new HexDisplay();
		sequentialFill = new JCheckBox("Auto-advance →");
		sequentialFill.setFont(FontManager.getRunescapeSmallFont());
		sequentialFill.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sequentialFill.setBackground(getBackground());
		sequentialFill.setToolTipText("Auto-advance to the next tick slot after each pick");
		sequentialFill.addActionListener(e -> plugin.setSequentialFill(sequentialFill.isSelected()));

		JPanel underPicker = new JPanel(new BorderLayout());
		underPicker.setBackground(getBackground());
		underPicker.setAlignmentX(CENTER_ALIGNMENT);
		underPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		underPicker.add(hexDisplay, BorderLayout.WEST);
		underPicker.add(sequentialFill, BorderLayout.EAST);
		content.add(underPicker);

		addDivider(content);

		// ─── Display ──────────────────────────────────────────────────────
		JPanel displayHeader = new JPanel(new BorderLayout());
		displayHeader.setBackground(getBackground());
		displayHeader.setAlignmentX(CENTER_ALIGNMENT);
		displayHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		displayHeader.add(sectionLabel("Display"), BorderLayout.WEST);
		content.add(displayHeader);
		content.add(Box.createVerticalStrut(4));

		displayToggle = new PillToggle(new String[]{"Tile", "HUD", "Both"});
		displayToggle.setAlignmentX(CENTER_ALIGNMENT);
		displayToggle.addListener(idx -> plugin.setDisplayMode(displayModeForIdx(idx)));
		content.add(displayToggle);
		content.add(Box.createVerticalStrut(10));

		ChromatickConfig cfg = plugin.getConfig();

		// ─── Tile section ─────────────────────────────────────────────────
		tileSection = column();
		JPanel tileHeader = new JPanel(new BorderLayout());
		tileHeader.setBackground(getBackground());
		tileHeader.setAlignmentX(CENTER_ALIGNMENT);
		tileHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		tileHeader.add(sectionLabel("Tile"), BorderLayout.WEST);
		tileSection.add(tileHeader);
		tileSection.add(Box.createVerticalStrut(4));

		JPanel tileBody = new JPanel();
		tileBody.setLayout(new BoxLayout(tileBody, BoxLayout.Y_AXIS));
		tileBody.setBackground(getBackground());
		tileBody.setAlignmentX(CENTER_ALIGNMENT);

		// Border width
		borderWidthSlider     = themedSlider(1, 5);
		borderWidthValueLabel = compactValueLabel();
		borderWidthSlider.addChangeListener(e -> {
			int v = borderWidthSlider.getValue();
			borderWidthValueLabel.setText(valueFmt(v, "px"));
			plugin.setBorderWidth(v);
		});
		addResetGesture(borderWidthSlider, 2);
		tileBody.add(labeledSliderRow("Border width", borderWidthSlider, borderWidthValueLabel));

		// Fill enable — label "Fill tile" + outline/filled tile icon on left, checkbox right.
		// (Listener wired below, after fillOpacitySlider exists.)
		fillEnableBox = themedCheckBox();
		fillTileGlyph = new FillTileGlyph(cfg.enableFillColor());
		fillEnableTextLabel = themedRowLabel("Fill tile");
		tileBody.add(labeledCheckBoxRow(inlineLabelArea(fillEnableTextLabel, fillTileGlyph), fillEnableBox));

		// Fill opacity
		fillOpacitySlider     = themedSlider(0, 100);
		fillOpacityValueLabel = compactValueLabel();
		fillOpacitySlider.addChangeListener(e -> {
			int pct  = fillOpacitySlider.getValue();
			int v255 = Math.round(pct * 2.55f);
			fillOpacityValueLabel.setText(valueFmt(pct, "%"));
			plugin.setFillOpacity(v255);
		});
		// Config default fillOpacity=50 (255-scale) ≈ 20% in slider units.
		addResetGesture(fillOpacitySlider, 20);
		tileBody.add(labeledSliderRow("Fill opacity", fillOpacitySlider, fillOpacityValueLabel));

		// Now that fillOpacitySlider exists, wire the fill-enable side effects.
		fillEnableBox.addActionListener(e -> {
			boolean on = fillEnableBox.isSelected();
			plugin.setEnableFillColor(on);
			fillTileGlyph.setFilled(on);
			fillOpacitySlider.setEnabled(on);
			fillOpacityValueLabel.setEnabled(on);
		});

		// Draw tile row — dynamic text "Draw tile under/above player" + tile glyph on left.
		drawBelowBox = themedCheckBox();
		drawBelowBox.setToolTipText("Requires GPU rendering mode in RuneLite settings");
		drawBelowTextLabel = themedRowLabel(drawBelowText(cfg.drawBelowPlayer()));
		tileGlyph = new PlayerTileGlyph(cfg.drawBelowPlayer());
		drawBelowBox.addActionListener(e -> {
			boolean below = drawBelowBox.isSelected();
			plugin.setDrawBelowPlayer(below);
			drawBelowTextLabel.setText(drawBelowText(below));
			tileGlyph.setBelow(below);
		});
		tileBody.add(labeledCheckBoxRow(inlineLabelArea(drawBelowTextLabel, tileGlyph), drawBelowBox));

		tileSection.add(tileBody);
		content.add(tileSection);
		addDivider(content);

		// ─── HUD section ──────────────────────────────────────────────────
		hudSection = column();
		// Visual glyphs as labels — ● is the dot itself, 1 is the digit. Sans-serif
		// because the runescape pixel font lacks the Unicode bullet. Size 10 keeps
		// these pills at the same visual height as the other (runescape-font) pills.
		hudStyleToggle  = new PillToggle(new String[]{"●", "1"});
		hudStyleToggle.setPillFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		hudStyleToggle.setPillTooltips(new String[]{"Dots", "Numbers"});

		JPanel hudHeader = new JPanel(new BorderLayout());
		hudHeader.setBackground(getBackground());
		hudHeader.setAlignmentX(CENTER_ALIGNMENT);
		hudHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		hudHeader.add(sectionLabel("HUD"), BorderLayout.WEST);
		hudHeader.add(hudStyleToggle, BorderLayout.EAST);
		hudSection.add(hudHeader);
		hudSection.add(Box.createVerticalStrut(6));

		JPanel hudBody = new JPanel();
		hudBody.setLayout(new BoxLayout(hudBody, BoxLayout.Y_AXIS));
		hudBody.setBackground(getBackground());
		hudBody.setAlignmentX(CENTER_ALIGNMENT);

		// ── Position subgroup ─────────────────────────────────────────────
		hudBody.add(subgroupLabelRow("Position"));
		hudBody.add(Box.createVerticalStrut(2));

		// Anchor target — Head, Feet, or None. Alt+drag flips to None.
		hudAnchorToggle = new PillToggle(new String[]{"Head", "Feet", "None"});
		hudAnchorToggle.setPillTooltips(new String[]{
			"Anchor under the player's head",
			"Anchor under the player's feet",
			"Free-floating (alt+drag to reposition)"});
		hudAnchorToggle.addListener(idx -> {
			String target = ANCHOR_TARGETS[idx];
			plugin.setHudAnchorTarget(target);
			setOffsetSlidersEnabled(!"none".equals(target));
		});
		hudBody.add(labeledToggleRow("Anchor", hudAnchorToggle));

		// X offset — horizontal nudge from anchor point to HUD bar center.
		hudXOffsetSlider     = themedSlider(-100, 100);
		hudXOffsetValueLabel = compactValueLabel();
		hudXOffsetSlider.addChangeListener(e -> {
			int v = hudXOffsetSlider.getValue();
			hudXOffsetValueLabel.setText(valueFmt(v, "px"));
			plugin.setHudHorizontalOffset(v);
		});
		addResetGesture(hudXOffsetSlider, 0);
		JPanel xOffsetRow = labeledSliderRow("X offset", hudXOffsetSlider, hudXOffsetValueLabel);
		String xTip = "Horizontal offset from the anchor point. Negative = left. Right-click to reset.";
		xOffsetRow.setToolTipText(xTip);
		hudXOffsetSlider.setToolTipText(xTip);
		hudBody.add(xOffsetRow);

		// Y offset — vertical nudge from anchor point.
		hudOffsetSlider     = themedSlider(-100, 100);
		hudOffsetValueLabel = compactValueLabel();
		hudOffsetSlider.addChangeListener(e -> {
			int v = hudOffsetSlider.getValue();
			hudOffsetValueLabel.setText(valueFmt(v, "px"));
			plugin.setHudVerticalOffset(v);
		});
		addResetGesture(hudOffsetSlider, 30);
		JPanel offsetRow = labeledSliderRow("Y offset", hudOffsetSlider, hudOffsetValueLabel);
		String yTip = "Vertical offset from the anchor point. Negative = up. Right-click to reset.";
		offsetRow.setToolTipText(yTip);
		hudOffsetSlider.setToolTipText(yTip);
		hudBody.add(offsetRow);

		// Orient — dynamic-text checkbox + 3-dot glyph. Checked = vertical.
		hudOrientBox = themedCheckBox();
		hudOrientBox.setToolTipText("Layout direction of the HUD bar");
		hudOrientTextLabel = themedRowLabel(orientText(cfg.hudVertical()));
		hudOrientGlyph = new HudOrientGlyph(cfg.hudVertical());
		hudOrientBox.addActionListener(e -> {
			boolean vert = hudOrientBox.isSelected();
			plugin.setHudVertical(vert);
			hudOrientTextLabel.setText(orientText(vert));
			hudOrientGlyph.setVertical(vert);
		});
		hudBody.add(labeledCheckBoxRow(inlineLabelArea(hudOrientTextLabel, hudOrientGlyph), hudOrientBox));

		// Cycle in place — single glyph at one spot, color/number cycles each tick.
		hudCycleInPlaceBox = themedCheckBox();
		hudCycleInPlaceBox.setToolTipText(
			"<html>Show a single glyph that changes color (and number) each tick,<br>"
			+ "instead of a row of every tick. Spacing and Direction become moot.</html>");
		hudCycleInPlaceBox.addActionListener(e -> plugin.setHudCycleInPlace(hudCycleInPlaceBox.isSelected()));
		hudBody.add(labeledCheckBoxRow("Cycle in place", hudCycleInPlaceBox));

		hudBody.add(Box.createVerticalStrut(8));

		// ── Size subgroup ─────────────────────────────────────────────────
		hudBody.add(subgroupLabelRow("Size"));
		hudBody.add(Box.createVerticalStrut(2));

		// Scale — bigger range now (50-400%) since users want bigger glyphs
		hudScaleSlider     = themedSlider(50, 400);
		hudScaleValueLabel = compactValueLabel();
		hudScaleSlider.addChangeListener(e -> {
			int v = hudScaleSlider.getValue();
			hudScaleValueLabel.setText(valueFmt(v, "%"));
			if (v != plugin.getConfig().hudScale())
			{
				plugin.setHudScale(v);
			}
		});
		addResetGesture(hudScaleSlider, 200);
		hudScaleSlider.setToolTipText("HUD scale. Right-click to reset.");
		hudBody.add(labeledSliderRow("Scale", hudScaleSlider, hudScaleValueLabel));

		// Spacing — gap between glyphs in px at 100% scale; negative = overlap
		hudSpacingSlider     = themedSlider(-10, 10);
		hudSpacingValueLabel = compactValueLabel();
		hudSpacingSlider.addChangeListener(e -> {
			int v = hudSpacingSlider.getValue();
			hudSpacingValueLabel.setText(valueFmt(v, "px"));
			plugin.setHudSpacing(v);
		});
		addResetGesture(hudSpacingSlider, 0);
		JPanel spacingRow = labeledSliderRow("Spacing", hudSpacingSlider, hudSpacingValueLabel);
		String spacingTip = "Gap between glyphs. Negative = overlap. Right-click to reset.";
		spacingRow.setToolTipText(spacingTip);
		hudSpacingSlider.setToolTipText(spacingTip);
		hudBody.add(spacingRow);

		// Active size — extra size on the active tick (was "Pop")
		hudPopSlider     = themedSlider(0, 200);
		hudPopValueLabel = compactValueLabel();
		hudPopSlider.addChangeListener(e -> {
			int v = hudPopSlider.getValue();
			hudPopValueLabel.setText(valueFmt(v, "%"));
			plugin.setHudPop(v);
		});
		addResetGesture(hudPopSlider, 0);
		JPanel popRow = labeledSliderRow("Active size", hudPopSlider, hudPopValueLabel);
		String popTip = "Extra size on the active tick — siblings stay put. Right-click to reset.";
		popRow.setToolTipText(popTip);
		hudPopSlider.setToolTipText(popTip);
		hudBody.add(popRow);

		hudBody.add(Box.createVerticalStrut(8));

		// ── Visibility subgroup ───────────────────────────────────────────
		hudBody.add(subgroupLabelRow("Visibility"));
		hudBody.add(Box.createVerticalStrut(2));

		// Active opacity
		hudActiveOpacitySlider     = themedSlider(0, 100);
		hudActiveOpacityValueLabel = compactValueLabel();
		hudActiveOpacitySlider.addChangeListener(e -> {
			int v = hudActiveOpacitySlider.getValue();
			hudActiveOpacityValueLabel.setText(valueFmt(v, "%"));
			plugin.setHudActiveOpacity(v);
		});
		addResetGesture(hudActiveOpacitySlider, 100);
		hudActiveOpacitySlider.setToolTipText("Active glyph opacity. Right-click to reset.");
		hudBody.add(labeledSliderRow("Active opacity", hudActiveOpacitySlider, hudActiveOpacityValueLabel));

		// Inactive opacity
		hudInactiveOpacitySlider     = themedSlider(0, 100);
		hudInactiveOpacityValueLabel = compactValueLabel();
		hudInactiveOpacitySlider.addChangeListener(e -> {
			int v = hudInactiveOpacitySlider.getValue();
			hudInactiveOpacityValueLabel.setText(valueFmt(v, "%"));
			plugin.setHudInactiveOpacity(v);
		});
		addResetGesture(hudInactiveOpacitySlider, 40);
		hudInactiveOpacitySlider.setToolTipText("Inactive glyph opacity. Right-click to reset.");
		hudBody.add(labeledSliderRow("Inactive opacity", hudInactiveOpacitySlider, hudInactiveOpacityValueLabel));

		// Bold active checkbox — only meaningful for the Numbers glyph
		hudBoldBox = themedCheckBox();
		hudBoldBox.setToolTipText("Render the active tick in bold (Numbers style only)");
		hudBoldBox.addActionListener(e -> plugin.setHudBold(hudBoldBox.isSelected()));
		hudBody.add(labeledCheckBoxRow("Bold active", hudBoldBox));

		// Wire glyph toggle — must come after hudBoldBox is initialized since the
		// listener flips its enabled state.
		hudStyleToggle.addListener(idx -> {
			String glyph = idx == 0 ? "dots" : "numbers";
			plugin.setHudGlyph(glyph);
			hudBoldBox.setEnabled("numbers".equals(glyph));
		});

		hudSection.add(hudBody);
		content.add(hudSection);

		add(content, BorderLayout.NORTH);

		// ─── Initial state ────────────────────────────────────────────────
		modeToggle.setSelected(cfg.staticMode() ? 1 : 0);
		pickerToggle.setSelected(CARD_WHEEL.equals(cfg.paletteMode()) ? 1 : 0);
		((CardLayout) pickerCard.getLayout()).show(pickerCard, cfg.paletteMode());
		sequentialFill.setSelected(cfg.sequentialFill());
		setBorderWidthControls((int) Math.round(cfg.tileBorderWidth()));
		fillEnableBox.setSelected(cfg.enableFillColor());
		fillTileGlyph.setFilled(cfg.enableFillColor());
		setOpacityControls(cfg.fillOpacity());
		fillOpacitySlider.setEnabled(cfg.enableFillColor());
		fillOpacityValueLabel.setEnabled(cfg.enableFillColor());
		drawBelowBox.setSelected(cfg.drawBelowPlayer());
		drawBelowTextLabel.setText(drawBelowText(cfg.drawBelowPlayer()));

		displayToggle.setSelected(displayModeIdx(cfg.displayMode()));
		hudStyleToggle.setSelected("numbers".equals(cfg.hudGlyph()) ? 1 : 0);
		hudOrientBox.setSelected(cfg.hudVertical());
		hudOrientTextLabel.setText(orientText(cfg.hudVertical()));
		hudOrientGlyph.setVertical(cfg.hudVertical());
		hudAnchorToggle.setSelected(anchorTargetIdx(cfg.hudAnchorTarget()));
		hudCycleInPlaceBox.setSelected(cfg.hudCycleInPlace());
		setPctSliderControls(hudScaleSlider, hudScaleValueLabel, cfg.hudScale());
		setPctSliderControls(hudActiveOpacitySlider, hudActiveOpacityValueLabel, cfg.hudActiveOpacity());
		setPctSliderControls(hudInactiveOpacitySlider, hudInactiveOpacityValueLabel, cfg.hudInactiveOpacity());
		setPctSliderControls(hudPopSlider, hudPopValueLabel, cfg.hudPop());
		setIntSliderControls(hudSpacingSlider, hudSpacingValueLabel, cfg.hudSpacing(), "px");
		setIntSliderControls(hudXOffsetSlider, hudXOffsetValueLabel, cfg.hudHorizontalOffset(), "px");
		setIntSliderControls(hudOffsetSlider, hudOffsetValueLabel, cfg.hudVerticalOffset(), "px");
		setOffsetSlidersEnabled(!"none".equals(cfg.hudAnchorTarget()));
		hudBoldBox.setSelected(cfg.hudBold());
		hudBoldBox.setEnabled("numbers".equals(cfg.hudGlyph()));

		selectedCycle = clampCycle(plugin.getEffectiveCycleLength());
		applyModeVisibility(cfg.staticMode());
		applyDisplayVisibility(cfg.displayMode());
		applyCycleLengthEnabled(cfg);
		highlightActiveCycleTab();
		rebuildTickSwatches();
		selectInitialEditingSlot();
	}

	private static int displayModeIdx(String mode)
	{
		if ("hud".equals(mode))  return 1;
		if ("both".equals(mode)) return 2;
		return 0;
	}

	private static String displayModeForIdx(int idx)
	{
		switch (idx)
		{
			case 1:  return "hud";
			case 2:  return "both";
			default: return "tile";
		}
	}

	private void applyDisplayVisibility(String mode)
	{
		boolean tile = "tile".equals(mode) || "both".equals(mode);
		boolean hud  = "hud".equals(mode)  || "both".equals(mode);
		if (!tile && !hud)
		{
			tile = true;
		}
		tileSection.setVisible(tile);
		hudSection.setVisible(hud);
		revalidate();
		repaint();
	}

	/**
	 * Cycle length is only meaningful when something visualizes the cycle:
	 * any non-static mode, OR static mode with the HUD visible. If we're in
	 * static + tile-only the cycle is just a frozen color — gray the tabs out.
	 */
	private void applyCycleLengthEnabled(ChromatickConfig cfg)
	{
		boolean hudVisible = "hud".equals(cfg.displayMode()) || "both".equals(cfg.displayMode());
		boolean enabled = !cfg.staticMode() || hudVisible;
		for (CycleTabButton b : tabButtons)
		{
			b.setTabEnabled(enabled);
		}
	}

	private static void setPctSliderControls(JSlider s, JLabel l, int v)
	{
		s.setValue(v);
		l.setText(valueFmt(v, "%"));
	}

	private static void setIntSliderControls(JSlider s, JLabel l, int v, String unit)
	{
		s.setValue(v);
		l.setText(valueFmt(v, unit));
	}

	private void setOffsetSlidersEnabled(boolean en)
	{
		hudOffsetSlider.setEnabled(en);
		hudOffsetValueLabel.setEnabled(en);
		hudXOffsetSlider.setEnabled(en);
		hudXOffsetValueLabel.setEnabled(en);
	}

	private static int anchorTargetIdx(String target)
	{
		switch (target)
		{
			case "head": return 0;
			case "none": return 2;
			default:     return 1; // feet
		}
	}

	// ─── Public sync hooks ────────────────────────────────────────────────

	void refreshFromConfig()
	{
		ChromatickConfig cfg      = plugin.getConfig();
		boolean          isStatic = cfg.staticMode();
		modeToggle.setSelected(isStatic ? 1 : 0);
		applyModeVisibility(isStatic);

		int activeCycle = clampCycle(plugin.getEffectiveCycleLength());
		if (activeCycle != selectedCycle)
		{
			selectedCycle = activeCycle;
			rebuildTickSwatches();
			if (!isStatic)
			{
				selectFirstTickSlot();
			}
		}
		highlightActiveCycleTab();

		staticBorderSwatch.setColor(cfg.staticColor());
		staticFillSwatch.setColor(cfg.staticFillColor());

		setBorderWidthControls((int) Math.round(cfg.tileBorderWidth()));
		fillEnableBox.setSelected(cfg.enableFillColor());
		fillTileGlyph.setFilled(cfg.enableFillColor());
		setOpacityControls(cfg.fillOpacity());
		fillOpacitySlider.setEnabled(cfg.enableFillColor());
		fillOpacityValueLabel.setEnabled(cfg.enableFillColor());

		boolean below = cfg.drawBelowPlayer();
		drawBelowBox.setSelected(below);
		drawBelowTextLabel.setText(drawBelowText(below));
		tileGlyph.setBelow(below);

		displayToggle.setSelected(displayModeIdx(cfg.displayMode()));
		applyDisplayVisibility(cfg.displayMode());
		applyCycleLengthEnabled(cfg);
		hudStyleToggle.setSelected("numbers".equals(cfg.hudGlyph()) ? 1 : 0);
		hudOrientBox.setSelected(cfg.hudVertical());
		hudOrientTextLabel.setText(orientText(cfg.hudVertical()));
		hudOrientGlyph.setVertical(cfg.hudVertical());
		hudAnchorToggle.setSelected(anchorTargetIdx(cfg.hudAnchorTarget()));
		hudCycleInPlaceBox.setSelected(cfg.hudCycleInPlace());
		setPctSliderControls(hudScaleSlider, hudScaleValueLabel, cfg.hudScale());
		setPctSliderControls(hudActiveOpacitySlider, hudActiveOpacityValueLabel, cfg.hudActiveOpacity());
		setPctSliderControls(hudInactiveOpacitySlider, hudInactiveOpacityValueLabel, cfg.hudInactiveOpacity());
		setPctSliderControls(hudPopSlider, hudPopValueLabel, cfg.hudPop());
		setIntSliderControls(hudSpacingSlider, hudSpacingValueLabel, cfg.hudSpacing(), "px");
		setIntSliderControls(hudXOffsetSlider, hudXOffsetValueLabel, cfg.hudHorizontalOffset(), "px");
		setIntSliderControls(hudOffsetSlider, hudOffsetValueLabel, cfg.hudVerticalOffset(), "px");
		setOffsetSlidersEnabled(!"none".equals(cfg.hudAnchorTarget()));
		hudBoldBox.setSelected(cfg.hudBold());
		hudBoldBox.setEnabled("numbers".equals(cfg.hudGlyph()));
	}

	void onPaletteChanged(int cycleN)
	{
		if (cycleN == selectedCycle && !plugin.isStaticMode())
		{
			Color[] palette = plugin.getCustomPaletteForCycle(selectedCycle);
			for (int i = 0; i < tickSwatches.size() && i < palette.length; i++)
			{
				tickSwatches.get(i).setColor(palette[i]);
			}
			syncPickerToEditingSlot();
		}
	}

	// ─── Picker handlers ──────────────────────────────────────────────────

	private void onPickerColor(Color c)
	{
		updateHex(c);
		if (editing != null)
		{
			editing.applyColor(c);
		}
	}

	private void onPickerCommit()
	{
		if (sequentialFill.isSelected()
			&& editing instanceof TickSwatch
			&& !plugin.isStaticMode())
		{
			int next = (((TickSwatch) editing).index + 1) % selectedCycle;
			selectTickSlot(next);
		}
	}

	// ─── State helpers ────────────────────────────────────────────────────

	private void applyModeVisibility(boolean isStatic)
	{
		cycleSection.setVisible(!isStatic);
		staticSection.setVisible(isStatic);
		sequentialFill.setEnabled(!isStatic);
		if (isStatic)
		{
			if (!(editing instanceof StaticSwatch))
			{
				selectStaticSwatch(staticBorderSwatch);
			}
		}
		else if (!(editing instanceof TickSwatch) && !tickSwatches.isEmpty())
		{
			selectTickSlot(0);
		}
		revalidate();
		repaint();
	}

	private void highlightActiveCycleTab()
	{
		for (CycleTabButton b : tabButtons)
		{
			b.setTabSelected(b.cycle == selectedCycle);
		}
	}

	private void rebuildTickSwatches()
	{
		swatchRow.removeAll();
		tickSwatches.clear();
		Color[] palette = plugin.getCustomPaletteForCycle(selectedCycle);
		int perRow = Math.min(selectedCycle, 5);
		int rows   = (int) Math.ceil(selectedCycle / (double) perRow);
		swatchRow.setLayout(new GridLayout(rows, perRow, 4, 4));
		for (int i = 0; i < selectedCycle; i++)
		{
			TickSwatch s = new TickSwatch(i, palette[i]);
			tickSwatches.add(s);
			swatchRow.add(s);
		}
		swatchRow.revalidate();
		swatchRow.repaint();
	}

	private void selectInitialEditingSlot()
	{
		if (plugin.isStaticMode())
		{
			selectStaticSwatch(staticBorderSwatch);
		}
		else
		{
			selectFirstTickSlot();
		}
	}

	private void selectFirstTickSlot()
	{
		if (!tickSwatches.isEmpty())
		{
			selectTickSlot(0);
		}
	}

	private void selectTickSlot(int idx)
	{
		if (idx < 0 || idx >= tickSwatches.size())
		{
			return;
		}
		editing = tickSwatches.get(idx);
		for (int i = 0; i < tickSwatches.size(); i++)
		{
			tickSwatches.get(i).setSelected(i == idx);
		}
		syncPickerToEditingSlot();
	}

	private void selectStaticSwatch(StaticSwatch s)
	{
		editing = s;
		staticBorderSwatch.setSelected(s == staticBorderSwatch);
		staticFillSwatch.setSelected(s == staticFillSwatch);
		syncPickerToEditingSlot();
	}

	private void syncPickerToEditingSlot()
	{
		if (editing == null)
		{
			return;
		}
		Color c = editing.getColor();
		wheel.setColorSilent(c);
		discrete.setColorSilent(c);
		updateHex(c);
	}

	private void updateHex(Color c)
	{
		hexDisplay.setColor(c);
	}

	private static int clampCycle(int n)
	{
		return Math.max(MIN_CYCLE, Math.min(MAX_CYCLE, n));
	}

	// ─── Layout helpers ───────────────────────────────────────────────────

	private JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(getBackground());
		p.setAlignmentX(CENTER_ALIGNMENT);
		return p;
	}

	private void addDivider(JPanel parent)
	{
		parent.add(Box.createVerticalStrut(10));
		JPanel divider = new JPanel();
		divider.setBackground(DIVIDER);
		divider.setOpaque(true);
		divider.setMinimumSize(new Dimension(0, 1));
		divider.setPreferredSize(new Dimension(0, 1));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		divider.setAlignmentX(CENTER_ALIGNMENT);
		parent.add(divider);
		parent.add(Box.createVerticalStrut(6));
	}

	private JLabel sectionLabel(String text)
	{
		JLabel l = new JLabel(text.toUpperCase());
		l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		l.setForeground(TEXT_DIM);
		l.setAlignmentX(CENTER_ALIGNMENT);
		return l;
	}

	private JPanel labelRow(String text, JLabel right)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(getBackground());
		row.setAlignmentX(CENTER_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		row.add(sectionLabel(text), BorderLayout.WEST);
		if (right != null)
		{
			row.add(right, BorderLayout.EAST);
		}
		return row;
	}

	private JPanel centerWrap(JPanel inner)
	{
		JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		wrap.setBackground(getBackground());
		wrap.add(inner);
		return wrap;
	}

	private JCheckBox themedCheckBox(String text)
	{
		JCheckBox b = new JCheckBox(text);
		b.setBackground(getBackground());
		b.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setAlignmentX(LEFT_ALIGNMENT);
		return b;
	}

	private JCheckBox themedCheckBox()
	{
		return themedCheckBox("");
	}

	/**
	 * Subgroup label inside a section — same caps as section labels but without
	 * the bold weight, so it reads as a sub-header and not a peer header.
	 */
	private JPanel subgroupLabelRow(String text)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(getBackground());
		// Match the other rows in the HUD body — mixed alignments in BoxLayout
		// cause horizontal shifts when sibling widths fluctuate.
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		JLabel l = new JLabel(text.toUpperCase());
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(TEXT_DIM);
		row.add(l, BorderLayout.WEST);
		return row;
	}

	private JSlider themedSlider(int min, int max)
	{
		JSlider s = new JSlider(min, max);
		s.setBackground(getBackground());
		s.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		s.setOpaque(false);
		return s;
	}

	/**
	 * Right-click the slider to reset it to the supplied default. The slider's
	 * change listener then fires normally, so the config and value label update
	 * for free. Used on every numeric slider so resets are uniform.
	 */
	private static void addResetGesture(JSlider slider, int defaultValue)
	{
		slider.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e))
				{
					slider.setValue(defaultValue);
					e.consume();
				}
			}
		});
	}

	private JLabel compactValueLabel()
	{
		JLabel l = new JLabel();
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setHorizontalAlignment(SwingConstants.RIGHT);
		// HTML labels ignore preferredSize unless min/max are also locked —
		// without this the column shifts horizontally as the value width changes.
		Dimension d = new Dimension(40, 16);
		l.setMinimumSize(d);
		l.setPreferredSize(d);
		l.setMaximumSize(d);
		return l;
	}

	/** Returns HTML with bright number and dim unit: "3 px" or "40 %" */
	private static String valueFmt(int n, String unit)
	{
		return "<html><font color='#E4E4E4'>" + n + "</font>"
			+ "<font color='#6E6E6E'> " + unit + "</font></html>";
	}

	private JPanel labeledSliderRow(String text, JSlider slider, JLabel valueLabel)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(getBackground());
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		// 92px fits "Inactive opacity" (16 chars) at the RuneScape small-font width.
		l.setPreferredSize(new Dimension(92, 16));
		row.add(l,          BorderLayout.WEST);
		row.add(slider,     BorderLayout.CENTER);
		row.add(valueLabel, BorderLayout.EAST);
		return row;
	}

	private JPanel labeledToggleRow(String text, PillToggle toggle)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(getBackground());
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setPreferredSize(new Dimension(92, 16));
		row.add(l,      BorderLayout.WEST);
		row.add(toggle, BorderLayout.CENTER);
		return row;
	}

	// Uniform height for all checkbox rows — keeps glyph rows (taller content)
	// visually peer with text-only rows in BoxLayout.Y_AXIS.
	private static final int CHECKBOX_ROW_HEIGHT = 22;

	/** Same shape as labeledSliderRow but with a checkbox right-anchored in EAST. */
	private JPanel labeledCheckBoxRow(String text, JCheckBox box)
	{
		JPanel row = lockedHeightRow();
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setPreferredSize(new Dimension(92, 16));
		row.add(l,   BorderLayout.WEST);
		row.add(box, BorderLayout.EAST);
		return row;
	}

	/**
	 * Variant where the left "label area" is a custom component — used for rows
	 * with dynamic text + state-icon (e.g., "Draw tile under player" + icon).
	 */
	private JPanel labeledCheckBoxRow(JComponent labelArea, JCheckBox box)
	{
		JPanel row = lockedHeightRow();
		row.add(labelArea, BorderLayout.WEST);
		row.add(box,       BorderLayout.EAST);
		return row;
	}

	/** Row with BorderLayout + a locked height so all checkbox rows match. */
	private JPanel lockedHeightRow()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0))
		{
			@Override
			public Dimension getPreferredSize()
			{
				Dimension d = super.getPreferredSize();
				return new Dimension(d.width, CHECKBOX_ROW_HEIGHT);
			}
		};
		row.setBackground(getBackground());
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, CHECKBOX_ROW_HEIGHT));
		return row;
	}

	/**
	 * Build a label area: dynamic-text JLabel + state-icon, packed left-to-right.
	 * Uses BoxLayout.X_AXIS instead of FlowLayout to avoid FlowLayout's implicit
	 * hgap padding before the first child, which would indent the text by 6px
	 * relative to peer rows using plain JLabel labels.
	 */
	private JPanel inlineLabelArea(JLabel text, JComponent icon)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		p.setOpaque(false);
		p.setBackground(getBackground());
		p.add(text);
		p.add(Box.createHorizontalStrut(6));
		p.add(icon);
		return p;
	}

	/** Row-content JLabel — same font/colour as labeledSliderRow's WEST label. */
	private JLabel themedRowLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private void setBorderWidthControls(int v)
	{
		borderWidthSlider.setValue(v);
		borderWidthValueLabel.setText(valueFmt(v, "px"));
	}

	private void setOpacityControls(int v255)
	{
		int pct = Math.round(v255 / 2.55f);
		fillOpacitySlider.setValue(pct);
		fillOpacityValueLabel.setText(valueFmt(pct, "%"));
	}

	private static String drawBelowText(boolean below)
	{
		String word = below ? "under" : "above";
		return "<html>Draw tile <b><font color='#E4E4E4'>" + word + "</font></b> player</html>";
	}

	private static String orientText(boolean vertical)
	{
		String word = vertical ? "vertically" : "horizontally";
		return "<html>Orient <b><font color='#E4E4E4'>" + word + "</font></b></html>";
	}

	// ─── Slot abstraction ─────────────────────────────────────────────────

	private interface Slot
	{
		Color getColor();
		void applyColor(Color c);
	}

	// ─── Tick swatch ──────────────────────────────────────────────────────

	private class TickSwatch extends JPanel implements Slot
	{
		final int index;
		private Color   color;
		private boolean selected;

		TickSwatch(int index, Color color)
		{
			this.index = index;
			this.color = color;
			setPreferredSize(new Dimension(28, 30));
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Tick " + (index + 1));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					selectTickSlot(index);
				}
			});
		}

		@Override
		public Color getColor()
		{
			return color;
		}

		@Override
		public void applyColor(Color c)
		{
			color = c;
			plugin.setCustomPaletteColor(selectedCycle, index, c);
			repaint();
		}

		void setColor(Color c)
		{
			color = c;
			repaint();
		}

		void setSelected(boolean sel)
		{
			selected = sel;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();

			if (selected)
			{
				g2.setColor(ColorScheme.BRAND_ORANGE);
				g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.drawRoundRect(1, 1, w - 2, h - 2, 6, 6);
				g2.setColor(color);
				g2.fillRoundRect(3, 3, w - 6, h - 6, 4, 4);
			}
			else
			{
				g2.setColor(color);
				g2.fillRoundRect(0, 0, w, h, 6, 6);
				g2.setColor(new Color(0, 0, 0, 70));
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			}

			// Tick number — bold, contrast-aware
			Font bold = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
			g2.setFont(bold);
			String s    = String.valueOf(index + 1);
			double luma = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
			g2.setColor(luma > 140 ? Color.BLACK : Color.WHITE);
			int sw = g2.getFontMetrics().stringWidth(s);
			int sh = g2.getFontMetrics().getAscent();
			g2.drawString(s, (w - sw) / 2, (h + sh) / 2 - 2);
			g2.dispose();
		}
	}

	// ─── Static swatch ────────────────────────────────────────────────────

	private class StaticSwatch extends JPanel implements Slot
	{
		private final String  label;
		private final boolean border;
		private Color         color;
		private boolean       selected;

		StaticSwatch(String label, boolean border)
		{
			this.label  = label;
			this.border = border;
			this.color  = border ? plugin.getConfig().staticColor() : plugin.getConfig().staticFillColor();
			setPreferredSize(new Dimension(72, 48));
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Click to edit " + label.toLowerCase());
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					selectStaticSwatch(StaticSwatch.this);
				}
			});
		}

		@Override
		public Color getColor()
		{
			return color;
		}

		@Override
		public void applyColor(Color c)
		{
			Color withAlpha = new Color(c.getRed(), c.getGreen(), c.getBlue(), color.getAlpha());
			color = withAlpha;
			if (border)
			{
				plugin.setStaticColor(withAlpha);
			}
			else
			{
				plugin.setStaticFillColor(withAlpha);
			}
			repaint();
		}

		void setColor(Color c)
		{
			color = c;
			repaint();
		}

		void setSelected(boolean sel)
		{
			selected = sel;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			Color opaque = new Color(color.getRed(), color.getGreen(), color.getBlue());

			if (selected)
			{
				g2.setColor(ColorScheme.BRAND_ORANGE);
				g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.drawRoundRect(1, 1, w - 2, h - 2, 6, 6);
				g2.setColor(opaque);
				g2.fillRoundRect(3, 3, w - 6, h - 6, 4, 4);
			}
			else
			{
				g2.setColor(opaque);
				g2.fillRoundRect(0, 0, w, h, 6, 6);
				g2.setColor(new Color(0, 0, 0, 70));
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
			}

			g2.setFont(FontManager.getRunescapeSmallFont());
			double luma = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
			g2.setColor(luma > 140 ? Color.BLACK : Color.WHITE);
			int sw = g2.getFontMetrics().stringWidth(label);
			g2.drawString(label, (w - sw) / 2, h - 6);
			g2.dispose();
		}
	}

	// ─── Cycle tab button ─────────────────────────────────────────────────

	private class CycleTabButton extends JPanel
	{
		final int     cycle;
		private final JLabel label;
		private boolean tabSelected;
		private boolean tabEnabled = true;

		CycleTabButton(int cycle)
		{
			this.cycle = cycle;
			setLayout(new BorderLayout());
			setOpaque(false);
			setBorder(new EmptyBorder(4, 0, 4, 0));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			label = new JLabel(String.valueOf(cycle), SwingConstants.CENTER);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			add(label, BorderLayout.CENTER);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (!tabEnabled)
					{
						return;
					}
					if (cycle != selectedCycle)
					{
						plugin.setActiveCycle(cycle);
					}
				}
			});
		}

		void setTabSelected(boolean sel)
		{
			tabSelected = sel;
			applyLabelColor();
			repaint();
		}

		void setTabEnabled(boolean en)
		{
			tabEnabled = en;
			setCursor(en ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			applyLabelColor();
			repaint();
		}

		private void applyLabelColor()
		{
			if (!tabEnabled)
			{
				label.setForeground(TEXT_DIM);
			}
			else if (tabSelected)
			{
				label.setForeground(Color.BLACK);
			}
			else
			{
				label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Color fill;
			if (!tabEnabled)
			{
				fill = tabSelected ? new Color(0x5C3F1A) : new Color(0x252525);
			}
			else
			{
				fill = tabSelected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR;
			}
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
			g2.dispose();
		}
	}

	// ─── Pill toggle ──────────────────────────────────────────────────────

	private class PillToggle extends JPanel
	{
		// Fixed pill height so every PillToggle instance is the same height
		// regardless of label font. Without this, the runescape pixel font and
		// sans-serif (used for unicode glyphs) produce different preferred
		// heights and the toggles end up visually inconsistent.
		private static final int PILL_HEIGHT = 22;

		private final List<JLabel> pills = new ArrayList<>();
		private int selected = 0;
		private IntConsumer listener;

		PillToggle(String[] options)
		{
			setLayout(new GridLayout(1, options.length, 2, 0));
			setOpaque(false);
			setBorder(new EmptyBorder(2, 2, 2, 2));
			setMinimumSize(new Dimension(0, PILL_HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, PILL_HEIGHT));

			for (int i = 0; i < options.length; i++)
			{
				final int idx = i;
				JLabel pill = new JLabel(options[i], SwingConstants.CENTER)
				{
					final int pillIdx = idx;

					@Override
					protected void paintComponent(Graphics g)
					{
						if (pillIdx == selected)
						{
							Graphics2D g2 = (Graphics2D) g.create();
							g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
							g2.setColor(ColorScheme.BRAND_ORANGE);
							g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
							g2.dispose();
						}
						super.paintComponent(g);
					}
				};
				pill.setOpaque(false);
				pill.setFont(FontManager.getRunescapeSmallFont());
				pill.setBorder(new EmptyBorder(3, 6, 3, 6));
				pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				pill.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mousePressed(MouseEvent e)
					{
						if (idx != selected)
						{
							setSelected(idx);
							if (listener != null)
							{
								listener.accept(idx);
							}
						}
					}
				});
				pills.add(pill);
				add(pill);
			}
			applyStyles();
		}

		void setSelected(int idx)
		{
			selected = idx;
			applyStyles();
		}

		void addListener(IntConsumer l)
		{
			listener = l;
		}

		/** Override the pill label font — used when labels need unicode glyphs the runescape pixel font lacks. */
		void setPillFont(Font f)
		{
			for (JLabel pill : pills)
			{
				pill.setFont(f);
			}
		}

		/** Per-pill tooltips — handy when the visual label alone is ambiguous (e.g. ● vs 1). */
		void setPillTooltips(String[] tips)
		{
			for (int i = 0; i < pills.size() && i < tips.length; i++)
			{
				pills.get(i).setToolTipText(tips[i]);
			}
		}

		@Override
		public Dimension getPreferredSize()
		{
			// Width comes from the underlying layout; height is locked so all
			// pill toggles render at the same height regardless of label font.
			Dimension d = super.getPreferredSize();
			return new Dimension(d.width, PILL_HEIGHT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(TRACK_BG);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
			g2.dispose();
		}

		private void applyStyles()
		{
			for (int j = 0; j < pills.size(); j++)
			{
				pills.get(j).setForeground(j == selected ? Color.BLACK : ColorScheme.LIGHT_GRAY_COLOR);
				pills.get(j).repaint();
			}
			repaint();
		}
	}

	// ─── ChromaTick glyph (5 coloured bars) ──────────────────────────────

	private static class ChromaTickGlyph extends JPanel
	{
		private static final Color[] BANDS = {
			new Color(0xE9, 0x4B, 0x4B),
			new Color(0xF4, 0xD0, 0x3F),
			new Color(0x5B, 0xCB, 0x6A),
			new Color(0x3D, 0x8A, 0xE0),
			new Color(0xA4, 0x5C, 0xDB),
		};

		ChromaTickGlyph()
		{
			setPreferredSize(new Dimension(18, 13));
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int h = getHeight();
			for (int i = 0; i < BANDS.length; i++)
			{
				int bandH = h - (i % 2 == 0 ? 0 : 2);
				int y     = i % 2 == 0 ? 0 : 1;
				int x     = i * 4;
				g2.setColor(BANDS[i]);
				g2.fillRoundRect(x, y, 2, bandH, 2, 2);
			}
			g2.dispose();
		}
	}

	// ─── Hex display (coloured square + hex text) ─────────────────────────

	private class HexDisplay extends JPanel
	{
		private Color currentColor = Color.WHITE;

		HexDisplay()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(82, 14));
		}

		void setColor(Color c)
		{
			currentColor = c;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(currentColor);
			g2.fillRoundRect(0, 2, 10, 10, 2, 2);
			g2.setColor(new Color(0, 0, 0, 100));
			g2.setStroke(new BasicStroke(1f));
			g2.drawRoundRect(0, 2, 10, 10, 2, 2);
			g2.setFont(FontManager.getRunescapeSmallFont());
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			String hex = String.format("#%02X%02X%02X",
				currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue());
			g2.drawString(hex, 15, g2.getFontMetrics().getAscent() + 2);
			g2.dispose();
		}
	}

	// ─── Player tile glyph (tile over/under player indicator) ────────────

	private static class PlayerTileGlyph extends JPanel
	{
		private static final int[] TILE_X = {2, 11, 20, 11};
		private static final int[] TILE_Y = {12, 7, 12, 17};

		private boolean below;

		PlayerTileGlyph(boolean below)
		{
			this.below = below;
			setPreferredSize(new Dimension(22, 18));
			setOpaque(false);
		}

		void setBelow(boolean b)
		{
			below = b;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			if (below)
			{
				paintTile(g2, 0.55f);
				paintPlayer(g2);
			}
			else
			{
				paintPlayer(g2);
				paintTile(g2, 0.65f);
			}
			g2.dispose();
		}

		private void paintTile(Graphics2D g2, float alpha)
		{
			Polygon tile = new Polygon(TILE_X, TILE_Y, 4);
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			g2.setColor(ColorScheme.BRAND_ORANGE);
			g2.fillPolygon(tile);
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
			g2.setStroke(new BasicStroke(1f));
			g2.drawPolygon(tile);
		}

		private void paintPlayer(Graphics2D g2)
		{
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
			g2.setColor(TEXT_BRIGHT);
			g2.fillOval(9, 4, 4, 4);
			g2.fillRoundRect(9, 8, 4, 6, 1, 1);
		}
	}

	// ─── Fill tile glyph (outline-only ↔ filled) ──────────────────────────

	private static class FillTileGlyph extends JPanel
	{
		private static final int[] TILE_X = {2, 11, 20, 11};
		private static final int[] TILE_Y = {12, 7, 12, 17};

		private boolean filled;

		FillTileGlyph(boolean filled)
		{
			this.filled = filled;
			setPreferredSize(new Dimension(22, 18));
			setOpaque(false);
		}

		void setFilled(boolean f)
		{
			filled = f;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Polygon tile = new Polygon(TILE_X, TILE_Y, 4);
			if (filled)
			{
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
				g2.setColor(ColorScheme.BRAND_ORANGE);
				g2.fillPolygon(tile);
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
			}
			g2.setStroke(new BasicStroke(1f));
			g2.setColor(ColorScheme.BRAND_ORANGE);
			g2.drawPolygon(tile);
			g2.dispose();
		}
	}

	// ─── HUD orientation glyph (3 dots horizontal ↔ vertical) ──────────────

	private static class HudOrientGlyph extends JPanel
	{
		private boolean vertical;

		HudOrientGlyph(boolean vertical)
		{
			this.vertical = vertical;
			setPreferredSize(new Dimension(20, 18));
			setOpaque(false);
		}

		void setVertical(boolean v)
		{
			vertical = v;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.BRAND_ORANGE);
			int cx = getWidth() / 2;
			int cy = getHeight() / 2;
			final int dotR = 2;
			final int spacing = 5;
			for (int i = -1; i <= 1; i++)
			{
				int x = vertical ? cx               : cx + i * spacing;
				int y = vertical ? cy + i * spacing : cy;
				g2.fillOval(x - dotR, y - dotR, dotR * 2, dotR * 2);
			}
			g2.dispose();
		}
	}

	// ─── Card layout that sizes to the visible card only ──────────────────
	// Stock CardLayout reports max-of-all-cards for its preferred size, which
	// leaves dead space below the smaller (GRID) card. This subclass returns
	// only the currently-visible child's preferred/minimum size.

	private static class DynamicCardLayout extends CardLayout
	{
		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			return sizeOfVisible(parent, true);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			return sizeOfVisible(parent, false);
		}

		private Dimension sizeOfVisible(Container parent, boolean preferred)
		{
			synchronized (parent.getTreeLock())
			{
				for (Component c : parent.getComponents())
				{
					if (c.isVisible())
					{
						Insets insets = parent.getInsets();
						Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
						return new Dimension(
							d.width  + insets.left + insets.right,
							d.height + insets.top  + insets.bottom);
					}
				}
				return preferred ? super.preferredLayoutSize(parent) : super.minimumLayoutSize(parent);
			}
		}
	}
}
