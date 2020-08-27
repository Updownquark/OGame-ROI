package org.quark.ogame.uni.ui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.ArrayUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.io.Format;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.AccountUpgradeType;
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
		panel.addTable(theUpgrades, upgradeTable -> upgradeTable.fill()//
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
				fromCol -> fromCol.withWidths(25, 35, 40)//
					.formatText((u, i) -> u.getUpgrade() == null ? "" : ("" + i)))//
			.withColumn("To", int.class, upgrade -> upgrade.getTo(),
				toCol -> toCol.withWidths(25, 35, 40)//
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
				return OGameUniGui.printUpgradeTime(upgrade.getCost().getUpgradeTime());
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
				roiCol -> roiCol.formatText(roi -> roi == null ? "" : QommonsUtils.printDuration(roi, true)).withWidths(50, 100, 150))//
				.withMultiAction(upgrades -> sortUpgrades(upgrades), action -> action//
					.allowWhenMulti(items -> canSortUpgrades(items), null).modifyButton(button -> button.withText("Sort by ROI")))
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
}
