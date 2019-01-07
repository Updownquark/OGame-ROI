package org.quark.ogame;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.ObservableTableModel;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

public class OGameRoiGui extends JPanel {
	private final OGameROI theROI;
	private final ObservableCollection<OGameImprovement> theSequence;
	private OGameROI.ROIComputation theComputation;

	public OGameRoiGui(OGameROI roi) {
		super(new BorderLayout());
		theROI = roi;
		theSequence=ObservableCollection.create(TypeToken.of(OGameImprovement.class));

		JPanel configPanel=new JPanel();
		configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.X_AXIS));
		add(configPanel, BorderLayout.NORTH);
		JPanel labelPanel = new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));
		configPanel.add(labelPanel);
		JPanel fieldPanel = new JPanel();
		fieldPanel.setLayout(new BoxLayout(fieldPanel, BoxLayout.Y_AXIS));
		configPanel.add(fieldPanel);
		
		labelPanel.add(new JLabel("Metal Trade Rate:"));
		fieldPanel.add(fieldFor(theROI.getMetalTradeRate()));
		labelPanel.add(new JLabel("Crystal Trade Rate:"));
		fieldPanel.add(fieldFor(theROI.getCrystalTradeRate()));
		labelPanel.add(new JLabel("Deut Trade Rate:"));
		fieldPanel.add(fieldFor(theROI.getDeutTradeRate()));
		labelPanel.add(new JLabel("Avg. Planet Temp:"));
		fieldPanel.add(fieldFor(theROI.getPlanetTemp()));
		labelPanel.add(new JLabel("With Fusion:"));
		JCheckBox fusionCheck=new JCheckBox();
		ObservableSwingUtils.checkFor(fusionCheck, "Whether to use fusion instead of satellites for energy", theROI.isWithFusion());
		fieldPanel.add(fusionCheck);
		labelPanel.add(new JLabel("Compute:"));
		JButton computeButton = new JButton("Compute");
		fieldPanel.add(computeButton);
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
				OGameImprovementType.Planet, OGameImprovementType.Plasma, OGameImprovementType.Fusion, OGameImprovementType.Energy);
		JTable table=new JTable(new ObservableTableModel<OGameImprovement>(theSequence,//
				new String[]{"ROI", "Metal", "Crystal", "Deut", "Planets", "Plasma", "Fusion", "Energy"},//
				new Function[]{//
						(Function<OGameImprovement, Duration>) imp->imp.roi, //
						(Function<OGameImprovement, Integer>) imp->imp.metal, //
						(Function<OGameImprovement, Integer>) imp->imp.crystal, //
						(Function<OGameImprovement, Integer>) imp->imp.deut, //
						(Function<OGameImprovement, Integer>) imp->imp.planets, //
						(Function<OGameImprovement, Integer>) imp->imp.plasma, //
						(Function<OGameImprovement, Integer>) imp->imp.fusion, //
						(Function<OGameImprovement, Integer>) imp->imp.energy //
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
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
					int column) {
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
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
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
					int column) {
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
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

	private JTextField fieldFor(SettableValue<? extends Number> value) {
		JTextField field = new JTextField(10);
		field.setText(value.get().toString());
		boolean isFloat = value.getType().unwrap().getRawType() != int.class;
		class FieldListener extends KeyAdapter implements FocusListener {
			private boolean isDirty;
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					updateValue();
				} else {
					isDirty=true;
				}
			}

			@Override
			public void focusGained(FocusEvent e) {
			}

			@Override
			public void focusLost(FocusEvent e) {
				updateValue();
			}

			private void updateValue() {
				if (!isDirty) {
					return;
				}
				isDirty = true;
				if (isFloat) {
					((SettableValue<Double>) value).set(Double.parseDouble(field.getText()), null);
				} else {
					((SettableValue<Integer>) value).set(Integer.parseInt(field.getText()), null);
				}
			}
		}
		FieldListener listener = new FieldListener();
		field.addKeyListener(listener);
		field.addFocusListener(listener);
		return field;
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
