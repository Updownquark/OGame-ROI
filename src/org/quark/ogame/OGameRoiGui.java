package org.quark.ogame;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleSettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.ObservableTableModel;
import org.observe.util.swing.ObservableTextField;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

import net.miginfocom.swing.MigLayout;

public class OGameRoiGui extends JPanel {
	private final OGameROI theROI;
	private final ObservableCollection<OGameImprovement> theSequence;
	private OGameROI.ROIComputation theComputation;

	private final SettableValue<Integer> theFieldOffset;

	public OGameRoiGui(OGameROI roi) {
		super(new BorderLayout());
		theROI = roi;
		theSequence = ObservableCollection.create(TypeToken.of(OGameImprovement.class));
		theFieldOffset = new SimpleSettableValue<>(int.class, false);
		theFieldOffset.set(12, null);

		JPanel configPanel = new JPanel(new MigLayout("fillx", "[shrink][grow, fill]"));
		add(configPanel, BorderLayout.NORTH);

		configPanel.add(new JLabel("Metal Trade Rate:"), "align right");
		configPanel.add(new ObservableTextField<>(theROI.getMetalTradeRate(), //
			Format.validate(Format.doubleFormat("0.00"), v -> v <= 0 ? "Trade rate must be >0" : null), null), "wrap");
		configPanel.add(new JLabel("Crystal Trade Rate:"), "align right");
		configPanel.add(new ObservableTextField<>(theROI.getCrystalTradeRate(), //
			Format.validate(Format.doubleFormat("0.00"), v -> v <= 0 ? "Trade rate must be >0" : null), null), "wrap");
		configPanel.add(new JLabel("Deut Trade Rate:"), "align right");
		configPanel.add(new ObservableTextField<>(theROI.getDeutTradeRate(), //
			Format.validate(Format.doubleFormat("0.00"), v -> v <= 0 ? "Trade rate must be >0" : null), null), "wrap");
		configPanel.add(new JLabel("Avg. Planet Temp:"), "align right");
		configPanel.add(new ObservableTextField<>(theROI.getPlanetTemp(), Format.INT, null), "wrap");
		configPanel.add(new JLabel("Economy Speed:"), "align right");
		configPanel.add(new ObservableTextField<>(theROI.getEconomySpeed(),
			Format.validate(Format.INT, i -> i <= 0 ? "Economy speed must be >0" : null), null), "wrap");
		configPanel.add(new JLabel("Research Speed:"), "align right");
		configPanel.add(new ObservableTextField<>(theROI.getResearchSpeed(),
			Format.validate(Format.INT, i -> i <= 0 ? "Economy speed must be >0" : null), null), "wrap");
		configPanel.add(new JLabel("Aggressive Helpers:"), "align right");
		JCheckBox aggHelperCheck = new JCheckBox();
		ObservableSwingUtils.checkFor(aggHelperCheck, "Whether to upgrade speed-improvement buildings/techs aggressively",
			theROI.isWithAggressiveHelpers());
		configPanel.add(aggHelperCheck, "wrap");
		configPanel.add(new JLabel("Fusion Contribution:"), "align right");
		configPanel.add(new ObservableTextField<>(//
			theROI.getFusionContribution().map(v -> v * 100.0, v -> v / 100.0).filterAccept(
				v -> v < 0 || v > 100 ? "Fusion contribution must be between 0 and 100%" : null),
			Format.doubleFormat("0.0"), null)
				.withToolTip("The amount of energy that should be supplied by fusion instead of solar satellites"));
		configPanel.add(new JLabel("%"), "wrap");
		configPanel.add(new JLabel("Production Storage:"), "align right");
		configPanel.add(new ObservableTextField<>(//
			theROI.getDailyProductionStorageRequired(), Format.doubleFormat("0.00"), null));
		configPanel.add(new JLabel("days"), "wrap");
		configPanel.add(new JLabel("Other Buildings:"), "align right");
		configPanel.add(new ObservableTextField<>(theFieldOffset, //
			Format.validate(Format.INT, i -> i < 0 ? "Other Buildings cannot be negative" : null), null)
				.withToolTip("The total level of other buildings on your planet(s), e.g. Shipyard, Silo, Terraformer, etc."),
			"wrap");

		JPanel buttonPanel = new JPanel(new MigLayout("fillx"));
		configPanel.add(buttonPanel, "span, grow");
		JButton computeButton = new JButton("Compute");
		buttonPanel.add(computeButton, "align center");
		computeButton.addActionListener(evt -> {
			new Thread(() -> {
				Consumer<OGameImprovement> seqAdd = theSequence::add;
				if (theComputation == null) {
					theComputation = theROI.compute();
					for (int i = 0; i < 175; i++) {
						theComputation.tryAdvance(seqAdd);
					}
					computeButton.setText("More Levels...");
				} else {
					for (int i = 0; i < 25; i++) {
						try (Transaction t = theSequence.lock(true, null)) {
							theComputation.tryAdvance(seqAdd);
						}
					}
				}
			}, "ROI Calculation").start();
		});
		TypeToken<CategoryRenderStrategy<OGameImprovement, ?>> columnType = new TypeToken<CategoryRenderStrategy<OGameImprovement, ?>>() {};
		ObservableCollection<CategoryRenderStrategy<OGameImprovement, ?>> variableColumns = ObservableCollection.create(columnType);
		ObservableCollection<CategoryRenderStrategy<OGameImprovement, ?>>[] columns = new ObservableCollection[1];
		Font normal = getFont();
		Font bold = normal.deriveFont(Font.BOLD);
		ObservableCellRenderer<OGameImprovement, Integer> upgradeRenderer = ObservableCellRenderer
			.fromTableRenderer(new DefaultTableCellRenderer() {
				@Override
				public Component getTableCellRendererComponent(JTable _table, Object value, boolean isSelected, boolean hasFocus, int row,
					int column) {
					super.getTableCellRendererComponent(_table, value, isSelected, hasFocus, row, column);
					CategoryRenderStrategy<OGameImprovement, ?> c = columns[0].get(column);
					if (row == 0 || c.getCategoryValue(theSequence.get(row)).equals(c.getCategoryValue(theSequence.get(row - 1)))) {
						setFont(normal);
					} else {
						setFont(bold);
					}
					return this;
				}
			});
		columns[0] = ObservableCollection.flattenCollections(columnType, //
			ObservableCollection.of(columnType,
				Arrays.asList(//
					new CategoryRenderStrategy<OGameImprovement, Duration>("ROI", TypeTokens.get().of(Duration.class), imp -> imp.roi)
						.formatText(d -> d == null ? "" : QommonsUtils.printDuration(d, true)), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Metal", TypeTokens.get().INT, imp -> imp.metal)
						.withIdentifier(OGameImprovementType.Metal).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Crystal", TypeTokens.get().INT, imp -> imp.crystal)
						.withIdentifier(OGameImprovementType.Crystal).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Deut", TypeTokens.get().INT, imp -> imp.deut)
						.withIdentifier(OGameImprovementType.Deut).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Planets", TypeTokens.get().INT, imp -> imp.planets)
						.withIdentifier(OGameImprovementType.Planet).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Plasma", TypeTokens.get().INT, imp -> imp.plasma)
						.withIdentifier(OGameImprovementType.Plasma).withRenderer(upgradeRenderer))), //
			variableColumns, //
			ObservableCollection.of(columnType,
				Arrays.asList(//
					new CategoryRenderStrategy<OGameImprovement, Integer>("Robotics", TypeTokens.get().INT, imp -> imp.robotics)
						.withIdentifier(OGameImprovementType.Robotics).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Nanite", TypeTokens.get().INT, imp -> imp.nanites)
						.withIdentifier(OGameImprovementType.Nanite).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Lab", TypeTokens.get().INT, imp -> imp.researchLab)
						.withIdentifier(OGameImprovementType.ResearchLab).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("IRN", TypeTokens.get().INT, imp -> imp.irn)
						.withIdentifier(OGameImprovementType.IRN).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Metal St.", TypeTokens.get().INT, imp -> imp.metalStorage)
						.withIdentifier(OGameImprovementType.MetalStorage).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Crystal St.", TypeTokens.get().INT, imp -> imp.crystalStorage)
						.withIdentifier(OGameImprovementType.CrystalStorage).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Deut St.", TypeTokens.get().INT, imp -> imp.deutStorage)
						.withIdentifier(OGameImprovementType.DeutStorage).withRenderer(upgradeRenderer), //
					new CategoryRenderStrategy<OGameImprovement, Integer>("Fields", TypeTokens.get().INT,
						imp -> theFieldOffset.get() + imp.buildings), //
					new CategoryRenderStrategy<>("Economy Value", TypeTokens.get().DOUBLE,
						imp -> imp.accountValue.getValue(theROI.getMetalTradeRate().get(), theROI.getCrystalTradeRate().get(),
							theROI.getDeutTradeRate().get())), //
					new CategoryRenderStrategy<>("Metal Ratio", TypeTokens.get().DOUBLE,
						imp -> imp.accountValue.getTotalCost(2) == 0 //
							? imp.accountValue.getTotalCost(0) * 1.0 / imp.accountValue.getTotalCost(1)
							: imp.accountValue.getTotalCost(0) * 1.0 / imp.accountValue.getTotalCost(2)), //
					new CategoryRenderStrategy<>("Crystal Ratio", TypeTokens.get().DOUBLE, imp -> imp.accountValue.getTotalCost(2) == 0 //
						? 1 : imp.accountValue.getTotalCost(1) * 1.0 / imp.accountValue.getTotalCost(2))//
				))).collect();
		CategoryRenderStrategy<OGameImprovement, Integer> fusionCategory = new CategoryRenderStrategy<OGameImprovement, Integer>("Fusion",
			TypeTokens.get().INT, imp -> imp.fusion).withIdentifier(OGameImprovementType.Fusion).withRenderer(upgradeRenderer);
		CategoryRenderStrategy<OGameImprovement, Integer> energyCategory = new CategoryRenderStrategy<OGameImprovement, Integer>("Energy",
			TypeTokens.get().INT, imp -> imp.energy).withIdentifier(OGameImprovementType.Energy).withRenderer(upgradeRenderer);
		theROI.getFusionContribution().changes().act(new Consumer<ObservableValueEvent<Double>>() {
			private boolean usesFusion = false;

			@Override
			public void accept(ObservableValueEvent<Double> evt) {
				boolean nowUsesFusion = evt.getNewValue() > 0.0;
				if (nowUsesFusion == usesFusion) {
					return;
				}
				usesFusion = nowUsesFusion;
				if (nowUsesFusion) {
					variableColumns.with(fusionCategory, energyCategory);
				} else {
					variableColumns.clear();
				}
			}
		});

		JButton exportButton = new JButton("Copy CSV to Clipboard");
		theSequence.observeSize().changes().act(evt -> {
			exportButton.setEnabled(evt.getNewValue() > 0);
		});
		exportButton.addActionListener(evt -> {
			StringBuilder str = new StringBuilder();
			boolean firstCol = true;
			for (CategoryRenderStrategy<OGameImprovement, ?> column : columns[0]) {
				if (!firstCol) {
					str.append(',');
				}
				firstCol = false;
				str.append(column.getName());
			}
			str.append('\n');
			for (OGameImprovement row : theSequence) {
				firstCol = true;
				for (CategoryRenderStrategy<OGameImprovement, ?> column : columns[0]) {
					if (firstCol) {
						Duration rowRoi = (Duration) column.getCategoryValue(row);
						if (rowRoi != null) {
							QommonsUtils.printTimeLength(rowRoi.getSeconds(), rowRoi.getNano(), str, true);
						}
						firstCol = false;
					} else {
						str.append(',').append(column.getCategoryValue(row));
					}
				}
				str.append('\n');
			}
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(str.toString()), null);
		});
		buttonPanel.add(exportButton, "align center");

