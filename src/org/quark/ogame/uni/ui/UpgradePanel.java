package org.quark.ogame.uni.ui;

import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.EventQueue;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableTableModel;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.TableContentControl;
import org.observe.util.swing.WindowPopulation;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.DurationComponentType;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.qommons.threading.QommonsTimer;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.roi.RoiAccount;
import org.quark.ogame.roi.RoiAccount.RoiPlanet;
import org.quark.ogame.roi.RoiCompoundSequenceElement;
import org.quark.ogame.roi.RoiSequenceCoreElement;
import org.quark.ogame.roi.RoiSequenceElement;
import org.quark.ogame.roi.RoiSequenceGenerator;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.OGameEconomyRuleSet.FullProduction;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.ui.OGameUniGui.PlannedAccountUpgrade;

import com.google.common.reflect.TypeToken;

public class UpgradePanel extends JPanel {
	public static final TimeUtils.RelativeTimeFormat DURATION_FORMAT = TimeUtils.relativeFormat()//
		.abbreviated(true, false).withMaxPrecision(DurationComponentType.Second).withMaxElements(4).withWeeks();

	private final OGameUniGui theUniGui;

	private final PlannedAccountUpgrade theTotalUpgrade;

	public UpgradePanel(OGameUniGui uniGui) {
		theUniGui = uniGui;

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
	}

