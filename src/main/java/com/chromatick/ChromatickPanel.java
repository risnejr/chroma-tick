package com.chromatick;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
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

	// Design tokens
	private static final Color TRACK_BG  = new Color(0x1A1A1A);
	private static final Color TEXT_DIM  = new Color(0x6E6E6E);
	private static final Color TEXT_BRIGHT = new Color(0xE4E4E4);
	private static final Color DIVIDER   = new Color(0x3A3A3A);

	private final ChromatickPlugin plugin;

	// Mode toggle
	private final PillToggle modeToggle;

	// Cycle section
	private final JPanel cycleSection;
	private final JPanel cycleTabs;
	private final JPanel swatchRow;
	private final List<CycleTabButton> tabButtons   = new ArrayList<>();
	private final List<TickSwatch>     tickSwatches = new ArrayList<>();

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

	// Appearance
	private final JSlider   borderWidthSlider;
	private final JLabel    borderWidthValueLabel;
	private final JCheckBox fillEnableBox;
	private final JSlider   fillOpacitySlider;
	private final JLabel    fillOpacityValueLabel;
	private final JCheckBox drawBelowBox;
	private final PlayerTileGlyph tileGlyph;

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

		// ─── Mode toggle ─────────────────────────────────────────────────
		modeToggle = new PillToggle(new String[]{"Cycle", "Static"});
		modeToggle.setAlignmentX(CENTER_ALIGNMENT);
		modeToggle.addListener(idx -> plugin.setStaticMode(idx == 1));
		content.add(modeToggle);
		content.add(Box.createVerticalStrut(10));

		// ─── Cycle section ───────────────────────────────────────────────
		cycleSection = column();
		cycleSection.add(labelRow("Cycle length", null));
		cycleSection.add(Box.createVerticalStrut(4));

		cycleTabs = new JPanel(new GridLayout(1, MAX_CYCLE - MIN_CYCLE + 1, 2, 0));
		cycleTabs.setBackground(getBackground());
		cycleTabs.setAlignmentX(CENTER_ALIGNMENT);
		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			CycleTabButton btn = new CycleTabButton(n);
			tabButtons.add(btn);
			cycleTabs.add(btn);
		}
		cycleSection.add(cycleTabs);
		cycleSection.add(Box.createVerticalStrut(8));

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

		pickerCard = new JPanel(new CardLayout());
		pickerCard.setBackground(getBackground());
		pickerCard.setAlignmentX(CENTER_ALIGNMENT);
		// Grid fills full width; wheel is centered via FlowLayout
		pickerCard.add(discrete, CARD_GRID);
		pickerCard.add(wheel, CARD_WHEEL);
		content.add(pickerCard);
		content.add(Box.createVerticalStrut(4));

		pickerToggle.addListener(idx -> {
			String mode = idx == 0 ? CARD_GRID : CARD_WHEEL;
			plugin.setPaletteMode(mode);
			((CardLayout) pickerCard.getLayout()).show(pickerCard, mode);
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

		// ─── Thin divider ─────────────────────────────────────────────────
		content.add(Box.createVerticalStrut(10));
		JPanel divider = new JPanel();
		divider.setBackground(DIVIDER);
		divider.setOpaque(true);
		divider.setMinimumSize(new Dimension(0, 1));
		divider.setPreferredSize(new Dimension(0, 1));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		divider.setAlignmentX(CENTER_ALIGNMENT);
		content.add(divider);
		content.add(Box.createVerticalStrut(6));

		// ─── Appearance (always visible) ──────────────────────────────────
		JPanel appearanceHeader = new JPanel(new BorderLayout());
		appearanceHeader.setBackground(getBackground());
		appearanceHeader.setAlignmentX(CENTER_ALIGNMENT);
		appearanceHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		appearanceHeader.add(sectionLabel("Appearance"), BorderLayout.WEST);
		content.add(appearanceHeader);
		content.add(Box.createVerticalStrut(4));

		JPanel appearanceBody = new JPanel();
		appearanceBody.setLayout(new BoxLayout(appearanceBody, BoxLayout.Y_AXIS));
		appearanceBody.setBackground(getBackground());
		appearanceBody.setAlignmentX(CENTER_ALIGNMENT);

		// Border width
		borderWidthSlider     = themedSlider(1, 5);
		borderWidthValueLabel = compactValueLabel();
		borderWidthSlider.addChangeListener(e -> {
			int v = borderWidthSlider.getValue();
			borderWidthValueLabel.setText(valueFmt(v, "px"));
			plugin.setBorderWidth(v);
		});
		appearanceBody.add(labeledSliderRow("Border width", borderWidthSlider, borderWidthValueLabel));

		// Fill enable
		fillEnableBox = themedCheckBox("Fill tile");
		appearanceBody.add(fillEnableBox);

		// Fill opacity
		fillOpacitySlider     = themedSlider(0, 100);
		fillOpacityValueLabel = compactValueLabel();
		fillOpacitySlider.addChangeListener(e -> {
			int pct  = fillOpacitySlider.getValue();
			int v255 = Math.round(pct * 2.55f);
			fillOpacityValueLabel.setText(valueFmt(pct, "%"));
			plugin.setFillOpacity(v255);
		});
		fillEnableBox.addActionListener(e -> {
			boolean on = fillEnableBox.isSelected();
			plugin.setEnableFillColor(on);
			fillOpacitySlider.setEnabled(on);
			fillOpacityValueLabel.setEnabled(on);
		});
		appearanceBody.add(labeledSliderRow("Fill opacity", fillOpacitySlider, fillOpacityValueLabel));

		// Draw tile row: checkbox (dynamic text) + tile glyph
		ChromatickConfig cfg = plugin.getConfig();
		drawBelowBox = themedCheckBox(drawBelowText(cfg.drawBelowPlayer()));
		drawBelowBox.setToolTipText("Requires GPU rendering mode in RuneLite settings");
		tileGlyph = new PlayerTileGlyph(cfg.drawBelowPlayer());
		drawBelowBox.addActionListener(e -> {
			boolean below = drawBelowBox.isSelected();
			plugin.setDrawBelowPlayer(below);
			drawBelowBox.setText(drawBelowText(below));
			tileGlyph.setBelow(below);
		});
		JPanel drawBelowRow = new JPanel(new BorderLayout(6, 0));
		drawBelowRow.setBackground(getBackground());
		drawBelowRow.setAlignmentX(LEFT_ALIGNMENT);
		drawBelowRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		drawBelowRow.add(drawBelowBox, BorderLayout.CENTER);
		drawBelowRow.add(tileGlyph, BorderLayout.EAST);
		appearanceBody.add(drawBelowRow);

		content.add(appearanceBody);
		add(content, BorderLayout.NORTH);

		// ─── Initial state ────────────────────────────────────────────────
		modeToggle.setSelected(cfg.staticMode() ? 1 : 0);
		pickerToggle.setSelected(CARD_WHEEL.equals(cfg.paletteMode()) ? 1 : 0);
		((CardLayout) pickerCard.getLayout()).show(pickerCard, cfg.paletteMode());
		sequentialFill.setSelected(cfg.sequentialFill());
		setBorderWidthControls((int) Math.round(cfg.tileBorderWidth()));
		fillEnableBox.setSelected(cfg.enableFillColor());
		setOpacityControls(cfg.fillOpacity());
		fillOpacitySlider.setEnabled(cfg.enableFillColor());
		fillOpacityValueLabel.setEnabled(cfg.enableFillColor());
		drawBelowBox.setSelected(cfg.drawBelowPlayer());

		selectedCycle = clampCycle(plugin.getEffectiveCycleLength());
		applyModeVisibility(cfg.staticMode());
		highlightActiveCycleTab();
		rebuildTickSwatches();
		selectInitialEditingSlot();
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
		setOpacityControls(cfg.fillOpacity());
		fillOpacitySlider.setEnabled(cfg.enableFillColor());
		fillOpacityValueLabel.setEnabled(cfg.enableFillColor());

		boolean below = cfg.drawBelowPlayer();
		drawBelowBox.setSelected(below);
		drawBelowBox.setText(drawBelowText(below));
		tileGlyph.setBelow(below);
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

	private JSlider themedSlider(int min, int max)
	{
		JSlider s = new JSlider(min, max);
		s.setBackground(getBackground());
		s.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		s.setOpaque(false);
		return s;
	}

	private JLabel compactValueLabel()
	{
		JLabel l = new JLabel();
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setHorizontalAlignment(SwingConstants.RIGHT);
		l.setPreferredSize(new Dimension(40, 16));
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
		l.setPreferredSize(new Dimension(72, 16));
		row.add(l,          BorderLayout.WEST);
		row.add(slider,     BorderLayout.CENTER);
		row.add(valueLabel, BorderLayout.EAST);
		return row;
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
			label.setForeground(sel ? Color.BLACK : ColorScheme.LIGHT_GRAY_COLOR);
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(tabSelected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
			g2.dispose();
		}
	}

	// ─── Pill toggle ──────────────────────────────────────────────────────

	private class PillToggle extends JPanel
	{
		private final List<JLabel> pills = new ArrayList<>();
		private int selected = 0;
		private IntConsumer listener;

		PillToggle(String[] options)
		{
			setLayout(new GridLayout(1, options.length, 2, 0));
			setOpaque(false);
			setBorder(new EmptyBorder(2, 2, 2, 2));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

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
}
