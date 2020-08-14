package org.quark.ogame.uni.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.PanelPopulation;
import org.qommons.ArrayUtils;
import org.qommons.TimeUtils;
import org.qommons.collect.CollectionElement;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.AccountUpgrade;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.PlannedUpgrade;
import org.quark.ogame.uni.Research;
import org.quark.ogame.uni.ResearchType;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.RockyBody;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.TradeRatios;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.UpgradeType;

import com.google.common.reflect.TypeToken;

public class PlanetTable {
	private final OGameUniGui theUniGui;

	private final ObservableCollection<PlanetWithProduction> selectedPlanets;
	private final SettableValue<PlanetWithProduction> theSelectedPlanet;

	private final SettableValue<Boolean> showFields;
	private final SettableValue<Boolean> showTemps;
	private final SettableValue<Boolean> showMines;
	private final SettableValue<Boolean> showUsage;
	private final SettableValue<Boolean> showItems;
	private final SettableValue<Boolean> showEnergy;
	private final SettableValue<Boolean> showStorage;
	private final SettableValue<ProductionDisplayType> productionType;
	private final SettableValue<Boolean> showProductionTotals;
	private final SettableValue<Boolean> showMainFacilities;
	private final SettableValue<Boolean> showOtherFacilities;
	private final SettableValue<Boolean> showDefense;
	private final SettableValue<Boolean> showFleet;
	private final SettableValue<Boolean> showMoonInfo;

	private final ObservableCollection<PlanetWithProduction> theTotalProduction;
	private final ObservableCollection<AccountUpgrade> theUpgrades;
	private final AccountUpgrade thePlanetTotalUpgrade;
	private final AccountUpgrade theTotalUpgrade;

	public PlanetTable(OGameUniGui uniGui) {
		theUniGui = uniGui;

		ObservableConfig config = uniGui.getConfig();
		showFields = config.asValue(boolean.class).at("planet-categories/fields").withFormat(Format.BOOLEAN, () -> false).buildValue(null);
		showTemps = config.asValue(boolean.class).at("planet-categories/temps").withFormat(Format.BOOLEAN, () -> true).buildValue(null);
		showMines = config.asValue(boolean.class).at("planet-categories/mines").withFormat(Format.BOOLEAN, () -> true).buildValue(null);
		showUsage = config.asValue(boolean.class).at("planet-categories/usage").withFormat(Format.BOOLEAN, () -> false).buildValue(null);
		showItems = config.asValue(boolean.class).at("planet-categories/items").withFormat(Format.BOOLEAN, () -> false).buildValue(null);
		showEnergy = config.asValue(boolean.class).at("planet-categories/energy").withFormat(Format.BOOLEAN, () -> false).buildValue(null);
		showStorage = config.asValue(boolean.class).at("planet-categories/storage").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		showMainFacilities = config.asValue(boolean.class).at("planet-categories/main-facilities").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		showOtherFacilities = config.asValue(boolean.class).at("planet-categories/other-facilities").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		showMoonInfo = config.asValue(boolean.class).at("planet-categories/moon-info").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		productionType = config.asValue(ProductionDisplayType.class).at("planet-categories/production")
			.withFormat(ObservableConfigFormat.enumFormat(ProductionDisplayType.class, () -> ProductionDisplayType.Hourly))
			.buildValue(null);
		showProductionTotals = config.asValue(boolean.class).at("planet-categories/productionTotals")
			.withFormat(Format.BOOLEAN, () -> false).buildValue(null)
			.combine(TypeTokens.get().BOOLEAN, (totals, type) -> type == null ? false : totals, productionType,
				(newTotals, type) -> newTotals, opts -> {})
			.disableWith(productionType.map(type -> type == null ? "Production is not selected" : null));
		showDefense = config.asValue(boolean.class).at("planet-categories/defense").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		showFleet = config.asValue(boolean.class).at("planet-categories/fleet").withFormat(Format.BOOLEAN, () -> false).buildValue(null);

		Production zero = new Production(Collections.emptyMap(), 0, 0);
		PlanetWithProduction total = new PlanetWithProduction(null)//
			.setProduction(zero, zero, zero, zero);
		theTotalProduction = ObservableCollection.build(PlanetWithProduction.class).safe(false).build().with(total);
		// Update total for each change
		updateTotalProduction(total, zero);
		theUniGui.getPlanets().simpleChanges().act(__ -> updateTotalProduction(total, zero));
		selectedPlanets = ObservableCollection.flattenCollections(TypeTokens.get().of(PlanetWithProduction.class), //
			theUniGui.getPlanets().flow().refresh(productionType.noInitChanges()).collect(), //
			theTotalProduction).collect();

		theSelectedPlanet = SettableValue.build(PlanetWithProduction.class).safe(false).build();

		ObservableCollection<PlannedAccountUpgrade> calculatedUpgrades = ObservableCollection.build(PlannedAccountUpgrade.class).safe(false)
			.build();
		Observable.or(theUniGui.getSelectedAccount().changes(), theUniGui.getReferenceAccount().noInitChanges()).act(__ -> {
			Account account = theUniGui.getSelectedAccount().get();
			Account refAcct = theUniGui.getReferenceAccount().get();
			if (account == null || refAcct == null) {
				calculatedUpgrades.clear();
				return;
			}
			List<PlannedAccountUpgrade> newUpgrades = new ArrayList<>();
			// for (PlannedUpgrade upgrade : theUniGui.getSelectedAccount().get().getPlannedUpgrades().getValues()) { TODO
			// newUpgrades.add(new PlannedAccountUpgrade(upgrade));
			// }
			for (AccountUpgradeType type : AccountUpgradeType.values()) {
				if (type.type == UpgradeType.Research) {
					int fromLevel = refAcct.getResearch().getResearchLevel(type.research);
					if (refAcct.getResearch().getCurrentUpgrade() == type.research) {
						fromLevel++;
					}
					int toLevel = account.getResearch().getResearchLevel(type.research);
					if (fromLevel != toLevel) {
						UpgradeCost cost = theUniGui.getRules().get().economy().getUpgradeCost(account, null, type, fromLevel, toLevel);
						newUpgrades.add(new PlannedAccountUpgrade(type, null, false, fromLevel, toLevel, cost));
					}
				} else {
					for (int p = 0; p < account.getPlanets().getValues().size(); p++) {
						Planet planet = account.getPlanets().getValues().get(p);
						Planet refPlanet = p < refAcct.getPlanets().getValues().size() ? refAcct.getPlanets().getValues().get(p) : null;

						int fromLevel = refPlanet == null ? 0 : type.getLevel(refAcct, refPlanet);
						if (refPlanet != null && type.building != null && refPlanet.getCurrentUpgrade() == type.building) {
							fromLevel++;
						}
						int toLevel = type.getLevel(account, planet);
						if (fromLevel != toLevel) {
							UpgradeCost cost = theUniGui.getRules().get().economy().getUpgradeCost(account, planet, type, fromLevel,
								toLevel);
							newUpgrades.add(new PlannedAccountUpgrade(type, planet, false, fromLevel, toLevel, cost));
						}

						Moon moon = planet.getMoon();
						Moon refMoon = refPlanet == null ? null : refPlanet.getMoon();
						fromLevel = refMoon == null ? 0 : type.getLevel(refAcct, refMoon);
						if (refMoon != null && type.building != null && refMoon.getCurrentUpgrade() == type.building) {
							fromLevel++;
						}
						toLevel = type.getLevel(account, moon);
						if (fromLevel != toLevel) {
							UpgradeCost cost = theUniGui.getRules().get().economy().getUpgradeCost(account, moon, type, fromLevel, toLevel);
							newUpgrades.add(new PlannedAccountUpgrade(type, planet, true, fromLevel, toLevel, cost));
						}
					}
				}
			}

			ArrayUtils.adjust(calculatedUpgrades, newUpgrades,
				new ArrayUtils.DifferenceListener<PlannedAccountUpgrade, PlannedAccountUpgrade>() {
				@Override
					public boolean identity(PlannedAccountUpgrade o1, PlannedAccountUpgrade o2) {
					return o1.equals(o2);
				}

				@Override
					public PlannedAccountUpgrade added(PlannedAccountUpgrade o, int mIdx, int retIdx) {
					return o;
				}

				@Override
					public PlannedAccountUpgrade removed(PlannedAccountUpgrade o, int oIdx, int incMod, int retIdx) {
					return null;
				}

				@Override
					public PlannedAccountUpgrade set(PlannedAccountUpgrade o1, int idx1, int incMod, PlannedAccountUpgrade o2, int idx2,
						int retIdx) {
					return o1;
				}
			});
		});
		thePlanetTotalUpgrade = new AccountUpgrade(null, null, false, 0, 0, null) {
			@Override
			public UpgradeCost getCost() {
				UpgradeCost cost = UpgradeCost.ZERO;
				PlanetWithProduction selectedPlanet = theSelectedPlanet.get();
				if (selectedPlanet != null) {
					for (AccountUpgrade upgrade : calculatedUpgrades) {
						if (upgrade.getPlanet() == selectedPlanet.planet) {
							cost = cost.plus(upgrade.getCost());
						}
					}
				}
				return cost;
			}
		};
		theTotalUpgrade = new AccountUpgrade(null, null, false, 0, 0, null) {
			@Override
			public UpgradeCost getCost() {
				UpgradeCost cost = UpgradeCost.ZERO;
				for (AccountUpgrade upgrade : calculatedUpgrades) {
					cost = cost.plus(upgrade.getCost());
				}
				return cost;
			}
		};
		ObservableCollection<AccountUpgrade> totalUpgrades = ObservableCollection.build(AccountUpgrade.class).safe(false).build();
		totalUpgrades.add(theTotalUpgrade);
		theSelectedPlanet.changes().act(p -> {
			if (p != null && totalUpgrades.size() == 1) {
				totalUpgrades.add(0, thePlanetTotalUpgrade);
			} else if (p == null && totalUpgrades.size() == 2) {
				totalUpgrades.remove(0);
			}
		});
		theUpgrades = ObservableCollection.flattenCollections(TypeTokens.get().of(AccountUpgrade.class), //
			calculatedUpgrades.flow().refresh(theSelectedPlanet.noInitChanges()).collect(), //
			totalUpgrades).collect();
	}