	public PlannedAccountUpgrade getTotalUpgrades() {
		return theTotalUpgrade;
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		SettableValue<TableContentControl> uiFilter = SettableValue.build(TableContentControl.class).safe(false)
			.withValue(TableContentControl.DEFAULT).build();
		ObservableValue<TableContentControl> tableFilter = uiFilter.map(filter -> {
			if (filter == null || filter == TableContentControl.DEFAULT) {
				return filter;
			} else {
				return filter.or(TableContentControl.of("Type", v -> "Total".equals(v.toString())));
			}
		});

		UpgradeCost[] planetTotalCost = new UpgradeCost[] { UpgradeCost.ZERO };
		UpgradeCost[] selectedTotalCost = new UpgradeCost[] { UpgradeCost.ZERO };
		UpgradeCost[] totalCost = new UpgradeCost[] { UpgradeCost.ZERO };
		ObservableCollection<PlannedAccountUpgrade> upgrades;
		PlannedAccountUpgrade planetTotalUpgrade = theUniGui.new PlannedAccountUpgrade(null) {
			@Override
			public UpgradeCost getCost() {
				return planetTotalCost[0];
			}
		};
		PlannedAccountUpgrade selectedTotalUpgrade = theUniGui.new PlannedAccountUpgrade(null) {
			@Override
			public UpgradeCost getCost() {
				return selectedTotalCost[0];
			}
		};
		PlannedAccountUpgrade filteredTotalUpgrade = theUniGui.new PlannedAccountUpgrade(null) {
			@Override
			public UpgradeCost getCost() {
				return totalCost[0];
			}
		};

		ObservableCollection<PlannedAccountUpgrade> selectedUpgrades = ObservableCollection.build(PlannedAccountUpgrade.class).safe(false)
			.build();
		SettableValue<PlannedAccountUpgrade> selectedTotalUpgradeV = SettableValue.build(PlannedAccountUpgrade.class).safe(false).build();
		TypeToken<PlannedAccountUpgrade> upgradeType = TypeTokens.get().of(PlannedAccountUpgrade.class);
		boolean[] planetCallbackLock = new boolean[1];
		ObservableCollection<PlannedAccountUpgrade> totalUpgrades = ObservableCollection.flattenCollections(upgradeType,
			ObservableCollection.flattenValue(theUniGui.getSelectedPlanet()
				.map(sp -> sp == null ? ObservableCollection.of(upgradeType) : ObservableCollection.of(upgradeType, planetTotalUpgrade)))
				.flow().refresh(theUniGui.getSelectedPlanet().noInitChanges()).collect(), //
			// TODO Can't get the selected upgrade total to not throw reentrant exceptions
			ObservableCollection
				.flattenValue(selectedTotalUpgradeV
					.map(ug -> ug == null ? ObservableCollection.of(upgradeType) : ObservableCollection.of(upgradeType, ug)))
				.flow().refresh(selectedTotalUpgradeV.noInitChanges().filter(__ -> !planetCallbackLock[0])).collect(), //
			ObservableCollection.of(upgradeType, filteredTotalUpgrade)//
		).collect();
		Runnable[] calcCosts = new Runnable[1];
		theUniGui.getSelectedPlanet().changes().act(evt -> {
			if (calcCosts[0] != null) {
				calcCosts[0].run();
			}
		});
		upgrades = ObservableCollection.flattenCollections(TypeTokens.get().of(PlannedAccountUpgrade.class), //
			theUniGui.getUpgrades().flow().refresh(theUniGui.getSelectedPlanet().noInitChanges()).collect(), //
			totalUpgrades).collect();

		SettableValue<PlannedAccountUpgrade> selection = SettableValue.build(PlannedAccountUpgrade.class).safe(false).build();
		CausableKey key = Causable.key((cause, values) -> {
			PlannedAccountUpgrade upgrade = selection.get();
			if (planetCallbackLock[0] || upgrade == null || upgrade.getPlanet() == null) {
				return;
			}
			PlanetWithProduction planet = theUniGui.getSelectedPlanet().get();
			if (planet == null || planet.planet == null || planet.planet != upgrade.getPlanet()) {
				EventQueue.invokeLater(() -> {
					if (planetCallbackLock[0]) {
						return;
					}
					Account account = theUniGui.getSelectedAccount().get();
					if (account == null) {
						return;
					}
					int index = account.getPlanets().getValues().indexOf(upgrade.getPlanet());
					if (index < 0) {
						return;
					}
					if (calcCosts[0] != null) {
						calcCosts[0].run();
					}
					planetCallbackLock[0] = true;
					try {
						theUniGui.getSelectedPlanet().set(theUniGui.getPlanets().get(index), null);
					} finally {
						planetCallbackLock[0] = false;
					}
				});
			}
		});
		selection.changes().act(evt -> evt.getRootCausable().onFinish(key));
		Format<Double> commaFormat = Format.doubleFormat("#,##0");
		TableBuilder<PlannedAccountUpgrade, ?>[] table = new TableBuilder[1];
		panel.addTextField("Filter:", uiFilter, TableContentControl.FORMAT,
			f -> f.fill().withTooltip(TableContentControl.TABLE_CONTROL_TOOLTIP).modifyEditor(tf -> tf.setCommitOnType(true)));
		panel.addTable(upgrades, upgradeTable -> {
			table[0] = upgradeTable;
			upgradeTable.fill().fillV()//
				.dragSourceRow(null).dragAcceptRow(null)// Make the rows draggable
				.withFiltering(tableFilter)//
				.withSelection(selection, false)//
				.withSelection(selectedUpgrades)//
				.withColumn("Planet", String.class, upgrade -> {
					if (upgrade == filteredTotalUpgrade) {
						return "Total";
					} else if (upgrade == planetTotalUpgrade) {
						PlanetWithProduction selectedPlanet = theUniGui.getSelectedPlanet().get();
						return ((selectedPlanet == null || selectedPlanet.planet == null) ? "Planet" : selectedPlanet.planet.getName())
							+ " Total";
					} else if (upgrade == selectedTotalUpgrade) {
						return "Selected Total";
					} else if (upgrade.getPlanet() != null) {
						return upgrade.getPlanet().getName() + (upgrade.getUpgrade().isMoon() ? " Moon" : "");
					} else {
						return "";
					}
				}, planetCol -> {
					planetCol.decorate((cell, d) -> {
						PlanetWithProduction p = theUniGui.getSelectedPlanet().get();
						if (p != null && (cell.getModelValue().getPlanet() == p.planet || cell.getModelValue() == selectedTotalUpgrade
							|| cell.getModelValue() == planetTotalUpgrade || cell.getModelValue() == filteredTotalUpgrade)) {
							d.bold();
						}
					}).withWidths(80, 150, 300);
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
					return DURATION_FORMAT.print(upgrade.getCost().getUpgradeTime());
				}, timeCol -> timeCol.withWidths(40, 100, 120))//
				.withColumn("Cargoes", Long.class, upgrade -> {
					if (upgrade.getCost() == null) {
						return null;
					}
					long cost = upgrade.getCost().getTotal();
					int cargoSpace = theUniGui.getRules().get().fleet().getCargoSpace(ShipyardItemType.LargeCargo,
						theUniGui.getSelectedAccount().get());
					return (long) Math.ceil(cost * 1.0 / cargoSpace);
				}, cargoCol -> cargoCol.formatText(i -> i == null ? "" : commaFormat.format(i * 1.0)).withWidths(40, 50, 80))//
				.withColumn("Value", String.class,
					upgrade -> upgrade.getCost() == null ? ""
						: OGameUtils.printResourceAmount(
							upgrade.getCost().getMetalValue(theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios())),
					valueCol -> valueCol.decorate((cell, d) -> {
						if (cell.getModelValue().getUpgrade() == null) {
							d.bold();
						}
					}))//
				.withColumn("ROI", Duration.class, upgrade -> upgrade.getROI(), //
					roiCol -> roiCol.formatText(roi -> roi == null ? "" : DURATION_FORMAT.print(roi)).withWidths(50, 100, 150))//
				.withColumn("Type", String.class, this::getType, col -> col.withWidths(50, 80, 150))//
				.withColumn("Sub-Type", String.class, this::getSubType, col -> col.withWidths(50, 80, 150))//
			// .withMultiAction(upgrades -> sortUpgrades(upgrades), action -> action//
			// .allowWhenMulti(items -> canSortUpgrades(items), null).modifyButton(button -> button.withText("Sort by ROI")))
			;
		});
		ObservableCollection<PlannedAccountUpgrade> rows = ((ObservableTableModel<PlannedAccountUpgrade>) table[0].getEditor().getModel())
			.getRows();
		calcCosts[0] = () -> {
			totalCost[0] = planetTotalCost[0] = UpgradeCost.ZERO;
			PlanetWithProduction selectedPlanet = theUniGui.getSelectedPlanet().get();
			for (PlannedAccountUpgrade row : rows) {
				if (row == planetTotalUpgrade || row == selectedTotalUpgrade || row == filteredTotalUpgrade) {
					continue;
				}
				UpgradeCost cost = row.getCost();
				if (cost != null) {
					totalCost[0] = totalCost[0].plus(cost);
					if (selectedPlanet != null && row.getPlanet() == selectedPlanet.planet) {
						planetTotalCost[0] = planetTotalCost[0].plus(cost);
					}
				}
			}
		};
		rows.simpleChanges().act(__ -> {
			calcCosts[0].run();
		});
		selectedUpgrades.simpleChanges().act(__ -> {
			EventQueue.invokeLater(() -> {
				selectedTotalCost[0] = UpgradeCost.ZERO;
				for (PlannedAccountUpgrade row : selectedUpgrades) {
					if (row == planetTotalUpgrade || row == selectedTotalUpgrade || row == filteredTotalUpgrade) {
						continue;
					}
					UpgradeCost cost = row.getCost();
					if (cost != null) {
						selectedTotalCost[0] = selectedTotalCost[0].plus(cost);
					}
				}
				PlannedAccountUpgrade target = selectedUpgrades.isEmpty() ? null : selectedTotalUpgrade;
				selectedTotalUpgradeV.set(target, null);
			});
		});
		calcCosts[0].run();
		panel.addButton("Generate ROI Sequence", __ -> showRoiSequenceConfigPanel(), null);
	}

