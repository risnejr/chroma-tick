package com.chromatick;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
 * Sidebar panel. Top-to-bottom:
 *   - Title
 *   - Cycle / Static mode pill toggle
 *   - Cycle tabs (cycle mode) OR static border/fill swatches (static mode)
 *   - Tick swatch grid (cycle mode only) with inline "Reset" link
 *   - Picker section: grid | wheel toggle, picker, sequential-fill checkbox, hex
 *   - Collapsible "Appearance" section: border width, fill on/off, opacity, draw-below-player
 *
 * The cycle tabs drive the active in-game cycle (writes config.cycleLength
 * via plugin.setActiveCycle), so panel selection == active cycle by design.
 */
class ChromatickPanel extends PluginPanel
{
	private static final int MIN_CYCLE = 2;
	private static final int MAX_CYCLE = 10;
	private static final String CARD_GRID = "grid";
	private static final String CARD_WHEEL = "wheel";

	private final ChromatickPlugin plugin;

	// Top
	private final PillToggle modeToggle;

	// Cycle-mode section
	private final JPanel cycleSection;
	private final JPanel cycleTabs;
	private final JPanel swatchRow;
	private final List<CycleTabButton> tabButtons = new ArrayList<>();
	private final List<TickSwatch> tickSwatches = new ArrayList<>();

	// Static-mode section
	private final JPanel staticSection;
	private final StaticSwatch staticBorderSwatch;
	private final StaticSwatch staticFillSwatch;

	// Picker
	private final PillToggle pickerToggle;
	private final DiscretePalette discrete;
	private final ColorWheelPicker wheel;
	private final JPanel pickerCard;
	private final JCheckBox sequentialFill;
	private final JLabel hexLabel;

	// Appearance
	private final CollapsibleSection appearanceSection;
	private final JSlider borderWidthSlider;
	private final JCheckBox fillEnableBox;
	private final JSlider fillOpacitySlider;
	private final JCheckBox drawBelowBox;

	private int selectedCycle;
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

		// ─── Title ───
		JLabel title = new JLabel("ChromaTick");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(CENTER_ALIGNMENT);
		content.add(title);
		content.add(Box.createVerticalStrut(8));

		// ─── Mode toggle ───
		modeToggle = new PillToggle(new String[]{"Cycle", "Static"});
		modeToggle.setAlignmentX(CENTER_ALIGNMENT);
		modeToggle.addListener(idx -> {
			plugin.setStaticMode(idx == 1);
			// onConfigChanged → refreshFromConfig() will update visibility
		});
		content.add(modeToggle);
		content.add(Box.createVerticalStrut(10));

		// ─── Cycle section ───
		cycleSection = column();
		JLabel cycleLabel = sectionLabel("Cycle length");
		cycleSection.add(cycleLabel);
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

