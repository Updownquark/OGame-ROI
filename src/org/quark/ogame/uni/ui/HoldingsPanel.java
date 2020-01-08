package org.quark.ogame.uni.ui;

import java.time.Duration;

import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableCellEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.Nameable;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.Holding;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.UpgradeType;

import com.google.common.reflect.TypeToken;

public class HoldingsPanel {
	private final OGameUniGui theUniGui;

	private final ObservableCollection<Holding> theHoldings;
	private final Holding theTotalHolding;
	private final Holding theProductionTimeHolding;

	public HoldingsPanel(OGameUniGui uniGui) {
		theUniGui = uniGui;

		ObservableCollection<Holding> flatHoldings = ObservableCollection.flattenValue(theUniGui.getSelectedAccount().map(
			new TypeToken<ObservableCollection<Holding>>() {}, acct -> (ObservableCollection<Holding>) acct.getHoldings().getValues()));
		theTotalHolding = new SyntheticHolding() {
			@Override
			public String getName() {
				return "Total";
			}

			@Override
			public Nameable setName(String name) {
				throw new UnsupportedOperationException();
			}

			@Override
			public long getMetal() {
				long amount = 0;
				try (Transaction t = flatHoldings.lock(false, null)) {
					for (Holding h : flatHoldings) {
						amount += h.getMetal();
					}
				}
				return amount;
			}

			@Override
			public long getCrystal() {
				long amount = 0;
				try (Transaction t = flatHoldings.lock(false, null)) {
					for (Holding h : flatHoldings) {
						amount += h.getCrystal();
					}
				}
				return amount;
			}

			@Override
			public long getDeuterium() {
				long amount = 0;
				try (Transaction t = flatHoldings.lock(false, null)) {
					for (Holding h : flatHoldings) {
						amount += h.getDeuterium();
					}
				}
				return amount;
			}
		};
		theProductionTimeHolding = new SyntheticHolding() {
			@Override
			public String getName() {
				return "Production Time";
			}

			@Override
			public long getMetal() {
				long holdings = theTotalHolding.getMetal();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getMetal().totalProduction;
				}
				return holdings * 3600 / production; // Seconds
			}

			@Override
			public long getDeuterium() {
				long holdings = theTotalHolding.getCrystal();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getCrystal().totalProduction;
				}
				return holdings * 3600 / production; // Seconds
			}