	private String getType(PlannedAccountUpgrade upgrade) {
		if (upgrade.getUpgrade() == null) {
			return "Total";
		}
		switch (upgrade.getUpgrade().getType().type) {
		case Building:
			return "Building";
		case Research:
			return "Research";
		case ShipyardItem:
			return "Ship/Defense";
		}
		throw new IllegalStateException(upgrade.getUpgrade().getType().name());
	}

	private String getSubType(PlannedAccountUpgrade upgrade) {
		if (upgrade.getUpgrade() == null) {
			return "";
		}
		switch (upgrade.getUpgrade().getType().type) {
		case Building:
			BuildingType building = upgrade.getUpgrade().getType().building;
			if (building.isMine() != null) {
				return "Mine";
			} else if (building.isStorage() != null) {
				return "Storage";
			}
			switch (upgrade.getUpgrade().getType().building) {
			case SolarPlant:
			case FusionReactor:
				return "Energy";
			default:
				return "Facility";
			}
		case Research:
			switch (upgrade.getUpgrade().getType().research) {
			case Armor:
			case Shielding:
			case Weapons:
				return "Military";
			case Combustion:
			case Impulse:
			case Hyperdrive:
				return "Drive";
			case Graviton:
			case Ion:
			case Laser:
			case Hyperspace:
				return "Requirement";
			default:
				return "Other";
			}
		case ShipyardItem:
			ShipyardItemType ship = upgrade.getUpgrade().getType().shipyardItem;
			if (ship.name().endsWith("Missile")) {
				return "Missile";
			}
			if (ship.defense) {
				return "Defense";
			}
			switch (ship) {
			case BattleCruiser:
			case BattleShip:
			case Bomber:
			case Cruiser:
			case DeathStar:
			case Destroyer:
			case HeavyFighter:
			case LightFighter:
			case Reaper:
			case PathFinder:
				return "Combat";
			default:
				return "Civil";
			}
		}
		throw new IllegalStateException(upgrade.getUpgrade().getType().name());
	}