		Observable.or(theROI.getMetalTradeRate().noInitChanges(), //
			theROI.getCrystalTradeRate().noInitChanges(), //
			theROI.getDeutTradeRate().noInitChanges(), //
			theROI.getPlanetTemp().noInitChanges(), //
			theROI.getFusionContribution().noInitChanges(), //
			theROI.isWithAggressiveHelpers().noInitChanges(), //
			theROI.getEconomySpeed().noInitChanges(), //
			theROI.getResearchSpeed().noInitChanges(), //
			theROI.getDailyProductionStorageRequired().noInitChanges()//
		).act(v -> {
				theComputation = null;
				theSequence.clear();
				computeButton.setText("Compute");
			});

		ObservableTableModel<OGameImprovement> tableModel = new ObservableTableModel<>(
			theSequence.flow().refresh(theFieldOffset.noInitChanges()).collect(), columns[0]);
		JTable table = new JTable(tableModel);
		ObservableTableModel.hookUp(table, tableModel);
		JScrollPane scroll = new JScrollPane(table);
		scroll.getVerticalScrollBar().setUnitIncrement(10);
		add(scroll);
	}

	public static void main(String[] args) {
		JFrame frame = new JFrame("OGame ROI Calculator");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(new OGameRoiGui(new OGameROI()));
		frame.setSize(600, 600);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
}