			@Override
			public long getCrystal() {
				long holdings = theTotalHolding.getDeuterium();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getDeuterium().totalProduction;
				}
				return holdings * 3600 / production; // Seconds
			}
		};
		ObservableCollection<Holding> synthHoldings = ObservableCollection.build(TypeTokens.get().of(Holding.class)).safe(false).build()
			.with(theTotalHolding, theProductionTimeHolding);
		ElementId totalId = synthHoldings.getElement(0).getElementId();
		ElementId productionId = synthHoldings.getElement(1).getElementId();
		flatHoldings.simpleChanges().act(__ -> {
			synthHoldings.mutableElement(totalId).set(theTotalHolding);
			synthHoldings.mutableElement(productionId).set(theProductionTimeHolding);
		});
		theUniGui.getPlanets().simpleChanges().act(__ -> synthHoldings.mutableElement(productionId).set(theProductionTimeHolding));
		theHoldings = ObservableCollection.flattenCollections(TypeTokens.get().of(Holding.class), flatHoldings, synthHoldings).collect();
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		ObservableCollection<AccountUpgradeType> holdingTypes = ObservableCollection.build(TypeTokens.get().of(AccountUpgradeType.class))
			.safe(false).build();
		holdingTypes.add(null);
		for (AccountUpgradeType type : AccountUpgradeType.values()) {
			switch (type.type) {
			case Building:
			case Research:
				holdingTypes.add(type);
				break;
			case ShipyardItem:
				break; // Shipyard item's can't be canceled, so they can't be used for stashing resources
			}
		}
		panel.addHPanel(null, new JustifiedBoxLayout(false).mainJustified().crossJustified(),
			split -> split.fill().fillV()//
				.addVPanel(holdingsPanel -> holdingsPanel.fill().fillV()//
					.addTable(theHoldings, holdingsTable -> holdingsTable.fill().fillV()//
						.withColumn("Name", String.class, Holding::getName, nameCol -> nameCol.withMutation(nameMutator -> {
							nameMutator.mutateAttribute(Holding::setName).editableIf((h, n) -> !(h instanceof SyntheticHolding))
								.withEditor(ObservableCellEditor.createTextEditor(SpinnerFormat.NUMERICAL_TEXT));
						}))//
						.withColumn("Type", AccountUpgradeType.class, Holding::getType, typeCol -> typeCol.withMutation(typeMutator -> {
							typeMutator.mutateAttribute((h, t) -> {
								h.setType(t);
								if (t == null) {//
								} else if (t.type == UpgradeType.Research) {
									int preLevel = theUniGui.getSelectedAccount().get().getResearch().getResearchLevel(t.research);
									h.setLevel(preLevel + 1);
									UpgradeCost cost = theUniGui.getRules().get().economy().getUpgradeCost(//
										theUniGui.getSelectedAccount().get(), null, t, preLevel, preLevel + 1);
									h.setMetal(cost.getMetal());
									h.setCrystal(cost.getCrystal());
									h.setDeuterium(cost.getDeuterium());
								} else if (h.getLevel() <= 0) {
									h.setLevel(1);
								}
							}).editableIf((h, n) -> !(h instanceof SyntheticHolding))//
								.asCombo(t -> t == null ? "None" : t.toString(), holdingTypes);
						}))//
						.withColumn("#", Integer.class, Holding::getLevel, levelCol -> levelCol.formatText((h, lvl) -> {
							if (h.getType() == null) {
								return "";
							} else {
								return String.valueOf(lvl);
							}
						}).withWidths(20, 20, 30).withMutation(lvlMutator -> lvlMutator.mutateAttribute((h, lvl) -> {
							h.setLevel(lvl);
							UpgradeCost cost = theUniGui.getRules().get().economy().getUpgradeCost(//
								theUniGui.getSelectedAccount().get(),
								theUniGui.getSelectedAccount().get().getPlanets().getValues().peekFirst(), h.getType(), lvl - 1, lvl);
							h.setMetal(cost.getMetal());
							h.setCrystal(cost.getCrystal());
							h.setDeuterium(cost.getDeuterium());
						}).editableIf((h, lvl) -> {
							if (h instanceof SyntheticHolding) {
								return false;
							} else {
								return h.getType()!=null;
							}
						}).filterAccept((h, lvl) -> lvl > 0 ? null : "Level must be positive")))//
						.withColumn("Metal (KK)", Double.class, h->{
							if(h==theProductionTimeHolding) {
								return h.getMetal()*1.0;
							} else {
								return h.getMetal()/1E6;
							}
						}, metalCol -> metalCol.formatText((h, m) -> {
							if (h == theProductionTimeHolding) {
								return OGameUniGui.printUpgradeTime(Duration.ofSeconds(m.longValue()));
							} else {
								return OGameUtils.TWO_DIGIT_FORMAT.format(m);
							}
						}).withMutation(metalMutation -> {
							metalMutation.mutateAttribute((h, m) -> h.setMetal((long) (m * 1E6))).editableIf((h, n) -> {
								if (h instanceof SyntheticHolding) {
									return false;
								} else {
									return h.getType() == null;
								}
							}).asText(Format.doubleFormat("0.00"));
						}))//
						.withColumn("Crystal (KK)", Double.class, h -> {
							if (h == theProductionTimeHolding) {
								return h.getCrystal() * 1.0;
							} else {
								return h.getCrystal() / 1E6;
							}
						}, crystalCol -> crystalCol.formatText((h, d) -> {
							if (h == theProductionTimeHolding) {
								return OGameUniGui.printUpgradeTime(Duration.ofSeconds(d.longValue()));
							} else {
								return OGameUtils.TWO_DIGIT_FORMAT.format(d);
							}
						}).withMutation(crystalMutation -> {
							crystalMutation.mutateAttribute((h, c) -> h.setCrystal((long) (c * 1E6))).editableIf((h, n) -> {
								if (h instanceof SyntheticHolding) {
									return false;
								} else {
									return h.getType() == null;
								}
							}).asText(Format.doubleFormat("0.00"));
						}))//
						.withColumn("Deuterium (KK)", Double.class, h -> {
							if (h == theProductionTimeHolding) {
								return h.getDeuterium() * 1.0;
							} else {
								return h.getDeuterium() / 1E6;
							}
						}, metalCol -> metalCol.formatText((h, d) -> {
							if (h == theProductionTimeHolding) {
								return OGameUniGui.printUpgradeTime(Duration.ofSeconds(d.longValue()));
							} else {
								return OGameUtils.TWO_DIGIT_FORMAT.format(d);
							}
						}).withMutation(metalMutation -> {
							metalMutation.mutateAttribute((h, d) -> h.setDeuterium((long) (d * 1E6))).editableIf((h, n) -> {
								if (h instanceof SyntheticHolding) {
									return false;
								} else {
									return h.getType() == null;
								}
							}).asText(Format.doubleFormat("0.00"));
						}))//
						.withAdd(
							() -> theUniGui.getSelectedAccount().get().getHoldings().create()//
								.with(Holding::getName, "").create().get(),
							addAction -> addAction.modifyButton(addBtn -> addBtn
								.disableWith(theUniGui.getSelectedAccount().map(acct -> acct == null ? "No Account Selected" : null))))//
						.withRemove(holdings -> theUniGui.getSelectedAccount().get().getHoldings().getValues().removeAll(holdings),
							remAction -> remAction.modifyButton(addBtn -> addBtn
								.disableWith(theUniGui.getSelectedAccount().map(acct -> acct == null ? "No Account Selected" : null))))//
				)//
				)//
				.addVPanel(tradePanel -> tradePanel.fill().fillV()//
			// TODO
			)//
		);
	}

	private static abstract class SyntheticHolding implements Holding {
		@Override
		public Nameable setName(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AccountUpgradeType getType() {
			return null;
		}

		@Override
		public Holding setType(AccountUpgradeType type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getLevel() {
			return 0;
		}

		@Override
		public Holding setLevel(int level) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Holding setMetal(long metal) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Holding setCrystal(long crystal) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Holding setDeuterium(long deuterium) {
			throw new UnsupportedOperationException();
		}
	}
}
