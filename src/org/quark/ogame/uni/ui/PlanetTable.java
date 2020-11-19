package org.quark.ogame.uni.ui;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JToggleButton;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.CategoryRenderStrategy.CategoryKeyAdapter;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.PanelPopulation;
import org.qommons.BiTuple;
import org.qommons.io.AdjustableComponent;
import org.qommons.io.ParsedAdjustable;
import org.qommons.io.SpinnerFormat;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.Coordinate;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.PlannedUpgrade;
import org.quark.ogame.uni.Research;
import org.quark.ogame.uni.ResearchType;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.RockyBody;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.UpgradeAccount;
import org.quark.ogame.uni.UpgradeAccount.UpgradePlanet;
import org.quark.ogame.uni.UpgradeAccount.UpgradePlanet.UpgradeMoon;
import org.quark.ogame.uni.UpgradeAccount.UpgradeRockyBody;
import org.quark.ogame.uni.Utilizable;

import com.google.common.reflect.TypeToken;

public class PlanetTable {
	public static TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>> PLANET_COLUMN_TYPE;
	static {
		PLANET_COLUMN_TYPE = new TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>>() {};
	}

	enum PlanetColumnSet {
		Mines, Utilization, Facilities, Defense, Fleet, Moon, MoonFleet
	}
	private final OGameUniGui theUniGui;

	private final SettableValue<PlanetColumnSet> theSelectedColumns;

	public PlanetTable(OGameUniGui uniGui) {
		theUniGui = uniGui;

		ObservableConfig config = uniGui.getConfig();
		theSelectedColumns = config.asValue(PlanetColumnSet.class).at("planet-categories/columns")
			.withFormat(ObservableConfigFormat.enumFormat(PlanetColumnSet.class, () -> PlanetColumnSet.Mines)).buildValue(null);
	}

	static final String UPGRADE_DONE = "Done";

