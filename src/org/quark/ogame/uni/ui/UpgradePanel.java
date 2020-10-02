package org.quark.ogame.uni.ui;

import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.EventQueue;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.WindowPopulation;
import org.qommons.ArrayUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.qommons.threading.QommonsTimer;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.roi.RoiCompoundSequenceElement;
import org.quark.ogame.roi.RoiSequenceElement;
import org.quark.ogame.roi.RoiSequenceGenerator;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.ui.OGameUniGui.PlannedAccountUpgrade;

public class UpgradePanel extends JPanel {
	private final OGameUniGui theUniGui;

	private final ObservableCollection<PlannedAccountUpgrade> theUpgrades;
	private final PlannedAccountUpgrade thePlanetTotalUpgrade;
	private final PlannedAccountUpgrade theTotalUpgrade;

	public UpgradePanel(OGameUniGui uniGui) {
		theUniGui = uniGui;

		thePlanetTotalUpgrade = theUniGui.new PlannedAccountUpgrade(null) {
			@Override
			public UpgradeCost getCost() {
				UpgradeCost cost = UpgradeCost.ZERO;
				PlanetWithProduction selectedPlanet = theUniGui.getSelectedPlanet().get();
				if (selectedPlanet != null) {
					for (PlannedAccountUpgrade upgrade : theUniGui.getUpgrades()) {
						if (upgrade.getPlanet() == selectedPlanet.planet) {
							cost = cost.plus(upgrade.getCost());
						}
					}
				}
				return cost;
			}
		};
		theTotalUpgrade = theUniGui.new PlannedAccountUpgrade(null) {
			@Override
			public UpgradeCost getCost() {
				UpgradeCost cost = UpgradeCost.ZERO;
				for (PlannedAccountUpgrade upgrade : theUniGui.getUpgrades()) {
					cost = cost.plus(upgrade.getCost());
				}
				return cost;
			}
		};

		ObservableCollection<PlannedAccountUpgrade> totalUpgrades = ObservableCollection.build(PlannedAccountUpgrade.class).safe(false)
			.build();
		totalUpgrades.add(theTotalUpgrade);
		theUniGui.getSelectedPlanet().changes().act(p -> {
			if (p != null && totalUpgrades.size() == 1) {
				totalUpgrades.add(0, thePlanetTotalUpgrade);
			} else if (p == null && totalUpgrades.size() == 2) {
				totalUpgrades.remove(0);
			}
		});
		theUpgrades = ObservableCollection.flattenCollections(TypeTokens.get().of(PlannedAccountUpgrade.class), //
			theUniGui.getUpgrades().flow().refresh(theUniGui.getSelectedPlanet().noInitChanges()).collect(), //
			totalUpgrades).collect();
	}

