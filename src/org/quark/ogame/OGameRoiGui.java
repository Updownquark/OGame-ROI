package org.quark.ogame;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import org.observe.Observable;
import org.observe.collect.ObservableCollection;
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

	public OGameRoiGui(OGameROI roi) {
		super(new BorderLayout());
		theROI = roi;
		theSequence=ObservableCollection.create(TypeToken.of(OGameImprovement.class));

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
		configPanel.add(new JLabel("Universe Speed:"), "align right");
		configPanel.add(
				new ObservableTextField<>(theUniSpeed, Format.validate(Format.INT, i -> i <= 0 ? "Universe speed must be >0" : null), null),
				"wrap");
		configPanel.add(new JLabel("With Fusion:"), "align right");
		JCheckBox fusionCheck = new JCheckBox();
		ObservableSwingUtils.checkFor(fusionCheck, "Whether to use fusion instead of satellites for energy", theROI.isWithFusion());
		configPanel.add(fusionCheck, "wrap");
		JPanel buttonPanel = new JPanel(new MigLayout("fillx"));
		configPanel.add(buttonPanel, "span, grow");
		JButton computeButton = new JButton("Compute");
		buttonPanel.add(computeButton, "align center");
		computeButton.addActionListener(evt -> {
			Consumer<OGameImprovement> seqAdd = theSequence::add;
			try (Transaction t = theSequence.lock(true, null)) {
				if (theComputation == null) {
					theComputation = theROI.compute();
					for (int i = 0; i < 175; i++) {
						theComputation.tryAdvance(seqAdd);
					}
					computeButton.setText("More...");
				} else {
					for (int i = 0; i < 25; i++) {
						theComputation.tryAdvance(seqAdd);
					}
				}
			}
		});
		
		Observable.or(theROI.getMetalTradeRate().changes(), //
				theROI.getCrystalTradeRate().changes(), //
				theROI.getDeutTradeRate().changes(), //
				theROI.getPlanetTemp().changes(), //
				theROI.isWithFusion().changes()).act(v -> {
					theComputation = null;
					theSequence.clear();
					computeButton.setText("Compute");
				});

		List<OGameImprovementType> columnTypes = Arrays.asList(null, //
				OGameImprovementType.Metal, OGameImprovementType.Crystal, OGameImprovementType.Deut, //
				OGameImprovementType.Planet, OGameImprovementType.Plasma, OGameImprovementType.Fusion, OGameImprovementType.Energy, //
				null, null);
		JTable table=new JTable(new ObservableTableModel<OGameImprovement>(theSequence,//
				new String[] { "ROI", "Metal", "Crystal", "Deut", "Planets", "Plasma", "Fusion", "Energy", "Account Value", "Metal Ratio",
						"Crystal Ratio" }, //
				new Function[]{//
						(Function<OGameImprovement, Duration>) imp->imp.roi, //
						(Function<OGameImprovement, Integer>) imp->imp.metal, //
						(Function<OGameImprovement, Integer>) imp->imp.crystal, //
						(Function<OGameImprovement, Integer>) imp->imp.deut, //
						(Function<OGameImprovement, Integer>) imp->imp.planets, //
						(Function<OGameImprovement, Integer>) imp->imp.plasma, //
						(Function<OGameImprovement, Integer>) imp->imp.fusion, //
						(Function<OGameImprovement, Integer>) imp -> imp.energy, //
						(Function<OGameImprovement, Double>) imp -> //
						imp.accountValue.getValue(theROI.getMetalTradeRate().get(), theROI.getCrystalTradeRate().get(),
								theROI.getDeutTradeRate().get()), //
						(Function<OGameImprovement, Double>) imp -> //
						imp.accountValue.getTotalCost(2) == 0 //
								? imp.accountValue.getTotalCost(0) * 1.0 / imp.accountValue.getTotalCost(1)
								: imp.accountValue.getTotalCost(0) * 1.0 / imp.accountValue.getTotalCost(2), //
						(Function<OGameImprovement, Double>) imp -> //
						imp.accountValue.getTotalCost(2) == 0 //
								? 1
								: imp.accountValue.getTotalCost(1) * 1.0 / imp.accountValue.getTotalCost(2) //
		}) {
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex) {
				return false;
			}

			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			}
		});
		table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable _table, Object value, boolean isSelected, boolean hasFocus, int row,
					int column) {
				super.getTableCellRendererComponent(_table, value, isSelected, hasFocus, row, column);
				if (value != null) {
					setText(QommonsUtils
							.printTimeLength(((Duration) value).getSeconds(), ((Duration) value).getNano(), new StringBuilder(), true)
							.toString());
				}
				return this;
			}
		});
		Font normal = getFont();
		Font bold = normal.deriveFont(Font.BOLD);
		class UpgradeRenderer extends DefaultTableCellRenderer {
			@Override
			public Component getTableCellRendererComponent(JTable _table, Object value, boolean isSelected, boolean hasFocus, int row,
					int column) {
				super.getTableCellRendererComponent(_table, value, isSelected, hasFocus, row, column);
				if (columnTypes.get(column) == theSequence.get(row).type) {
					setFont(bold);
				} else {
					setFont(normal);
				}
				return this;
			}
		}
		for (int i = 1; i < columnTypes.size(); i++) {
			table.getColumnModel().getColumn(i).setCellRenderer(new UpgradeRenderer());
		}
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
