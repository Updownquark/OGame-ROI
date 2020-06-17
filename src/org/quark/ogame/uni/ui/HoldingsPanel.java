package org.quark.ogame.uni.ui;

import java.awt.Color;
import java.time.Duration;

import org.observe.Observable;
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
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.Trade;
import org.quark.ogame.uni.TradeRatios;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.UpgradeType;

import com.google.common.reflect.TypeToken;

public class HoldingsPanel {
	private final OGameUniGui theUniGui;

	private final ObservableCollection<Holding> theHoldings;
	private final Holding theTotalHolding;
	private final Holding theProductionTimeHolding;
	private final Holding theUpgradeTimeHolding;

	private final ObservableCollection<Trade> theTrades;
	private final Trade theTotalTrade;
	private final Trade theUpgradeTimeTrade;
	private final SyntheticTrade theTradesNeeded;
	private final Trade theTotalTradesUpgradeTime;

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
					production += planet.getMetal().totalNet;
				}
				return holdings * 3600 / production; // Seconds
			}

			@Override
			public long getCrystal() {
				long holdings = theTotalHolding.getCrystal();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getCrystal().totalNet;
				}
				return holdings * 3600 / production; // Seconds
			}

			@Override
			public long getDeuterium() {
				long holdings = theTotalHolding.getDeuterium();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getDeuterium().totalNet;
				}
				return holdings * 3600 / production; // Seconds
			}
		};
		theUpgradeTimeHolding = new SyntheticHolding() {
			@Override
			public String getName() {
				return "Goal Completion";
			}

			@Override
			public long getMetal() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getMetal();
				if (needed == 0) {
					return 0;
				}
				needed -= theTotalHolding.getMetal();
				if (needed <= 0) {
					return 0;
				}
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getMetal().totalNet;
				}
				return needed * 3600 / production; // seconds
			}

			@Override
			public long getCrystal() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getCrystal();
				if (needed == 0) {
					return 0;
				}
				needed -= theTotalHolding.getCrystal();
				if (needed <= 0) {
					return 0;
				}
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getCrystal().totalNet;
				}
				return needed * 3600 / production; // seconds
			}

			@Override
			public long getDeuterium() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getDeuterium();
				if (needed == 0) {
					return 0;
				}
				needed -= theTotalHolding.getDeuterium();
				if (needed <= 0) {
					return 0;
				}
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getDeuterium().totalNet;
				}
				return needed * 3600 / production; // seconds
			}
		};
		ObservableCollection<Holding> synthHoldings = ObservableCollection.build(TypeTokens.get().of(Holding.class)).safe(false).build()
			.with(theTotalHolding, theProductionTimeHolding, theUpgradeTimeHolding);
		ElementId totalId = synthHoldings.getElement(0).getElementId();
		ElementId productionId = synthHoldings.getElement(1).getElementId();
		ElementId upgradeTimeId = synthHoldings.getElement(2).getElementId();
		flatHoldings.simpleChanges().act(__ -> {
			synthHoldings.mutableElement(totalId).set(theTotalHolding);
			synthHoldings.mutableElement(productionId).set(theProductionTimeHolding);
			synthHoldings.mutableElement(upgradeTimeId).set(theUpgradeTimeHolding);
		});
		theUniGui.getPlanets().simpleChanges().act(__ -> {
			synthHoldings.mutableElement(productionId).set(theProductionTimeHolding);
			synthHoldings.mutableElement(upgradeTimeId).set(theUpgradeTimeHolding);
		});
		theHoldings = ObservableCollection.flattenCollections(TypeTokens.get().of(Holding.class), flatHoldings, synthHoldings).collect();

		ObservableCollection<Trade> flatTrades = ObservableCollection.flattenValue(theUniGui.getSelectedAccount()
			.map(new TypeToken<ObservableCollection<Trade>>() {}, acct -> (ObservableCollection<Trade>) acct.getTrades().getValues()));
		theTotalTrade = new SyntheticTrade() {
			@Override
			public String getName() {
				return "Total Trades";
			}

			@Override
			public long getMetal() {
				long amount = 0;
				try (Transaction t = flatTrades.lock(false, null)) {
					for (Trade h : flatTrades) {
						amount += h.getMetal();
					}
				}
				return amount;
			}

			@Override
			public long getCrystal() {
				long amount = 0;
				try (Transaction t = flatTrades.lock(false, null)) {
					for (Trade h : flatTrades) {
						amount += h.getCrystal();
					}
				}
				return amount;
			}

			@Override
			public long getDeuterium() {
				long amount = 0;
				try (Transaction t = flatTrades.lock(false, null)) {
					for (Trade h : flatTrades) {
						amount += h.getDeuterium();
					}
				}
				return amount;
			}
		};
		theUpgradeTimeTrade = new SyntheticTrade() {
			@Override
			public String getName() {
				return "Goal Completion";
			}

			@Override
			public long getMetal() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getMetal();
				needed -= theTotalHolding.getMetal();
				needed -= theTotalTrade.getMetal();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getMetal().totalNet;
				}
				return needed * 3600 / production; // seconds
			}

			@Override
			public long getCrystal() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getCrystal();
				needed -= theTotalHolding.getCrystal();
				needed -= theTotalTrade.getCrystal();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getCrystal().totalNet;
				}
				return needed * 3600 / production; // seconds
			}

			@Override
			public long getDeuterium() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getDeuterium();
				needed -= theTotalHolding.getDeuterium();
				needed -= theTotalTrade.getDeuterium();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getDeuterium().totalNet;
				}
				return needed * 3600 / production; // seconds
			}
		};
		theTradesNeeded = new SyntheticTrade() {
			@Override
			public String getName() {
				return "Trades Needed";
			}

			// At the moment, this is a colossal waste of computation, as this value is calculated for each resource type and duration
			// But it's only done once (for each resource) when anything changes, so it may not be worth the trouble of optimizing
			private double[] calcNeededTrade() {
				UpgradeCost cost = theUniGui.getPlanetPanel().getTotalUpgrades().getCost();
				cost = cost.plus(UpgradeCost.of('b', 'e', theTotalHolding.getMetal(), theTotalHolding.getCrystal(),
					theTotalHolding.getDeuterium(), 0, Duration.ZERO).negate());
				cost = cost.plus(UpgradeCost
					.of('b', 'e', theTotalTrade.getMetal(), theTotalTrade.getCrystal(), theTotalTrade.getDeuterium(), 0, Duration.ZERO)
					.negate());
				UpgradeCost production = UpgradeCost.ZERO;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production = production.plus(UpgradeCost.of('b', 'e', planet.getMetal().totalNet, planet.getCrystal().totalNet,
						planet.getDeuterium().totalNet, 0, Duration.ZERO));
				}
				// Trade rates
				double rM = theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios().getMetal();
				double rC = theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios().getCrystal();
				double rD = theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios().getDeuterium();
				// The amount needed beyond holdings and planned trades to achieve all goals
				double nM = cost.getMetal();
				double nC = cost.getCrystal();
				double nD = cost.getDeuterium();
				// Production rates
				double pM = production.getMetal();
				double pC = production.getCrystal();
				double pD = production.getDeuterium();

				double x = rM * pC / rC / pM + rM * pD / rD / pM;
				// The amount of resources needed via additional, as yet unplanned, trades
				double tM, tC, tD;
				tM = (nM * x - rM * nC / rC - rM * nD / rD) / (x + 1);
				double t = (nM - tM) / pM; // The amount of time (in hours) needed to achieve all goals (with all planned and unplanned
											// trades)
				tC = nC - pC * t;
				tD = nD - pD * t;
				return new double[] { tM, tC, tD, t };
			}

			@Override
			public long getMetal() {
				return Math.round(calcNeededTrade()[0]);
			}

			@Override
			public long getCrystal() {
				return Math.round(calcNeededTrade()[1]);
			}

			@Override
			public long getDeuterium() {
				return Math.round(calcNeededTrade()[2]);
			}
		};
		theTotalTradesUpgradeTime = new SyntheticTrade() {
			@Override
			public String getName() {
				return "Goal Completion 2";
			}

			@Override
			public long getMetal() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getMetal();
				needed -= theTotalHolding.getMetal();
				needed -= theTotalTrade.getMetal();
				needed -= theTradesNeeded.getMetal();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getMetal().totalNet;
				}
				return needed * 3600 / production; // seconds
			}

			@Override
			public long getCrystal() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getCrystal();
				needed -= theTotalHolding.getCrystal();
				needed -= theTotalTrade.getCrystal();
				needed -= theTradesNeeded.getCrystal();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getCrystal().totalNet;
				}
				return needed * 3600 / production; // seconds
			}

			@Override
			public long getDeuterium() {
				long needed = theUniGui.getPlanetPanel().getTotalUpgrades().getCost().getDeuterium();
				needed -= theTotalHolding.getDeuterium();
				needed -= theTotalTrade.getDeuterium();
				needed -= theTradesNeeded.getDeuterium();
				long production = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					production += planet.getDeuterium().totalNet;
				}
				return needed * 3600 / production; // seconds
			}
		};
		ObservableCollection<Trade> synthTrades = ObservableCollection.build(TypeTokens.get().of(Trade.class)).safe(false).build()
			.with(theTotalTrade, theUpgradeTimeTrade, theTradesNeeded, theTotalTradesUpgradeTime);
		ElementId totalTradeId = synthTrades.getElement(0).getElementId();
		ElementId upgradeTimeTradeId = synthTrades.getElement(1).getElementId();
		ElementId tradesNeededId = synthTrades.getElement(2).getElementId();
		ElementId totalTradesUpgradeId = synthTrades.getElement(3).getElementId();
		Observable.or(flatTrades.simpleChanges(), flatHoldings.simpleChanges()).act(__ -> {
			synthTrades.mutableElement(totalTradeId).set(theTotalTrade);
			synthTrades.mutableElement(upgradeTimeTradeId).set(theUpgradeTimeTrade);
			synthTrades.mutableElement(tradesNeededId).set(theTradesNeeded);
			synthTrades.mutableElement(totalTradesUpgradeId).set(theTotalTradesUpgradeTime);
		});
		theUniGui.getPlanets().simpleChanges().act(__ -> {
			synthTrades.mutableElement(upgradeTimeTradeId).set(theUpgradeTimeTrade);
			synthTrades.mutableElement(tradesNeededId).set(theTradesNeeded);
			synthTrades.mutableElement(totalTradesUpgradeId).set(theTotalTradesUpgradeTime);
		});
		theTrades = ObservableCollection.flattenCollections(TypeTokens.get().of(Trade.class), flatTrades, synthTrades).collect();
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
				.addVPanel(holdingsPanel -> holdingsPanel.fill().fillV().decorate(d -> d.withTitledBorder("Holdings", Color.black))//
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
							if (h == theProductionTimeHolding || h == theUpgradeTimeHolding) {
								return h.getMetal()*1.0;
							} else {
								return h.getMetal()/1E6;
							}
						}, metalCol -> metalCol.formatText((h, m) -> {
							if (h == theProductionTimeHolding || h == theUpgradeTimeHolding) {
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
							if (h == theProductionTimeHolding || h == theUpgradeTimeHolding) {
								return h.getCrystal() * 1.0;
							} else {
								return h.getCrystal() / 1E6;
							}
						}, crystalCol -> crystalCol.formatText((h, d) -> {
							if (h == theProductionTimeHolding || h == theUpgradeTimeHolding) {
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
							if (h == theProductionTimeHolding || h == theUpgradeTimeHolding) {
								return h.getDeuterium() * 1.0;
							} else {
								return h.getDeuterium() / 1E6;
							}
						}, deutCol -> deutCol.formatText((h, d) -> {
							if (h == theProductionTimeHolding || h == theUpgradeTimeHolding) {
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
						.withColumn("Cargoes", Integer.class, h -> {
							long total = h.getMetal() + h.getCrystal() + h.getDeuterium();
							long cap = theUniGui.getRules().get().fleet().getCargoSpace(ShipyardItemType.LargeCargo,
								theUniGui.getSelectedAccount().get());
							return (int) Math.ceil(total * 1.0 / cap);
						}, col -> col.formatText(cargoes -> Format.INT.format(cargoes)))//
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
				.addVPanel(tradePanel -> tradePanel.fill().fillV().decorate(d -> d.withTitledBorder("Trades", Color.black))//
					.addTable(theTrades, tradeTable -> tradeTable.fill().fillV()//
						.withColumn("Name", String.class, Trade::getName, nameCol -> nameCol.withMutation(nameMutator -> {
							nameMutator.mutateAttribute(Trade::setName).editableIf((h, n) -> !(h instanceof SyntheticTrade))
								.withEditor(ObservableCellEditor.createTextEditor(SpinnerFormat.NUMERICAL_TEXT));
						}))//
						.withColumn("M Rate", Double.class, h -> {
							if (h instanceof SyntheticTrade) {
								return 0.0;
							}
							return h.getRate().getMetal();
						}, rateCol -> rateCol.withWidths(45, 45, 45).formatText(r -> r == 0.0 ? "" : OGameUtils.TWO_DIGIT_FORMAT.format(r))
							.withMutation(mrm -> {
							mrm.mutateAttribute((t, r) -> t.getRate().setMetal(r)).withRowUpdate(true).asText(Format.doubleFormat("0.00"))//
								.editableIf((t, r) -> !(t instanceof SyntheticTrade))
								.filterAccept((t, r) -> r <= 0 ? "Rate must be positive" : null);
						}))//
						.withColumn("C Rate", Double.class, h -> {
							if (h instanceof SyntheticTrade) {
								return 0.0;
							}
							return h.getRate().getCrystal();
						}, rateCol -> rateCol.withWidths(45, 45, 45).formatText(r -> r == 0.0 ? "" : OGameUtils.TWO_DIGIT_FORMAT.format(r))
							.withMutation(mrm -> {
							mrm.mutateAttribute((t, r) -> t.getRate().setCrystal(r)).withRowUpdate(true).asText(Format.doubleFormat("0.00"))//
								.editableIf((t, r) -> !(t instanceof SyntheticTrade))
								.filterAccept((t, r) -> r <= 0 ? "Rate must be positive" : null);
						}))//
						.withColumn("D Rate", Double.class, h -> {
							if (h instanceof SyntheticTrade) {
								return 0.0;
							}
							return h.getRate().getDeuterium();
						}, rateCol -> rateCol.withWidths(45, 45, 45).formatText(r -> r == 0.0 ? "" : OGameUtils.TWO_DIGIT_FORMAT.format(r))
							.withMutation(mrm -> {
							mrm.mutateAttribute((t, r) -> t.getRate().setDeuterium(r)).withRowUpdate(true)
								.asText(Format.doubleFormat("0.00"))//
								.editableIf((t, r) -> !(t instanceof SyntheticTrade))
								.filterAccept((t, r) -> r <= 0 ? "Rate must be positive" : null);
						}))//
						.withColumn("Metal (KK)", Double.class, h -> {
							if (h == theUpgradeTimeTrade || h == theTotalTradesUpgradeTime) {
								return h.getMetal() * 1.0;
							} else {
								return h.getMetal() / 1E6;
							}
						}, metalCol -> metalCol.formatText((h, m) -> {
							if (h == theUpgradeTimeTrade || h == theTotalTradesUpgradeTime) {
								return OGameUniGui.printUpgradeTime(Duration.ofSeconds(m.longValue()));
							} else {
								return OGameUtils.TWO_DIGIT_FORMAT.format(m);
							}
						}).withMutation(metalMutation -> {
							metalMutation.mutateAttribute((h, m) -> h.setMetal((long) (m * 1E6))).editableIf((h, n) -> {
								if (h instanceof SyntheticTrade) {
									return false;
								} else {
									return h.getType() != ResourceType.Metal;
								}
							}).asText(Format.doubleFormat("0.00"));
						}))//
						.withColumn("Crystal (KK)", Double.class, h -> {
							if (h == theUpgradeTimeTrade || h == theTotalTradesUpgradeTime) {
								return h.getCrystal() * 1.0;
							} else {
								return h.getCrystal() / 1E6;
							}
						}, crystalCol -> crystalCol.formatText((h, d) -> {
							if (h == theUpgradeTimeTrade || h == theTotalTradesUpgradeTime) {
								return OGameUniGui.printUpgradeTime(Duration.ofSeconds(d.longValue()));
							} else {
								return OGameUtils.TWO_DIGIT_FORMAT.format(d);
							}
						}).withMutation(crystalMutation -> {
							crystalMutation.mutateAttribute((h, c) -> h.setCrystal((long) (c * 1E6))).editableIf((h, n) -> {
								if (h instanceof SyntheticTrade) {
									return false;
								} else {
									return h.getType() != ResourceType.Crystal;
								}
							}).asText(Format.doubleFormat("0.00"));
						}))//
						.withColumn("Deuterium (KK)", Double.class, h -> {
							if (h == theUpgradeTimeTrade || h == theTotalTradesUpgradeTime) {
								return h.getDeuterium() * 1.0;
							} else {
								return h.getDeuterium() / 1E6;
							}
						}, deutCol -> deutCol.formatText((h, d) -> {
							if (h == theUpgradeTimeTrade || h == theTotalTradesUpgradeTime) {
								return OGameUniGui.printUpgradeTime(Duration.ofSeconds(d.longValue()));
							} else {
								return OGameUtils.TWO_DIGIT_FORMAT.format(d);
							}
						}).withMutation(metalMutation -> {
							metalMutation.mutateAttribute((h, d) -> h.setDeuterium((long) (d * 1E6))).editableIf((h, n) -> {
								if (h instanceof SyntheticTrade) {
									return false;
								} else {
									return h.getType() != ResourceType.Deuterium;
								}
							}).asText(Format.doubleFormat("0.00"));
						}))//
						.withAdd(
							() -> theUniGui.getSelectedAccount().get().getTrades().create()//
								.with(Trade::getName, "").with(Trade::getType, ResourceType.Metal)//
								.create(t -> t.getRate().set(theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios())).get(),
							addAction -> addAction.modifyButton(addBtn -> addBtn.withText("M")
								.disableWith(theUniGui.getSelectedAccount().map(acct -> acct == null ? "No Account Selected" : null))))//
						.withAdd(
							() -> theUniGui.getSelectedAccount().get().getTrades().create()//
								.with(Trade::getName, "").with(Trade::getType, ResourceType.Crystal)//
								.create(t -> t.getRate().set(theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios())).get(),
							addAction -> addAction.modifyButton(addBtn -> addBtn.withText("C")
								.disableWith(theUniGui.getSelectedAccount().map(acct -> acct == null ? "No Account Selected" : null))))//
						.withAdd(
							() -> theUniGui.getSelectedAccount().get().getTrades().create()//
								.with(Trade::getName, "").with(Trade::getType, ResourceType.Deuterium)//
								.create(t -> t.getRate().set(theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios())).get(),
							addAction -> addAction.modifyButton(addBtn -> addBtn.withText("D")
								.disableWith(theUniGui.getSelectedAccount().map(acct -> acct == null ? "No Account Selected" : null))))//
						.withRemove(trades -> theUniGui.getSelectedAccount().get().getTrades().getValues().removeAll(trades),
							remAction -> remAction.modifyButton(addBtn -> addBtn
								.disableWith(theUniGui.getSelectedAccount().map(acct -> acct == null ? "No Account Selected" : null))))//
			)//
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

	private static abstract class SyntheticTrade implements Trade {
		@Override
		public ResourceType getType() {
			return null;
		}

		@Override
		public TradeRatios getRate() {
			return null;
		}

		@Override
		public Nameable setName(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getResource1() {
			return 0;
		}

		@Override
		public Trade setResource1(long res1) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getResource2() {
			return 0;
		}

		@Override
		public Trade setResource2(long res2) {
			throw new UnsupportedOperationException();
		}
	}
}