	/** @return An ObservableCollection with a single element that is the total production of the selected account */
	public ObservableCollection<PlanetWithProduction> getTotalProduction() {
		return theTotalProduction;
	}

	public AccountUpgrade getTotalUpgrades() {
		return theTotalUpgrade;
	}

	private void updateTotalProduction(PlanetWithProduction total, Production zero) {
		// At some point there may be a use case for adding each component of production,
		// e.g. to see how much deuterium you're using on fusion throughout the empire.
		// But at the moment, all that's needed is the total net production of material resources
		// and the calculation for all that detail is cumbersome.
		int metal = 0, crystal = 0, deuterium = 0;
		for (PlanetWithProduction planet : theUniGui.getPlanets()) {
			metal += planet.getMetal().totalNet;
			crystal += planet.getCrystal().totalNet;
			deuterium += planet.getDeuterium().totalNet;
		}
		total.setProduction(zero, //
			new Production(Collections.emptyMap(), metal, 0), //
			new Production(Collections.emptyMap(), crystal, 0), //
			new Production(Collections.emptyMap(), deuterium, 0));
		theTotalProduction.mutableElement(theTotalProduction.getTerminalElement(true).getElementId()).set(total);
	}

	static final String UPGRADE_DONE = "Done";

	public void addPlanetTable(PanelPopulation.PanelPopulator<?, ?> panel) {
		ObservableCollection<Integer> usageOptions = ObservableCollection.of(TypeTokens.get().INT, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10,
			0);
		ObservableCollection<Integer> itemOptions = ObservableCollection.of(TypeTokens.get().INT, 30, 20, 10, 0);

		ObservableCollection<Object> planetUpgrades = ObservableCollection.build(Object.class).safe(false).build();
		ObservableCollection<Object> moonUpgrades = ObservableCollection.build(Object.class).safe(false).build();
		planetUpgrades.add("None");
		moonUpgrades.add("None");
		for (BuildingType b : BuildingType.values()) {
			if (b.isPlanetBuilding) {
				planetUpgrades.add(b);
			}
			if (b.isMoonBuilding) {
				moonUpgrades.add(b);
			}
		}
		theSelectedPlanet.changes().act(evt -> {
			boolean currentlyHasDone = UPGRADE_DONE.equals(planetUpgrades.peekFirst());
			boolean shouldHaveDone;
			if (evt.getNewValue() == null || evt.getNewValue().planet == null) {
				shouldHaveDone= false;
			} else {
				shouldHaveDone=evt.getNewValue().planet.getCurrentUpgrade() != null;
			}
			if (currentlyHasDone && !shouldHaveDone) {
				planetUpgrades.removeFirst();
			} else if (!currentlyHasDone && shouldHaveDone) {
				planetUpgrades.addFirst(UPGRADE_DONE);
			}

			currentlyHasDone = UPGRADE_DONE.equals(moonUpgrades.peekFirst());
			shouldHaveDone = evt.getNewValue() != null && evt.getNewValue().planet != null
				&& evt.getNewValue().planet.getMoon().getCurrentUpgrade() != null;
			if (currentlyHasDone && !shouldHaveDone) {
				moonUpgrades.removeFirst();
			} else if (!currentlyHasDone && shouldHaveDone) {
				moonUpgrades.addFirst(UPGRADE_DONE);
			}
		});

		ObservableCollection<Object> researchUpgrades = ObservableCollection.build(Object.class).safe(false).build();
		researchUpgrades.add("None");
		for (ResearchType r : ResearchType.values()) {
			researchUpgrades.add(r);
		}

		theUniGui.getSelectedAccount().changes().act(evt -> {
			boolean currentlyHasDone = UPGRADE_DONE.equals(researchUpgrades.peekFirst());
			boolean shouldHaveDone = evt.getNewValue() != null && evt.getNewValue().getResearch().getCurrentUpgrade() != null;
			if (currentlyHasDone && !shouldHaveDone) {
				researchUpgrades.removeFirst();
			} else if (!currentlyHasDone && shouldHaveDone) {
				researchUpgrades.addFirst(UPGRADE_DONE);
			}
		});

		TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumnType = new TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>>() {};
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> initPlanetColumns = ObservableCollection
			.create(planetColumnType);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> lastPlanetColumns = ObservableCollection
			.create(planetColumnType);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> fieldColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Total Fields", false, false, p -> theUniGui.getRules().get().economy().getFields(p), (p, f) -> {
				int currentTotal = theUniGui.getRules().get().economy().getFields(p);
				int currentBase = p.getBaseFields();
				int diff = f - currentTotal;
				int newBase = currentBase + diff;
				if (newBase < 0) {
					newBase = 0;
				}
				p.setBaseFields(newBase);
			}, 80).withMutation(m -> m.filterAccept((p, f) -> {
				int currentTotal = theUniGui.getRules().get().economy().getFields(p.get().planet);
				int currentBase = p.get().planet.getBaseFields();
				int diff = f - currentTotal;
				int newBase = currentBase + diff;
				if (newBase < 0) {
					return p.get().planet.getUsedFields() + " fields are used";
				}
				return null;
			})), //
			intPlanetColumn("Free Fields", false, false, planet -> {
				return theUniGui.getRules().get().economy().getFields(planet) - planet.getUsedFields();
			}, null, 80)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonFieldColumns = ObservableCollection.of(planetColumnType,
			intMoonColumn("Moon Fields", false, moon -> theUniGui.getRules().get().economy().getFields(moon), null, 80), //
			intMoonColumn("Bonus Fields", false, Moon::getFieldBonus, Moon::setFieldBonus, 80), //
			intMoonColumn("Free Fields", false, moon -> {
				return theUniGui.getRules().get().economy().getFields(moon) - moon.getUsedFields();
			}, null, 80)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> tempColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Min T", true, false, Planet::getMinimumTemperature, (planet, t) -> {
				planet.setMinimumTemperature(t);
				planet.setMaximumTemperature(t + 40);
			}, 40), //
			intPlanetColumn("Max T", true, false, Planet::getMaximumTemperature, (planet, t) -> {
				planet.setMaximumTemperature(t);
				planet.setMinimumTemperature(t - 40);
			}, 40)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mineColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("M Mine", false, true, Planet::getMetalMine, Planet::setMetalMine, 55), //
			intPlanetColumn("C Mine", false, true, Planet::getCrystalMine, Planet::setCrystalMine, 55), //
			intPlanetColumn("D Synth", false, true, Planet::getDeuteriumSynthesizer, Planet::setDeuteriumSynthesizer, 50), //
			intPlanetColumn("Crawlers", false, true, Planet::getCrawlers, Planet::setCrawlers, 60).formatText((planet, crawlers) -> {
				StringBuilder str = new StringBuilder();
				str.append(crawlers);
				if (planet.planet != null) {
					str.append('/');
					str.append(theUniGui.getRules().get().economy().getMaxCrawlers(theUniGui.getSelectedAccount().get(), planet.planet));
				}
				return str.toString();
			}) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> usageColumns1 = ObservableCollection.of(planetColumnType,
			planetColumn("M %", int.class, p -> p.planet == null ? null : p.planet.getMetalUtilization(), Planet::setMetalUtilization, 45)
				.withHeaderTooltip("Metal Mine Utilization").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", usageOptions).clicks(1)), //
			planetColumn("C %", int.class, p -> p.planet == null ? null : p.planet.getCrystalUtilization(), Planet::setCrystalUtilization,
				45).withHeaderTooltip("Crystal Mine Utilization").formatText(v -> v == null ? "" : v + "%")
					.withMutation(m -> m.asCombo(v -> v + "%", usageOptions).clicks(1)), //
			planetColumn("D %", int.class, p -> p.planet == null ? null : p.planet.getDeuteriumUtilization(),
				Planet::setDeuteriumUtilization, 45).withHeaderTooltip("Deuterium Synthesizer Utilization")
					.formatText(v -> v == null ? "" : v + "%")
					.withMutation(m -> m.asCombo(v -> v + "%", usageOptions).clicks(1)), //
			planetColumn("Cr %", int.class, p -> p.planet == null ? null : p.planet.getCrawlerUtilization(), Planet::setCrawlerUtilization,
				45).withHeaderTooltip("Crawler Utilization").formatText(v -> v == null ? "" : v + "%")
					.withMutation(m -> m.asCombo(v -> v + "%", usageOptions).clicks(1)) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> itemColumns = ObservableCollection.of(planetColumnType,
			planetColumn("M+", int.class, p -> p.planet == null ? null : p.planet.getMetalBonus(), Planet::setMetalBonus, 45)
				.withHeaderTooltip("Metal Bonus Item").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", itemOptions).clicks(1)), //
			planetColumn("C+", int.class, p -> p.planet == null ? null : p.planet.getCrystalBonus(), Planet::setCrystalBonus, 45)
				.withHeaderTooltip("Crystal Bonus Item").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", itemOptions).clicks(1)), //
			planetColumn("D+", int.class, p -> p.planet == null ? null : p.planet.getDeuteriumBonus(), Planet::setDeuteriumBonus, 45)
				.withHeaderTooltip("Deuterium Bonus Item").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", itemOptions).clicks(1)) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> energyBldgs = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Sats", false, true, Planet::getSolarSatellites, Planet::setSolarSatellites, 75), //
			intPlanetColumn("Solar", false, true, Planet::getSolarPlant, Planet::setSolarPlant, 55), //
			intPlanetColumn("Fusion", false, true, Planet::getFusionReactor, Planet::setFusionReactor, 55),
			planetColumn("Net Energy", int.class, p -> p.planet == null ? null : p.getEnergy().totalNet, null, 60));
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> usageColumns2 = ObservableCollection.of(planetColumnType,
			planetColumn("FZN %", int.class, p -> p.planet == null ? null : p.planet.getFusionReactorUtilization(),
				Planet::setFusionReactorUtilization, 45).withHeaderTooltip("Fusion Reactor Utilization")
					.formatText(v -> v == null ? "" : v + "%").withMutation(m -> m.asCombo(v -> v + "%", usageOptions).clicks(1)) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> storageColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("M Stor", false, true, Planet::getMetalStorage, Planet::setMetalStorage, 55), //
			intPlanetColumn("C Stor", false, true, Planet::getCrystalStorage, Planet::setCrystalStorage, 55), //
			intPlanetColumn("D Stor", false, true, Planet::getDeuteriumStorage, Planet::setDeuteriumStorage, 55)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionColumns = ObservableCollection.of(planetColumnType,
			planetColumn("M Prod", String.class, planet -> printProduction(planet.getMetal().totalNet, productionType.get()), null, 80), //
			planetColumn("C Prod", String.class, planet -> printProduction(planet.getCrystal().totalNet, productionType.get()), null, 80), //
			planetColumn("D Prod", String.class, planet -> printProduction(planet.getDeuterium().totalNet, productionType.get()), null, 80) //
		).flow().refresh(productionType.noInitChanges()).collect();
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionCargoColumns = ObservableCollection
			.of(planetColumnType,
				planetColumn("Cargoes", int.class, planet -> getCargoes(planet, false, productionType.get()), null, 80)
					.withHeaderTooltip("The number of Large Cargo ships required to carry away the planet's production"), //
				planetColumn("SS Cargoes", int.class, planet -> getCargoes(planet, true, productionType.get()), null, 80)
					.withHeaderTooltip("The number of Large Cargo ships, built from the production resources of the planet,"
						+ " required to carry away the planet's production")//
			).flow().refresh(productionType.noInitChanges()).collect();
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionTotalColumns = ObservableCollection
			.of(planetColumnType,
				planetColumn("P. Total", String.class,
					planet -> printProduction(planet.getMetal().totalNet + planet.getCrystal().totalNet + planet.getDeuterium().totalNet,
						productionType.get()),
					null, 80), //
				planetColumn("P. Value", String.class, planet -> {
					TradeRatios ratios = theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios();
					return printProduction((long) (//
					planet.getMetal().totalNet//
						+ planet.getCrystal().totalNet / ratios.getCrystal() * ratios.getMetal()//
						+ planet.getDeuterium().totalNet / ratios.getDeuterium() * ratios.getMetal()), productionType.get());
				}, null, 80).withHeaderTooltip("Metal-equivalent value of each planet's production")//
			).flow().refresh(productionType.noInitChanges()).collect();
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mainFacilities = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Robotics", false, true, Planet::getRoboticsFactory, Planet::setRoboticsFactory, 60), //
			intPlanetColumn("Shipyard", false, true, Planet::getShipyard, Planet::setShipyard, 60), //
			intPlanetColumn("Lab", false, true, Planet::getResearchLab, Planet::setResearchLab, 55), //
			intPlanetColumn("Nanite", false, true, Planet::getNaniteFactory, Planet::setNaniteFactory, 55)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> otherFacilities = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Ally Depot", false, true, Planet::getAllianceDepot, Planet::setAllianceDepot, 65), //
			intPlanetColumn("Silo", false, true, Planet::getMissileSilo, Planet::setMissileSilo, 45), //
			intPlanetColumn("Terraformer", false, true, Planet::getTerraformer, Planet::setTerraformer, 65), //
			intPlanetColumn("Space Dock", false, true, Planet::getSpaceDock, Planet::setSpaceDock, 65)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonBuildings = ObservableCollection.of(planetColumnType,
			new CategoryRenderStrategy<PlanetWithProduction, Object>("Upgrd", TypeTokens.get().OBJECT,
				p -> p.planet == null ? null : p.planet.getMoon().getCurrentUpgrade())//
					.withWidths(45, 45, 45).formatText(bdg -> bdg == null ? "" : ((BuildingType) bdg).shortName)
					.withMutation(m -> m.asCombo(bdg -> {
						if (bdg instanceof BuildingType) {
							return ((BuildingType) bdg).shortName;
						} else if (bdg != null) {
							return bdg.toString();
						} else {
							return "";
						}
					}, moonUpgrades).clicks(1).mutateAttribute((p, bdg) -> {
						if (bdg instanceof BuildingType) {
							p.planet.getMoon().setCurrentUpgrade((BuildingType) bdg);
						} else {
							BuildingType upgrade = p.planet.getMoon().getCurrentUpgrade();
							p.planet.getMoon().setCurrentUpgrade(null);
							if (UPGRADE_DONE.equals(bdg)) {
								upgrade(p.planet.getMoon(), upgrade.getUpgrade());
							}
						}
					})), //
			intMoonColumn("Lunar Base", true, Moon::getLunarBase, Moon::setLunarBase, 60), //
			intMoonColumn("Phalanx", true, Moon::getSensorPhalanx, Moon::setSensorPhalanx, 55), //
			intMoonColumn("Jump Gate", true, Moon::getJumpGate, Moon::setJumpGate, 60), //
			intMoonColumn("Shipyard", true, Moon::getShipyard, Moon::setShipyard, 55), //
			intMoonColumn("Robotics", true, Moon::getRoboticsFactory, Moon::setRoboticsFactory, 55)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> defenseColumns = ObservableCollection.of(planetColumnType,
			shipColumn("RL", ShipyardItemType.RocketLauncher, false, 55), //
			shipColumn("LL", ShipyardItemType.LightLaser, false, 55), //
			shipColumn("HL", ShipyardItemType.HeavyLaser, false, 55), //
			shipColumn("GC", ShipyardItemType.GaussCannon, false, 55), //
			shipColumn("IC", ShipyardItemType.IonCannon, false, 55), //
			shipColumn("PT", ShipyardItemType.PlasmaTurret, false, 55), //
			shipColumn("SS", ShipyardItemType.SmallShield, false, 55), //
			shipColumn("LS", ShipyardItemType.LargeShield, false, 55), //
			shipColumn("ABM", ShipyardItemType.AntiBallisticMissile, false, 55), //
			shipColumn("IPM", ShipyardItemType.InterPlanetaryMissile, false, 55) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonDefenseColumns = ObservableCollection.of(planetColumnType,
			shipColumn("RL", ShipyardItemType.RocketLauncher, true, 55), //
			shipColumn("LL", ShipyardItemType.LightLaser, true, 55), //
			shipColumn("HL", ShipyardItemType.HeavyLaser, true, 55), //
			shipColumn("GC", ShipyardItemType.GaussCannon, true, 55), //
			shipColumn("IC", ShipyardItemType.IonCannon, true, 55), //
			shipColumn("PT", ShipyardItemType.PlasmaTurret, true, 55), //
			shipColumn("SS", ShipyardItemType.SmallShield, true, 55), //
			shipColumn("LS", ShipyardItemType.LargeShield, true, 55) //
		);

		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> emptyColumns = ObservableCollection.of(planetColumnType);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumns = ObservableCollection
			.flattenCollections(planetColumnType, //
				initPlanetColumns, //
				ObservableCollection.flattenValue(showFields.map(show -> show ? fieldColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showTemps.map(show -> show ? tempColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showMines.map(show -> show ? mineColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showUsage.map(show -> show ? usageColumns1 : emptyColumns)), //
				ObservableCollection.flattenValue(showItems.map(show -> show ? itemColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showEnergy.map(show -> show ? energyBldgs : emptyColumns)), //
				ObservableCollection.flattenValue(showUsage.map(show -> show ? usageColumns2 : emptyColumns)), //
				ObservableCollection.flattenValue(showStorage.map(show -> show ? storageColumns : emptyColumns)), //
				ObservableCollection.flattenValue(productionType.map(type -> type.type == null ? emptyColumns : productionColumns)), //
				ObservableCollection.flattenValue(showProductionTotals.map(show -> show ? productionTotalColumns : emptyColumns)), //
				ObservableCollection.flattenValue(productionType.map(type -> type.type == null ? emptyColumns : productionCargoColumns)), //
				ObservableCollection.flattenValue(showMainFacilities.map(show -> show ? mainFacilities : emptyColumns)), //
				ObservableCollection.flattenValue(showOtherFacilities.map(show -> show ? otherFacilities : emptyColumns)), //
				ObservableCollection.flattenValue(showDefense.map(show -> show ? defenseColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showMoonInfo.combine((m, f) -> (m && f) ? moonFieldColumns : emptyColumns, showFields)), //
				ObservableCollection.flattenValue(showMoonInfo.map(show -> show ? moonBuildings : emptyColumns)), //
				ObservableCollection
					.flattenValue(showMoonInfo.combine((m, d) -> (m && d) ? moonDefenseColumns : emptyColumns, showDefense)), //
				lastPlanetColumns
			).collect();

		ObservableCollection<Research> researchColl = ObservableCollection.flattenValue(theUniGui.getSelectedAccount()
			.<ObservableCollection<Research>> map(ObservableCollection.TYPE_KEY.getCompoundType(Research.class), account -> {
				ObservableCollection<Research> rsrch = ObservableCollection.build(Research.class).safe(false).build();
				if (account != null) {
					rsrch.with(account.getResearch());
				}
				return rsrch;
			}, opts -> opts.cache(true).reEvalOnUpdate(false)));
		researchColl.simpleChanges().act(__ -> theUniGui.refreshProduction());
		Format<Double> commaFormat = Format.doubleFormat("#,##0");
		panel.fill().fillV()//
			.addTable(researchColl,
				researchTable -> researchTable.fill().withAdaptiveHeight(1, 1, 1).decorate(d -> d.withTitledBorder("Research", Color.black))//
					.withColumn("Upgrd", Object.class, r -> r.getCurrentUpgrade(), upgradeCol -> upgradeCol.withWidths(60, 60, 60)
						.withHeaderTooltip("Current Research Upgrade")
						.formatText(rsrch -> rsrch == null ? "" : ((ResearchType) rsrch).shortName).withMutation(m -> m.asCombo(rsrch -> {
							if (rsrch instanceof ResearchType) {
								return ((ResearchType) rsrch).shortName;
							} else if (rsrch != null) {
								return rsrch.toString();
							} else {
								return "";
							}
						}, researchUpgrades).clicks(1).mutateAttribute((r, rsrch) -> {
							if (rsrch instanceof ResearchType) {
								r.setCurrentUpgrade((ResearchType) rsrch);
							} else {
								ResearchType upgrade = r.getCurrentUpgrade();
								r.setCurrentUpgrade(null);
								if (UPGRADE_DONE.equals(rsrch)) {
									upgrade(null, upgrade.getUpgrade());
									researchColl.mutableElement(researchColl.getTerminalElement(true).getElementId())//
										.set(theUniGui.getSelectedAccount().get().getResearch());
								}
							}
						})))//
					.withColumn(intResearchColumn("Energy", Research::getEnergy, Research::setEnergy, 60))//
					.withColumn(intResearchColumn("Laser", Research::getLaser, Research::setLaser, 55))//
					.withColumn(intResearchColumn("Ion", Research::getIon, Research::setIon, 35))//
					.withColumn(intResearchColumn("Hyperspace", Research::getHyperspace, Research::setHyperspace, 75)//
						.withValueTooltip((r, v) -> "<html>LC Capacity: "
							+ theUniGui.getRules().get().fleet().getCargoSpace(ShipyardItemType.LargeCargo,
								theUniGui.getSelectedAccount().get())
							+ "<br>"//
							+ "SC Capacity: "
							+ theUniGui.getRules().get().fleet().getCargoSpace(ShipyardItemType.SmallCargo,
								theUniGui.getSelectedAccount().get())
							+ "<br>"//
							+ "Recycler Capacity: " + theUniGui.getRules().get().fleet().getCargoSpace(ShipyardItemType.Recycler,
								theUniGui.getSelectedAccount().get())//
					))//
					.withColumn(intResearchColumn("Plasma", Research::getPlasma, Research::setPlasma, 60))//
					.withColumn(intResearchColumn("Combustion", Research::getCombustionDrive, Research::setCombustionDrive, 65))//
					.withColumn(intResearchColumn("Impulse", Research::getImpulseDrive, Research::setImpulseDrive, 60))//
					.withColumn(intResearchColumn("Hyperdrive", Research::getHyperspaceDrive, Research::setHyperspaceDrive, 70))//
					.withColumn(intResearchColumn("Espionage", Research::getEspionage, Research::setEspionage, 70))//
					.withColumn(intResearchColumn("Computer", Research::getComputer, Research::setComputer, 70))//
					.withColumn(intResearchColumn("Astro", Research::getAstrophysics, Research::setAstrophysics, 55))//
					.withColumn(
						intResearchColumn("IRN", Research::getIntergalacticResearchNetwork, Research::setIntergalacticResearchNetwork, 35))//
					.withColumn(intResearchColumn("Graviton", Research::getGraviton, Research::setGraviton, 65))//
					.withColumn(intResearchColumn("Weapons", Research::getWeapons, Research::setWeapons, 65))//
					.withColumn(intResearchColumn("Shielding", Research::getShielding, Research::setShielding, 65))//
					.withColumn(intResearchColumn("Armor", Research::getArmor, Research::setArmor, 55))//
			)//
			.addHPanel("Columns:", new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING),
				fieldPanel -> fieldPanel//
					.addCheckField("Fields:", showFields, null).spacer(2)//
					.addCheckField("Temps:", showTemps, null).spacer(2)//
					.addCheckField("Mines:", showMines, null).spacer(2)//
					.addCheckField("Usage:", showUsage, null).spacer(2)//
					.addCheckField("Items:", showItems, null).spacer(2)//
					.addCheckField("Energy:", showEnergy, null).spacer(2)//
					.addCheckField("Storage:", showStorage, null).spacer(2)//
					.addComboField("Prod:", productionType, null, ProductionDisplayType.values()).spacer(3)//
					.addCheckField("P. Totals:", showProductionTotals, null).spacer(2)//
					.addCheckField("Main Facil:", showMainFacilities, null).spacer(2)//
					.addCheckField("Other Facil:", showOtherFacilities, null).spacer(2)//
					.addCheckField("Def:", showDefense, null).spacer(2)//
					.addCheckField("Moon Info:", showMoonInfo, null).spacer(2)//
			)//
			.addTable(selectedPlanets,
				planetTable -> planetTable.fill().withItemName("planet").withAdaptiveHeight(6, 30, 50)//
					.dragSourceRow(s -> s.toObject()).dragAcceptRow(a -> a.fromObject())// Allow dragging to reorder planets
					.decorate(d -> d.withTitledBorder("Planets", Color.black))//
					// This is a little hacky, but the next line tells the column the item name
					.withColumns(initPlanetColumns)
					// function
					.withNameColumn(p -> p.planet == null ? "Totals" : p.planet.getName(), (p, name) -> p.planet.setName(name), false,
						nameCol -> nameCol.withWidths(50, 100, 150).decorate((cell, decorator) -> {
							if (cell.getModelValue().planet == null) {
								decorator.bold();
							}
						}))//
					.withColumn("Upgrd", Object.class, p -> p.planet == null ? null : p.planet.getCurrentUpgrade(),
						upgradeCol -> upgradeCol.withWidths(45, 45, 45).withHeaderTooltip("Current Building Upgrade")
							.formatText(bdg -> bdg == null ? "" : ((BuildingType) bdg).shortName).withMutation(m -> m.asCombo(bdg -> {
								if (bdg instanceof BuildingType) {
									return ((BuildingType) bdg).shortName;
								} else if (bdg != null) {
									return bdg.toString();
								} else {
									return "";
								}
							}, planetUpgrades).clicks(1).mutateAttribute((p, bdg) -> {
								if (bdg instanceof BuildingType) {
									p.planet.setCurrentUpgrade((BuildingType) bdg);
								} else {
									BuildingType upgrade = p.planet.getCurrentUpgrade();
									p.planet.setCurrentUpgrade(null);
									if (UPGRADE_DONE.equals(bdg)) {
										upgrade(p.planet, upgrade.getUpgrade());
									}
								}
							})))//
					// This is a little hacky, but the next line adds the movement columns
					.withColumns(lastPlanetColumns)//
					.withColumns(planetColumns)//
					.withSelection(theSelectedPlanet, false)//
					.withAdd(() -> theUniGui.createPlanet(), null)//
					.withRemove(
						planets -> theUniGui.getSelectedAccount().get().getPlanets().getValues().removeAll(//
							planets.stream().map(p -> p.planet).collect(Collectors.toList())),
						action -> action//
							.confirmForItems("Delete Planets?", "Are you sure you want to delete ", null, true))//
			)//
			.addHPanel(null, new JustifiedBoxLayout(false).mainJustified().crossJustified(),
				bottomSplit -> bottomSplit.fill()//
					.addVPanel(resPanel -> resPanel.fill().fillV()//
						.visibleWhen(theSelectedPlanet.map(p -> p != null && p.planet != null))
						.addComponent(null, ObservableSwingUtils.label("Production").bold().withFontSize(16).label, null)//
						.addTable(
							ObservableCollection.of(TypeTokens.get().of(ResourceRow.class), ResourceRow.values()).flow()
								.refresh(theSelectedPlanet.noInitChanges()).collect(),
							resTable -> resTable.fill()//
								.withColumn("Type", ResourceRow.class, t -> t, typeCol -> typeCol.withWidths(100, 100, 100))//
								.withColumn(resourceColumn("", int.class, this::getPSValue, this::setPSValue, theSelectedPlanet, 0, 35)
									.formatText((row, v) -> renderResourceRow(row, v))//
									.withMutation(m -> m.asText(SpinnerFormat.INT).editableIf((row, v) -> canEditPSValue(row))))
								.withColumn(resourceColumn("Metal", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Metal), null, theSelectedPlanet, "0",
									55))//
								.withColumn(resourceColumn("Crystal", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Crystal), null, theSelectedPlanet,
									"0", 55))//
								.withColumn(resourceColumn("Deuterium", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Deuterium), null, theSelectedPlanet,
									"0", 65))//
								.withColumn(resourceColumn("Energy", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Energy), null, theSelectedPlanet,
									"0", 65))//
								.withColumn("Utilization", String.class, row -> getUtilization(theSelectedPlanet.get(), row),
									utilColumn -> utilColumn.withWidths(60, 60, 60)
										.withMutation(m -> m.mutateAttribute((row, u) -> setUtilization(theSelectedPlanet.get(), row, u))
											.editableIf((row, u) -> isUtilEditable(row))
											.asCombo(s -> s, ObservableCollection.of(TypeTokens.get().STRING, "100%", "90%", "80%", "70%",
												"60%", "50%", "40%", "30%", "20%", "10%", "0%"))
											.clicks(1)))//
					)//
					)//
					.addVPanel(upgradePanel -> upgradePanel.fill().fillV()//
						.addComponent(null, ObservableSwingUtils.label("Upgrade Costs").bold().withFontSize(16).label, null)//
						.visibleWhen(theUniGui.getReferenceAccount().map(a -> a != null))//
						.addTable(theUpgrades, upgradeTable -> upgradeTable.fill()//
							.withColumn("Planet", String.class, upgrade -> {
								if (upgrade == theTotalUpgrade) {
									return "Total";
								} else if (upgrade == thePlanetTotalUpgrade) {
									return "Planet Total";
								} else if (upgrade.getPlanet() != null) {
									return upgrade.getPlanet().getName() + (upgrade.isMoon() ? " Moon" : "");
								} else {
									return "";
								}
							}, planetCol -> {
								planetCol.decorate((cell, d) -> {
									PlanetWithProduction p = theSelectedPlanet.get();
									if (p != null && cell.getModelValue().getPlanet() == p.planet) {
										d.bold();
									}
								});
							})//
							.withColumn("Upgrade", AccountUpgradeType.class, upgrade -> upgrade.getType(), null)//
							.withColumn("From", int.class, upgrade -> upgrade.getFromLevel(),
								fromCol -> fromCol.withWidths(25, 35, 40)//
									.formatText((u, i) -> u.getType() == null ? "" : ("" + i)))//
							.withColumn("To", int.class, upgrade -> upgrade.getToLevel(),
								toCol -> toCol.withWidths(25, 35, 40)//
									.formatText((u, i) -> u.getType() == null ? "" : ("" + i)))//
							.withColumn("Metal", String.class, upgrade -> OGameUtils.printResourceAmount(upgrade.getCost().getMetal()),
								metalCol -> metalCol.decorate((cell, d) -> {
									if (cell.getModelValue().getType() == null) {
										d.bold();
									}
								}))//
							.withColumn("Crystal", String.class, upgrade -> OGameUtils.printResourceAmount(upgrade.getCost().getCrystal()),
								crystalCol -> crystalCol.decorate((cell, d) -> {
									if (cell.getModelValue().getType() == null) {
										d.bold();
									}
								}))//
							.withColumn("Deut", String.class, upgrade -> OGameUtils.printResourceAmount(upgrade.getCost().getDeuterium()),
								deutCol -> deutCol.decorate((cell, d) -> {
									if (cell.getModelValue().getType() == null) {
										d.bold();
									}
								}))//
							.withColumn("Time", String.class, upgrade -> {
								if (upgrade.getCost().getUpgradeTime() == null) {
									return "";
								}
								return OGameUniGui.printUpgradeTime(upgrade.getCost().getUpgradeTime());
							}, timeCol -> timeCol.withWidths(40, 100, 120))//
							.withColumn("Cargoes", Long.class, upgrade -> {
								if (upgrade.getType() == null) {
									return null;
								}
								long cost = upgrade.getCost().getTotal();
								int cargoSpace = theUniGui.getRules().get().fleet().getCargoSpace(ShipyardItemType.LargeCargo,
									theUniGui.getSelectedAccount().get());
								return (long) Math.ceil(cost * 1.0 / cargoSpace);
							}, cargoCol -> cargoCol.formatText(i -> i == null ? "" : commaFormat.format(i * 1.0)).withWidths(40, 50, 80))//
				)//
				)//
		);
	}

	<T> CategoryRenderStrategy<Research, T> researchColumn(String name, Class<T> type, Function<Research, T> getter,
		BiConsumer<Research, T> setter, int width) {
		CategoryRenderStrategy<Research, T> column = new CategoryRenderStrategy<Research, T>(name, TypeTokens.get().of(type), getter);
		column.withWidths(width, width, width);
		if (setter != null) {
			column.withMutation(m -> m.mutateAttribute((p, v) -> {
				setter.accept(p, v);
				theUniGui.getSelectedAccount().set(theUniGui.getSelectedAccount().get(), null);
			}).withRowUpdate(false));
		}
		return column;
	}

	CategoryRenderStrategy<Research, Integer> intResearchColumn(String name, Function<Research, Integer> getter,
		BiConsumer<Research, Integer> setter, int width) {
		CategoryRenderStrategy<Research, Integer> column = researchColumn(name, int.class, getter, setter, width);
		OGameUniGui.decorateDiffColumn(column, __ -> {
			Account refAccount = theUniGui.getReferenceAccount().get();
			if (refAccount == null) {
				return null;
			}
			return getter.apply(refAccount.getResearch());
		});
		if (setter != null) {
			column.withMutation(m -> m.asText(SpinnerFormat.INT).withRowUpdate(true).clicks(1)
				.filterAccept((p, value) -> value >= 0 ? null : "Must not be negative"));
		}
		return column;
	}

	static <T> CategoryRenderStrategy<PlanetWithProduction, T> planetColumn(String name, Class<T> type,
		Function<PlanetWithProduction, T> getter, BiConsumer<Planet, T> setter, int width) {
		CategoryRenderStrategy<PlanetWithProduction, T> column = new CategoryRenderStrategy<PlanetWithProduction, T>(name,
			TypeTokens.get().of(type), getter);
		column.withWidths(width, width, width);
		if (setter != null) {
			column.withMutation(
				m -> m.editableIf((p, v) -> p.planet != null).mutateAttribute((p, v) -> setter.accept(p.planet, v)).withRowUpdate(true));
		}
		column.decorate((cell, decorator) -> {
			if (cell.getModelValue().planet == null) {
				decorator.bold();
			}
		});
		return column;
	}

	CategoryRenderStrategy<PlanetWithProduction, Integer> intPlanetColumn(String name, boolean allowNegative, boolean useTotal,
		Function<Planet, Integer> getter, BiConsumer<Planet, Integer> setter, int width) {
		CategoryRenderStrategy<PlanetWithProduction, Integer> column = planetColumn(name, int.class, p -> {
			if (p.planet != null) {
				return getter.apply(p.planet);
			} else if (!useTotal) {
				return null;
			} else {
				int total = 0;
				for (PlanetWithProduction planet : theUniGui.getPlanets()) {
					total += getter.apply(planet.planet);
				}
				return total;
			}
		}, setter, width);
		OGameUniGui.decorateDiffColumn(column, planetIdx -> {
			if (planetIdx >= theUniGui.getPlanets().size()) {
				return null;
			}
			Account refAccount = theUniGui.getReferenceAccount().get();
			if (refAccount == null) {
				return null;
			} else if (refAccount.getPlanets().getValues().size() <= planetIdx) {
				return 0;
			}
			return getter.apply(refAccount.getPlanets().getValues().get(planetIdx));
		});
		if (setter != null) {
			column.withMutation(m -> {
				m.asText(SpinnerFormat.INT).clicks(1);
				if (!allowNegative) {
					m.filterAccept((p, value) -> value >= 0 ? null : "Must not be negative");
				}
			});
		}
		return column;
	}

	CategoryRenderStrategy<PlanetWithProduction, Integer> intMoonColumn(String name, boolean useTotal, Function<Moon, Integer> getter,
		BiConsumer<Moon, Integer> setter, int width) {
		return intPlanetColumn(name, false, useTotal, p -> getter.apply(p.getMoon()), (p, v) -> setter.accept(p.getMoon(), v), width);
	}

	CategoryRenderStrategy<PlanetWithProduction, Integer> shipColumn(String name, ShipyardItemType type, boolean moon, int width) {
		return intPlanetColumn(name, false, true, planet -> {
			RockyBody target = moon ? planet.getMoon() : planet;
			return target.getStationedShips(type);
		}, (planet, value) -> {
			RockyBody target = moon ? planet.getMoon() : planet;
			target.setStationedShips(type, value);
		}, width);
	}

	void upgrade(RockyBody target, AccountUpgradeType upgrade) {
		Account account = theUniGui.getSelectedAccount().get();
		int currentLevel = upgrade.getLevel(account, target);
		upgrade.setLevel(account, target, currentLevel + 1);
	}

	class PlannedAccountUpgrade extends AccountUpgrade {
		CollectionElement<PlannedUpgrade> planned;

		PlannedAccountUpgrade(AccountUpgradeType upgradeType, Planet planet, boolean moon, int fromLevel, int toLevel, UpgradeCost cost) {
			super(upgradeType, planet, moon, fromLevel, toLevel, cost);
		}

		void setPlanned(boolean planned) {
			if (planned && this.planned == null) {
				this.planned = theUniGui.getSelectedAccount().get().getPlannedUpgrades().create()//
					.with(PlannedUpgrade::getType, getType())//
					.with(PlannedUpgrade::getLocation, isMoon() ? getPlanet().getMoon() : getPlanet())//
					.create();
			} else if (!planned && this.planned != null) {
				theUniGui.getSelectedAccount().get().getPlannedUpgrades().getValues().mutableElement(this.planned.getElementId()).remove();
				this.planned = null;
			}
		}
	}

	// private static final Format<int []> COORD_FORMAT=Format. TODO

	interface TriConsumer<T, U, V> {
		void accept(T t, U u, V v);
	}

	static <T> CategoryRenderStrategy<ResourceRow, T> resourceColumn(String name, Class<T> type,
		BiFunction<PlanetWithProduction, ResourceRow, T> getter, TriConsumer<PlanetWithProduction, ResourceRow, T> setter,
		SettableValue<PlanetWithProduction> selectedPlanet, T defValue, int width) {
		CategoryRenderStrategy<ResourceRow, T> column = new CategoryRenderStrategy<ResourceRow, T>(name, TypeTokens.get().of(type), t -> {
			PlanetWithProduction planet = selectedPlanet.get();
			if (planet == null || planet.planet == null) {
				return defValue;
			} else {
				return getter.apply(planet, t);
			}
		});
		column.withWidths(width, width, width);
		if (setter != null) {
			column.withMutation(m -> m.mutateAttribute((t, v) -> setter.accept(selectedPlanet.get(), t, v)).withRowUpdate(false));
		}
		return column;
	}

	enum ResourceRow {
		Basic("Basic Income"),
		Metal("Metal Mine"),
		Crystal("Crystal Mine"),
		Deut("Deuterium Synthesizer"), //
		Solar("Solar Plant"),
		Fusion("Fusion Reactor"),
		Satellite("Solar Satellite"),
		Crawler("Crawler"), //
		Plasma("Plasma Technology"),
		Items("Items"),
		Geologist("Geologist"),
		Engineer("Engineer"),
		CommandingStaff("Commanding Staff"), //
		Collector("Collector"),
		Storage("Storage Capacity"),
		Divider("-------------------"),
		Hourly("Total per Hour"),
		Daily("Total per Day"),
		Weeky("Total per Week");

		private final String display;

		private ResourceRow(String display) {
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}

	enum ResourceColumn {
		Type, Level, Metal, Crystal, Deut, Energy, Util;
	}

	static String printProduction(double production, ProductionDisplayType time) {
		if (time.type == null) {
			return "";
		}
		switch (time.type) {
		case Year:
			production *= TimeUtils.getDaysInYears(1) * 24;
			break;
		case Month:
			production *= TimeUtils.getDaysInMonths(1) * 24;
			break;
		case Week:
			production *= 7 * 24;
			break;
		case Day:
			production *= 24;
			break;
		default:
			break;
		}
		return OGameUtils.printResourceAmount(production);
	}

	int getCargoes(PlanetWithProduction planet, boolean subtractCargoCost, ProductionDisplayType time) {
		double production = planet.getMetal().totalNet * 1.0 + planet.getCrystal().totalNet + planet.getDeuterium().totalNet;
		switch (time.type) {
		case Year:
			production *= TimeUtils.getDaysInYears(1) * 24;
			break;
		case Month:
			production *= TimeUtils.getDaysInMonths(1) * 24;
			break;
		case Week:
			production *= 7 * 24;
			break;
		case Day:
			production *= 24;
			break;
		default:
			break;
		}
		long capacity = theUniGui.getRules().get().fleet().getCargoSpace(//
			ShipyardItemType.LargeCargo, theUniGui.getSelectedAccount().get());
		if (subtractCargoCost) {
			capacity += theUniGui.getRules().get().economy()
				.getUpgradeCost(theUniGui.getSelectedAccount().get(), planet.planet, AccountUpgradeType.LargeCargo, 0, 1).getTotal();
		}
		return (int) Math.ceil(production / capacity);
	}

	int getPSValue(PlanetWithProduction planet, ResourceRow type) {
		switch (type) {
		case Basic:
			return 0;
		case Metal:
			return planet.planet.getMetalMine();
		case Crystal:
			return planet.planet.getCrystalMine();
		case Deut:
			return planet.planet.getDeuteriumSynthesizer();
		case Solar:
			return planet.planet.getSolarPlant();
		case Fusion:
			return planet.planet.getFusionReactor();
		case Satellite:
			return planet.planet.getSolarSatellites();
		case Crawler:
			return Math.min(planet.planet.getCrawlers(), //
				theUniGui.getRules().get().economy().getMaxCrawlers(theUniGui.getSelectedAccount().get(), planet.planet));
		case Plasma:
			return theUniGui.getSelectedAccount().get().getResearch().getPlasma();
		case Items:
			int items = 0;
			if (planet.planet.getMetalBonus() > 0) {
				items++;
			}
			if (planet.planet.getCrystalBonus() > 0) {
				items++;
			}
			if (planet.planet.getDeuteriumBonus() > 0) {
				items++;
			}
			return items;
		case Geologist:
			return theUniGui.getSelectedAccount().get().getOfficers().isGeologist() ? 1 : 0;
		case Engineer:
			return theUniGui.getSelectedAccount().get().getOfficers().isEngineer() ? 1 : 0;
		case CommandingStaff:
			return theUniGui.getSelectedAccount().get().getOfficers().isCommandingStaff() ? 1 : 0;
		case Collector:
			return theUniGui.getSelectedAccount().get().getGameClass() == AccountClass.Collector ? 1 : 0;
		case Storage:
			return 0;
		case Divider:
			return 0;
		case Hourly:
			return 0;
		case Daily:
			return 0;
		case Weeky:
			return 0;
		}
		throw new IllegalStateException("Unrecognized resource row: " + type);
	}

	static String renderResourceRow(ResourceRow row, int value) {
		switch (row) {
		case Basic:
			return "";
		case Metal:
		case Crystal:
		case Deut:
		case Solar:
		case Fusion:
		case Satellite:
		case Crawler:
		case Plasma:
		case Items:
			return String.valueOf(value);
		case Geologist:
		case Engineer:
		case CommandingStaff:
		case Collector:
			return value == 0 ? "" : "Active";
		case Storage:
			return "";
		case Divider:
			return "-----";
		case Hourly:
		case Daily:
		case Weeky:
			return "";
		}
		throw new IllegalStateException("Unrecognized resource row: " + row);
	}

	static boolean canEditPSValue(ResourceRow type) {
		switch (type) {
		case Metal:
		case Crystal:
		case Deut:
		case Solar:
		case Fusion:
		case Satellite:
		case Plasma:
			return true;
		default:
			break;
		}
		return false;
	}

	void setPSValue(PlanetWithProduction planet, ResourceRow type, int value) {
		switch (type) {
		case Metal:
			planet.planet.setMetalMine(value);
			break;
		case Crystal:
			planet.planet.setCrystalMine(value);
			break;
		case Deut:
			planet.planet.setDeuteriumSynthesizer(value);
			break;
		case Solar:
			planet.planet.setSolarPlant(value);
			break;
		case Fusion:
			planet.planet.setFusionReactor(value);
			break;
		case Satellite:
			planet.planet.setSolarSatellites(value);
			break;
		case Crawler:
			planet.planet.setCrawlers(value);
			break;
		case Plasma:
			theUniGui.getSelectedAccount().get().getResearch().setPlasma(value);
			break;
		default:
			break;
		}
	}

	String printProductionBySource(PlanetWithProduction planet, ResourceRow row, ResourceType resource) {
		Production p = null;
		switch (resource) {
		case Metal:
			p = planet.getMetal();
			break;
		case Crystal:
			p = planet.getCrystal();
			break;
		case Deuterium:
			p = planet.getDeuterium();
			break;
		case Energy:
			p = planet.getEnergy();
			break;
		}
		switch (row) {
		case Basic:
			return printProduction(p.byType.getOrDefault(ProductionSource.Base, 0), ProductionDisplayType.Hourly);
		case Metal:
			return printProduction(p.byType.getOrDefault(ProductionSource.MetalMine, 0), ProductionDisplayType.Hourly);
		case Crystal:
			return printProduction(p.byType.getOrDefault(ProductionSource.CrystalMine, 0), ProductionDisplayType.Hourly);
		case Deut:
			return printProduction(p.byType.getOrDefault(ProductionSource.DeuteriumSynthesizer, 0), ProductionDisplayType.Hourly);
		case Solar:
			return printProduction(p.byType.getOrDefault(ProductionSource.Solar, 0), ProductionDisplayType.Hourly);
		case Fusion:
			return printProduction(p.byType.getOrDefault(ProductionSource.Fusion, 0), ProductionDisplayType.Hourly);
		case Satellite:
			return printProduction(p.byType.getOrDefault(ProductionSource.Satellite, 0), ProductionDisplayType.Hourly);
		case Crawler:
			return printProduction(p.byType.getOrDefault(ProductionSource.Crawler, 0), ProductionDisplayType.Hourly);
		case Plasma:
			return printProduction(p.byType.getOrDefault(ProductionSource.Plasma, 0), ProductionDisplayType.Hourly);
		case Items:
			return printProduction(p.byType.getOrDefault(ProductionSource.Item, 0), ProductionDisplayType.Hourly);
		case Geologist:
			return printProduction(p.byType.getOrDefault(ProductionSource.Geologist, 0), ProductionDisplayType.Hourly);
		case Engineer:
			return printProduction(p.byType.getOrDefault(ProductionSource.Engineer, 0), ProductionDisplayType.Hourly);
		case CommandingStaff:
			return printProduction(p.byType.getOrDefault(ProductionSource.CommandingStaff, 0), ProductionDisplayType.Hourly);
		case Collector:
			return printProduction(p.byType.getOrDefault(ProductionSource.Collector, 0), ProductionDisplayType.Hourly);
		case Storage:
			if (resource == ResourceType.Energy) {
				return "0";
			}
			return printProduction(theUniGui.getRules().get().economy().getStorage(planet.planet, resource), ProductionDisplayType.Hourly);
		case Divider:
			return "----------";
		case Hourly:
			return printProduction(p.totalNet, ProductionDisplayType.Hourly);
		case Daily:
			if (resource == ResourceType.Energy) {
				return printProduction(p.totalNet, ProductionDisplayType.Hourly);
			}
			return printProduction(p.totalNet, ProductionDisplayType.Daily);
		case Weeky:
			if (resource == ResourceType.Energy) {
				return printProduction(p.totalNet, ProductionDisplayType.Hourly);
			}
			return printProduction(p.totalNet, ProductionDisplayType.Weekly);
		}
		return "";
	}

	static String getUtilization(PlanetWithProduction planet, ResourceRow row) {
		switch (row) {
		case Metal:
			return planet.planet.getMetalUtilization() + "%";
		case Crystal:
			return planet.planet.getCrystalUtilization() + "%";
		case Deut:
			return planet.planet.getDeuteriumUtilization() + "%";
		case Solar:
			return planet.planet.getSolarPlantUtilization() + "%";
		case Fusion:
			return planet.planet.getFusionReactorUtilization() + "%";
		case Satellite:
			return planet.planet.getSolarSatelliteUtilization() + "%";
		case Crawler:
			return planet.planet.getCrawlerUtilization() + "%";
		case Divider:
			return "-----------";
		default:
			return "";
		}
	}

	static boolean isUtilEditable(ResourceRow row) {
		switch (row) {
		case Metal:
		case Crystal:
		case Deut:
		case Solar:
		case Fusion:
		case Satellite:
		case Crawler:
			return true;
		default:
			break;
		}
		return false;
	}

	static void setUtilization(PlanetWithProduction planet, ResourceRow row, String util) {
		int value = Integer.parseInt(util.substring(0, util.length() - 1));
		switch (row) {
		case Metal:
			planet.planet.setMetalUtilization(value);
			break;
		case Crystal:
			planet.planet.setCrystalUtilization(value);
			break;
		case Deut:
			planet.planet.setDeuteriumUtilization(value);
			break;
		case Solar:
			planet.planet.setSolarPlantUtilization(value);
			break;
		case Fusion:
			planet.planet.setFusionReactorUtilization(value);
			break;
		case Satellite:
			planet.planet.setSolarSatelliteUtilization(value);
			break;
		case Crawler:
			planet.planet.setCrawlerUtilization(value);
			break;
		default:
			break;
		}
	}
}