	private void sortUpgrades(BetterList<PlannedAccountUpgrade> allUpgrades, List<? extends PlannedAccountUpgrade> toSort) {
		if (toSort.size() <= 1) {
			return;
		}
		// The selected upgrades should be in model order, and contiguous
		CollectionElement<PlannedAccountUpgrade> target = allUpgrades.getElement(toSort.get(0), true);
		List<PlannedAccountUpgrade> sorted = new ArrayList<>(toSort);
		ElementId[] movedIds = new ElementId[toSort.size()];
		ElementId tempId = target.getElementId();
		for (int i = 0; i < toSort.size(); i++) {
			movedIds[i] = tempId;
			tempId = allUpgrades.getAdjacentElement(tempId, true).getElementId();
		}
		ArrayUtils.sort(toSort.toArray(new PlannedAccountUpgrade[toSort.size()]), new ArrayUtils.SortListener<PlannedAccountUpgrade>() {
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
		CollectionElement<PlannedAccountUpgrade> after = allUpgrades.getAdjacentElement(target.getElementId(), false);
		for (int i = 0; i < sorted.size(); i++) {
			after = allUpgrades.move(movedIds[i], after.getElementId(), null, true, null);
		}
	}

	private String canSortUpgrades(BetterList<PlannedAccountUpgrade> allUpgrades, List<? extends PlannedAccountUpgrade> upgrades) {
		switch (upgrades.size()) {
		case 0:
			return "Select a contiguous sequence of upgrades to sort";
		case 1:
			return "A single upgrade cannot be sorted";
		}
		boolean found = false, done = false;
		for (PlannedAccountUpgrade upgrade : allUpgrades) {
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
		ObservableCollection<RoiSequenceCoreElement> sequence = ObservableCollection.build(RoiSequenceCoreElement.class).build(); // Safe
		SettableValue<RoiSequenceCoreElement> selected = SettableValue.build(RoiSequenceCoreElement.class).safe(false).build();
		JDialog dialog = WindowPopulation
			.populateDialog(new JDialog(SwingUtilities.getWindowAncestor(this), "ROI Sequence", ModalityType.MODELESS), //
				Observable.empty(), true)//
			.withVContent(panel -> panel.fill().fillV()//
				.addLabel("Status:", sequenceGenerator.getStatus(), Format.TEXT, null)//
				.addLabel("Stage Progress:", sequenceGenerator.getProgress().refresh(sequence.observeSize().noInitChanges()),
					new Format<Integer>() {
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
				.addLabel("Sequence Time:", sequenceGenerator.getLifetimeMetric(), SpinnerFormat.flexDuration(DURATION_FORMAT), null)//
				.addTable(sequence,
					table -> table.fill().fillV().withAdaptiveHeight(10, 15, 100).withSelection(selected, false)//
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
						.withColumn("ROI", Duration.class,
							el -> el.getRoi() <= 0 ? null : Duration.ofSeconds(Math.round(el.getRoi() * 3600)),
							col -> col.formatText(d -> d == null ? "" : DURATION_FORMAT.print(d)))//
						.withColumn("Time", Duration.class, el -> el.getTime() == 0 ? null : Duration.ofSeconds(el.getTime()),
							col -> col.formatText(d -> d == null ? "" : DURATION_FORMAT.print(d)))//
				)//
				.addHPanel(null, new JustifiedBoxLayout(false).mainCenter().crossJustified(),
					p -> p
						.addButton("Cancel", __ -> sequenceGenerator.cancel(),
							btn -> btn.disableWith(sequenceGenerator.isActive().map(a -> a == null ? "Sequence is not generating" : null)))//
						.addButton("Export", __ -> export(account, sequence),
							btn -> btn.disableWith(sequenceGenerator.isActive().map(a -> a != null ? "Sequence is generating" : null)))//
				)//
				.addVPanel(editor -> editor.fill().fillV().addVPanel(//
					p -> configureSequenceEditor(p.fill().fillV().visibleWhen(selected.map(s -> s != null)), account, sequenceGenerator,
						sequence, selected))))
			.getWindow();
		dialog.setSize(1100, 1100);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
		QommonsTimer.getCommonInstance().offload(() -> {
			sequenceGenerator.getEnergyType().set(//
				sequenceGenerator.getNewPlanetTemp().get() < -100 ? ProductionSource.Fusion : ProductionSource.Satellite, null);
			sequenceGenerator.produceSequence(sequence);
			// List<RoiCompoundSequenceElement> condensed = RoiSequenceGenerator.condense(sequence);
			// EventQueue.invokeLater(() -> displayRoiSequence(condensed, account, sequenceGenerator));
			// dialog.setVisible(false);
		});
	}

	private void configureSequenceEditor(PanelPopulator<?, ?> editor, Account account, RoiSequenceGenerator generator,
		ObservableCollection<RoiSequenceCoreElement> sequence, SettableValue<RoiSequenceCoreElement> selected) {
		RoiAccount roiAccount = new RoiAccount(generator, account);
		Map<AccountUpgradeType, Upgrade> upgrades = new HashMap<>();
		ObservableCollection<UpgradeDependency> dependencies = ObservableCollection.build(UpgradeDependency.class).safe(false).build();
		selected.changes().act(evt -> {
			RoiAccount acct = roiAccount; // Just putting this in a variable so I can inspect it while debugging inside the lambda
			dependencies.clear();
			if (evt.getNewValue() != null) {
				acct.reset();
				generator.init(roiAccount);
				for (RoiSequenceCoreElement el : sequence) {
					if (el == evt.getNewValue()) {
						acct.completeUpgrades().flush();
						upgrades.put(AccountUpgradeType.Astrophysics, tempUpgrade(acct, generator, AccountUpgradeType.Astrophysics));
						upgrades.put(AccountUpgradeType.Plasma, tempUpgrade(acct, generator, AccountUpgradeType.Plasma));
						upgrades.put(AccountUpgradeType.MetalMine, tempUpgrade(acct, generator, AccountUpgradeType.MetalMine));
						upgrades.put(AccountUpgradeType.CrystalMine, tempUpgrade(acct, generator, AccountUpgradeType.CrystalMine));
						upgrades.put(AccountUpgradeType.DeuteriumSynthesizer,
							tempUpgrade(acct, generator, AccountUpgradeType.DeuteriumSynthesizer));
						break;
					}
					generator.upgrade(acct, el, false);
				}
				addDependencies(evt.getNewValue(), dependencies);
			}
		});
		editor.addCollapsePanel(true, new JustifiedBoxLayout(true),
			cp -> cp.fill()//
				.withHeader(header -> header.addLabel(null, "Dependencies", lbl -> lbl.decorate(deco -> deco.bold().withFontSize(14))))//
				.addTable(dependencies, table -> {
					table.fill().withAdaptiveHeight(3, 7, 20)//
						.withColumn("Type", String.class, dep -> dep.dependencyType, null)//
						.withColumn("Upgrade", AccountUpgradeType.class, dep -> dep.upgrade.upgrade, null)//
						.withColumn("From", int.class, dep -> dep.upgrade.getLevel(roiAccount), null)//
						.withColumn("To", int.class, dep -> dep.upgrade.getTargetLevel(), null)//
						.withColumn("Metal", long.class, dep -> dep.upgrade.getCost().getMetal(), null)//
						.withColumn("Crystal", long.class, dep -> dep.upgrade.getCost().getCrystal(), null)//
						.withColumn("Deuterium", long.class, dep -> dep.upgrade.getCost().getDeuterium(), null)//
					;
				}));
		editor.addCollapsePanel(true, new JustifiedBoxLayout(true), cp -> cp.fill()//
			.withHeader(header -> header.addLabel(null, "Account Status", lbl -> lbl.decorate(deco -> deco.bold().withFontSize(14))))//
			.addTable(ObservableCollection.of(RoiAccount.class, roiAccount).flow().refresh(selected.noInitChanges()).collect(), acct -> {
				acct.fill().withAdaptiveHeight(1, 1, 1)//
					.withColumn("Planets", int.class, a -> a.getPlanets().getValues().size(), null)//
					.withColumn("Astro", int.class, a -> a.getResearch().getAstrophysics(), null)//
					.withColumn("Plasma", int.class, a -> a.getResearch().getPlasma(), null)//
					// TODO Energy, etc.
					.withColumn("Total Production", long.class, a -> a.getProduction(), null)//
				;
			}));
		SettableValue<AccountUpgradeType> selectedHypoUpgrade = SettableValue.build(AccountUpgradeType.class).safe(false).build();
		editor.addCollapsePanel(true, new JustifiedBoxLayout(true),
			cp -> cp.fill()//
				.withHeader(header -> header.addLabel(null, "Account Upgrades", lbl -> lbl.decorate(deco -> deco.bold().withFontSize(14))))//
				.addTable(ObservableCollection.of(AccountUpgradeType.class, AccountUpgradeType.Astrophysics, AccountUpgradeType.Plasma)
					.flow().refresh(selected.noInitChanges()).collect(), acct -> {
						acct.fill().withAdaptiveHeight(2, 2, 2).withSelection(selectedHypoUpgrade, false)//
							.withNameColumn(AccountUpgradeType::name, null, false, null)//
							.withColumn("M Cost", String.class, u -> {
								Upgrade ug = upgrades.get(u);
								return ug.upgrade == null ? "" : ("" + ug.upgrade.getTotalCost().getMetal());
							}, null)//
							.withColumn("C Cost", String.class, u -> {
								Upgrade ug = upgrades.get(u);
								return ug.upgrade == null ? "" : ("" + ug.upgrade.getTotalCost().getCrystal());
							}, null)//
							.withColumn("D Cost", String.class, u -> {
								Upgrade ug = upgrades.get(u);
								return ug.upgrade == null ? "" : ("" + ug.upgrade.getTotalCost().getDeuterium());
							}, null)//
							.withColumn("T Cost", String.class, u -> {
								Upgrade ug = upgrades.get(u);
								return ug.upgrade == null ? ""
									: ("" + ug.upgrade.getTotalCost().getMetalValue(account.getUniverse().getTradeRatios()));
							}, null)//
							.withColumn("New Prod", String.class, u -> {
								long prod = upgrades.get(u).newProduction;
								long delta = prod - roiAccount.getProduction();
								return prod + " (" + (delta < 0 ? "" : "+") + delta + ")";
							}, null)//
							.withColumn("ROI", String.class, u -> {
								Upgrade ug = upgrades.get(u);
								if (ug.upgrade == null) {
									return "";
								}
								return DURATION_FORMAT.print(Duration
									.ofSeconds((long) (ug.upgrade.getTotalCost().getMetalValue(roiAccount.getUniverse().getTradeRatios())
										/ (ug.newProduction - roiAccount.getProduction()) * 3600)));
							}, null)//
						;
					}));

		SettableValue<RoiPlanet> selectedPlanet = SettableValue.build(RoiPlanet.class).safe(false).build();
		selected.noInitChanges().act(evt -> {
			if (evt.getNewValue() == null || evt.getOldValue() == evt.getNewValue()) {
				return;
			}
			RoiPlanet planet = selectedPlanet.get();
			if (planet == null) {
				return;
			}
			for (RoiPlanet p : roiAccount.roiPlanets()) {
				if (p.getName().equals(planet.getName())) {
					selectedPlanet.set(p, evt);
					break;
				}
			}
		});
		editor.addCollapsePanel(true, new JustifiedBoxLayout(true),
			cp -> cp.fill()//
				.withHeader(header -> header.addLabel(null, "Planet Status", lbl -> lbl.decorate(deco -> deco.bold().withFontSize(14))))//
				.addTable(roiAccount.roiPlanets().flow().refresh(selected.noInitChanges()).collect(), planets -> {
					planets.fill().withAdaptiveHeight(5, 10, 17).withSelection(selectedPlanet, false)//
						.withNameColumn(Planet::getName, null, true, null)//
						.withColumn("M", int.class, Planet::getMetalMine, null)//
						.withColumn("C", int.class, Planet::getCrystalMine, null)//
						.withColumn("D", int.class, Planet::getDeuteriumSynthesizer, null)//
						.withColumn("M Prod", int.class, p -> p == null ? 0 : p.getProduction().metal, null)//
						.withColumn("C Prod", int.class, p -> p == null ? 0 : p.getProduction().crystal, null)//
						.withColumn("D Prod", int.class, p -> p == null ? 0 : p.getProduction().deuterium, null)//
						.withColumn("Prod Value", long.class, p -> p == null ? 0 : p.getProductionValue(), null)//
						.withColumn("Sats", int.class, Planet::getSolarSatellites, null)//
						.withColumn("Solar", int.class, Planet::getSolarPlant, null)//
						.withColumn("Fzn", int.class, Planet::getFusionReactor, null)//
						.withColumn("Crawler", int.class, Planet::getCrawlers, null)//
						.withColumn("Fzn Util", int.class, Planet::getFusionReactorUtilization, null)//
						.withColumn("Crawler Util", int.class, Planet::getCrawlerUtilization, null)//
					;
				}));
		selectedPlanet.noInitChanges().act(evt -> {
			if (evt.getNewValue() != null) {
				System.out.println(
					evt.getNewValue() + ": " + theUniGui.getRules().get().economy().getFullProduction(roiAccount, evt.getNewValue()));
			}
		});
		editor.addCollapsePanel(true, new JustifiedBoxLayout(true),
			cp -> cp.fill().fillV()//
				.withHeader(header -> header.addLabel(null, "Planet Upgrades", lbl -> lbl.decorate(deco -> deco.bold().withFontSize(14))))//
				.addVPanel(planetEditor -> {
					planetEditor.fill().fillV().visibleWhen(selectedPlanet.map(p -> p != null));
					planetEditor.addTable(ObservableCollection
						.of(AccountUpgradeType.class, AccountUpgradeType.MetalMine, AccountUpgradeType.CrystalMine,
							AccountUpgradeType.DeuteriumSynthesizer, AccountUpgradeType.Astrophysics, AccountUpgradeType.Plasma)
						.flow().refresh(selectedPlanet.noInitChanges()).collect(), planet -> {
							planet.fill().fillV().withAdaptiveHeight(5, 5, 5).withSelection(selectedHypoUpgrade, false)//
								.withNameColumn(AccountUpgradeType::name, null, false, null)//
								.withColumn("M Cost", String.class, u -> {
									PlanetUpgrade pu = upgrades.get(u).planetProductions
										.get(roiAccount.roiPlanets().indexOf(selectedPlanet.get()));
									return pu.upgrade == null ? "" : ("" + pu.upgrade.getTotalCost().getMetal());
								}, null)//
								.withColumn("C Cost", String.class, u -> {
									PlanetUpgrade pu = upgrades.get(u).planetProductions
										.get(roiAccount.roiPlanets().indexOf(selectedPlanet.get()));
									return pu.upgrade == null ? "" : ("" + pu.upgrade.getTotalCost().getCrystal());
								}, null)//
								.withColumn("D Cost", String.class, u -> {
									PlanetUpgrade pu = upgrades.get(u).planetProductions
										.get(roiAccount.roiPlanets().indexOf(selectedPlanet.get()));
									return pu.upgrade == null ? "" : ("" + pu.upgrade.getTotalCost().getDeuterium());
								}, null)//
								.withColumn("T Cost", String.class, u -> {
									PlanetUpgrade pu = upgrades.get(u).planetProductions
										.get(roiAccount.roiPlanets().indexOf(selectedPlanet.get()));
									return pu.upgrade == null ? ""
										: ("" + pu.upgrade.getTotalCost().getMetalValue(account.getUniverse().getTradeRatios()));
								}, null)//
								.withColumn("M Prod", String.class, u -> {
									RoiPlanet p = selectedPlanet.get();
									PlanetUpgrade pu = upgrades.get(u).planetProductions.get(roiAccount.roiPlanets().indexOf(p));
									long prod = pu.production.metal;
									long delta = prod - p.getProduction().metal;
									return prod + " (" + (delta < 0 ? "" : "+") + delta + ")";
								}, null)//
								.withColumn("C Prod", String.class, u -> {
									RoiPlanet p = selectedPlanet.get();
									PlanetUpgrade pu = upgrades.get(u).planetProductions.get(roiAccount.roiPlanets().indexOf(p));
									long prod = pu.production.crystal;
									long delta = prod - p.getProduction().crystal;
									return prod + " (" + (delta < 0 ? "" : "+") + delta + ")";
								}, null)//
								.withColumn("D Prod", String.class, u -> {
									RoiPlanet p = selectedPlanet.get();
									PlanetUpgrade pu = upgrades.get(u).planetProductions.get(roiAccount.roiPlanets().indexOf(p));
									long prod = pu.production.deuterium;
									long delta = prod - p.getProduction().deuterium;
									return prod + " (" + (delta < 0 ? "" : "+") + delta + ")";
								}, null)//
								.withColumn("Total Prod", String.class, u -> {
									RoiPlanet p = selectedPlanet.get();
									PlanetUpgrade pu = upgrades.get(u).planetProductions.get(roiAccount.roiPlanets().indexOf(p));
									long prod = pu.productonValue;
									long delta = prod - p.getProductionValue();
									return prod + " (" + (delta < 0 ? "" : "+") + delta + ")";
								}, null)//
								.withColumn("ROI", String.class, u -> {
									RoiPlanet p = selectedPlanet.get();
									PlanetUpgrade pu = upgrades.get(u).planetProductions.get(roiAccount.roiPlanets().indexOf(p));
									if (pu.upgrade == null) {
										return "";
									}
									return DURATION_FORMAT.print(Duration.ofSeconds(
										(long) (pu.upgrade.getTotalCost().getMetalValue(roiAccount.getUniverse().getTradeRatios())
											/ (pu.productonValue - p.getProductionValue()) * 3600)));
								}, null)//
							;
						});
				}));
		ObservableCollection<UpgradeDependency> hypoDependencies = ObservableCollection.build(UpgradeDependency.class).safe(false).build();
		selectedHypoUpgrade.refresh(selectedPlanet.noInitChanges()).changes().act(evt -> {
			hypoDependencies.clear();
			if (evt.getNewValue() == null) {
				return;
			}
			RoiSequenceCoreElement upgrade;
			if (evt.getNewValue().research != null) {
				upgrade = upgrades.get(evt.getNewValue()).upgrade;
			} else {
				upgrade = upgrades.get(evt.getNewValue()).planetProductions
					.get(roiAccount.roiPlanets().indexOf(selectedPlanet.get())).upgrade;
			}
			addDependencies(upgrade, hypoDependencies);
		});
		editor.addCollapsePanel(true, new JustifiedBoxLayout(true),
			cp -> cp.fill()//
				.withHeader(header -> header.addLabel(null, "Hypothetical Upgrade Dependencies",
					lbl -> lbl.decorate(deco -> deco.bold().withFontSize(14))))//
				.addTable(hypoDependencies, table -> {
					table.fill().withAdaptiveHeight(3, 7, 20)//
						.withColumn("Type", String.class, dep -> dep.dependencyType, null)//
						.withColumn("Upgrade", AccountUpgradeType.class, dep -> dep.upgrade.upgrade, null)//
						.withColumn("From", int.class, dep -> dep.upgrade.getLevel(roiAccount), null)//
						.withColumn("To", int.class, dep -> dep.upgrade.getTargetLevel(), null)//
						.withColumn("Metal", long.class, dep -> dep.upgrade.getCost().getMetal(), null)//
						.withColumn("Crystal", long.class, dep -> dep.upgrade.getCost().getCrystal(), null)//
						.withColumn("Deuterium", long.class, dep -> dep.upgrade.getCost().getDeuterium(), null)//
					;
				}));
	}

	private void addDependencies(RoiSequenceCoreElement coreEl, ObservableCollection<UpgradeDependency> dependencies) {
		for (RoiSequenceElement helper : coreEl.getPreHelpers()) {
			addDependency(helper, "Pre Helper", dependencies);
		}
		if (coreEl.upgrade != null) {
			addDependency(coreEl, "Core", dependencies);
		}
		for (RoiSequenceElement helper : coreEl.getPostHelpers()) {
			addDependency(helper, "Post Helper", dependencies);
		}
		for (RoiSequenceElement helper : coreEl.getAccessories()) {
			addDependency(helper, "Accessory", dependencies);
		}
	}

	private void addDependency(RoiSequenceElement dependency, String type, ObservableCollection<UpgradeDependency> dependencies) {
		for (RoiSequenceElement dep : dependency.getDependencies()) {
			addDependency(dep, "Dependency", dependencies);
		}
		dependencies.add(new UpgradeDependency(type, dependency));
	}

	static class Upgrade {
		final long newProduction;
		final RoiSequenceCoreElement upgrade;
		final List<PlanetUpgrade> planetProductions;

		Upgrade(long newProduction, RoiSequenceCoreElement upgrade, List<PlanetUpgrade> planetProductions) {
			this.newProduction = newProduction;
			this.upgrade = upgrade;
			this.planetProductions = planetProductions;
		}
	}

	static class PlanetUpgrade {
		final RoiSequenceCoreElement upgrade;
		final FullProduction production;
		final long productonValue;

		PlanetUpgrade(RoiSequenceCoreElement upgrade, FullProduction production, long productionValue) {
			this.upgrade = upgrade;
			this.production = production;
			this.productonValue = productionValue;
		}
	}

	static class UpgradeDependency {
		final String dependencyType;
		final RoiSequenceElement upgrade;

		UpgradeDependency(String dependencyType, RoiSequenceElement upgrade) {
			this.dependencyType = dependencyType;
			this.upgrade = upgrade;
		}
	}

	private Upgrade tempUpgrade(RoiAccount roiAccount, RoiSequenceGenerator generator, AccountUpgradeType upgrade) {
		long production;
		List<PlanetUpgrade> planetProductions = new ArrayList<>(roiAccount.roiPlanets().size());
		RoiSequenceCoreElement cost;
		if (upgrade.research != null) {
			try (Transaction branch = roiAccount.branch()) {
				cost = generator.coreUpgrade(roiAccount, -1, upgrade);
				for (RoiPlanet planet : roiAccount.roiPlanets()) {
					planetProductions.add(new PlanetUpgrade(null, planet.getProduction(), planet.getProductionValue()));
				}
				production = roiAccount.getProduction();
			}
		} else {
			production = 0;
			cost = null;
			for (int p = 0; p < roiAccount.roiPlanets().size(); p++) {
				RoiPlanet planet = roiAccount.roiPlanets().get(p);
				try (Transaction branch = roiAccount.branch()) {
					planetProductions.add(new PlanetUpgrade(//
						generator.coreUpgrade(roiAccount, p, upgrade), //
						planet.getProduction(), planet.getProductionValue()));
				}
			}
		}
		return new Upgrade(production, cost, planetProductions);
	}

	private void displayRoiSequence(List<RoiCompoundSequenceElement> condensed, Account account, RoiSequenceGenerator sequenceGenerator) {
		List<String> planetNames = QommonsUtils.map(account.getPlanets().getValues(), Planet::getName, true);
		JDialog dialog = WindowPopulation
			.populateDialog(new JDialog(SwingUtilities.getWindowAncestor(this), "ROI Sequence", ModalityType.MODELESS), //
				Observable.empty(), true)//
			.disposeOnClose(true)//
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
						.withColumn("Planet", String.class, el -> {
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
									planets.append("Planet " + (el.planetIndexes[p] + 1));
								}
							}
							return planets.toString();
						}, col -> col.withWidths(50, 200, 2000))//
						.withColumn("Accumulated Time", Duration.class, el -> Duration.ofSeconds(el.time),
							col -> col.formatText(d -> d == null ? "" : DURATION_FORMAT.print(d)))//
			)).getWindow();
		dialog.setSize(500, 700);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private final JFileChooser theChooser = new JFileChooser();

	private void export(Account account, List<RoiSequenceCoreElement> sequence) {
		if (theChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selected = theChooser.getSelectedFile();
		if (selected.exists()) {
			if (selected.isDirectory()) {
				JOptionPane.showMessageDialog(this, selected.getName() + " is a directory", "Directory Selected",
					JOptionPane.ERROR_MESSAGE);
				return;
			} else if (JOptionPane.OK_OPTION != JOptionPane.showConfirmDialog(this, selected.getName() + " exits. Overwrite?",
				"File Exists", JOptionPane.WARNING_MESSAGE)) {
				return;
			}
		}
		int astro = account.getResearch().getAstrophysics();
		int plasma = account.getResearch().getPlasma();
		int irn = account.getResearch().getIntergalacticResearchNetwork();
		Planet planet = account.getPlanets().getValues().peekFirst();
		int metal, crystal, deut, crawlers, robo, nanite, lab;
		if (planet == null) {
			metal = crystal = deut = crawlers = robo = nanite = lab = 0;
		} else {
			metal = planet.getMetalMine();
			crystal = planet.getCrystalMine();
			deut = planet.getDeuteriumSynthesizer();
			crawlers = planet.getCrawlers();
			robo = planet.getRoboticsFactory();
			nanite = planet.getNaniteFactory();
			lab = planet.getResearchLab();
		}
		TimeUtils.RelativeTimeFormat timeFormat = TimeUtils.relativeFormat().abbreviated(true, false).withWeeks()
			.withMaxPrecision(DurationComponentType.Second).withMaxElements(3);
		try (Writer w = new BufferedWriter(new FileWriter(selected))) {
			w.write("Upgrade,Level,ROI,Metal,Crystal,Deut,Astro,Plasma");
			// w.write(",Crawlers,Robotics,Nanite,Lab,IRN");
			w.write('\n');
			StringBuilder str = new StringBuilder();
			for (RoiSequenceCoreElement el : sequence) {
				if (el.planetIndex > 0) {
					continue;
				}
				switch (el.upgrade) {
				case MetalMine:
					metal = el.getTargetLevel();
					break;
				case CrystalMine:
					crystal = el.getTargetLevel();
					break;
				case DeuteriumSynthesizer:
					deut = el.getTargetLevel();
					break;
				case Astrophysics:
					astro = el.getTargetLevel();
					break;
				case Plasma:
					plasma = el.getTargetLevel();
					break;
				default:
				}
				for (RoiSequenceElement helper : el.getPreHelpers()) {
					switch (el.upgrade) {
					case Crawler:
						crawlers = helper.getTargetLevel();
						break;
					case RoboticsFactory:
						robo = helper.getTargetLevel();
						break;
					case NaniteFactory:
						nanite = helper.getTargetLevel();
						break;
					case ResearchLab:
						lab = helper.getTargetLevel();
						break;
					case IntergalacticResearchNetwork:
						irn = helper.getTargetLevel();
						break;
					default:
					}
				}
				for (RoiSequenceElement helper : el.getPostHelpers()) {
					switch (el.upgrade) {
					case Crawler:
						crawlers = helper.getTargetLevel();
						break;
					case RoboticsFactory:
						robo = helper.getTargetLevel();
						break;
					case NaniteFactory:
						nanite = helper.getTargetLevel();
						break;
					case ResearchLab:
						lab = helper.getTargetLevel();
						break;
					case IntergalacticResearchNetwork:
						irn = helper.getTargetLevel();
						break;
					default:
					}
				}
				str.setLength(0);
				str.append(el.upgrade).append(',').append(el.getTargetLevel()).append(',')
					.append(timeFormat.print(Duration.ofSeconds((long) (el.getRoi() * 3600)))).append(',')//
					.append(metal).append(',').append(crystal).append(',').append(deut).append(',').append(astro).append(',')
					.append(plasma);
				// str.append(',').append(crawlers).append(',').append(robo).append(',').append(nanite).append(',').append(lab).append(',')
				// .append(irn);
				str.append('\n');
				w.write(str.toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Could not write to " + selected.getName(), "Export Failed", JOptionPane.ERROR_MESSAGE);
		}
		JOptionPane.showMessageDialog(this, "ROI Sequence exported to " + selected.getName(), "Export Successful",
			JOptionPane.INFORMATION_MESSAGE);
	}
}
