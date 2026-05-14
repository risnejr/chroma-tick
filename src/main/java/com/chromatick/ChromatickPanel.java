package com.chromatick;

import java.awt.BorderLayout;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel for picking per-cycle colors using a continuous color wheel.
 *
 * Layout (top → bottom):
 *   - Color wheel (HSV hue ring + SV square)
 *   - Sequential / Targeted fill toggle
 *   - Cycle length tabs (2..10)
 *   - Per-tick color swatches for the selected cycle
 *   - Reset button for the current cycle
 */
class ChromatickPanel extends PluginPanel
{
	private static final int MIN_CYCLE = 2;
	private static final int MAX_CYCLE = 10;

	private final ChromatickPlugin plugin;

	private final ColorWheelPicker wheel;
	private final JCheckBox sequentialFill;
	private final JPanel cycleTabs;
	private final JPanel swatchRow;
	private final JLabel currentColorLabel;
	private final JLabel hexLabel;

	private final List<CycleTabButton> tabButtons = new ArrayList<>();
	private final List<SwatchButton> swatches = new ArrayList<>();

	private int selectedCycle = 4;
	private int selectedSlot = 0;

	ChromatickPanel(ChromatickPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(getBackground());

		// Title
		JLabel title = new JLabel("ChromaTick");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(CENTER_ALIGNMENT);
		content.add(title);
		content.add(Box.createVerticalStrut(8));

		// Color wheel
		wheel = new ColorWheelPicker();
		JPanel wheelHolder = new JPanel();
		wheelHolder.setBackground(getBackground());
		wheelHolder.add(wheel);
		wheelHolder.setAlignmentX(CENTER_ALIGNMENT);
		content.add(wheelHolder);

		// Current color preview + hex
		JPanel preview = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		preview.setBackground(getBackground());
		currentColorLabel = new JLabel();
		currentColorLabel.setOpaque(true);
		currentColorLabel.setPreferredSize(new Dimension(28, 18));
		currentColorLabel.setBorder(new LineBorder(ColorScheme.LIGHT_GRAY_COLOR, 1));
		hexLabel = new JLabel("#FFFFFF");
		hexLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hexLabel.setFont(FontManager.getRunescapeSmallFont());
		preview.add(currentColorLabel);
		preview.add(hexLabel);
		preview.setAlignmentX(CENTER_ALIGNMENT);
		content.add(preview);
		content.add(Box.createVerticalStrut(8));

		// Fill mode toggle
		sequentialFill = new JCheckBox("Sequential fill");
		sequentialFill.setToolTipText("On: each pick auto-advances to the next tick slot. "
			+ "Off: pick edits the highlighted slot only.");
		sequentialFill.setBackground(getBackground());
		sequentialFill.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sequentialFill.setFont(FontManager.getRunescapeSmallFont());
		sequentialFill.setAlignmentX(CENTER_ALIGNMENT);
		sequentialFill.setSelected(true);
		content.add(sequentialFill);
		content.add(Box.createVerticalStrut(10));

		// Section: Cycle
		JLabel cycleLabel = new JLabel("Cycle length");
		cycleLabel.setFont(FontManager.getRunescapeSmallFont());
		cycleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		cycleLabel.setAlignmentX(CENTER_ALIGNMENT);
		content.add(cycleLabel);
		content.add(Box.createVerticalStrut(4));

		cycleTabs = new JPanel(new GridLayout(1, MAX_CYCLE - MIN_CYCLE + 1, 2, 0));
		cycleTabs.setBackground(getBackground());
		cycleTabs.setAlignmentX(CENTER_ALIGNMENT);
		for (int n = MIN_CYCLE; n <= MAX_CYCLE; n++)
		{
			CycleTabButton btn = new CycleTabButton(n);
			tabButtons.add(btn);
			cycleTabs.add(btn);
		}
		content.add(cycleTabs);
		content.add(Box.createVerticalStrut(8));

		// Section: Swatches
		JLabel ticksLabel = new JLabel("Tick colors");
		ticksLabel.setFont(FontManager.getRunescapeSmallFont());
		ticksLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		ticksLabel.setAlignmentX(CENTER_ALIGNMENT);
		content.add(ticksLabel);
		content.add(Box.createVerticalStrut(4));

		swatchRow = new JPanel();
		swatchRow.setBackground(getBackground());
		swatchRow.setAlignmentX(CENTER_ALIGNMENT);
		content.add(swatchRow);
		content.add(Box.createVerticalStrut(10));

		// Reset
		JButton reset = new JButton("Reset cycle to defaults");
		reset.setFocusable(false);
		reset.setAlignmentX(CENTER_ALIGNMENT);
		reset.addActionListener(e -> {
			plugin.resetCustomPaletteForCycle(selectedCycle);
			rebuildSwatches();
			syncWheelToSelectedSlot();
		});
		content.add(reset);

		add(content, BorderLayout.NORTH);

		wheel.addColorListener(this::onWheelColor);
		wheel.addCommitListener(this::onWheelCommit);

		selectCycle(plugin.getEffectiveCycleLength());
	}

