package org.quark.ogame.roi;

import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleSettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.TableContentControl;
import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.io.Format;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.PointType;

import com.google.common.reflect.TypeToken;

public class OGameRoiGui extends JPanel {
	private final OGameROI theROI;
	private final ObservableCollection<OGameImprovement> theSequence;
	private ROIComputation theComputation;

	private final SettableValue<Integer> theFieldOffset;
	private final SettableValue<TableContentControl> theUpgradeFilter;
	private final SettableValue<Boolean> isComputing;
	private boolean isCanceled;

	public OGameRoiGui(OGameROI roi) {
		theROI = roi;
		theSequence = ObservableCollection.create(TypeToken.of(OGameImprovement.class));
		theFieldOffset = new SimpleSettableValue<>(int.class, false)//
			.filterAccept(offset -> offset < 0 ? "Other Buildings cannot be negative" : null)//
			.withValue(12, null);
		theUpgradeFilter = new SimpleSettableValue<>(TableContentControl.class, false).withValue(TableContentControl.DEFAULT, null);
		isComputing = new SimpleSettableValue<>(boolean.class, false).withValue(false, null);

		ObservableValue<String> computingText = isComputing.map(c -> c ? "Currently computing build sequence..." : null);
		Format<Double> d2Format = Format.doubleFormat("0.00");
		Font normal = getFont();
		Font bold = normal.deriveFont(Font.BOLD);
		ObservableCollection<CategoryRenderStrategy<OGameImprovement, ?>>[] columns = new ObservableCollection[1];
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
			}, (imp, i) -> String.valueOf(i));
		TypeToken<CategoryRenderStrategy<OGameImprovement, ?>> columnType = new TypeToken<CategoryRenderStrategy<OGameImprovement, ?>>() {};
		ObservableCollection<CategoryRenderStrategy<OGameImprovement, ?>> variableColumns = ObservableCollection.create(columnType);
		ObservableCollection<CategoryRenderStrategy<OGameImprovement, ?>> preVarColumns = ObservableCollection.of(columnType,
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
					.withIdentifier(OGameImprovementType.Plasma).withRenderer(upgradeRenderer), //
				new CategoryRenderStrategy<OGameImprovement, Integer>("Crawlers", TypeTokens.get().INT, imp -> imp.crawlers)
					.withIdentifier(OGameImprovementType.Crawler).withRenderer(upgradeRenderer) //
			));
		ObservableCollection<CategoryRenderStrategy<OGameImprovement, ?>> postVarColumns = ObservableCollection.of(columnType,
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
				new CategoryRenderStrategy<>("Eco Points", TypeTokens.get().STRING,
					imp -> OGameUtils.printResourceAmount(imp.accountValue.getPoints(PointType.Economy))), //
				new CategoryRenderStrategy<>("Metal Ratio", TypeTokens.get().DOUBLE,
					imp -> imp.accountValue.getDeuterium() == 0 //
						? imp.accountValue.getMetal() * 1.0 / imp.accountValue.getCrystal()
						: imp.accountValue.getMetal() * 1.0 / imp.accountValue.getDeuterium()), //
				new CategoryRenderStrategy<>("Crystal Ratio", TypeTokens.get().DOUBLE, imp -> imp.accountValue.getDeuterium() == 0 //
					? 1 : imp.accountValue.getCrystal() * 1.0 / imp.accountValue.getDeuterium())//
			));
		columns[0] = ObservableCollection.flattenCollections(columnType, //
			preVarColumns, variableColumns, postVarColumns).collect();

		PanelPopulation.populateVPanel(this, null)//
			.addCheckField("Mining Class:", theROI.getMiningClass().disableWith(computingText),
				f -> f.fill().withTooltip("Whether to apply mining class bonus to production"))//
			.addCheckField("Use Crawlers:", theROI.getUsingCrawlers().disableWith(computingText),
				f -> f.fill().withTooltip("Whether to apply mining class bonus to production"))//
			.addTextField("Metal Trade Rate:", theROI.getMetalTradeRate().disableWith(computingText), d2Format,
				f -> f.fill().withTooltip("The relative amount of metal needed in trades"))//
			.addTextField("Crystal Trade Rate:", theROI.getCrystalTradeRate().disableWith(computingText), d2Format,
				f -> f.fill().withTooltip("The relative amount of crystal needed in trades"))//
			.addTextField("Deut Trade Rate:", theROI.getDeutTradeRate().disableWith(computingText), d2Format,
				f -> f.fill().withTooltip("The relative amount of deuterium needed in trades"))//
			.addTextField("Avg. Planet Temp:", theROI.getPlanetTemp().disableWith(computingText), Format.INT,
				f -> f.fill().withTooltip("The average ( (min+max)/2 ) planet temperature to assume for deuterium production"))//
			.addTextField("Economy Speed:", theROI.getEconomySpeed().disableWith(computingText), Format.INT,
				f -> f.fill().withTooltip("The economy speed of the universe"))//
			.addTextField("Research Speed:", theROI.getResearchSpeed().disableWith(computingText), Format.INT,
				f -> f.fill().withTooltip("The research speed of the universe"))//
			.addCheckField("Aggressive Helpers:", theROI.isWithAggressiveHelpers().disableWith(computingText),
				f -> f.fill().withTooltip("Whether to upgrade speed-improvement buildings/techs aggressively"))//
			.addTextField("Fusion Contribution:", //
				theROI.getFusionContribution().map(TypeTokens.get().DOUBLE, am -> am * 100, pc -> pc / 100, opts -> {})
					.disableWith(computingText),
				Format.doubleFormat("0.0"),
				f -> f.fill().withTooltip("The amount of energy that should be supplied by fusion instead of solar satellites")
					.withPostLabel("%"))//
			.addTextField("Production Storage:", theROI.getDailyProductionStorageRequired().disableWith(computingText), d2Format,
				f -> f.fill().withTooltip("The number of days' of planetary production each planets' storages should be able to hold"))//
			.addTextField("Other Buildings:", theFieldOffset.disableWith(computingText), Format.INT,
				f -> f.fill().withTooltip("The total level of other buildings on your planet(s), e.g. Shipyard, Silo, Terraformer, etc."))//
			.addHPanel(null, new JustifiedBoxLayout(false).mainCenter(), buttons -> {
				buttons.fill().addButton("Compute", new ObservableAction<Object>() {
					@Override
					public TypeToken<Object> getType() {
						return TypeTokens.get().OBJECT;
					}

					@Override
					public Object act(Object cause) throws IllegalStateException {
						if (isComputing.get()) {
							isCanceled = true;
						} else {
							computeSequence();
						}
						return null;
					}

					@Override
					public ObservableValue<String> isEnabled() {
						return SettableValue.ALWAYS_ENABLED;
					}
				}, button -> button.withText(isComputing.combine((c, sz) -> new BiTuple<>(c, sz), theSequence.observeSize())//
					.map(tuple -> tuple.getValue1() ? "Cancel" : (tuple.getValue2() == 0 ? "Compute" : "More Levels..."))))//
					.addButton("Export CSV", new ObservableAction<Object>() {
						@Override
						public TypeToken<Object> getType() {
							return TypeTokens.get().OBJECT;
						}

						@Override
						public Object act(Object cause) throws IllegalStateException {
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
										str.append(',');
										Object cv = column.getCategoryValue(row);
										if (cv != null) {
											String cvStr = cv.toString();
											if (cvStr.indexOf(',') >= 0) {
												str.append('"').append(cvStr).append('"');
											} else {
												str.append(cvStr);
											}
										}
									}
								}
								str.append('\n');
							}
							Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(str.toString()), null);
							return null;
						}

						@Override
						public ObservableValue<String> isEnabled() {
							return computingText;
						}
					}, button -> button.withTooltip("Copy the currently displayed build sequence as CSV text to the clipboard"));
			})//
			.addTextField(null, theUpgradeFilter, TableContentControl.FORMAT,
				f -> f.withTooltip("Filter the view by an improvement type").modifyEditor(tf -> {
					tf.setIcon(ObservableSwingUtils.getFixedIcon(ObservableSwingUtils.class, "/icons/search.png", 16, 16))
						.setEmptyText("Search...").withColumns(25);
				}))//
			.addTable(theSequence.flow().refresh(theFieldOffset.noInitChanges()).collect(), table -> table.fillV()//
				.fill().withColumns(columns[0]).withFiltering(theUpgradeFilter))
		;

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
			});
	}

	private void computeSequence() {
		isCanceled = false;
		isComputing.set(true, null);
		new Thread(() -> {
			try {
				if (theComputation == null) {
					theComputation = theROI.compute();
					for (int i = 0; i < 175 && !isCanceled; i++) {
						upgradeOne();
					}
				} else {
					for (int i = 0; i < 25 && !isCanceled; i++) {
						try (Transaction t = theSequence.lock(true, null)) {
							upgradeOne();
						}
					}
				}
			} finally {
				isComputing.set(false, null);
			}
		}, "ROI Calculation").start();
	}

	private void upgradeOne() {
		boolean[] upgradeAgain = new boolean[] { true };
		while (upgradeAgain[0]) {
			upgradeAgain[0] = false;
			theComputation.tryAdvance(improvement -> {
				OGameImprovement prev = theSequence.peekLast();
				if (prev != null && prev.type == improvement.type) {
					upgradeAgain[0] = true;
					theSequence.mutableElement(theSequence.getTerminalElement(false).getElementId()).set(improvement);
				} else {
					theSequence.add(improvement);
				}
			});
		}
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