	public void addPlanetTable(PanelPopulation.PanelPopulator<?, ?> panel) {
		ObservableCollection<Integer> itemOptions = ObservableCollection.of(TypeTokens.get().INT, 40, 30, 20, 10, 0);

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
		SettableValue<PlanetWithProduction> selectedPlanet = theUniGui.getSelectedPlanet();
		selectedPlanet.changes().act(evt -> {
			boolean currentlyHasDone = UPGRADE_DONE.equals(planetUpgrades.peekFirst());
			boolean shouldHaveDone;
			if (evt.getNewValue() == null || evt.getNewValue().planet == null) {
				shouldHaveDone = false;
			} else {
				shouldHaveDone = evt.getNewValue().planet.getCurrentUpgrade() != null;
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

		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> initPlanetColumns = ObservableCollection
			.create(PLANET_COLUMN_TYPE);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> fieldColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
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
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonNameColumn = ObservableCollection.of(PLANET_COLUMN_TYPE,
			planetColumn("Name", String.class, p -> p.planet == null ? "" : p.planet.getMoon().getName(),
				(p, name) -> p.getMoon().setName(name), 100));
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonFieldColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
			intMoonColumn("Moon Fields", false, moon -> theUniGui.getRules().get().economy().getFields(moon), null, 80), //
			intMoonColumn("Bonus Fields", false, Moon::getFieldBonus, Moon::setFieldBonus, 80), //
			intMoonColumn("Free Fields", false, moon -> {
				return theUniGui.getRules().get().economy().getFields(moon) - moon.getUsedFields();
			}, null, 80)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> tempColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
			intPlanetColumn("Min T", true, false, Planet::getMinimumTemperature, (planet, t) -> {
				planet.setMinimumTemperature(t);
				planet.setMaximumTemperature(t + 40);
			}, 40), //
			intPlanetColumn("Max T", true, false, Planet::getMaximumTemperature, (planet, t) -> {
				planet.setMaximumTemperature(t);
				planet.setMinimumTemperature(t - 40);
			}, 40)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> upgradeColumn = ObservableCollection.of(PLANET_COLUMN_TYPE,
			new CategoryRenderStrategy<PlanetWithProduction, Object>("Upgrd", TypeTokens.get().OBJECT,
				p -> p.planet == null ? null : p.planet.getCurrentUpgrade())//
					.withWidths(45, 45, 45).formatText(bdg -> bdg == null ? "" : ((BuildingType) bdg).shortName)
					.withMutation(m -> m.asCombo(bdg -> {
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
								upgrade(p.upgradePlanet, upgrade.getUpgrade());
							}
						}
					}))//
		);

		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mineColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
			intPlanetColumn("M Mine", AccountUpgradeType.MetalMine, 55), //
			intPlanetColumn("C Mine", AccountUpgradeType.CrystalMine, 55), //
			intPlanetColumn("D Synth", AccountUpgradeType.DeuteriumSynthesizer, 55), //
			intPlanetColumn("Crawlers", AccountUpgradeType.Crawler, 60)//
		/*.formatText((planet, crawlers) -> {
		StringBuilder str = new StringBuilder();
		str.append(crawlers);
		if (planet.planet != null) {
		str.append('/');
		str.append(theUniGui.getRules().get().economy().getMaxCrawlers(theUniGui.getSelectedAccount().get(), planet.planet));
		}
		return str.toString();
		}) //*/
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> usageColumns1 = ObservableCollection.of(PLANET_COLUMN_TYPE,
			planetColumn("M %", int.class, p -> p.planet == null ? null : p.planet.getMetalUtilization(), Planet::setMetalUtilization, 45)
				.withHeaderTooltip("Metal Mine Utilization").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", (cell, until) -> {
					int max = theUniGui.getRules().get().economy().getMaxUtilization(Utilizable.MetalMine,
						theUniGui.getSelectedAccount().get(), cell.getModelValue().planet);
					return ResourceSettingsPanel.getPercentages(max);
				}).clicks(1)), //
			planetColumn("C %", int.class, p -> p.planet == null ? null : p.planet.getCrystalUtilization(), Planet::setCrystalUtilization,
				45).withHeaderTooltip("Crystal Mine Utilization").formatText(v -> v == null ? "" : v + "%")
					.withMutation(m -> m.asCombo(v -> v + "%", (cell, until) -> {
						int max = theUniGui.getRules().get().economy().getMaxUtilization(Utilizable.CrystalMine,
							theUniGui.getSelectedAccount().get(), cell.getModelValue().planet);
						return ResourceSettingsPanel.getPercentages(max);
					}).clicks(1)), //
			planetColumn("D %", int.class, p -> p.planet == null ? null : p.planet.getDeuteriumUtilization(),
				Planet::setDeuteriumUtilization, 45).withHeaderTooltip("Deuterium Synthesizer Utilization")
					.formatText(v -> v == null ? "" : v + "%").withMutation(m -> m.asCombo(v -> v + "%", (cell, until) -> {
						int max = theUniGui.getRules().get().economy().getMaxUtilization(Utilizable.DeuteriumSynthesizer,
							theUniGui.getSelectedAccount().get(), cell.getModelValue().planet);
						return ResourceSettingsPanel.getPercentages(max);
					}).clicks(1)), //
			planetColumn("Cr %", int.class, p -> p.planet == null ? null : p.planet.getCrawlerUtilization(), Planet::setCrawlerUtilization,
				45).withHeaderTooltip("Crawler Utilization").formatText(v -> v == null ? "" : v + "%")
					.withMutation(m -> m.asCombo(v -> v + "%", (cell, until) -> {
						int max = theUniGui.getRules().get().economy().getMaxUtilization(Utilizable.Crawler,
							theUniGui.getSelectedAccount().get(), cell.getModelValue().planet);
						return ResourceSettingsPanel.getPercentages(max);
					}).clicks(1)) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> itemColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
			planetColumn("M+", int.class, p -> p.planet == null ? null : p.planet.getMetalBonus(), Planet::setMetalBonus, 45)
				.withHeaderTooltip("Metal Bonus Item").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", itemOptions).clicks(1)), //
			planetColumn("C+", int.class, p -> p.planet == null ? null : p.planet.getCrystalBonus(), Planet::setCrystalBonus, 45)
				.withHeaderTooltip("Crystal Bonus Item").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", itemOptions).clicks(1)), //
			planetColumn("D+", int.class, p -> p.planet == null ? null : p.planet.getDeuteriumBonus(), Planet::setDeuteriumBonus, 45)
				.withHeaderTooltip("Deuterium Bonus Item").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", itemOptions).clicks(1)), //
			planetColumn("E+", int.class, p -> p.planet == null ? null : p.planet.getEnergyBonus(), Planet::setEnergyBonus, 45)
				.withHeaderTooltip("Energy Bonus Item").formatText(v -> v == null ? "" : v + "%")
				.withMutation(m -> m.asCombo(v -> v + "%", itemOptions).clicks(1)) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> energyBldgs = ObservableCollection.of(PLANET_COLUMN_TYPE,
			intPlanetColumn("Sats", AccountUpgradeType.SolarSatellite, 75)
				.withKeyListener(new CategoryKeyAdapter<PlanetWithProduction, Levels>() {
					@Override
					public void keyTyped(ModelCell<? extends PlanetWithProduction, ? extends Levels> cell, KeyEvent e) {
						if (cell.getModelValue().planet == null || e.getKeyChar() != '*') {
							return;
						}
						int energyNeeded = -cell.getModelValue().getEnergy().totalNet
							+ cell.getModelValue().getEnergy().byType.get(ProductionSource.Satellite);
						int newSats;
						if (energyNeeded < 0) {
							newSats = 0;
						} else {
							int satEnergy = theUniGui.getRules().get().economy().getSatelliteEnergy(theUniGui.getSelectedAccount().get(),
								cell.getModelValue().planet);
							if (satEnergy <= 0) {
								return;
							}
							newSats = (int) Math.ceil(energyNeeded * 1.0 / satEnergy);
							cell.getModelValue().planet.setSolarSatellites(newSats);
							int energyExcess = theUniGui.getRules().get().economy().getProduction(theUniGui.getSelectedAccount().get(),
								cell.getModelValue().planet, ResourceType.Energy, 1).totalNet;
							if (energyExcess > satEnergy) {
								// Bonuses like for Collector or Officers can bump this up
								int removeSats = (int) Math.floor(energyExcess * 1.0 / (energyExcess + energyNeeded) * newSats);
								newSats -= removeSats;
							}
						}
						cell.getModelValue().planet.setSolarSatellites(newSats);
						e.consume();
					}
				}), //
			intPlanetColumn("Solar", AccountUpgradeType.SolarPlant, 55), //
			intPlanetColumn("Fusion", AccountUpgradeType.FusionReactor, 55),
			planetColumn("Net Enrgy", int.class, p -> p.planet == null ? null : p.getEnergy().totalNet, null, 65));
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> usageColumns2 = ObservableCollection.of(PLANET_COLUMN_TYPE,
			planetColumn("Solar %", int.class, p -> p.planet == null ? null : p.planet.getSolarPlantUtilization(),
				Planet::setSolarPlantUtilization, 55).withHeaderTooltip("Solar Plant Utilization").formatText(v -> v == null ? "" : v + "%")
					.withMutation(m -> m.asCombo(v -> v + "%", (cell, until) -> {
						int max = theUniGui.getRules().get().economy().getMaxUtilization(Utilizable.SolarPlant,
							theUniGui.getSelectedAccount().get(), cell.getModelValue().planet);
						return ResourceSettingsPanel.getPercentages(max);
					}).clicks(1)), //
			planetColumn("Sat %", int.class, p -> p.planet == null ? null : p.planet.getSolarSatelliteUtilization(),
				Planet::setSolarSatelliteUtilization, 45).withHeaderTooltip("Solar Satellite Utilization")
					.formatText(v -> v == null ? "" : v + "%").withMutation(m -> m.asCombo(v -> v + "%", (cell, until) -> {
						int max = theUniGui.getRules().get().economy().getMaxUtilization(Utilizable.SolarSatellite,
							theUniGui.getSelectedAccount().get(), cell.getModelValue().planet);
						return ResourceSettingsPanel.getPercentages(max);
					}).clicks(1)), //
			planetColumn("FZN %", int.class, p -> p.planet == null ? null : p.planet.getFusionReactorUtilization(),
				Planet::setFusionReactorUtilization, 45).withHeaderTooltip("Fusion Reactor Utilization")
					.formatText(v -> v == null ? "" : v + "%").withMutation(m -> m.asCombo(v -> v + "%", (cell, until) -> {
						int max = theUniGui.getRules().get().economy().getMaxUtilization(Utilizable.FusionReactor,
							theUniGui.getSelectedAccount().get(), cell.getModelValue().planet);
						return ResourceSettingsPanel.getPercentages(max);
					}).clicks(1)), //
			planetColumn("Net Enrgy", int.class, p -> p.planet == null ? null : p.getEnergy().totalNet, null, 65)
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> storageColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
			intPlanetColumn("M Stor", AccountUpgradeType.MetalStorage, 45), //
			intPlanetColumn("C Stor", AccountUpgradeType.CrystalStorage, 45), //
			intPlanetColumn("D Stor", AccountUpgradeType.DeuteriumStorage, 45)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mainFacilities = ObservableCollection.of(PLANET_COLUMN_TYPE,
			intPlanetColumn("Robotics", AccountUpgradeType.RoboticsFactory, 60), //
			intPlanetColumn("Shipyard", AccountUpgradeType.Shipyard, 60), //
			intPlanetColumn("Lab", AccountUpgradeType.ResearchLab, 55), //
			intPlanetColumn("Nanite", AccountUpgradeType.NaniteFactory, 55)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> otherFacilities = ObservableCollection.of(PLANET_COLUMN_TYPE,
			intPlanetColumn("Ally Depot", AccountUpgradeType.AllianceDepot, 65), //
			intPlanetColumn("Silo", AccountUpgradeType.MissileSilo, 45), //
			intPlanetColumn("Terraformer", AccountUpgradeType.Terraformer, 65), //
			intPlanetColumn("Space Dock", AccountUpgradeType.SpaceDock, 65)//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> coordColumn = ObservableCollection.of(PLANET_COLUMN_TYPE,
			new CategoryRenderStrategy<PlanetWithProduction, PlanetTable.AdjustableCoords>("Coords",
				TypeTokens.get().of(PlanetTable.AdjustableCoords.class),
				p -> p.planet == null ? null : toAdjustable(p.planet.getCoordinates()))//
					.withMutation(mut -> {
						mut.asText(COORD_FORMAT).mutateAttribute((pwp, coords) -> {
							if (pwp.planet != null) {
								adjustCoord(pwp.planet.getCoordinates(), coords);
							}
						});
					}));
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonBuildings = ObservableCollection.of(PLANET_COLUMN_TYPE,
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
								upgrade(p.upgradePlanet.getMoon(), upgrade.getUpgrade());
							}
						}
					})), //
			intMoonColumn("Lunar Base", AccountUpgradeType.LunarBase, 60), //
			intMoonColumn("Robotics", AccountUpgradeType.RoboticsFactory, 55), //
			intMoonColumn("Phalanx", AccountUpgradeType.SensorPhalanx, 55), //
			intMoonColumn("Jump Gate", AccountUpgradeType.JumpGate, 60), //
			intMoonColumn("Shipyard", AccountUpgradeType.Shipyard, 55) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> defenseColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
			shipColumn(ShipyardItemType.RocketLauncher, false, 55), //
			shipColumn(ShipyardItemType.LightLaser, false, 55), //
			shipColumn(ShipyardItemType.HeavyLaser, false, 55), //
			shipColumn(ShipyardItemType.GaussCannon, false, 55), //
			shipColumn(ShipyardItemType.IonCannon, false, 55), //
			shipColumn(ShipyardItemType.PlasmaTurret, false, 55), //
			shipColumn(ShipyardItemType.SmallShield, false, 55), //
			shipColumn(ShipyardItemType.LargeShield, false, 55), //
			shipColumn(ShipyardItemType.AntiBallisticMissile, false, 55), //
			shipColumn(ShipyardItemType.InterPlanetaryMissile, false, 55) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> fleetColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
			shipColumn(ShipyardItemType.LargeCargo, false, 65), //
			shipColumn(ShipyardItemType.SmallCargo, false, 55), //
			shipColumn(ShipyardItemType.Recycler, false, 55), //
			shipColumn(ShipyardItemType.LightFighter, false, 65), //
			shipColumn(ShipyardItemType.HeavyFighter, false, 65), //
			shipColumn(ShipyardItemType.Cruiser, false, 65), //
			shipColumn(ShipyardItemType.BattleShip, false, 65), //
			shipColumn(ShipyardItemType.BattleCruiser, false, 65), //
			shipColumn(ShipyardItemType.Bomber, false, 65), //
			shipColumn(ShipyardItemType.Destroyer, false, 65), //
			shipColumn(ShipyardItemType.DeathStar, false, 45), //
			shipColumn(ShipyardItemType.Reaper, false, 65), //
			shipColumn(ShipyardItemType.PathFinder, false, 55), //
			shipColumn(ShipyardItemType.ColonyShip, false, 55), //
			shipColumn(ShipyardItemType.EspionageProbe, false, 55) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonDefenseColumns = ObservableCollection.of(
			PLANET_COLUMN_TYPE, shipColumn(ShipyardItemType.RocketLauncher, true, 55), //
			shipColumn(ShipyardItemType.LightLaser, true, 55), //
			shipColumn(ShipyardItemType.HeavyLaser, true, 55), //
			shipColumn(ShipyardItemType.GaussCannon, true, 55), //
			shipColumn(ShipyardItemType.IonCannon, true, 55), //
			shipColumn(ShipyardItemType.PlasmaTurret, true, 55), //
			shipColumn(ShipyardItemType.SmallShield, true, 55), //
			shipColumn(ShipyardItemType.LargeShield, true, 55) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonFleetColumns = ObservableCollection.of(PLANET_COLUMN_TYPE,
			shipColumn(ShipyardItemType.LargeCargo, true, 65), //
			shipColumn(ShipyardItemType.SmallCargo, true, 55), //
			shipColumn(ShipyardItemType.Recycler, true, 55), //
			shipColumn(ShipyardItemType.LightFighter, true, 65), //
			shipColumn(ShipyardItemType.HeavyFighter, true, 65), //
			shipColumn(ShipyardItemType.Cruiser, true, 65), //
			shipColumn(ShipyardItemType.BattleShip, true, 65), //
			shipColumn(ShipyardItemType.BattleCruiser, true, 65), //
			shipColumn(ShipyardItemType.Bomber, true, 65), //
			shipColumn(ShipyardItemType.Destroyer, true, 65), //
			shipColumn(ShipyardItemType.DeathStar, true, 45), //
			shipColumn(ShipyardItemType.Reaper, true, 65), //
			shipColumn(ShipyardItemType.PathFinder, true, 55), //
			shipColumn(ShipyardItemType.ColonyShip, true, 55), //
			shipColumn(ShipyardItemType.EspionageProbe, true, 55) //
		);

		EnumMap<PlanetColumnSet, ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>>> columnSets = new EnumMap<>(
			PlanetColumnSet.class);
		for (PlanetColumnSet columnSet : PlanetColumnSet.values()) {
			switch (columnSet) {
			case Mines:
				columnSets.put(columnSet, ObservableCollection.flattenCollections(PLANET_COLUMN_TYPE, //
					upgradeColumn, mineColumns, energyBldgs, tempColumns, storageColumns, coordColumn).collect());
				break;
			case Utilization:
				columnSets.put(columnSet, ObservableCollection.flattenCollections(PLANET_COLUMN_TYPE, //
					usageColumns1, itemColumns, usageColumns2).collect());
				break;
			case Facilities:
				columnSets.put(columnSet, ObservableCollection.flattenCollections(PLANET_COLUMN_TYPE, //
					upgradeColumn, fieldColumns, mainFacilities, otherFacilities).collect());
				break;
			case Defense:
				columnSets.put(columnSet, // ObservableCollection.flattenCollections(planetColumnType, //
					defenseColumns// ).collect());
				);
				break;
			case Fleet:
				columnSets.put(columnSet, // ObservableCollection.flattenCollections(planetColumnType, //
					fleetColumns// ).collect());
				);
				break;
			case Moon:
				columnSets.put(columnSet, ObservableCollection.flattenCollections(PLANET_COLUMN_TYPE, //
					moonNameColumn, moonFieldColumns, moonBuildings, moonDefenseColumns).collect());
				break;
			case MoonFleet:
				columnSets.put(columnSet, // ObservableCollection.flattenCollections(planetColumnType, //
					moonFleetColumns// ).collect());
				);
				break;
			}
		}
		TypeToken<ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>>> columnSetType = TypeTokens.get()
			.keyFor(ObservableCollection.class).parameterized(PLANET_COLUMN_TYPE);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> selectedColumns = ObservableCollection.flattenValue(//
			theSelectedColumns.map(columnSetType, columnSets::get));
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumns = ObservableCollection
			.flattenCollections(PLANET_COLUMN_TYPE, //
				initPlanetColumns, //
				selectedColumns)
			.collect();

		ObservableCollection<Research> researchColl = ObservableCollection
			.flattenValue(theUniGui.getSelectedAccount().<ObservableCollection<Research>> map(
				TypeTokens.get().keyFor(ObservableCollection.class).parameterized(Research.class), account -> {
					if (account == null) {
						return ObservableCollection.of(Research.class);
					} else {
						return ObservableCollection.of(Research.class, account.getResearch());
					}
				}));
		researchColl.simpleChanges().act(__ -> theUniGui.refreshProduction());
		panel.fill().fillV()//
			.addTable(researchColl,
				researchTable -> researchTable.fill().withAdaptiveHeight(1, 1, 1).decorate(d -> d.withTitledBorder("Research", Color.black))//
					.withColumn("Upgrade", Object.class, r -> r.getCurrentUpgrade(),
						upgradeCol -> upgradeCol.withWidths(60, 60, 60).withHeaderTooltip("Current Research Upgrade")
							.formatText(rsrch -> rsrch == null ? "" : ((ResearchType) rsrch).shortName)
							.withMutation(m -> m.asCombo(rsrch -> {
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
										theUniGui.refreshProduction();
									}
								}
							})))//
					.withColumn(intResearchColumn("Energy", ResearchType.Energy, 45))//
					.withColumn(intResearchColumn("Laser", ResearchType.Laser, 40))//
					.withColumn(intResearchColumn("Ion", ResearchType.Ion, 35))//
					.withColumn(intResearchColumn("HyprSpc", ResearchType.Hyperspace, 55)//
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
					.withColumn(intResearchColumn("Plasma", ResearchType.Plasma, 45))//
					.withColumn(intResearchColumn("Combustn", ResearchType.Combustion, 60))//
					.withColumn(intResearchColumn("Impulse", ResearchType.Impulse, 50))//
					.withColumn(intResearchColumn("HyprDv", ResearchType.Hyperdrive, 50))//
					.withColumn(intResearchColumn("Esp", ResearchType.Espionage, 35))//
					.withColumn(intResearchColumn("Comptr", ResearchType.Computer, 50))//
					.withColumn(intResearchColumn("Astro", ResearchType.Astrophysics, 40))//
					.withColumn(intResearchColumn("IRN", ResearchType.IntergalacticResearchNetwork, 35))//
					.withColumn(intResearchColumn("Grav", ResearchType.Graviton, 40))//
					.withColumn(intResearchColumn("Weapon", ResearchType.Weapons, 55))//
					.withColumn(intResearchColumn("Shield", ResearchType.Shielding, 45))//
					.withColumn(intResearchColumn("Armor", ResearchType.Armor, 40))//
			)//
			.addTable(theUniGui.getPlanetsWithTotal(),
				planetTable -> planetTable.fill().fillV().withItemName("planet").withAdaptiveHeight(6, 30, 50)//
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
					.withColumns(planetColumns)//
					.withSelection(selectedPlanet, false)//
					.withAdd(() -> theUniGui.createPlanet(), null)//
					.withRemove(
						planets -> theUniGui.getSelectedAccount().get().getPlanets().getValues().removeAll(//
							planets.stream().map(p -> p.planet).collect(Collectors.toList())),
						action -> action//
							.confirmForItems("Delete Planets?", "Are you sure you want to delete ", null, true))//
					.withTableOption(columnsPanel -> {
						columnsPanel.addToggleField("Columns:", theSelectedColumns, Arrays.asList(PlanetColumnSet.values()), //
							JToggleButton.class, cs -> new JToggleButton("" + cs), columnButtons -> {});
					})//
		);
	}

	<T> CategoryRenderStrategy<Research, T> researchColumn(String name, Class<T> type, Function<Research, T> getter,
		BiConsumer<Research, T> setter, int width) {
		CategoryRenderStrategy<Research, T> column = new CategoryRenderStrategy<Research, T>(name, TypeTokens.get().of(type), getter);
		column.withWidths(width, width, width + 10);
		if (setter != null) {
			column.withMutation(m -> m.mutateAttribute((p, v) -> {
				setter.accept(p, v);
				theUniGui.getSelectedAccount().set(theUniGui.getSelectedAccount().get(), null);
			}).withRowUpdate(false));
		}
		return column;
	}

	CategoryRenderStrategy<Research, Levels> intResearchColumn(String name, ResearchType type, int width) {
		return researchColumn(name, Levels.class, //
			r -> {
				UpgradeAccount ua = theUniGui.getUpgradeAccount().get();
				return new Levels(ua.getWrapped().getResearch().getResearchLevel(type), //
					ua.getResearch().getResearchLevel(type));
			}, (r, levels) -> {
				UpgradeAccount ua = theUniGui.getUpgradeAccount().get();
				adjustLevels(ua, null, type.getUpgrade(), levels.current, levels.goal);
			}, width)//
				.withMutation(m -> m.asText(LEVELS_FORMAT).clicks(1));
	}

	void parseLevels(Research r, Levels levels, ResearchType type) {
		Account account = theUniGui.getUpgradeAccount().get().getWrapped();
		int current = r.getResearchLevel(type);
		int currentDiff = levels.current - current;
		int goal = theUniGui.getUpgradeAccount().get().getResearch().getResearchLevel(type);
		int goalDiff = levels.goal - goal - currentDiff;

		account.getResearch().setResearchLevel(type, levels.current);
		ListIterator<PlannedUpgrade> upgradeIter = account.getPlannedUpgrades().getValues().listIterator();
		while (upgradeIter.hasNext()) {
			PlannedUpgrade upgrade = upgradeIter.next();
			if (upgrade.getType().research == type && (upgrade.getQuantity() > 0) == (goalDiff > 0)) {
				int qComp = Integer.compare(Math.abs(upgrade.getQuantity()), Math.abs(goalDiff));
				if (qComp <= 0) {
					goalDiff -= upgrade.getQuantity();
					upgradeIter.remove();
				} else {
					upgrade.setQuantity(upgrade.getQuantity() - goalDiff);
					goalDiff = 0;
					upgradeIter.set(upgrade);
					break;
				}
			}
		}
		if (goalDiff != 0) {
			int goalDiffAbs = Math.abs(goalDiff);
			for (int i = 0; i < goalDiffAbs; i++) {
				account.getPlannedUpgrades().create()//
					.with(PlannedUpgrade::getType, type.getUpgrade())//
					.with(PlannedUpgrade::getQuantity, goalDiff > 0 ? 1 : -1)//
					.create();
			}
		}
	}

	public static <T> CategoryRenderStrategy<PlanetWithProduction, T> planetColumn(String name, Class<T> type,
		Function<PlanetWithProduction, T> getter, BiConsumer<Planet, T> setter, int width) {
		CategoryRenderStrategy<PlanetWithProduction, T> column = new CategoryRenderStrategy<PlanetWithProduction, T>(name,
			TypeTokens.get().of(type), getter).formatText(v -> v == null ? "" : v.toString());
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

	static class Levels {
		final int current;
		final int goal;

		Levels(int current, int goal) {
			this.current = current;
			this.goal = goal;
		}

		@Override
		public String toString() {
			if (current == goal) {
				return "" + current;
			} else {
				return current + "/" + goal;
			}
		}
	}

	static final SpinnerFormat<Levels> LEVELS_FORMAT = new SpinnerFormat<Levels>() {
		@Override
		public void append(StringBuilder text, Levels value) {
			if (value == null) {
				return;
			}
			text.append(value.current);
			if (value.goal != value.current) {
				text.append('/').append(value.goal);
			}
		}

		@Override
		public Levels parse(CharSequence text) throws ParseException {
			int current = 0, goal = 0;
			int c = 0;
			while (c < text.length() && Character.isWhitespace(text.charAt(c))) {
				c++;
			}
			int startCurrent = c;
			for (; c < text.length() && text.charAt(c) >= '0' && text.charAt(c) <= '9'; c++) {
				current = current * 10 + (text.charAt(c) - '0');
			}
			if (c == startCurrent) {
				current = -1;
			}
			while (c < text.length() && Character.isWhitespace(text.charAt(c))) {
				c++;
			}
			if (c < text.length()) {
				if (text.charAt(c) != '/') {
					throw new ParseException("Unrecognized character: '" + text.charAt(c) + "'", c);
				}
				c++;
				while (c < text.length() && Character.isWhitespace(text.charAt(c))) {
					c++;
				}
				int startGoal = c;
				for (; c < text.length() && text.charAt(c) >= '0' && text.charAt(c) <= '9'; c++) {
					goal = goal * 10 + (text.charAt(c) - '0');
				}
				if (c == startGoal) {
					goal = -1;
				}
				if (current < 0 && goal < 0) {
					throw new ParseException("Missing values", 0);
				}
			} else {
				if (current < 0) {
					throw new ParseException("Missing values", 0);
				}
				goal = current;
			}
			return new Levels(current, goal);
		}

		@Override
		public boolean supportsAdjustment(boolean withContext) {
			return withContext;
		}

		@Override
		public BiTuple<Levels, String> adjust(Levels value, String formatted, int cursor, boolean up) {
			int slashIdx = formatted.indexOf('/');
			if (slashIdx < 0) {
				int newValue = value.current + (up ? 1 : -1);
				return new BiTuple<>(new Levels(newValue, newValue), "" + newValue);
			} else if (cursor <= slashIdx) {
				Levels newLevels = new Levels(value.current + (up ? 1 : -1), value.goal);
				return new BiTuple<>(newLevels, newLevels.current + "/" + newLevels.goal);
			} else {
				Levels newLevels = new Levels(value.current, value.goal + (up ? 1 : -1));
				return new BiTuple<>(newLevels, newLevels.current + "/" + newLevels.goal);
			}
		}
	};

	CategoryRenderStrategy<PlanetWithProduction, Levels> intPlanetColumn(String name, AccountUpgradeType type, int width) {
		return intPlanetColumn(name, type, false, width);
	}

	CategoryRenderStrategy<PlanetWithProduction, Levels> intPlanetColumn(String name, AccountUpgradeType type, boolean moon, int width) {
		return planetColumn(name, Levels.class, //
			p -> {
				UpgradeAccount ua = theUniGui.getUpgradeAccount().get();
				if (ua == null) {
					return new Levels(0, 0);
				}
				UpgradePlanet planet = p.planet == null ? null : ua.getPlanets().getUpgradePlanet(p.planet);
				if (p.planet != null && planet == null) {
					return new Levels(0, 0);
				}
				if (moon) {
					if (p.planet == null) {
						int current = 0, goal = 0;
						for (Planet planet2 : ua.getPlanets().getValues()) {
							current += type.getLevel(theUniGui.getSelectedAccount().get(),
								((UpgradePlanet) planet2).getWrapped().getMoon());
							goal += type.getLevel(ua, planet2.getMoon());
						}
						return new Levels(current, goal);
					} else {
						return new Levels(type.getLevel(p.planet.getAccount(), p.planet.getMoon()),
							type.getLevel(ua, planet.getMoon()));
					}
				} else {
					if (p.planet == null) {
						int current = 0, goal = 0;
						for (Planet planet2 : ua.getPlanets().getValues()) {
							current += type.getLevel(theUniGui.getSelectedAccount().get(), ((UpgradePlanet) planet2).getWrapped());
							goal += type.getLevel(ua, planet2);
						}
						return new Levels(current, goal);
					} else {
						return new Levels(type.getLevel(p.planet.getAccount(), p.planet),
							type.getLevel(ua, planet));
					}
				}
			}, (p, levels) -> {
				UpgradeAccount ua = theUniGui.getUpgradeAccount().get();
				UpgradeRockyBody upgradeTarget = moon ? ua.getPlanets().getUpgradePlanet(p).getMoon() : ua.getPlanets().getUpgradePlanet(p);
				adjustLevels(ua, upgradeTarget, type, levels.current, levels.goal);
			}, width)//
				.withMutation(m -> m.editableIf((p, lvl) -> p.planet != null).asText(LEVELS_FORMAT).clicks(1));
	}

	public static void adjustLevels(UpgradeAccount account, UpgradeRockyBody p, AccountUpgradeType type, int newCurrent, int newGoal) {
		if (newGoal < 0) { // Code for update the current value, but don't change the goal
			newGoal = type.getLevel(account, p);
		} else if (newCurrent < 0) { // Code for update the goal, but don't change the current value
			newCurrent = type.getLevel(account.getWrapped(), p == null ? null : p.getWrapped());
		}
		RockyBody wrappedBody = p == null ? null : p.getWrapped();
		boolean moon = p != null && !(p instanceof Planet);
		long planetId;
		if (p == null) {
			planetId = 0;
		} else if (moon) {
			planetId = ((UpgradeMoon) p).getPlanet().getWrapped().getId();
		} else {
			planetId = ((Planet) wrappedBody).getId();
		}
		int current = type.getLevel(account.getWrapped(), wrappedBody);
		int oldGoal = type.getLevel(account, p);
		int goalDiff;
		int currentDiff = newCurrent - current;
		goalDiff = newGoal - oldGoal - currentDiff;
		type.setLevel(account.getWrapped(), wrappedBody, newCurrent);
		ListIterator<PlannedUpgrade> upgradeIter = account.getWrapped().getPlannedUpgrades().getValues().reverse().listIterator();
		while (upgradeIter.hasNext()) {
			PlannedUpgrade upgrade = upgradeIter.next();
			if (upgrade.getPlanet() == planetId && upgrade.isMoon() == moon && upgrade.getType() == type
				&& (upgrade.getQuantity() > 0) != (goalDiff > 0)) {
				int qComp = Integer.compare(Math.abs(upgrade.getQuantity()), Math.abs(goalDiff));
				if (qComp <= 0) {
					goalDiff += upgrade.getQuantity();
					upgradeIter.remove();
				} else {
					upgrade.setQuantity(upgrade.getQuantity() + goalDiff);
					goalDiff = 0;
					upgradeIter.set(upgrade);
					break;
				}
			}
		}
		if (goalDiff != 0) {
			if (type.shipyardItem != null) {
				account.getWrapped().getPlannedUpgrades().create()//
					.with(PlannedUpgrade::getType, type)//
					.with(PlannedUpgrade::getPlanet, planetId)//
					.with(PlannedUpgrade::isMoon, moon)//
					.with(PlannedUpgrade::getQuantity, goalDiff)//
					.create();
			} else if (type.building != null) {
				int goalDiffAbs = Math.abs(goalDiff);
				for (int i = 0; i < goalDiffAbs; i++) {
					account.getWrapped().getPlannedUpgrades().create()//
						.with(PlannedUpgrade::getType, type)//
						.with(PlannedUpgrade::getPlanet, planetId)//
						.with(PlannedUpgrade::isMoon, moon)//
						.with(PlannedUpgrade::getQuantity, goalDiff > 0 ? 1 : -1)//
						.create();
				}
			} else {
				int goalDiffAbs = Math.abs(goalDiff);
				for (int i = 0; i < goalDiffAbs; i++) {
					account.getWrapped().getPlannedUpgrades().create()//
						.with(PlannedUpgrade::getType, type)//
						.with(PlannedUpgrade::getQuantity, goalDiff > 0 ? 1 : -1)//
						.create();
				}
			}
		}
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
			return getter.apply(theUniGui.getUpgradeAccount().get().getPlanets().getValues().get(planetIdx));
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

	CategoryRenderStrategy<PlanetWithProduction, Levels> intMoonColumn(String name, AccountUpgradeType type, int width) {
		return intPlanetColumn(name, type, true, width);
	}

	CategoryRenderStrategy<PlanetWithProduction, Levels> shipColumn(ShipyardItemType type, boolean moon, int width) {
		if (moon) {
			return intMoonColumn(OGameUtils.abbreviate(type), type.getUpgrade(), width).withWidths(width, width, width + 100);
		} else {
			return intPlanetColumn(OGameUtils.abbreviate(type), type.getUpgrade(), width).withWidths(width, width, width + 100);
		}
	}

	void upgrade(UpgradeRockyBody target, AccountUpgradeType upgrade) {
		UpgradeAccount ua = theUniGui.getUpgradeAccount().get();
		int currentLevel = upgrade.getLevel(ua.getWrapped(), target == null ? null : target.getWrapped());
		int goal = upgrade.getLevel(ua, target);
		if (goal <= currentLevel) {
			goal++;
		}
		adjustLevels(ua, target, upgrade, currentLevel + 1, goal);
	}

	class CoordComponent implements AdjustableComponent {
		private final int theType;
		private final int theValue;
		private final int theStart;
		private final int theEnd;

		public CoordComponent(int type, int value, int start, int end) {
			theType = type;
			theValue = value;
			theStart = start;
			theEnd = end;
		}

		public int getValue() {
			return theValue;
		}

		@Override
		public int getStart() {
			return theStart;
		}

		@Override
		public int getEnd() {
			return theEnd;
		}

		int getMax() {
			switch (theType) {
			case 0:
				return theUniGui.getSelectedAccount().get().getUniverse().getGalaxies();
			case 1:
				return 499;
			case 2:
				return 15;
			default:
				return 16; // Allow this for expeditions later
			}
		}

		CoordComponent adjust(int amount) {
			if (amount == 0) {
				return this;
			}
			int value;
			int digitChange = 0;
			if (amount > 0) {
				if (theValue == getMax()) {
					return this;
				}
				value = theValue + amount;
				if (value > getMax()) {
					value = getMax();
				}
				if (theValue < 10 && value >= 10) {
					digitChange++;
				}
			} else {
				if (theValue == 1) {
					return this;
				}
				value = theValue + amount;
				if (value < 1) {
					value = 1;
				}
				if (value < 10 && theValue >= 10) {
					digitChange++;
				}
			}
			return new CoordComponent(theType, value, theStart, theEnd + digitChange);
		}
	}

	class AdjustableCoords implements ParsedAdjustable<AdjustableCoords, CoordComponent> {
		private final CoordComponent[] theComponents;

		public AdjustableCoords(CoordComponent galaxy, CoordComponent system, CoordComponent slot) {
			theComponents = new CoordComponent[] { galaxy, system, slot };
		}

		@Override
		public List<CoordComponent> getComponents() {
			return Arrays.asList(theComponents);
		}

		@Override
		public AdjustableCoords adjust(int position, int amount) {
			if (position >= theComponents[2].getStart()) {
				if (position > theComponents[2].getEnd()) {
					return this;
				}
				CoordComponent adjusted = theComponents[2].adjust(amount);
				if (adjusted == theComponents[2]) {
					return this;
				} else {
					return new AdjustableCoords(theComponents[0], theComponents[1], adjusted);
				}
			} else if (position >= theComponents[1].getStart()) {
				if (position > theComponents[1].getEnd()) {
					return this;
				}
				CoordComponent adjusted = theComponents[1].adjust(amount);
				if (adjusted == theComponents[1]) {
					return this;
				} else if (adjusted.getEnd() == theComponents[1].getEnd()) {
					return new AdjustableCoords(theComponents[0], adjusted, theComponents[2]);
				} else {
					int digitChange = adjusted.getEnd() - theComponents[1].getEnd();
					return new AdjustableCoords(theComponents[0], adjusted, //
						new CoordComponent(2, theComponents[2].getValue(), theComponents[2].getStart() + digitChange,
							theComponents[2].getEnd() + digitChange));
				}
			} else if (position >= theComponents[0].getStart()) {
				if (position > theComponents[0].getEnd()) {
					return this;
				}
				CoordComponent adjusted = theComponents[0].adjust(amount);
				if (adjusted == theComponents[0]) {
					return this;
				} else if (adjusted.getEnd() == theComponents[0].getEnd()) {
					return new AdjustableCoords(adjusted, theComponents[1], theComponents[2]);
				} else {
					int digitChange = adjusted.getEnd() - theComponents[0].getEnd();
					return new AdjustableCoords(adjusted, //
						new CoordComponent(1, theComponents[1].getValue(), theComponents[1].getStart() + digitChange,
							theComponents[1].getEnd() + digitChange),
						new CoordComponent(2, theComponents[2].getValue(), theComponents[2].getStart() + digitChange,
							theComponents[2].getEnd() + digitChange));
				}
			} else {
				return this;
			}
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theComponents[0].getStart() > 0) {
				str.append('[');
			}
			while (str.length() < theComponents[0].getStart()) {
				str.append(' ');
			}
			str.append(theComponents[0].getValue());
			while (str.length() < theComponents[0].getEnd()) {
				str.append(' ');
			}
			str.append(':');
			while (str.length() < theComponents[1].getStart()) {
				str.append(' ');
			}
			str.append(theComponents[1].getValue());
			while (str.length() < theComponents[1].getEnd()) {
				str.append(' ');
			}
			str.append(':');
			while (str.length() < theComponents[2].getStart()) {
				str.append(' ');
			}
			str.append(theComponents[2].getValue());
			while (str.length() < theComponents[2].getEnd()) {
				str.append(' ');
			}
			str.append(']');
			return str.toString();
		}
	}

	AdjustableCoords toAdjustable(Coordinate coord) {
		int sysLen = coord.getSystem() < 10 ? 1 : (coord.getSystem() < 100 ? 2 : 3);
		int slotLen = coord.getSlot() < 10 ? 1 : 2;
		return new AdjustableCoords(//
			new CoordComponent(0, coord.getGalaxy(), 1, 2), //
			new CoordComponent(1, coord.getSystem(), 3, 3 + sysLen), //
			new CoordComponent(2, coord.getSlot(), 4 + sysLen, 4 + sysLen + slotLen));
	}

	static void adjustCoord(Coordinate coord, AdjustableCoords adjustable) {
		coord.setGalaxy(adjustable.getComponents().get(0).getValue());
		coord.setSystem(adjustable.getComponents().get(1).getValue());
		coord.setSlot(adjustable.getComponents().get(2).getValue());
	}

	final SpinnerFormat<AdjustableCoords> COORD_FORMAT = SpinnerFormat.forAdjustable(new SpinnerFormat.SimpleParser<AdjustableCoords>() {
		@Override
		public AdjustableCoords parse(CharSequence text) throws ParseException {
			String str = text.toString();
			int start = 0;
			if (str.length() > 0 && str.charAt(0) == '[') {
				start++;
			}
			while (start < str.length() && Character.isWhitespace(str.charAt(start))) {
				start++;
			}
			int colon = str.indexOf(':', start);
			if (colon < 0) {
				throw new ParseException("Colon expected", 0);
			}
			int gEnd = colon;
			while (Character.isWhitespace(str.charAt(gEnd))) {
				gEnd--;
			}
			int sysStart = colon + 1;
			while (sysStart < str.length() && Character.isWhitespace(str.charAt(sysStart))) {
				sysStart++;
			}
			int nextColon = str.indexOf(':', colon + 1);
			if (nextColon < 0) {
				throw new ParseException("Second colon expected", 0);
			}
			int sysEnd = nextColon;
			while (Character.isWhitespace(str.charAt(sysEnd - 1))) {
				sysEnd--;
			}
			int slotStart = nextColon + 1;
			while (slotStart < str.length() && Character.isWhitespace(str.charAt(slotStart))) {
				slotStart++;
			}
			int end = str.length();
			if (str.charAt(end - 1) == ']') {
				end--;
			}
			while (Character.isWhitespace(str.charAt(end - 1))) {
				end--;
			}
			String galaxyStr = str.substring(start, gEnd);
			if (galaxyStr.isEmpty()) {
				throw new ParseException("Integer expected for galaxy", start);
			}
			int galaxy;
			try {
				galaxy = Integer.parseInt(galaxyStr);
			} catch (NumberFormatException e) {
				throw new ParseException("Integer expected for galaxy, not " + galaxyStr, start);
			}
			int maxGalaxies = theUniGui.getSelectedAccount().get().getUniverse().getGalaxies();
			if (galaxy < 1) {
				throw new ParseException("Galaxy must be at least 1: " + galaxy, start);
			}
			if (galaxy > maxGalaxies) {
				throw new ParseException("Only " + maxGalaxies + " galaxies exist: " + galaxy, start);
			}

			String systemStr = str.substring(sysStart, sysEnd);
			if (systemStr.isEmpty()) {
				throw new ParseException("Integer expected for system", sysStart);
			}
			int system;
			try {
				system = Integer.parseInt(systemStr);
			} catch (NumberFormatException e) {
				throw new ParseException("Integer expected for system, not " + systemStr, sysStart);
			}
			int maxSystems = 499;
			if (system < 1) {
				throw new ParseException("System must be at least 1: " + system, sysStart);
			}
			if (system > maxSystems) {
				throw new ParseException("Only " + maxSystems + " systems exist: " + system, sysStart);
			}

			String slotStr = str.substring(slotStart, end);
			if (slotStr.isEmpty()) {
				throw new ParseException("Integer expected for slot", slotStart);
			}
			int slot;
			try {
				slot = Integer.parseInt(slotStr);
			} catch (NumberFormatException e) {
				throw new ParseException("Integer expected for system, not " + slotStr, slotStart);
			}
			int maxSlots = 15;
			if (slot < 1) {
				throw new ParseException("Slot must be at least 1: " + slot, start);
			}
			if (slot > maxSlots) {
				throw new ParseException("Only " + maxSlots + " slots exist: " + slot, slotStart);
			}
			return new AdjustableCoords(//
				new CoordComponent(0, galaxy, start, gEnd), //
				new CoordComponent(1, system, sysStart, sysEnd), //
				new CoordComponent(2, slot, slotStart, end));
		}
	});
}