	void refreshFromConfig()
	{
		int cycle = plugin.getEffectiveCycleLength();
		if (cycle != selectedCycle)
		{
			selectCycle(cycle);
		}
		else
		{
			rebuildSwatches();
			syncWheelToSelectedSlot();
		}
	}

	private void onWheelColor(Color c)
	{
		updatePreview(c);
		if (selectedSlot < 0 || selectedSlot >= selectedCycle)
		{
			return;
		}
		plugin.setCustomPaletteColor(selectedCycle, selectedSlot, c);
		swatches.get(selectedSlot).setColor(c);
	}

	/** Called when the user releases the mouse — advances slot in sequential mode. */
	private void onWheelCommit()
	{
		if (sequentialFill.isSelected())
		{
			int next = (selectedSlot + 1) % selectedCycle;
			selectSlot(next);
		}
	}

	private void updatePreview(Color c)
	{
		currentColorLabel.setBackground(c);
		hexLabel.setText(String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
	}

	private void selectCycle(int n)
	{
		selectedCycle = Math.max(MIN_CYCLE, Math.min(MAX_CYCLE, n));
		for (CycleTabButton b : tabButtons)
		{
			b.setSelected(b.cycle == selectedCycle);
		}
		selectedSlot = 0;
		rebuildSwatches();
		syncWheelToSelectedSlot();
	}

	private void rebuildSwatches()
	{
		swatchRow.removeAll();
		swatches.clear();
		Color[] palette = plugin.getCustomPaletteForCycle(selectedCycle);
		// Use grid to keep swatches uniform; max 5 per row for readability
		int perRow = Math.min(selectedCycle, 5);
		int rows = (int) Math.ceil(selectedCycle / (double) perRow);
		swatchRow.setLayout(new GridLayout(rows, perRow, 4, 4));
		for (int i = 0; i < selectedCycle; i++)
		{
			SwatchButton s = new SwatchButton(i, palette[i]);
			s.setSelected(i == selectedSlot);
			swatches.add(s);
			swatchRow.add(s);
		}
		swatchRow.revalidate();
		swatchRow.repaint();
	}

	private void selectSlot(int slot)
	{
		selectedSlot = slot;
		for (int i = 0; i < swatches.size(); i++)
		{
			swatches.get(i).setSelected(i == selectedSlot);
		}
		syncWheelToSelectedSlot();
	}

	private void syncWheelToSelectedSlot()
	{
		if (selectedSlot < 0 || selectedSlot >= swatches.size())
		{
			return;
		}
		Color c = swatches.get(selectedSlot).color;
		wheel.setColorSilent(c);
		updatePreview(c);
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
					selectCycle(cycle);
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

	// ─── Swatch button ───────────────────────────────────────────────────

	private class SwatchButton extends JPanel
	{
		final int index;
		Color color;
		private boolean selected;

		SwatchButton(int index, Color color)
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
					selectSlot(index);
				}
			});
		}

		void setColor(Color c)
		{
			this.color = c;
			repaint();
		}

		void setSelected(boolean sel)
		{
			this.selected = sel;
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
			// tick number
			String s = String.valueOf(index + 1);
			g2.setFont(FontManager.getRunescapeSmallFont());
			int sw = g2.getFontMetrics().stringWidth(s);
			int sh = g2.getFontMetrics().getAscent();
			// pick contrasting text color
			double luma = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
			g2.setColor(luma > 140 ? Color.BLACK : Color.WHITE);
			g2.drawString(s, (w - sw) / 2, (h + sh) / 2 - 2);
			g2.dispose();
		}
	}
}