	public PlannedAccountUpgrade getTotalUpgrades() {
		return theTotalUpgrade;
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		Format<Double> commaFormat = Format.doubleFormat("#,##0");
		panel.addButton("Generate ROI Sequence", __ -> showRoiSequenceConfigPanel(), null);
		panel.addTable(theUpgrades,
			upgradeTable -> upgradeTable.fill()//
				.dragSourceRow(null).dragAcceptRow(null)// Make the rows draggable
				.withColumn("Planet", String.class, upgrade -> {
					if (upgrade == theTotalUpgrade) {
						return "Total";
					} else if (upgrade == thePlanetTotalUpgrade) {
						return "Planet Total";
					} else if (upgrade.getPlanet() != null) {
						return upgrade.getPlanet().getName() + (upgrade.getUpgrade().isMoon() ? " Moon" : "");
					} else {
						return "";
					}
				}, planetCol -> {
					planetCol.decorate((cell, d) -> {
						PlanetWithProduction p = theUniGui.getSelectedPlanet().get();
						if (p != null && cell.getModelValue().getPlanet() == p.planet) {
							d.bold();
						}
					});
				})//
				.withColumn("Upgrade", AccountUpgradeType.class,
					upgrade -> upgrade.getUpgrade() == null ? null : upgrade.getUpgrade().getType(),
					c -> c.formatText(t -> t == null ? "" : t.toString()))//
				.withColumn("From", int.class, upgrade -> upgrade.getFrom(),
					fromCol -> fromCol.withWidths(25, 45, 100)//
						.formatText((u, i) -> u.getUpgrade() == null ? "" : ("" + i)))//
				.withColumn("To", int.class, upgrade -> upgrade.getTo(),
					toCol -> toCol.withWidths(25, 45, 100)//
						.formatText((u, i) -> u.getUpgrade() == null ? "" : ("" + i)))//
				.withColumn("Metal", String.class,
					upgrade -> upgrade.getCost() == null ? "" : OGameUtils.printResourceAmount(upgrade.getCost().getMetal()),
					metalCol -> metalCol.decorate((cell, d) -> {
						if (cell.getModelValue().getUpgrade() == null) {
							d.bold();
						}
					}))//
				.withColumn("Crystal", String.class,
					upgrade -> upgrade.getCost() == null ? "" : OGameUtils.printResourceAmount(upgrade.getCost().getCrystal()),
					crystalCol -> crystalCol.decorate((cell, d) -> {
						if (cell.getModelValue().getUpgrade() == null) {
							d.bold();
						}
					}))//
				.withColumn("Deut", String.class,
					upgrade -> upgrade.getCost() == null ? "" : OGameUtils.printResourceAmount(upgrade.getCost().getDeuterium()),
					deutCol -> deutCol.decorate((cell, d) -> {
						if (cell.getModelValue().getUpgrade() == null) {
							d.bold();
						}
					}))//
				.withColumn("Time", String.class, upgrade -> {
					if (upgrade.getCost() == null || upgrade.getCost().getUpgradeTime() == null) {
						return "";
					}
					return Format.DURATION.format(upgrade.getCost().getUpgradeTime());
				}, timeCol -> timeCol.withWidths(40, 100, 120))//
				.withColumn("Cargoes", Long.class, upgrade -> {
					if (upgrade.getUpgrade() == null || upgrade.getCost() == null) {
						return null;
					}
					long cost = upgrade.getCost().getTotal();
					int cargoSpace = theUniGui.getRules().get().fleet().getCargoSpace(ShipyardItemType.LargeCargo,
						theUniGui.getSelectedAccount().get());
					return (long) Math.ceil(cost * 1.0 / cargoSpace);
				}, cargoCol -> cargoCol.formatText(i -> i == null ? "" : commaFormat.format(i * 1.0)).withWidths(40, 50, 80))//
				.withColumn("ROI", Duration.class, upgrade -> upgrade.getROI(), //
					roiCol -> roiCol.formatText(roi -> roi == null ? "" : Format.DURATION.format(roi)).withWidths(50, 100, 150))//
		// .withMultiAction(upgrades -> sortUpgrades(upgrades), action -> action//
		// .allowWhenMulti(items -> canSortUpgrades(items), null).modifyButton(button -> button.withText("Sort by ROI")))
		);
	}

	private void sortUpgrades(List<? extends PlannedAccountUpgrade> upgrades) {
		if (upgrades.size() <= 1) {
			return;
		}
		// The selected upgrades should be in model order, and contiguous
		CollectionElement<PlannedAccountUpgrade> target = theUpgrades.getElement(upgrades.get(0), true);
		List<PlannedAccountUpgrade> sorted = new ArrayList<>(upgrades);
		ElementId[] movedIds = new ElementId[upgrades.size()];
		ElementId tempId = target.getElementId();
		for (int i = 0; i < upgrades.size(); i++) {
			movedIds[i] = tempId;
			tempId = theUpgrades.getAdjacentElement(tempId, true).getElementId();
		}
		ArrayUtils.sort(upgrades.toArray(new PlannedAccountUpgrade[upgrades.size()]), new ArrayUtils.SortListener<PlannedAccountUpgrade>() {
			@Override
			public int compare(PlannedAccountUpgrade u1, PlannedAccountUpgrade u2) {
				if (u1.getROI() == null) {
					if (u2.getROI() == null) {
						return 0;
					} else {
						return 1;
					}
				} else if (u2.getROI() == null) {
					return -1;
				} else {
					return u1.getROI().compareTo(u2.getROI());
				}
			}

			@Override
			public void swapped(PlannedAccountUpgrade o1, int idx1, PlannedAccountUpgrade o2, int idx2) {
				ElementId temp = movedIds[idx1];
				movedIds[idx1] = movedIds[idx2];
				movedIds[idx2] = temp;
			}
		});
		CollectionElement<PlannedAccountUpgrade> after = theUpgrades.getAdjacentElement(target.getElementId(), false);
		for (int i = 0; i < sorted.size(); i++) {
			after = theUpgrades.move(movedIds[i], after.getElementId(), null, true, null);
		}
	}