		// Tick colors header (label + inline reset)
		JPanel ticksHeader = new JPanel(new BorderLayout());
		ticksHeader.setBackground(getBackground());
		ticksHeader.setAlignmentX(CENTER_ALIGNMENT);
		ticksHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		JLabel ticksLabel = sectionLabel("Tick colors");
		ticksLabel.setHorizontalAlignment(SwingConstants.LEFT);
		ticksLabel.setAlignmentX(LEFT_ALIGNMENT);
		JLabel resetLink = new JLabel("Reset");
		resetLink.setFont(FontManager.getRunescapeSmallFont());
		resetLink.setForeground(ColorScheme.BRAND_ORANGE);
		resetLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		resetLink.setToolTipText("Reset this cycle's colors to defaults");
		resetLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				plugin.resetCustomPaletteForCycle(selectedCycle);
				rebuildTickSwatches();
				selectFirstTickSlot();
			}
		});
		ticksHeader.add(ticksLabel, BorderLayout.WEST);
		ticksHeader.add(resetLink, BorderLayout.EAST);
		cycleSection.add(ticksHeader);
		cycleSection.add(Box.createVerticalStrut(4));

		swatchRow = new JPanel();
		swatchRow.setBackground(getBackground());
		swatchRow.setAlignmentX(CENTER_ALIGNMENT);
		cycleSection.add(swatchRow);
		content.add(cycleSection);

		// ─── Static section ───
		staticSection = column();
		staticSection.add(sectionLabel("Static colors"));
		staticSection.add(Box.createVerticalStrut(4));
		JPanel staticRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
		staticRow.setBackground(getBackground());
		staticBorderSwatch = new StaticSwatch("Border", true);
		staticFillSwatch = new StaticSwatch("Fill", false);
		staticRow.add(staticBorderSwatch);
		staticRow.add(staticFillSwatch);
		staticSection.add(staticRow);
		content.add(staticSection);

		content.add(Box.createVerticalStrut(10));

		// ─── Picker ───
		JPanel pickerHeader = new JPanel(new BorderLayout());
		pickerHeader.setBackground(getBackground());
		pickerHeader.setAlignmentX(CENTER_ALIGNMENT);
		pickerHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		pickerHeader.add(sectionLabel("Palette"), BorderLayout.WEST);
		pickerToggle = new PillToggle(new String[]{"Grid", "Wheel"});
		pickerToggle.addListener(idx -> {
			String mode = idx == 0 ? CARD_GRID : CARD_WHEEL;
			plugin.setPaletteMode(mode);
			((CardLayout) pickerCard.getLayout()).show(pickerCard, mode);
			syncPickerToEditingSlot();
		});
		pickerHeader.add(pickerToggle, BorderLayout.EAST);
		content.add(pickerHeader);
		content.add(Box.createVerticalStrut(4));

		discrete = new DiscretePalette();
		wheel = new ColorWheelPicker();
		discrete.addColorListener(this::onPickerColor);
		discrete.addCommitListener(this::onPickerCommit);
		wheel.addColorListener(this::onPickerColor);
		wheel.addCommitListener(this::onPickerCommit);

		pickerCard = new JPanel(new CardLayout());
		pickerCard.setBackground(getBackground());
		pickerCard.setAlignmentX(CENTER_ALIGNMENT);
		// Wrap each card so it centers in the panel column
		pickerCard.add(centerWrap(discrete), CARD_GRID);
		pickerCard.add(centerWrap(wheel), CARD_WHEEL);
		content.add(pickerCard);
		content.add(Box.createVerticalStrut(4));

		// Hex + sequential fill on one row
		JPanel underPicker = new JPanel(new BorderLayout());
		underPicker.setBackground(getBackground());
		underPicker.setAlignmentX(CENTER_ALIGNMENT);
		underPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		hexLabel = new JLabel("#FFFFFF");
		hexLabel.setFont(FontManager.getRunescapeSmallFont());
		hexLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sequentialFill = new JCheckBox("Sequential");
		sequentialFill.setFont(FontManager.getRunescapeSmallFont());
		sequentialFill.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sequentialFill.setBackground(getBackground());
		sequentialFill.setToolTipText("Auto-advance to the next tick slot after each pick");
		sequentialFill.addActionListener(e -> plugin.setSequentialFill(sequentialFill.isSelected()));
		underPicker.add(hexLabel, BorderLayout.WEST);
		underPicker.add(sequentialFill, BorderLayout.EAST);
		content.add(underPicker);
		content.add(Box.createVerticalStrut(10));

		// ─── Appearance (collapsible) ───
		JPanel appearanceBody = new JPanel();
		appearanceBody.setLayout(new BoxLayout(appearanceBody, BoxLayout.Y_AXIS));
		appearanceBody.setBackground(getBackground());

		borderWidthSlider = themedSlider(1, 5);
		borderWidthSlider.addChangeListener(e -> plugin.setBorderWidth(borderWidthSlider.getValue()));
		appearanceBody.add(labeledRow("Border width", borderWidthSlider));

		fillEnableBox = themedCheckBox("Fill tile");
		fillEnableBox.addActionListener(e -> {
			plugin.setEnableFillColor(fillEnableBox.isSelected());
			fillOpacitySlider.setEnabled(fillEnableBox.isSelected());
		});
		appearanceBody.add(fillEnableBox);

		fillOpacitySlider = themedSlider(0, 255);
		fillOpacitySlider.addChangeListener(e -> plugin.setFillOpacity(fillOpacitySlider.getValue()));
		appearanceBody.add(labeledRow("Fill opacity", fillOpacitySlider));

		drawBelowBox = themedCheckBox("Draw below player");
		drawBelowBox.setToolTipText("Requires GPU rendering mode in RuneLite settings");
		drawBelowBox.addActionListener(e -> plugin.setDrawBelowPlayer(drawBelowBox.isSelected()));
		appearanceBody.add(drawBelowBox);

		appearanceSection = new CollapsibleSection("Appearance", appearanceBody);
		appearanceSection.setAlignmentX(CENTER_ALIGNMENT);
		content.add(appearanceSection);

		add(content, BorderLayout.NORTH);

		// Initial state from config
		ChromatickConfig cfg = plugin.getConfig();
		modeToggle.setSelected(cfg.staticMode() ? 1 : 0);
		pickerToggle.setSelected(CARD_WHEEL.equals(cfg.paletteMode()) ? 1 : 0);
		((CardLayout) pickerCard.getLayout()).show(pickerCard, cfg.paletteMode());
		sequentialFill.setSelected(cfg.sequentialFill());
		borderWidthSlider.setValue((int) Math.round(cfg.tileBorderWidth()));
		fillEnableBox.setSelected(cfg.enableFillColor());
		fillOpacitySlider.setValue(cfg.fillOpacity());
		fillOpacitySlider.setEnabled(cfg.enableFillColor());
		drawBelowBox.setSelected(cfg.drawBelowPlayer());

		selectedCycle = clampCycle(plugin.getEffectiveCycleLength());
		applyModeVisibility(cfg.staticMode());
		highlightActiveCycleTab();
		rebuildTickSwatches();
		selectInitialEditingSlot();
	}

	// ─── Public sync hooks (called by plugin) ─────────────────────────────

	void refreshFromConfig()
	{
		ChromatickConfig cfg = plugin.getConfig();
		boolean isStatic = cfg.staticMode();
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

		// Refresh static swatch colors from config
		staticBorderSwatch.setColor(cfg.staticColor());
		staticFillSwatch.setColor(cfg.staticFillColor());

		// Appearance values may have changed externally
		borderWidthSlider.setValue((int) Math.round(cfg.tileBorderWidth()));
		fillEnableBox.setSelected(cfg.enableFillColor());
		fillOpacitySlider.setValue(cfg.fillOpacity());
		fillOpacitySlider.setEnabled(cfg.enableFillColor());
		drawBelowBox.setSelected(cfg.drawBelowPlayer());
	}

	/** Called when a palette JSON config changed; only refreshes if relevant. */
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

	// ─── Picker event handlers ────────────────────────────────────────────

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

	// ─── Mode / selection state ───────────────────────────────────────────

	private void applyModeVisibility(boolean isStatic)
	{
		cycleSection.setVisible(!isStatic);
		staticSection.setVisible(isStatic);
		sequentialFill.setEnabled(!isStatic);
		if (isStatic)
		{
			// Editing target defaults to the border swatch in static mode
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
			b.setSelected(b.cycle == selectedCycle);
		}
	}

	private void rebuildTickSwatches()
	{
		swatchRow.removeAll();
		tickSwatches.clear();
		Color[] palette = plugin.getCustomPaletteForCycle(selectedCycle);
		int perRow = Math.min(selectedCycle, 5);
		int rows = (int) Math.ceil(selectedCycle / (double) perRow);
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
		hexLabel.setText(String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
	}

	private static int clampCycle(int n)
	{
		return Math.max(MIN_CYCLE, Math.min(MAX_CYCLE, n));
	}

	// ─── Small layout helpers ─────────────────────────────────────────────

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
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(CENTER_ALIGNMENT);
		return l;
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

	private JPanel labeledRow(String label, JSlider slider)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(getBackground());
		row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel l = new JLabel(label);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setPreferredSize(new Dimension(80, 16));
		row.add(l, BorderLayout.WEST);
		row.add(slider, BorderLayout.CENTER);
		return row;
	}

	// ─── Slot abstraction ────────────────────────────────────────────────

	private interface Slot
	{
		Color getColor();
		void applyColor(Color c);
	}

	// ─── Tick swatch (one cell of the cycle palette) ─────────────────────

	private class TickSwatch extends JPanel implements Slot
	{
		final int index;
		private Color color;
		private boolean selected;

		TickSwatch(int index, Color color)
		{
			this.index = index;
			this.color = color;
			setPreferredSize(new Dimension(28, 28));
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Tick " + (index + 1));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
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
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			int pad = selected ? 3 : 2;
			g2.setColor(color);
			g2.fillRoundRect(pad, pad, w - pad * 2, h - pad * 2, 6, 6);
			g2.setColor(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR.darker());
			g2.setStroke(new java.awt.BasicStroke(selected ? 2f : 1f));
			g2.drawRoundRect(pad, pad, w - pad * 2 - 1, h - pad * 2 - 1, 6, 6);
			String s = String.valueOf(index + 1);
			g2.setFont(FontManager.getRunescapeSmallFont());
			int sw = g2.getFontMetrics().stringWidth(s);
			int sh = g2.getFontMetrics().getAscent();
			double luma = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
			g2.setColor(luma > 140 ? Color.BLACK : Color.WHITE);
			g2.drawString(s, (w - sw) / 2, (h + sh) / 2 - 2);
			g2.dispose();
		}
	}

	// ─── Static color swatch ─────────────────────────────────────────────

	private class StaticSwatch extends JPanel implements Slot
	{
		private final String label;
		private final boolean border;
		private Color color;
		private boolean selected;

		StaticSwatch(String label, boolean border)
		{
			this.label = label;
			this.border = border;
			this.color = border ? plugin.getConfig().staticColor() : plugin.getConfig().staticFillColor();
			setPreferredSize(new Dimension(72, 48));
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Click to edit " + label.toLowerCase());
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
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
			// Preserve previous alpha so the user keeps their existing transparency.
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
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			int pad = selected ? 3 : 2;
			Color opaque = new Color(color.getRed(), color.getGreen(), color.getBlue());
			g2.setColor(opaque);
			g2.fillRoundRect(pad, pad, w - pad * 2, h - pad * 2, 6, 6);
			g2.setColor(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR.darker());
			g2.setStroke(new java.awt.BasicStroke(selected ? 2f : 1f));
			g2.drawRoundRect(pad, pad, w - pad * 2 - 1, h - pad * 2 - 1, 6, 6);
			g2.setFont(FontManager.getRunescapeSmallFont());
			int sw = g2.getFontMetrics().stringWidth(label);
			double luma = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
			g2.setColor(luma > 140 ? Color.BLACK : Color.WHITE);
			g2.drawString(label, (w - sw) / 2, h - 6);
			g2.dispose();
		}
	}

	// ─── Cycle tab button ────────────────────────────────────────────────

	private class CycleTabButton extends JPanel
	{
		final int cycle;
		private final JLabel label;

		CycleTabButton(int cycle)
		{
			this.cycle = cycle;
			setLayout(new BorderLayout());
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(new EmptyBorder(4, 0, 4, 0));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			label = new JLabel(String.valueOf(cycle), SwingConstants.CENTER);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			add(label, BorderLayout.CENTER);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (cycle != selectedCycle)
					{
						plugin.setActiveCycle(cycle);
						// onConfigChanged → refreshFromConfig will sync the panel
					}
				}
			});
		}

		void setSelected(boolean sel)
		{
			setBackground(sel ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
			label.setForeground(sel ? Color.BLACK : ColorScheme.LIGHT_GRAY_COLOR);
			repaint();
		}
	}

	// ─── Pill toggle (segmented control) ─────────────────────────────────

	private class PillToggle extends JPanel
	{
		private final List<JLabel> pills = new ArrayList<>();
		private int selected = 0;
		private IntConsumer listener;

		PillToggle(String[] options)
		{
			setLayout(new GridLayout(1, options.length, 2, 0));
			setBackground(getBackground());
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
			for (int i = 0; i < options.length; i++)
			{
				final int idx = i;
				JLabel pill = new JLabel(options[i], SwingConstants.CENTER);
				pill.setOpaque(true);
				pill.setFont(FontManager.getRunescapeSmallFont());
				pill.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				pill.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				pill.setBorder(new EmptyBorder(5, 8, 5, 8));
				pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				pill.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
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

		private void applyStyles()
		{
			for (int i = 0; i < pills.size(); i++)
			{
				JLabel p = pills.get(i);
				if (i == selected)
				{
					p.setBackground(ColorScheme.BRAND_ORANGE);
					p.setForeground(Color.BLACK);
				}
				else
				{
					p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
					p.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				}
			}
			repaint();
		}
	}

	// ─── Collapsible section ─────────────────────────────────────────────

	private class CollapsibleSection extends JPanel
	{
		private final JLabel header;
		private final JPanel body;
		private boolean expanded = false;

		CollapsibleSection(String title, JPanel content)
		{
			setLayout(new BorderLayout());
			setBackground(getBackground());
			setBorder(new EmptyBorder(4, 0, 4, 0));

			header = new JLabel("▸ " + title);
			header.setFont(FontManager.getRunescapeSmallFont());
			header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			header.setBorder(new EmptyBorder(4, 0, 4, 0));
			header.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					setExpanded(!expanded);
				}
			});

			body = content;
			body.setVisible(false);

			add(header, BorderLayout.NORTH);
			add(body, BorderLayout.CENTER);
		}

		void setExpanded(boolean exp)
		{
			expanded = exp;
			header.setText((exp ? "▾ " : "▸ ") + header.getText().substring(2));
			header.setForeground(exp ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
			body.setVisible(exp);
			revalidate();
		}
	}
}