	private String canSortUpgrades(List<? extends PlannedAccountUpgrade> upgrades) {
		switch (upgrades.size()) {
		case 0:
			return "Select a contiguous sequence of upgrades to sort";
		case 1:
			return "A single upgrade cannot be sorted";
		}
		boolean found = false, done = false;
		for (PlannedAccountUpgrade upgrade : theUpgrades) {
			if (upgrades.contains(upgrade)) {
				if (!found) {
					found = true;
				} else if (done) {
					return "Only a contiguous sequence of upgrades can be sorted";
				}
			} else if (found) {
				done = true;
			}
		}
		return null;
	}

	void showRoiSequenceConfigPanel() {
		Account account = theUniGui.getSelectedAccount().get();
		RoiSequenceGenerator sequenceGenerator = new RoiSequenceGenerator(theUniGui.getRules().get(), account);
		JDialog dialog = WindowPopulation
			.populateDialog(new JDialog(SwingUtilities.getWindowAncestor(this), "ROI Sequence", ModalityType.MODELESS), //
				Observable.empty(), true)//
			.withVContent(panel -> panel.fill().fillV()//
				.addLabel(null, ObservableValue.of("THIS IS AN ALPHA FEATURE!"), Format.TEXT,
					f -> f.decorate(deco -> deco.bold().withFontSize(20).withForeground(Color.red)))//
				.addLabel(null, ObservableValue.of("It may do weird things, cause errors, or give stupid advice."), Format.TEXT,
					f -> f.decorate(deco -> deco.bold().withFontSize(14).withForeground(Color.red)))//
				.addTextField("Target Planet:", sequenceGenerator.getTargetPlanet(), SpinnerFormat.INT, f -> f.fill())//
				.addTextField("New Planet Slot:", sequenceGenerator.getNewPlanetSlot(), SpinnerFormat.INT, f -> f.fill())//
				.addTextField("New Planet Temp:", sequenceGenerator.getNewPlanetTemp(), SpinnerFormat.INT, f -> f.fill())//
				.addButton("Generate", __ -> genRoiSequence(account, sequenceGenerator),
					btn -> btn.disableWith(sequenceGenerator.isActive())))
			.getWindow();
		dialog.setSize(500, 200);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private static final Map<AccountUpgradeType, Color> upgradeColors;
	static {
		upgradeColors = new HashMap<>();
		for (AccountUpgradeType upgrade : AccountUpgradeType.values()) {
			Color color = null;
			switch (upgrade) {
			case Astrophysics:
				color = Color.blue;
				break;
			case Plasma:
				color = Color.green;
				break;
			case MetalMine:
				color = Color.orange;
				break;
			case CrystalMine:
				color = Color.cyan;
				break;
			case DeuteriumSynthesizer:
				color = new Color(128, 128, 255);
				break;
			case Crawler:
			case SolarSatellite:
			case SolarPlant:
			case FusionReactor:
				color = Color.pink;
				break;
			case RoboticsFactory:
			case NaniteFactory:
			case Shipyard:
			case ResearchLab:
			case IntergalacticResearchNetwork:
				color = Color.yellow;
				break;
			default:
				break;
			}
			if (color != null) {
				upgradeColors.put(upgrade, color);
			}
		}
	}

	private void genRoiSequence(Account account, RoiSequenceGenerator sequenceGenerator) {
		Format<Double> dblFormat = Format.doubleFormat(3).build();
		ObservableCollection<RoiSequenceElement> sequence = ObservableCollection.build(RoiSequenceElement.class).build(); // Safe
		JDialog dialog = WindowPopulation
			.populateDialog(new JDialog(SwingUtilities.getWindowAncestor(this), "ROI Sequence", ModalityType.MODELESS), //
				Observable.empty(), true)//
			.withVContent(panel -> panel.fill().fillV()//
				.addLabel("Status:", sequenceGenerator.getStatus(), Format.TEXT, null)//
				.addLabel("Stage Progress:", sequenceGenerator.getProgress(), new Format<Integer>() {
					@Override
					public void append(StringBuilder text, Integer value) {
						if (value == null) {
							return;
						}
						text.append(value).append(" of ").append(sequence.size()).append(" (");
						if (sequence.isEmpty()) {
							text.append('0');
						} else {
							double pc = value * 100.0 / sequence.size();
							dblFormat.append(text, pc);
						}
						text.append("%)");
					}

					@Override
					public Integer parse(CharSequence text) throws ParseException {
						throw new IllegalStateException();
					}
				}, null)//
				.addLabel("Sequence Time:", sequenceGenerator.getLifetimeMetric(), Format.DURATION, null)//
				.addTable(sequence,
					table -> table.fill().fillV()//
						.withIndexColumn("#", col -> col.withWidths(20, 30, 60))//
						.withColumn("Upgrade", AccountUpgradeType.class, el -> el.upgrade, col -> {
							col.decorate((cell, deco) -> {
								Color borderColor = upgradeColors.get(cell.getCellValue());
								if (borderColor != null) {
									deco.withLineBorder(borderColor, 2, false);
								}
							});
						})//
						.withColumn("Planet", String.class, el -> {
							if (el.planetIndex < 0) {
								return "";
							} else if (el.planetIndex < account.getPlanets().getValues().size()) {
								return account.getPlanets().getValues().get(el.planetIndex).getName();
							} else {
								return "Planet " + (el.planetIndex + 1);
							}
						}, null)//
						.withColumn("Level", Integer.class, el -> el.getTargetLevel(), null)//
						.withColumn("ROI", Duration.class, el -> el.roi == 0 ? null : Duration.ofHours(el.roi),
							col -> col.formatText(d -> d == null ? "" : QommonsUtils.printDuration(d, true)))//
						.withColumn("Time", Duration.class, el -> el.getTime() == 0 ? null : Duration.ofSeconds(el.getTime()),
							col -> col.formatText(d -> d == null ? "" : QommonsUtils.printDuration(d, true)))//
			)).getWindow();
		dialog.setSize(500, 700);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
		QommonsTimer.getCommonInstance().offload(() -> {
			sequenceGenerator.produceSequence(sequence);
			List<RoiCompoundSequenceElement> condensed = RoiSequenceGenerator.condense(sequence);
			EventQueue.invokeLater(() -> displayRoiSequence(condensed, account, sequenceGenerator));
			// dialog.setVisible(false);
		});
	}

	private void displayRoiSequence(List<RoiCompoundSequenceElement> condensed, Account account, RoiSequenceGenerator sequenceGenerator) {
		List<String> planetNames = QommonsUtils.map(account.getPlanets().getValues(), Planet::getName, true);
		JDialog dialog = WindowPopulation
			.populateDialog(new JDialog(SwingUtilities.getWindowAncestor(this), "ROI Sequence", ModalityType.MODELESS), //
				Observable.empty(), true)//
			.withVContent(panel -> panel.fill().fillV()//
				.addLabel("Account:", ObservableValue.of(account.getName()), Format.TEXT, null)//
				.addLabel("Sequence Time:", ObservableValue.of(sequenceGenerator.getLifetimeMetric().get()), Format.DURATION, null)//
				.addTable(ObservableCollection.of(RoiCompoundSequenceElement.class, condensed),
					table -> table.fill().fillV()//
						.withIndexColumn("#", col -> col.withWidths(20, 30, 60))//
						.withColumn("Upgrade", AccountUpgradeType.class, el -> el.upgrade, col -> {
							col.decorate((cell, deco) -> {
								Color borderColor = upgradeColors.get(cell.getCellValue());
								if (borderColor != null) {
									deco.withLineBorder(borderColor, 2, false);
								}
							});
						})//
						.withColumn("Level", Integer.class, el -> el.level, null)//
						.withColumn("Planets", String.class, el -> {
							if (el.planetIndexes == null) {
								return "";
							}
							StringBuilder planets = new StringBuilder();
							for (int p = 0; p < el.planetIndexes.length; p++) {
								if (p > 0) {
									planets.append(' ');
								}
								if (el.planetIndexes[p] < planetNames.size()) {
									planets.append(planetNames.get(el.planetIndexes[p]));
								} else {
									planets.append("Planet "+(el.planetIndexes[p]+1));
								}
							}
							return planets.toString();
						}, col -> col.withWidths(50, 200, 2000))//
						.withColumn("Time", Duration.class, el -> Duration.ofSeconds(el.time),
							col -> col.formatText(d -> d == null ? "" : QommonsUtils.printDuration(d, true)))//
			)).getWindow();
		dialog.setSize(500, 700);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}
}
